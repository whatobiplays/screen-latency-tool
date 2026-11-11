package io.screenlatency.tool.usb

import org.junit.Assert.assertEquals
import org.junit.Test

class GamepadEventMatcherTest {
    private val fixtureButtonUsageOneKeyCode = 96
    private val fixtureDeviceId = 7

    @Test
    fun acceptsOnlyOneOrderedPressAndReleaseForTheAcceptedSequence() {
        val matcher = matcherFor(sequence = 41)

        assertEquals(
            GamepadEventResult.PressAccepted(sequence = 41, deviceId = fixtureDeviceId),
            matcher.observe(41, matchedDevice(), keyEvent(GamepadKeyAction.DOWN)),
        )
        assertEquals(
            GamepadEventResult.ReleaseAccepted(sequence = 41, deviceId = fixtureDeviceId),
            matcher.observe(41, matchedDevice(), keyEvent(GamepadKeyAction.UP)),
        )
        assertEquals(GamepadEventResult.Complete(41, fixtureDeviceId), matcher.finish(41))
    }

    @Test
    fun buffersPressAndReleaseUntilTheFirmwareSequenceIsAccepted() {
        val matcher = GamepadEventMatcher(fixtureButtonUsageOneKeyCode)
        matcher.arm(captureStartedAtMillis = 100)

        assertEquals(
            GamepadEventResult.Pending,
            matcher.observeCaptured(101, matchedDevice(), keyEvent(GamepadKeyAction.DOWN)),
        )
        assertEquals(
            GamepadEventResult.Pending,
            matcher.observeCaptured(102, matchedDevice(), keyEvent(GamepadKeyAction.UP)),
        )
        matcher.begin(sequence = 56)

        assertEquals(GamepadEventResult.Complete(56, fixtureDeviceId), matcher.finish(56))
    }

    @Test
    fun ignoresJoystickMotionBeforeAndDuringPulseWithoutPoisoningButtonOrder() {
        val matcher = GamepadEventMatcher(fixtureButtonUsageOneKeyCode)
        matcher.arm(captureStartedAtMillis = 100)

        assertEquals(
            GamepadEventResult.Ignored(GamepadEventIssue.UNSUPPORTED_EVENT),
            matcher.observeCaptured(101, GamepadAssociation.IgnoredMotion, null),
        )
        assertEquals(
            GamepadEventResult.Pending,
            matcher.observeCaptured(102, matchedDevice(), keyEvent(GamepadKeyAction.DOWN)),
        )
        matcher.begin(sequence = 59)
        assertEquals(
            GamepadEventResult.Ignored(GamepadEventIssue.UNSUPPORTED_EVENT),
            matcher.observe(59, GamepadAssociation.IgnoredMotion, null),
        )
        assertEquals(
            GamepadEventResult.ReleaseAccepted(59, fixtureDeviceId),
            matcher.observe(59, matchedDevice(), keyEvent(GamepadKeyAction.UP)),
        )
        assertEquals(GamepadEventResult.Complete(59, fixtureDeviceId), matcher.finish(59))
    }

    @Test
    fun ignoresGamepadOnlyMotionDuringActiveButtonSequence() {
        val matcher = matcherFor(sequence = 60)
        assertEquals(
            GamepadEventResult.PressAccepted(60, fixtureDeviceId),
            matcher.observe(60, matchedDevice(), keyEvent(GamepadKeyAction.DOWN)),
        )
        val fixture = ValidatedFixture(
            deviceName = "/dev/fixture",
            vendorId = FixtureIdentityValidator.VENDOR_ID,
            productId = FixtureIdentityValidator.PRODUCT_ID,
            manufacturer = FixtureIdentityValidator.EXPECTED_MANUFACTURER,
            product = FixtureIdentityValidator.EXPECTED_PRODUCT,
            serial = "0123456789ABCDEF",
        )
        val device = InputDeviceSnapshot(
            deviceId = fixtureDeviceId,
            name = fixture.product,
            vendorId = fixture.vendorId,
            productId = fixture.productId,
            sources = GamepadIdentityGuard.SOURCE_GAMEPAD,
            isVirtual = false,
            isExternal = true,
        )
        val motion = GamepadIdentityGuard.associate(
            event = InputEventSnapshot(
                deviceId = fixtureDeviceId,
                source = GamepadIdentityGuard.SOURCE_GAMEPAD,
                isKeyEvent = false,
                isFromSystem = true,
            ),
            connectedDevices = listOf(device),
            fixture = fixture,
        )

        assertEquals(GamepadAssociation.IgnoredMotion, motion)
        assertEquals(
            GamepadEventResult.Ignored(GamepadEventIssue.UNSUPPORTED_EVENT),
            matcher.observe(60, motion, null),
        )
        assertEquals(
            GamepadEventResult.ReleaseAccepted(60, fixtureDeviceId),
            matcher.observe(60, matchedDevice(), keyEvent(GamepadKeyAction.UP)),
        )
        assertEquals(GamepadEventResult.Complete(60, fixtureDeviceId), matcher.finish(60))
    }

