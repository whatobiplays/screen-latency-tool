package io.screenlatency.tool.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal const val USB_PERMISSION_URI_SCHEME = "slt-usb-permission"

/** Android maps fixture HID Button usage 1 to KEYCODE_BUTTON_A. */
internal const val FIXTURE_BUTTON_ANDROID_KEY_CODE = KeyEvent.KEYCODE_BUTTON_A

internal fun usbPermissionBroadcastMatches(
    expectedAction: String,
    action: String?,
    dataScheme: String?,
): Boolean = action == expectedAction && dataScheme == USB_PERMISSION_URI_SCHEME

/** Reports one pulse's state changes and terminal result on the main thread. */
interface EngineeringPulseListener {
    fun onProgress(update: PulseUpdate, information: EngineeringInfo?)
    fun onFinished(result: Result<PulseRunResult>)
    fun onSequenceAccepted(sequence: Long) = Unit
    fun onGamepadEventDiagnostics(result: GamepadEventResult) = Unit
}

/** Reports a bounded capture and its verified archive chunks on the main thread. */
interface EngineeringCaptureListener {
    fun onCapturePrepared(reference: EngineeringCaptureReference) = Unit
    fun onCaptureAccepted(reference: EngineeringCaptureReference) = Unit
    fun onCaptureReadyForStimulus(reference: EngineeringCaptureReference) = Unit
    fun onChunkReceived(reference: EngineeringCaptureReference, offset: Long, bytes: ByteArray) = Unit
    fun onFinished(result: Result<EngineeringCaptureRunResult>)
}

