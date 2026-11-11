package io.screenlatency.tool.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadIdentityGuardTest {
    @Test
    fun acceptsOnlyTheUniqueExternalSystemKeyEventForTheFixtureDevice() {
        val result = GamepadIdentityGuard.associate(
            event = event(42),
            connectedDevices = listOf(inputDevice(42)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.Matched(42), result)
    }

    @Test
    fun rejectsMultipleMatchingInputDevicesAsAmbiguous() {
        val result = GamepadIdentityGuard.associate(
            event = event(42),
            connectedDevices = listOf(inputDevice(42), inputDevice(43)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.Ambiguous, result)
    }

    @Test
    fun rejectsAnEventFromAnotherInputDevice() {
        val result = GamepadIdentityGuard.associate(
            event = event(99),
            connectedDevices = listOf(inputDevice(42), inputDevice(99, vendorId = 0x1234, productId = 7)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.WrongDevice, result)
    }

    @Test
    fun marksAnUnassociatedEventAmbiguous() {
        val result = GamepadIdentityGuard.associate(
            event = event(99),
            connectedDevices = listOf(inputDevice(42)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.Ambiguous, result)
    }

    @Test
    fun rejectsVidPidMatchesWithoutTheProtectedProductName() {
        val result = GamepadIdentityGuard.associate(
            event = event(42),
            connectedDevices = listOf(inputDevice(42, name = "Unrelated Gamepad")),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.Ambiguous, result)
    }

    @Test
    fun rejectsVirtualDevicesEvenWhenTheirIdentityMatches() {
        val result = GamepadIdentityGuard.associate(
            event = event(42),
            connectedDevices = listOf(inputDevice(42, isVirtual = true)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.Ambiguous, result)
    }

    @Test
    fun rejectsInputDevicesWithoutGamepadOrJoystickSources() {
        val result = GamepadIdentityGuard.associate(
            event = event(42),
            connectedDevices = listOf(inputDevice(42, sources = 0x00000101)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.WrongDevice, result)
    }

    @Test
    fun marksDeviceZeroAsAmbiguousBecauseItDoesNotIdentifyPhysicalInput() {
        val result = GamepadIdentityGuard.associate(
            event = event(0),
            connectedDevices = listOf(inputDevice(0)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.Ambiguous, result)
    }

    @Test
    fun ignoresJoystickMotionEventsForButtonEvidence() {
        val result = GamepadIdentityGuard.associate(
            event = event(42, source = GamepadIdentityGuard.SOURCE_JOYSTICK, isKeyEvent = false),
            connectedDevices = listOf(inputDevice(42)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.IgnoredMotion, result)
    }

    @Test
    fun ignoresGamepadOnlyMotionEventsForButtonEvidence() {
        val result = GamepadIdentityGuard.associate(
            event = event(42, source = GamepadIdentityGuard.SOURCE_GAMEPAD, isKeyEvent = false),
            connectedDevices = listOf(inputDevice(42)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.IgnoredMotion, result)
    }

    @Test
    fun acceptsCompositeGamepadSourceFlagsByMembership() {
        val compositeSource = GamepadIdentityGuard.SOURCE_GAMEPAD or (1 shl 12)
        assertTrue(GamepadIdentityGuard.isGamepadEventSource(compositeSource))
        val result = GamepadIdentityGuard.associate(
            event = event(42, source = compositeSource),
            connectedDevices = listOf(inputDevice(42, sources = compositeSource)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.Matched(42), result)
    }

    @Test
    fun marksEventsWithoutTrustedSystemProvenanceAsAmbiguous() {
        val result = GamepadIdentityGuard.associate(
            event = event(42, isFromSystem = false),
            connectedDevices = listOf(inputDevice(42)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.Ambiguous, result)
    }

    @Test
    fun marksNonExternalDevicesAsAmbiguous() {
        val result = GamepadIdentityGuard.associate(
            event = event(42),
            connectedDevices = listOf(inputDevice(42, isExternal = false)),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.Ambiguous, result)
    }

    private fun inputDevice(
        deviceId: Int,
        vendorId: Int = FixtureIdentityValidator.VENDOR_ID,
        productId: Int = FixtureIdentityValidator.PRODUCT_ID,
        name: String = FixtureIdentityValidator.EXPECTED_PRODUCT,
        sources: Int = GamepadIdentityGuard.SOURCE_GAMEPAD,
        isVirtual: Boolean = false,
        isExternal: Boolean = true,
    ) = InputDeviceSnapshot(deviceId, name, vendorId, productId, sources, isVirtual, isExternal)

    private fun event(
        deviceId: Int,
        source: Int = GamepadIdentityGuard.SOURCE_GAMEPAD,
        isKeyEvent: Boolean = true,
        isFromSystem: Boolean = true,
    ) = InputEventSnapshot(deviceId, source, isKeyEvent, isFromSystem)

    private val fixture = ValidatedFixture(
        deviceName = "/dev/bus/usb/001/002",
        vendorId = FixtureIdentityValidator.VENDOR_ID,
        productId = FixtureIdentityValidator.PRODUCT_ID,
        manufacturer = FixtureIdentityValidator.EXPECTED_MANUFACTURER,
        product = FixtureIdentityValidator.EXPECTED_PRODUCT,
        serial = "0123456789ABCDEF",
    )
}