    @Test
    fun ignoresEventsCreatedBeforeTheCaptureWasArmed() {
        val matcher = GamepadEventMatcher(fixtureButtonUsageOneKeyCode)
        matcher.arm(captureStartedAtMillis = 100)

        assertEquals(
            GamepadEventResult.Ignored(GamepadEventIssue.STALE_EVENT),
            matcher.observeCaptured(99, matchedDevice(), keyEvent(GamepadKeyAction.DOWN)),
        )
        assertEquals(
            GamepadEventResult.Pending,
            matcher.observeCaptured(101, matchedDevice(), keyEvent(GamepadKeyAction.DOWN)),
        )
        matcher.begin(sequence = 57)
        assertEquals(
            GamepadEventResult.ReleaseAccepted(57, fixtureDeviceId),
            matcher.observeCaptured(102, matchedDevice(), keyEvent(GamepadKeyAction.UP)),
        )
        assertEquals(GamepadEventResult.Complete(57, fixtureDeviceId), matcher.finish(57))
    }

    @Test
    fun marksTheSequenceAmbiguousWhenThePreAcknowledgementBufferOverflows() {
        val matcher = GamepadEventMatcher(fixtureButtonUsageOneKeyCode)
        matcher.arm(captureStartedAtMillis = 100)
        repeat(9) {
            matcher.observeCaptured(101, matchedDevice(), keyEvent(GamepadKeyAction.DOWN))
        }
        matcher.begin(sequence = 58)

        assertEquals(
            GamepadEventResult.Rejected(58, GamepadEventIssue.BUFFER_OVERFLOW),
            matcher.finish(58),
        )
    }

    @Test
    fun reportsMissingPressAndMissingRelease() {
        val missingPress = matcherFor(sequence = 42)
        assertEquals(
            GamepadEventResult.Rejected(42, GamepadEventIssue.MISSING_PRESS),
            missingPress.finish(42),
        )

        val missingRelease = matcherFor(sequence = 43)
        missingRelease.observe(43, matchedDevice(), keyEvent(GamepadKeyAction.DOWN))
        assertEquals(
            GamepadEventResult.Rejected(43, GamepadEventIssue.MISSING_RELEASE),
            missingRelease.finish(43),
        )
    }

    @Test
    fun rejectsDuplicatePressReleaseAndReleaseBeforePress() {
        val duplicatePress = matcherFor(sequence = 44)
        duplicatePress.observe(44, matchedDevice(), keyEvent(GamepadKeyAction.DOWN))
        assertEquals(
            GamepadEventResult.Rejected(44, GamepadEventIssue.DUPLICATE_PRESS),
            duplicatePress.observe(44, matchedDevice(), keyEvent(GamepadKeyAction.DOWN)),
        )

        val duplicateRelease = matcherFor(sequence = 45)
        duplicateRelease.observe(45, matchedDevice(), keyEvent(GamepadKeyAction.DOWN))
        duplicateRelease.observe(45, matchedDevice(), keyEvent(GamepadKeyAction.UP))
        assertEquals(
            GamepadEventResult.Rejected(45, GamepadEventIssue.DUPLICATE_RELEASE),
            duplicateRelease.observe(45, matchedDevice(), keyEvent(GamepadKeyAction.UP)),
        )

        val releaseFirst = matcherFor(sequence = 46)
        assertEquals(
            GamepadEventResult.Rejected(46, GamepadEventIssue.OUT_OF_ORDER),
            releaseFirst.observe(46, matchedDevice(), keyEvent(GamepadKeyAction.UP)),
        )
    }