/** Android 11+ adapter for permission, descriptor validation, vendor transport, and diagnostics. */
class AndroidUsbVendorProbe(
    context: Context,
    private val worker: ExecutorService = Executors.newSingleThreadExecutor(),
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val platform = AndroidUsbProbePlatform(usbManager)
    private val requestIdSequences = EngineeringRequestIdSequenceProvider { serial ->
        AndroidEngineeringRequestIdStateStore(appContext, serial)
    }
    private val captureReferences = AndroidEngineeringCaptureReferenceStore(appContext)
    private val coordinator = UsbProbeCoordinator(platform, requestIdSequences)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateLock = Any()
    private val permissionAction = "${appContext.packageName}.USB_PERMISSION.${UUID.randomUUID()}"
    private val gamepadEventMatcher = GamepadEventMatcher(FIXTURE_BUTTON_ANDROID_KEY_CODE)
    private var pendingPermission: PendingPermission? = null
    private var selectedCandidate: UsbCandidate? = null
    private var selectedIdentity: ValidatedFixture? = null
    private var activeSession: EngineeringUsbSession? = null
    private var activeGamepadSequence: Long? = null
    private var closed = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val received = intent ?: return
            if (!usbPermissionBroadcastMatches(permissionAction, received.action, received.data?.scheme)) return
            completePermissionBroadcast(received)
        }
    }

    private val usbEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val device = runCatching { usbDeviceExtra(intent) }.getOrNull()
            val (selectedNames, selectedVendorId, selectedProductId) = synchronized(stateLock) {
                val candidate = pendingPermission?.candidate ?: selectedCandidate
                Triple(
                    setOfNotNull(candidate?.deviceName, selectedIdentity?.deviceName),
                    selectedIdentity?.vendorId ?: candidate?.snapshot?.vendorId,
                    selectedIdentity?.productId ?: candidate?.snapshot?.productId,
                )
            }
            if (!UsbEventIdentityPolicy.mayAffectSelectedFixture(
                    eventDeviceName = device?.deviceName,
                    eventVendorId = device?.vendorId,
                    eventProductId = device?.productId,
                    selectedDeviceNames = selectedNames,
                    selectedVendorId = selectedVendorId,
                    selectedProductId = selectedProductId,
                )
            ) {
                return
            }
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> invalidateForReenumeration(attached = true)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> invalidateForReenumeration(attached = false)
            }
        }
    }

    private var permissionReceiverRegistered = false
    private var usbEventReceiverRegistered = false

    init {
        registerReceiverCompat(
            permissionReceiver,
            IntentFilter(permissionAction).apply { addDataScheme(USB_PERMISSION_URI_SCHEME) },
            Context.RECEIVER_NOT_EXPORTED,
        )
        permissionReceiverRegistered = true
        try {
            registerReceiverCompat(
                usbEventReceiver,
                IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED).apply {
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                },
                Context.RECEIVER_EXPORTED,
            )
            usbEventReceiverRegistered = true
        } catch (failure: Exception) {
            appContext.unregisterReceiver(permissionReceiver)
            permissionReceiverRegistered = false
            throw failure
        }
    }

    /** Screens public VID/PID and endpoint models before asking Android for USB permission. */
    fun discover(): ProbeDiscovery {
        synchronized(stateLock) {
            if (closed) return ProbeDiscovery.Closed
            if (pendingPermission != null) return ProbeDiscovery.Busy
        }
        val discovery = coordinator.discover()
        if (discovery is ProbeDiscovery.Ready) {
            synchronized(stateLock) {
                if (!closed && coordinator.isCurrentCandidate(discovery.candidate)) {
                    selectedCandidate = discovery.candidate
                    selectedIdentity = null
                }
            }
        }
        return discovery
    }

    /** Requests the system USB permission, then validates the complete protected identity. */
    fun requestPermission(candidate: UsbCandidate, callback: (ProbeConnectResult) -> Unit) {
        if (!coordinator.isCurrentCandidate(candidate)) {
            dispatch { callback(ProbeConnectResult.Stale) }
            return
        }
        synchronized(stateLock) {
            if (closed) {
                dispatch { callback(ProbeConnectResult.Stale) }
                return
            }
            if (pendingPermission != null) {
                dispatch { callback(ProbeConnectResult.Rejected("A USB permission request is already pending")) }
                return
            }
            pendingPermission = PendingPermission(candidate, callback)
            selectedCandidate = candidate
            selectedIdentity = null
        }

        try {
            val device = usbManager.deviceList.values.singleOrNull { it.deviceName == candidate.deviceName }
            val currentSnapshot = platform.connectedDevices().singleOrNull { it.deviceName == candidate.deviceName }
            if (device == null || currentSnapshot != candidate.snapshot || !coordinator.isCurrentCandidate(candidate)) {
                coordinator.completePermission(candidate, granted = false)
                finishPending(candidate, ProbeConnectResult.Stale)
                return
            }

            val permissionIntent = PendingIntent.getBroadcast(
                appContext,
                0,
                Intent(permissionAction)
                    .setPackage(appContext.packageName)
                    .setData(permissionRequestUri(candidate)),
                usbPermissionPendingIntentFlags(Build.VERSION.SDK_INT),
            )
            val stillPending = synchronized(stateLock) {
                pendingPermission?.takeIf { it.candidate.sameRequest(candidate) }?.let {
                    it.pendingIntent = permissionIntent
                    true
                } ?: false
            }
            if (!stillPending || !coordinator.isCurrentCandidate(candidate)) {
                permissionIntent.cancel()
                coordinator.completePermission(candidate, granted = false)
                finishPending(candidate, ProbeConnectResult.Stale)
                return
            }
            usbManager.requestPermission(device, permissionIntent)
        } catch (failure: Exception) {
            runCatching { coordinator.completePermission(candidate, granted = false) }
            finishPending(candidate, ProbeConnectResult.Rejected(failure.message ?: "USB permission request failed"))
        }
    }

    /** Runs one pulse away from the UI thread; all callbacks return on the main thread. */
    fun runEngineeringPulse(durationMs: Int, listener: EngineeringPulseListener) {
        val session = synchronized(stateLock) { activeSession }
        if (session == null) {
            dispatch { listener.onFinished(Result.success(PulseRunResult.Invalidated)) }
            return
        }
        try {
            worker.execute {
                val captureArmed = synchronized(stateLock) {
                    if (closed || activeSession !== session) false else {
                        activeGamepadSequence = null
                        gamepadEventMatcher.arm(SystemClock.uptimeMillis())
                        true
                    }
                }
                val result = if (!captureArmed) {
                    Result.success(PulseRunResult.Invalidated)
                } else {
                    runCatching {
                        session.runEngineeringPulse(
                            durationMs = durationMs,
                            onSequenceAccepted = { sequence ->
                                val accepted = synchronized(stateLock) {
                                    if (closed || activeSession !== session) false else {
                                        gamepadEventMatcher.begin(sequence)
                                        activeGamepadSequence = sequence
                                        true
                                    }
                                }
                                if (accepted) {
                                    dispatch {
                                        val stillActive = synchronized(stateLock) {
                                            activeSession === session && activeGamepadSequence == sequence
                                        }
                                        if (stillActive) listener.onSequenceAccepted(sequence)
                                    }
                                }
                            },
                            onProgress = { update, information ->
                                dispatch { listener.onProgress(update, information) }
                            },
                        )
                    }
                }
                val gamepadResult = synchronized(stateLock) {
                    if (activeSession !== session) {
                        null
                    } else {
                        val sequence = activeGamepadSequence
                        activeGamepadSequence = null
                        if (sequence == null) {
                            gamepadEventMatcher.reset()
                            null
                        } else {
                            gamepadEventMatcher.finish(sequence).also { gamepadEventMatcher.reset() }
                        }
                    }
                }
                gamepadResult?.let { diagnostic ->
                    dispatch { listener.onGamepadEventDiagnostics(diagnostic) }
                }
                if (session.isClosed) {
                    synchronized(stateLock) {
                        if (activeSession === session) activeSession = null
                    }
                }
                dispatch { listener.onFinished(result) }
            }
        } catch (failure: Exception) {
            dispatch { listener.onFinished(Result.failure(failure)) }
        }
    }

    /** Arms one engineer-only capture, stays quiet during acquisition, and retrieves the verified archive. */
    fun runEngineeringCapture(
        polarity: Int,
        rateStage: Int,
        timeoutMs: Long,
        listener: EngineeringCaptureListener,
    ) {
        val session = synchronized(stateLock) { activeSession }
        if (session == null) {
            dispatch {
                listener.onFinished(Result.success(
                    EngineeringCaptureRunResult.Rejected(ProtocolStatus.INVALID_STATE),
                ))
            }
            return
        }
        try {
            worker.execute {
                var attemptedReference: EngineeringCaptureReference? = null
                val result = runCatching {
                    val retained = captureReferences.load(session.identity.serial)
                    if (retained != null) {
                        throw IllegalStateException(
                            "Retrieve and release the retained capture archive before starting another run",
                        )
                    }
                    session.runEngineeringCapture(
                        polarity = polarity,
                        rateStage = rateStage,
                        timeoutMs = timeoutMs,
                        onCapturePrepared = { reference ->
                            if (!captureReferences.save(reference)) {
                                throw IllegalStateException(
                                    "Could not persist capture identity; no BEGIN_CAPTURE was sent",
                                )
                            }
                            attemptedReference = reference
                            dispatch { listener.onCapturePrepared(reference) }
                        },
                        onCaptureAccepted = { reference ->
                            if (captureReferences.save(reference)) {
                                attemptedReference = reference
                            }
                            dispatch { listener.onCaptureAccepted(reference) }
                        },
                        onCaptureReadyForStimulus = { reference ->
                            dispatch { listener.onCaptureReadyForStimulus(reference) }
                        },
                        onCaptureWindowElapsed = { reference ->
                            if (captureReferences.save(reference)) attemptedReference = reference
                        },
                        onChunkReceived = { reference, offset, bytes ->
                            dispatch { listener.onChunkReceived(reference, offset, bytes.copyOf()) }
                        },
                    )
                }
                val value = result.getOrNull()
                when (value) {
                    EngineeringCaptureRunResult.Unsupported,
                    is EngineeringCaptureRunResult.Rejected -> attemptedReference?.let {
                        captureReferences.clear(it.fixtureSerial, it.nonce)
                    }
                    EngineeringCaptureRunResult.Unavailable -> attemptedReference?.let {
                        // The full quiet capture window elapsed and the fixture
                        // reported no retained archive. Keep request-ID high-water
                        // state intact; only discard this archive reference.
                        captureReferences.clear(it.fixtureSerial, it.nonce)
                    }
                    is EngineeringCaptureRunResult.Interrupted ->
                        value.reference?.let(captureReferences::save)
                    else -> Unit
                }
                synchronized(stateLock) {
                    if (activeSession === session && session.isClosed) activeSession = null
                }
                dispatch { listener.onFinished(result) }
            }
        } catch (failure: Exception) {
            dispatch { listener.onFinished(Result.failure(failure)) }
        }
    }

    /** Resumes retrieval from a persisted fixture and archive reference after interruption. */
    fun retrieveEngineeringCapture(
        reference: EngineeringCaptureReference,
        listener: EngineeringCaptureListener,
    ) {
        val session = synchronized(stateLock) { activeSession }
        if (session == null) {
            dispatch {
                listener.onFinished(Result.failure(
                    IllegalStateException("Connect to the matching fixture before retrieving its archive"),
                ))
            }
            return
        }
        try {
            worker.execute {
                var retainedReference: EngineeringCaptureReference? = null
                val result: Result<EngineeringCaptureRunResult> = try {
                    val retained = captureReferences.load(reference.fixtureSerial)
                        ?: throw IllegalStateException("No retained capture archive reference exists for this fixture")
                    if (retained.fixtureSerial != session.identity.serial ||
                        retained.nonce != reference.nonce ||
                        retained.polarity != reference.polarity ||
                        retained.rateStage != reference.rateStage ||
                        retained.timeoutMs != reference.timeoutMs ||
                        (reference.archiveGeneration != 0L &&
                            reference.archiveGeneration != retained.archiveGeneration) ||
                        (reference.sessionGeneration != 0L &&
                            reference.sessionGeneration != retained.sessionGeneration)
                    ) {
                        throw ProtocolViolation("Capture reference does not match the retained fixture archive")
                    }
                    retainedReference = retained
                    var elapsedReference = retained
                    val archive = session.retrieveEngineeringCapture(
                        // The persisted boundary is authoritative. A caller's stale
                        // object cannot skip the quiet wait after reconnect.
                        reference = retained,
                        onCaptureWindowElapsed = { updated ->
                            elapsedReference = updated
                            captureReferences.save(updated)
                        },
                        onChunkReceived = { checkedReference, offset, bytes ->
                            dispatch {
                                listener.onChunkReceived(checkedReference, offset, bytes.copyOf())
                            }
                        },
                    )
                    val resolved = elapsedReference.copy(
                        archiveGeneration = archive.generation,
                        sessionGeneration = archive.sessionGeneration,
                    )
                    captureReferences.save(resolved)
                    Result.success(
                        if (archive.complete) EngineeringCaptureRunResult.Complete(archive)
                        else EngineeringCaptureRunResult.Incomplete(archive),
                    )
                } catch (_: EngineeringCaptureArchiveUnavailableException) {
                    val retained = retainedReference
                    if (retained != null && captureReferences.clear(retained.fixtureSerial, retained.nonce)) {
                        Result.success(EngineeringCaptureRunResult.Unavailable)
                    } else {
                        Result.failure(IllegalStateException("Could not clear the unavailable capture reference"))
                    }
                } catch (failure: Exception) {
                    Result.failure(failure)
                }
                synchronized(stateLock) {
                    if (activeSession === session && session.isClosed) activeSession = null
                }
                dispatch { listener.onFinished(result) }
            }
        } catch (failure: Exception) {
            dispatch { listener.onFinished(Result.failure(failure)) }
        }
    }

    /** Returns the last archive reference retained for a validated fixture serial. */
    fun retainedEngineeringCaptureReference(serial: String): EngineeringCaptureReference? =
        captureReferences.load(serial)

    /** Releases the fixture's single archive after the caller has stored the validated raw bytes. */
    fun releaseEngineeringCapture(
        reference: EngineeringCaptureReference,
        callback: (Result<Boolean>) -> Unit,
    ) {
        val session = synchronized(stateLock) { activeSession }
        if (session == null) {
            dispatch { callback(Result.failure(
                IllegalStateException("Connect to the matching fixture before releasing its archive"),
            )) }
            return
        }
        try {
            worker.execute {
                val result = runCatching {
                    val released = session.releaseEngineeringCapture(reference)
                    if (released) captureReferences.clear(reference.fixtureSerial, reference.nonce)
                    released
                }
                synchronized(stateLock) {
                    if (activeSession === session && session.isClosed) activeSession = null
                }
                dispatch { callback(result) }
            }
        } catch (failure: Exception) {
            dispatch { callback(Result.failure(failure)) }
        }
    }

    /** Initializes missing request-ID state after an explicit owner confirmation of a full fixture restart. */
    internal fun initializeMissingRequestIdsAfterOwnerConfirmedFirmwareRestart(): Boolean {
        val serial = synchronized(stateLock) {
            if (closed || activeSession?.isClosed != false) null else selectedIdentity?.serial
        } ?: return false
        return coordinator.initializeMissingRequestIdsAfterOwnerConfirmedFirmwareRestart(serial)
    }

    /** Classifies a system key event from a fresh input-device snapshot. */
    fun associateGamepadEvent(event: InputEvent): GamepadAssociation {
        val fixture = synchronized(stateLock) { selectedIdentity } ?: return GamepadAssociation.Ambiguous
        val currentDevices = InputDevice.getDeviceIds().toList().mapNotNull { id ->
            InputDevice.getDevice(id)?.let { device ->
                InputDeviceSnapshot(
                    deviceId = device.id,
                    name = device.name,
                    vendorId = device.vendorId.takeIf { it != 0 },
                    productId = device.productId.takeIf { it != 0 },
                    sources = device.sources,
                    isVirtual = device.isVirtual,
                    isExternal = device.isExternal,
                )
            }
        }
        val keyEvent = event as? KeyEvent
        return GamepadIdentityGuard.associate(
            InputEventSnapshot(
                deviceId = event.deviceId,
                source = event.source,
                isKeyEvent = keyEvent != null,
                isFromSystem = keyEvent?.let {
                    it.flags and KeyEvent.FLAG_FROM_SYSTEM != 0
                } == true,
            ),
            currentDevices,
            fixture,
        )
    }

    /** Captures a gamepad event for the active pulse; the result is not timing evidence. */
    fun observeGamepadEvent(event: InputEvent): GamepadEventResult {
        if (!GamepadIdentityGuard.isGamepadEventSource(event.source)) {
            return GamepadEventResult.Ignored(GamepadEventIssue.UNSUPPORTED_EVENT)
        }
        val association = associateGamepadEvent(event)
        val keyEvent = event as? KeyEvent
        val snapshot = keyEvent?.let {
            GamepadKeyEventSnapshot(
                action = when (it.action) {
                    KeyEvent.ACTION_DOWN -> GamepadKeyAction.DOWN
                    KeyEvent.ACTION_UP -> GamepadKeyAction.UP
                    else -> GamepadKeyAction.OTHER
                },
                keyCode = it.keyCode,
                repeatCount = it.repeatCount,
            )
        }
        return synchronized(stateLock) {
            gamepadEventMatcher.observeCaptured(event.eventTime, association, snapshot)
        }
    }

    /** Cancels a pending permission request or pulse and releases any active vendor claim. */
    fun cancel() {
        val pending = synchronized(stateLock) {
            selectedCandidate = null
            selectedIdentity = null
            activeSession = null
            clearGamepadEventStateLocked()
            pendingPermission.also { pendingPermission = null }
        }
        runCatching { pending?.pendingIntent?.cancel() }
        coordinator.cancel()
        pending?.let { dispatch { it.callback(ProbeConnectResult.Stale) } }
    }

    /** Unregisters receivers and closes all USB resources. */
    override fun close() {
        val wasClosed = synchronized(stateLock) {
            if (closed) true else {
                closed = true
                false
            }
        }
        if (wasClosed) return
        cancel()
        if (permissionReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(permissionReceiver) }
            permissionReceiverRegistered = false
        }
        if (usbEventReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(usbEventReceiver) }
            usbEventReceiverRegistered = false
        }
        synchronized(stateLock) {
            selectedCandidate = null
            selectedIdentity = null
        }
        coordinator.close()
        worker.shutdownNow()
    }

    private fun completePermissionBroadcast(intent: Intent) {
        val pending = synchronized(stateLock) {
            pendingPermission?.takeIf { intent.data == permissionRequestUri(it.candidate) }
                ?.also { pendingPermission = null }
        } ?: return
        runCatching { pending.pendingIntent?.cancel() }
        val isSameDevice: Boolean
        val granted: Boolean
        try {
            isSameDevice = usbDeviceExtra(intent)?.deviceName == pending.candidate.deviceName
            granted = isSameDevice && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
        } catch (failure: Exception) {
            coordinator.completePermission(pending.candidate, granted = false)
            dispatch { pending.callback(ProbeConnectResult.Rejected(failure.message ?: "USB permission response was invalid")) }
            return
        }
        try {
            worker.execute {
                val result = try {
                    if (isSameDevice) {
                        coordinator.completePermission(pending.candidate, granted)
                    } else {
                        coordinator.completePermission(pending.candidate, granted = false)
                        ProbeConnectResult.Stale
                    }
                } catch (failure: Exception) {
                    ProbeConnectResult.Rejected(failure.message ?: "USB connection failed")
                }
                if (result is ProbeConnectResult.Connected) {
                    val accepted = synchronized(stateLock) {
                        if (closed || result.session.isClosed) {
                            false
                        } else {
                            activeSession = result.session
                            selectedCandidate = pending.candidate
                            selectedIdentity = result.session.identity
                            true
                        }
                    }
                    if (!accepted) result.session.invalidate()
                }
                val finalResult = if (result is ProbeConnectResult.Connected && result.session.isClosed) {
                    ProbeConnectResult.Stale
                } else result
                dispatch { pending.callback(finalResult) }
            }
        } catch (failure: Exception) {
            runCatching { coordinator.completePermission(pending.candidate, granted = false) }
            dispatch { pending.callback(ProbeConnectResult.Rejected(failure.message ?: "USB connection failed")) }
        }
    }

    private fun invalidateForReenumeration(attached: Boolean) {
        val pending = synchronized(stateLock) {
            selectedCandidate = null
            selectedIdentity = null
            activeSession = null
            clearGamepadEventStateLocked()
            pendingPermission.also { pendingPermission = null }
        }
        if (attached) coordinator.onUsbAttached() else coordinator.onUsbDetached()
        runCatching { pending?.pendingIntent?.cancel() }
        pending?.let { dispatch { it.callback(ProbeConnectResult.Stale) } }
    }

    private fun finishPending(candidate: UsbCandidate, result: ProbeConnectResult) {
        val pending = synchronized(stateLock) {
            pendingPermission?.takeIf { it.candidate.sameRequest(candidate) }
                ?.also { pendingPermission = null }
        }
        runCatching { pending?.pendingIntent?.cancel() }
        pending?.let { dispatch { it.callback(result) } }
    }

    private fun permissionRequestUri(candidate: UsbCandidate): Uri = Uri.Builder()
        .scheme(USB_PERMISSION_URI_SCHEME)
        .authority(appContext.packageName)
        .appendPath(candidate.generation.toString())
        .appendPath(candidate.token.toString())
        .build()

    private fun clearGamepadEventStateLocked() {
        activeGamepadSequence = null
        gamepadEventMatcher.reset()
    }

    private fun dispatch(block: () -> Unit) {
        mainHandler.post(block)
    }

    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter, flags: Int) {
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, flags)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
    }

    @Suppress("DEPRECATION")
    private fun usbDeviceExtra(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
        }

    private data class PendingPermission(
        val candidate: UsbCandidate,
        val callback: (ProbeConnectResult) -> Unit,
        var pendingIntent: PendingIntent? = null,
    )

    private fun UsbCandidate.sameRequest(other: UsbCandidate): Boolean =
        generation == other.generation && token == other.token
}

