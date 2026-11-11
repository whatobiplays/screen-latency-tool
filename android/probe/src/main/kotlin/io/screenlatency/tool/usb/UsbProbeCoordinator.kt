package io.screenlatency.tool.usb

/** Platform boundary used by the probe core and deterministic host tests. */
interface UsbProbePlatform {
    fun connectedDevices(): List<UsbDeviceSnapshot>
    fun hasPermission(deviceName: String): Boolean
    fun openDevice(deviceName: String): UsbConnectionPort?
}

interface UsbConnectionPort {
    fun rawDescriptors(): ByteArray
    fun protectedStrings(): ProtectedUsbStrings
    fun claimInterface(interfaceNumber: Int, force: Boolean): Boolean
    fun releaseInterface(interfaceNumber: Int): Boolean
    fun bulkOut(endpoint: UsbEndpointSnapshot, bytes: ByteArray, timeoutMs: Int): Int
    fun bulkIn(endpoint: UsbEndpointSnapshot, maxLength: Int, timeoutMs: Int): ByteArray
    fun close()
}

class UsbCandidate internal constructor(
    val deviceName: String,
    val generation: Long,
    val token: Long,
    val snapshot: UsbDeviceSnapshot,
)

sealed interface ProbeDiscovery {
    data class Ready(val candidate: UsbCandidate) : ProbeDiscovery
    data object NoDevice : ProbeDiscovery
    data object Ambiguous : ProbeDiscovery
    data object Busy : ProbeDiscovery
    data object Closed : ProbeDiscovery
    data class Rejected(val reason: String) : ProbeDiscovery
}

sealed interface ProbeConnectResult {
    data class Connected(val session: EngineeringUsbSession) : ProbeConnectResult
    data object PermissionDenied : ProbeConnectResult
    data object Stale : ProbeConnectResult
    data class Rejected(val reason: String) : ProbeConnectResult
}

