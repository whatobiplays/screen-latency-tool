package io.screenlatency.tool.usb

data class InputDeviceSnapshot(
    val deviceId: Int,
    val name: String,
    val vendorId: Int?,
    val productId: Int?,
    val sources: Int,
    val isVirtual: Boolean,
    val isExternal: Boolean,
)

data class InputEventSnapshot(
    val deviceId: Int,
    val source: Int,
    val isKeyEvent: Boolean,
    val isFromSystem: Boolean,
)

sealed interface GamepadAssociation {
    data class Matched(val deviceId: Int) : GamepadAssociation
    data object WrongDevice : GamepadAssociation
    data object Ambiguous : GamepadAssociation
    data object IgnoredMotion : GamepadAssociation
}

/** Associates a system key event with one unique external fixture-like input device. */
object GamepadIdentityGuard {
    const val SOURCE_GAMEPAD = 0x00000401
    const val SOURCE_JOYSTICK = 0x01000010

    /**
     * Requires a system key event and a unique external device whose ID, source,
     * VID/PID, and product name agree with the selected USB fixture. A match is
     * only a logical association; it does not prove physical USB provenance.
     */
    fun associate(
        event: InputEventSnapshot,
        connectedDevices: List<InputDeviceSnapshot>,
        fixture: ValidatedFixture,
    ): GamepadAssociation {
        if (!event.isKeyEvent && isGamepadEventSource(event.source)) {
        return GamepadAssociation.IgnoredMotion
    }
        if (event.deviceId <= 0 || !event.isKeyEvent || !event.isFromSystem ||
            fixture.vendorId != FixtureIdentityValidator.VENDOR_ID ||
            fixture.productId != FixtureIdentityValidator.PRODUCT_ID
        ) {
            return GamepadAssociation.Ambiguous
        }

        val eventDevices = connectedDevices.filter { it.deviceId == event.deviceId }
        if (eventDevices.size != 1) return GamepadAssociation.Ambiguous

        val fixtureDevices = connectedDevices.filter { device ->
            device.vendorId == fixture.vendorId &&
                device.productId == fixture.productId &&
                supportsGamepadSource(device.sources)
        }
        if (fixtureDevices.size > 1) return GamepadAssociation.Ambiguous
        val selected = fixtureDevices.singleOrNull() ?: return GamepadAssociation.WrongDevice

        if (selected.isVirtual || !selected.isExternal) return GamepadAssociation.Ambiguous
        if (selected.deviceId != event.deviceId) return GamepadAssociation.WrongDevice
        if (selected.name != fixture.product) return GamepadAssociation.Ambiguous
        if (!supportsEventSource(selected.sources, event.source)) return GamepadAssociation.WrongDevice
        return GamepadAssociation.Matched(selected.deviceId)
    }

    fun isGamepadEventSource(source: Int): Boolean =
        hasSource(source, SOURCE_GAMEPAD) || hasSource(source, SOURCE_JOYSTICK)

    private fun supportsGamepadSource(sources: Int): Boolean =
        isGamepadEventSource(sources)

    private fun supportsEventSource(deviceSources: Int, eventSource: Int): Boolean =
    (hasSource(eventSource, SOURCE_GAMEPAD) && hasSource(deviceSources, SOURCE_GAMEPAD)) ||
        (hasSource(eventSource, SOURCE_JOYSTICK) && hasSource(deviceSources, SOURCE_JOYSTICK))

private fun hasSource(sources: Int, requiredSource: Int): Boolean =
    (sources and requiredSource) == requiredSource
}