internal fun usbPermissionPendingIntentFlags(sdkInt: Int): Int =
    PendingIntent.FLAG_UPDATE_CURRENT or
        if (sdkInt >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

private class AndroidUsbProbePlatform(private val manager: UsbManager) : UsbProbePlatform {
    override fun connectedDevices(): List<UsbDeviceSnapshot> = manager.deviceList.values.map { it.snapshot() }

    override fun hasPermission(deviceName: String): Boolean = manager.deviceList.values
        .singleOrNull { it.deviceName == deviceName }
        ?.let(manager::hasPermission)
        ?: false

    override fun openDevice(deviceName: String): UsbConnectionPort? {
        val device = manager.deviceList.values.singleOrNull { it.deviceName == deviceName } ?: return null
        val usbInterface = (0 until device.interfaceCount)
            .map(device::getInterface)
            .singleOrNull { it.id == FixtureIdentityValidator.VENDOR_INTERFACE }
            ?: return null
        val connection = manager.openDevice(device) ?: return null
        return AndroidUsbConnectionPort(device, usbInterface, connection)
    }

    private fun UsbDevice.snapshot(): UsbDeviceSnapshot = UsbDeviceSnapshot(
        deviceName = deviceName,
        vendorId = vendorId,
        productId = productId,
        configurationCount = configurationCount,
        interfaces = (0 until interfaceCount).map { index -> getInterface(index).snapshot() },
    )

    private fun UsbInterface.snapshot(): UsbInterfaceSnapshot = UsbInterfaceSnapshot(
        id = id,
        alternateSetting = alternateSetting,
        interfaceClass = interfaceClass,
        interfaceSubclass = interfaceSubclass,
        interfaceProtocol = interfaceProtocol,
        endpoints = (0 until endpointCount).map { index -> getEndpoint(index).snapshot() },
    )

    private fun UsbEndpoint.snapshot(): UsbEndpointSnapshot = UsbEndpointSnapshot(
        address = address,
        transferType = type,
        maxPacketSize = maxPacketSize,
        interval = interval,
    )
}

private class AndroidUsbConnectionPort(
    private val device: UsbDevice,
    private val vendorInterface: UsbInterface,
    private val connection: UsbDeviceConnection,
) : UsbConnectionPort {
    override fun rawDescriptors(): ByteArray = connection.rawDescriptors

    override fun protectedStrings() = ProtectedUsbStrings(
        manufacturer = device.manufacturerName,
        product = device.productName,
        serial = connection.serial,
    )

    override fun claimInterface(interfaceNumber: Int, force: Boolean): Boolean =
        interfaceNumber == FixtureIdentityValidator.VENDOR_INTERFACE &&
            connection.claimInterface(vendorInterface, force)

    override fun releaseInterface(interfaceNumber: Int): Boolean =
        interfaceNumber == FixtureIdentityValidator.VENDOR_INTERFACE && connection.releaseInterface(vendorInterface)

    override fun bulkOut(endpoint: UsbEndpointSnapshot, bytes: ByteArray, timeoutMs: Int): Int =
        connection.bulkTransfer(findEndpoint(endpoint), bytes, bytes.size, timeoutMs)

    override fun bulkIn(endpoint: UsbEndpointSnapshot, maxLength: Int, timeoutMs: Int): ByteArray {
        val buffer = ByteArray(maxLength)
        val transferred = connection.bulkTransfer(findEndpoint(endpoint), buffer, maxLength, timeoutMs)
        if (transferred <= 0) return byteArrayOf()
        if (transferred > buffer.size) throw IllegalStateException("Android returned an invalid USB transfer length")
        return buffer.copyOf(transferred)
    }

    override fun close() = connection.close()

    private fun findEndpoint(snapshot: UsbEndpointSnapshot): UsbEndpoint =
        (0 until vendorInterface.endpointCount)
            .map(vendorInterface::getEndpoint)
            .singleOrNull { endpoint ->
                endpoint.address == snapshot.address &&
                    endpoint.type == snapshot.transferType &&
                    endpoint.maxPacketSize == snapshot.maxPacketSize &&
                    endpoint.interval == snapshot.interval
            }
            ?: throw IllegalStateException("Validated USB endpoint is no longer present")
}