/** Enforces permission-before-identity validation and owns the claimed vendor connection. */
class UsbProbeCoordinator(
    private val platform: UsbProbePlatform,
    private val requestIdSequences: EngineeringRequestIdSequenceProvider,
) : AutoCloseable {
    private val lock = Any()
    private var generation = 0L
    private var nextToken = 1L
    private var pendingCandidate: UsbCandidate? = null
    private var connectingCandidateToken: Long? = null
    private var activeSession: EngineeringUsbSession? = null
    private var closed = false

    fun isCurrentCandidate(candidate: UsbCandidate): Boolean = synchronized(lock) {
        !closed && candidate.generation == generation && pendingCandidate?.token == candidate.token
    }

    /** Screens current public USB models before a caller requests Android USB permission. */
    fun discover(): ProbeDiscovery {
        val observedGeneration = synchronized(lock) {
            if (closed) return ProbeDiscovery.Closed
            if (connectingCandidateToken != null) return ProbeDiscovery.Busy
            if (activeSession?.isClosed == false) return ProbeDiscovery.Busy
            activeSession = null
            generation
        }
        val matching = platform.connectedDevices().filter {
            it.vendorId == FixtureIdentityValidator.VENDOR_ID && it.productId == FixtureIdentityValidator.PRODUCT_ID
        }
        synchronized(lock) {
            if (closed) return ProbeDiscovery.Closed
            if (connectingCandidateToken != null || activeSession?.isClosed == false) {
                return ProbeDiscovery.Busy
            }
            if (generation != observedGeneration) return ProbeDiscovery.Rejected("USB enumeration changed during discovery")
            if (matching.isEmpty()) return ProbeDiscovery.NoDevice
            if (matching.size != 1) return ProbeDiscovery.Ambiguous
            val device = matching.single()
            try {
                FixtureIdentityValidator.preflight(device)
            } catch (failure: FixtureIdentityViolation) {
                pendingCandidate = null
                return ProbeDiscovery.Rejected(failure.message ?: "Public USB model rejected")
            }
            val candidate = UsbCandidate(device.deviceName, generation, nextToken++, device)
            pendingCandidate = candidate
            return ProbeDiscovery.Ready(candidate)
        }
    }

    /** Completes one permission callback, then re-enumerates and validates the full device. */
    fun completePermission(candidate: UsbCandidate, granted: Boolean): ProbeConnectResult {
        val attempt = synchronized(lock) {
            if (closed || candidate.generation != generation || pendingCandidate?.token != candidate.token) {
                return ProbeConnectResult.Stale
            }
            pendingCandidate = null
            connectingCandidateToken = candidate.token
            ConnectionAttempt(candidate)
        }
        val currentCandidate = attempt.candidate
        try {
            if (!granted || !platform.hasPermission(currentCandidate.deviceName)) {
                return ProbeConnectResult.PermissionDenied
            }

            val currentDevices = platform.connectedDevices().filter { it.deviceName == currentCandidate.deviceName }
            if (currentDevices.size != 1 || currentDevices.single() != currentCandidate.snapshot) {
                return ProbeConnectResult.Stale
            }

            val connection = platform.openDevice(currentCandidate.deviceName)
                ?: return ProbeConnectResult.Rejected("Android could not open the USB device")
            var interfaceClaimed = false
            try {
                val identity = FixtureIdentityValidator.validate(
                    currentCandidate.snapshot,
                    connection.rawDescriptors(),
                    connection.protectedStrings(),
                )
                val requestIdSequence = requestIdSequences.forFixture(identity.serial)
                if (!connection.claimInterface(FixtureIdentityValidator.VENDOR_INTERFACE, false)) {
                    throw IllegalStateException("Could not claim vendor interface 1")
                }
                interfaceClaimed = true

                val vendorInterface = currentCandidate.snapshot.interfaces.single {
                    it.id == FixtureIdentityValidator.VENDOR_INTERFACE
                }
                val session = EngineeringUsbSession(
                    identity = identity,
                    connection = connection,
                    bulkOutEndpoint = vendorInterface.endpoints.single { it.address == 0x02 },
                    bulkInEndpoint = vendorInterface.endpoints.single { it.address == 0x83 },
                    requestIds = requestIdSequence,
                )
                val accepted = synchronized(lock) {
                    if (closed || generation != currentCandidate.generation ||
                        connectingCandidateToken != currentCandidate.token
                    ) {
                        false
                    } else {
                        activeSession = session
                        true
                    }
                }
                if (!accepted) {
                    session.invalidate()
                    return ProbeConnectResult.Stale
                }
                return ProbeConnectResult.Connected(session)
            } catch (failure: Exception) {
                cleanup(connection, interfaceClaimed)
                return ProbeConnectResult.Rejected(failure.message ?: failure.javaClass.simpleName)
            }
        } catch (failure: Exception) {
            return ProbeConnectResult.Rejected(failure.message ?: failure.javaClass.simpleName)
        } finally {
            synchronized(lock) {
                if (connectingCandidateToken == currentCandidate.token) {
                    connectingCandidateToken = null
                }
            }
        }
    }

    /** Invalidates candidates and sessions without changing the persisted request-ID high-water mark. */
    fun onUsbAttached() = invalidate()

    /** A detach invalidates the logical session; firmware request IDs remain monotonic across it. */
    fun onUsbDetached() = invalidate()

    /** Initializes only missing request-ID state after an owner-confirmed fixture firmware restart. */
    fun initializeMissingRequestIdsAfterOwnerConfirmedFirmwareRestart(serial: String): Boolean =
        synchronized(lock) {
            val session = activeSession ?: return@synchronized false
            if (closed || session.isClosed || session.identity.serial != serial) return@synchronized false
            requestIdSequences.initializeMissingAfterOwnerConfirmedFirmwareRestart(serial)
        }

    /** Cancels a pending permission request or active pulse session. */
    fun cancel() = invalidate()

    override fun close() {
        synchronized(lock) { closed = true }
        invalidate()
    }

    private fun invalidate() {
        val session = synchronized(lock) {
            generation++
            pendingCandidate = null
            // Keep connection admission closed until the in-flight attempt closes any stale session.
            activeSession.also { activeSession = null }
        }
        session?.invalidate()
    }

    private data class ConnectionAttempt(val candidate: UsbCandidate)

    private fun cleanup(connection: UsbConnectionPort, interfaceClaimed: Boolean) {
        if (interfaceClaimed) runCatching { connection.releaseInterface(FixtureIdentityValidator.VENDOR_INTERFACE) }
        runCatching { connection.close() }
    }
}