    @Test
    fun rejectsAmbiguousOrChangedFixtureIdentity() {
        val ambiguous = matcherFor(sequence = 47)
        assertEquals(
            GamepadEventResult.Rejected(47, GamepadEventIssue.AMBIGUOUS_DEVICE),
            ambiguous.observe(47, GamepadAssociation.Ambiguous, keyEvent(GamepadKeyAction.DOWN)),
        )

        val wrongDevice = matcherFor(sequence = 48)
        wrongDevice.observe(48, matchedDevice(), keyEvent(GamepadKeyAction.DOWN))
        assertEquals(
            GamepadEventResult.Rejected(48, GamepadEventIssue.WRONG_DEVICE),
            wrongDevice.observe(
                48,
                GamepadAssociation.Matched(deviceId = fixtureDeviceId + 1),
                keyEvent(GamepadKeyAction.UP),
            ),
        )
        assertEquals(
            GamepadEventResult.Rejected(49, GamepadEventIssue.WRONG_DEVICE),
            matcherFor(sequence = 49).observe(
                49,
                GamepadAssociation.WrongDevice,
                keyEvent(GamepadKeyAction.DOWN),
            ),
        )
    }

    @Test
    fun rejectsUnexpectedButtonRepeatedAndNonButtonEvents() {
        val wrongButton = matcherFor(sequence = 50)
        assertEquals(
            GamepadEventResult.Rejected(50, GamepadEventIssue.WRONG_BUTTON),
            wrongButton.observe(
                50,
                matchedDevice(),
                keyEvent(GamepadKeyAction.DOWN, keyCode = androidKeyCodeButtonB),
            ),
        )

        val repeated = matcherFor(sequence = 51)
        assertEquals(
            GamepadEventResult.Rejected(51, GamepadEventIssue.REPEATED_EVENT),
            repeated.observe(
                51,
                matchedDevice(),
                keyEvent(GamepadKeyAction.DOWN, repeatCount = 1),
            ),
        )

        val other = matcherFor(sequence = 52)
        assertEquals(
            GamepadEventResult.Rejected(52, GamepadEventIssue.UNSUPPORTED_EVENT),
            other.observe(52, matchedDevice(), keyEvent(GamepadKeyAction.OTHER)),
        )
    }

    @Test
    fun staleSequenceEventsDoNotChangeTheActivePulseEvidence() {
        val matcher = matcherFor(sequence = 53)

        assertEquals(
            GamepadEventResult.Rejected(52, GamepadEventIssue.STALE_SEQUENCE),
            matcher.observe(52, matchedDevice(), keyEvent(GamepadKeyAction.DOWN)),
        )
        assertEquals(
            GamepadEventResult.PressAccepted(53, fixtureDeviceId),
            matcher.observe(53, matchedDevice(), keyEvent(GamepadKeyAction.DOWN)),
        )
        assertEquals(
            GamepadEventResult.ReleaseAccepted(53, fixtureDeviceId),
            matcher.observe(53, matchedDevice(), keyEvent(GamepadKeyAction.UP)),
        )
        assertEquals(GamepadEventResult.Complete(53, fixtureDeviceId), matcher.finish(53))
    }

    @Test
    fun resetDiscardsIdentityAndSequenceEvidenceBeforeAReenumeratedPulse() {
        val matcher = matcherFor(sequence = 54)
        matcher.observe(54, matchedDevice(), keyEvent(GamepadKeyAction.DOWN))
        matcher.reset()

        assertEquals(
            GamepadEventResult.Rejected(54, GamepadEventIssue.NO_ACTIVE_SEQUENCE),
            matcher.finish(54),
        )
        matcher.begin(55)
        assertEquals(
            GamepadEventResult.PressAccepted(55, fixtureDeviceId + 1),
            matcher.observe(
                55,
                GamepadAssociation.Matched(deviceId = fixtureDeviceId + 1),
                keyEvent(GamepadKeyAction.DOWN),
            ),
        )
    }

    private fun matcherFor(sequence: Long): GamepadEventMatcher =
        GamepadEventMatcher(fixtureButtonUsageOneKeyCode).also { it.begin(sequence) }

    private fun matchedDevice(): GamepadAssociation =
        GamepadAssociation.Matched(deviceId = fixtureDeviceId)

    private fun keyEvent(
        action: GamepadKeyAction,
        keyCode: Int = fixtureButtonUsageOneKeyCode,
        repeatCount: Int = 0,
    ) = GamepadKeyEventSnapshot(action = action, keyCode = keyCode, repeatCount = repeatCount)

    private companion object {
        const val androidKeyCodeButtonB = 97
    }
}
