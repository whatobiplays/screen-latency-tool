package io.screenlatency.tool

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.util.TypedValue
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.screenlatency.tool.usb.AndroidUsbVendorProbe
import io.screenlatency.tool.usb.EngineeringInfo
import io.screenlatency.tool.usb.EngineeringPulseListener
import io.screenlatency.tool.usb.GamepadEventResult
import io.screenlatency.tool.usb.GamepadIdentityGuard
import io.screenlatency.tool.usb.PulseRunResult
import io.screenlatency.tool.usb.PulseUpdate
import io.screenlatency.tool.usb.ProbeConnectResult
import io.screenlatency.tool.usb.ProbeDiscovery
import io.screenlatency.tool.usb.UsbCandidate

/** Hosts the manual USB discovery, pulse, and diagnostic screen. */
class MainActivity : Activity() {
    private lateinit var probe: AndroidUsbVendorProbe
    private lateinit var connectionStatus: TextView
    private lateinit var pulseStatus: TextView
    private lateinit var engineeringInfo: TextView
    private lateinit var gamepadStatus: TextView
    private lateinit var connectButton: Button
    private lateinit var pulseButton: Button
    private lateinit var requestIdRecoveryButton: Button
    private lateinit var cancelButton: Button

    private var connected = false
    private var connectionPending = false
    private var pulseRunning = false
    private var pulseAllowed = true
    private var operationGeneration = 0L
    private var probeClosed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        probe = AndroidUsbVendorProbe(applicationContext)
        setContentView(createContent())
        updateControls()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isGamepadEvent(event)) {
            observeGamepadEvent(event)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (isGamepadEvent(event)) {
            observeGamepadEvent(event)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onDestroy() {
        operationGeneration++
        probeClosed = true
        probe.close()
        super.onDestroy()
    }

    private fun createContent(): ScrollView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        content.addView(label("Screen Latency USB Probe", 22f, bold = true))
        content.addView(label("Issue 4 engineering smoke test", 16f, bold = true, top = 8))
        content.addView(
            label(
                "Manual diagnostic only. A pulse sends the fixture test input. Do not use a physical fixture until the owner has released the documented current-limit, protected-supply, wiring, and VUSB isolation gates. Android input events and firmware completion ticks do not qualify bus-accurate T0.",
                14f,
                top = 12,
            ),
        )

        connectionStatus = label("No USB fixture selected.", 15f, bold = true, top = 20)
        content.addView(connectionStatus)

        connectButton = button("Find and connect one fixture") {
            discoverAndConnect()
        }
        content.addView(connectButton, buttonLayout(top = 12))

        pulseButton = button("Run 10 ms engineering pulse") {
            runPulse()
        }
        content.addView(pulseButton, buttonLayout(top = 8))

        cancelButton = button("Cancel / disconnect") {
            cancelAndDisconnect()
        }
        content.addView(cancelButton, buttonLayout(top = 8))

        requestIdRecoveryButton = button("Initialize IDs after full firmware restart") {
            confirmManualRequestIdRecovery()
        }
        content.addView(requestIdRecoveryButton, buttonLayout(top = 8))
        content.addView(
            label(
                "Manual recovery requires separate owner authorization, Stage-B electrical safety release, and a complete firmware restart that clears the fixture replay cache or a full power cycle. USB reset, detach, and re-enumeration alone do not qualify.",
                13f,
                top = 4,
            ),
        )

        pulseStatus = label("Pulse: idle", 15f, bold = true, top = 20)
        content.addView(pulseStatus)
        engineeringInfo = label("ENGINEERING_INFO: not polled yet", 14f, top = 8)
        content.addView(engineeringInfo)
        gamepadStatus = label("Android gamepad event: none observed", 14f, top = 8)
        content.addView(gamepadStatus)

        return ScrollView(this).apply {
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun discoverAndConnect() {
        val generation = ++operationGeneration
        connectionPending = true
        pulseAllowed = true
        connectionStatus.text = "Checking connected USB devices…"
        updateControls()

        when (val discovery = probe.discover()) {
            is ProbeDiscovery.Ready -> requestPermission(discovery.candidate, generation)
            ProbeDiscovery.NoDevice -> finishDiscovery("No matching USB device is connected.")
            ProbeDiscovery.Ambiguous -> finishDiscovery(
                "More than one matching USB device is connected. Disconnect extras before retrying.",
            )
            ProbeDiscovery.Busy -> finishDiscovery("The USB probe is busy. Cancel the current operation and retry.")
            ProbeDiscovery.Closed -> finishDiscovery("The USB probe is closed. Restart the app to continue.")
            is ProbeDiscovery.Rejected -> finishDiscovery("USB device rejected: ${discovery.reason}")
        }
    }

    private fun requestPermission(candidate: UsbCandidate, generation: Long) {
        connectionStatus.text = "Requesting Android USB permission and validating descriptors…"
        probe.requestPermission(candidate, permissionResult@{ result ->
            if (generation != operationGeneration || probeClosed) return@permissionResult
            connectionPending = false
            when (result) {
                is ProbeConnectResult.Connected -> {
                    connected = true
                    pulseAllowed = true
                    connectionStatus.text =
                        "USB session opened after descriptor validation. VID/PID is a candidate filter; it does not prove physical identity."
                }
                ProbeConnectResult.PermissionDenied -> {
                    connected = false
                    connectionStatus.text = "Android USB permission was denied."
                }
                ProbeConnectResult.Stale -> {
                    connected = false
                    connectionStatus.text = "USB enumeration changed. The selected device identity was discarded; discover again."
                }
                is ProbeConnectResult.Rejected -> {
                    connected = false
                    connectionStatus.text = "USB connection rejected: ${result.reason}"
                }
            }
            updateControls()
        })
    }

    private fun finishDiscovery(message: String) {
        connectionPending = false
        connected = false
        connectionStatus.text = message
        updateControls()
    }

    private fun runPulse() {
        if (!connected || !pulseAllowed || pulseRunning) return
        val generation = ++operationGeneration
        pulseRunning = true
        pulseStatus.text = "Pulse: starting 10 ms request…"
        engineeringInfo.text = "ENGINEERING_INFO: waiting for the first lifecycle snapshot"
        updateControls()

        probe.runEngineeringPulse(10, object : EngineeringPulseListener {
            override fun onProgress(update: PulseUpdate, information: EngineeringInfo?) {
                if (!isCurrent(generation)) return
                pulseStatus.text = "Pulse: $update"
                if (information != null) showEngineeringInfo(information)
            }

            override fun onSequenceAccepted(sequence: Long) {
                if (!isCurrent(generation)) return
                pulseStatus.text = "Pulse sequence $sequence accepted; waiting for measured press completion."
            }

            override fun onGamepadEventDiagnostics(result: GamepadEventResult) {
                if (!isCurrent(generation)) return
                showGamepadResult(result)
            }

            override fun onFinished(result: Result<PulseRunResult>) {
                if (!isCurrent(generation)) return
                pulseRunning = false
                connected = false
                pulseAllowed = false
                connectionStatus.text = "USB session ended after this pulse. Discover again before another pulse."
                val terminal = result.getOrNull()
                if (terminal == null) {
                    val failure = result.exceptionOrNull()
                    pulseStatus.text = "USB pulse failed: ${failure?.message ?: failure?.javaClass?.simpleName}"
                    updateControls()
                    return
                }
                pulseStatus.text = "Pulse result: $terminal"
                updateControls()
            }
        })
    }

    private fun confirmManualRequestIdRecovery() {
        if (!connected || connectionPending || pulseRunning || probeClosed) return
        AlertDialog.Builder(this)
            .setTitle("Confirm complete fixture restart")
            .setMessage(
                "Continue only if you have separate owner authorization and Stage-B electrical safety release, and have completed a full firmware restart that clears this fixture's replay cache or a full power cycle. A USB reset, detach, or re-enumeration alone does not prove this reset. This initializes only missing or corrupt request-ID state to ID 1; any valid stored counter is preserved. No USB command will be sent.",
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I confirm all prerequisites") { _, _ ->
                if (!connected || connectionPending || pulseRunning || probeClosed) return@setPositiveButton
                val ready = probe.initializeMissingRequestIdsAfterOwnerConfirmedFirmwareRestart()
                connectionStatus.text = if (ready) {
                    "Request-ID state is ready for the validated fixture. No USB command was sent."
                } else {
                    "Request-ID initialization failed or no validated fixture is connected. No USB command was sent."
                }
                updateControls()
            }
            .show()
    }

    private fun cancelAndDisconnect() {
        operationGeneration++
        probe.cancel()
        connected = false
        connectionPending = false
        pulseRunning = false
        pulseAllowed = false
        connectionStatus.text = "USB session cancelled and disconnected. Discover again before another pulse."
        pulseStatus.text = "Pulse: cancelled or idle"
        engineeringInfo.text = "ENGINEERING_INFO: session ended"
        updateControls()
    }

    private fun isGamepadEvent(event: InputEvent): Boolean =
    GamepadIdentityGuard.isGamepadEventSource(event.source)

    private fun observeGamepadEvent(event: InputEvent) {
        if (!probeClosed) showGamepadResult(probe.observeGamepadEvent(event))
    }

    private fun showEngineeringInfo(info: EngineeringInfo) {
    val serial = info.serialNumber.toULong().toString(16).uppercase().padStart(16, '0')
        val tick = if (info.completionTickValid) {
        "${info.completionTick} DWT cycles at ${info.completionCounterFrequencyHz} Hz (diagnostic only)"
        } else {
            "invalid"
        }
        engineeringInfo.text = buildString {
        append("ENGINEERING_INFO: protocol ${info.protocolMajor}.${info.protocolMinor}, ")
        append("firmware ${info.firmwareMajor}.${info.firmwareMinor}, ")
        append("speed ${info.usbSpeed}, serial $serial, ")
        append("sequence ${info.sequence}, state ${info.state}, ")
            append("session ${info.usbSessionGeneration}, transfer ${info.transferGeneration}, ")
            append("Full-Speed verified ${info.fullSpeedVerified}, USB session valid ${info.usbSessionValid}, ")
            append("tick $tick, fault ${info.fault}, recovery required ${info.recoveryRequired}")
        }
    }

    private fun showGamepadResult(result: GamepadEventResult) {
        gamepadStatus.text = when (result) {
            GamepadEventResult.Pending -> "Android gamepad event: pending; diagnostic only"
            is GamepadEventResult.Ignored -> "Android gamepad event ignored: ${result.issue}"
            is GamepadEventResult.PressAccepted ->
                "Android gamepad press associated with device ${result.deviceId}, sequence ${result.sequence}; diagnostic only"
            is GamepadEventResult.ReleaseAccepted ->
                "Android gamepad release associated with device ${result.deviceId}, sequence ${result.sequence}; diagnostic only"
            is GamepadEventResult.Complete ->
                "Android gamepad pair associated with device ${result.deviceId}, sequence ${result.sequence}; diagnostic only"
            is GamepadEventResult.Rejected ->
                "Android gamepad event rejected for sequence ${result.sequence}: ${result.issue}"
        }
    }

    private fun isCurrent(generation: Long): Boolean =
        generation == operationGeneration && !probeClosed

    private fun updateControls() {
        connectButton.isEnabled = !connectionPending && !connected && !pulseRunning && !probeClosed
        pulseButton.isEnabled = connected && pulseAllowed && !connectionPending && !pulseRunning && !probeClosed
        requestIdRecoveryButton.isEnabled = connected && !connectionPending && !pulseRunning && !probeClosed
        cancelButton.isEnabled = (connectionPending || connected || pulseRunning) && !probeClosed
    }

    private fun label(text: String, sizeSp: Float, bold: Boolean = false, top: Int = 0) =
        TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (top > 0) setPadding(0, dp(top), 0, 0)
        }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
    }

    private fun buttonLayout(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = dp(top)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
