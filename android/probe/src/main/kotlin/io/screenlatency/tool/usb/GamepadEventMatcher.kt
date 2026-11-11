package io.screenlatency.tool.usb

/** Android key-event action used for fixture-button diagnostics. */
enum class GamepadKeyAction {
    DOWN,
    UP,
    OTHER,
}

/** Small immutable snapshot that keeps Android framework types out of the matcher. */
data class GamepadKeyEventSnapshot(
    val action: GamepadKeyAction,
    val keyCode: Int,
    val repeatCount: Int,
)

enum class GamepadEventIssue {
    NO_ACTIVE_SEQUENCE,
    STALE_SEQUENCE,
    STALE_EVENT,
    AMBIGUOUS_DEVICE,
    WRONG_DEVICE,
    WRONG_BUTTON,
    REPEATED_EVENT,
    OUT_OF_ORDER,
    DUPLICATE_PRESS,
    DUPLICATE_RELEASE,
    UNSUPPORTED_EVENT,
    BUFFER_OVERFLOW,
    MISSING_PRESS,
    MISSING_RELEASE,
}

/** Results describe diagnostic Android input evidence and never establish HID timing or T0. */
sealed interface GamepadEventResult {
    /** The event is held until firmware supplies the pulse sequence. */
    data object Pending : GamepadEventResult

    /** The event cannot contribute to the current pulse's input evidence. */
    data class Ignored(val issue: GamepadEventIssue) : GamepadEventResult
    data class PressAccepted(val sequence: Long, val deviceId: Int) : GamepadEventResult
    data class ReleaseAccepted(val sequence: Long, val deviceId: Int) : GamepadEventResult
    data class Complete(val sequence: Long, val deviceId: Int) : GamepadEventResult
    data class Rejected(val sequence: Long, val issue: GamepadEventIssue) : GamepadEventResult
}

/** Matches one ordered button-down/button-up pair to a single accepted pulse sequence. */
class GamepadEventMatcher(private val expectedButtonKeyCode: Int) {
    private val pendingEvents = mutableListOf<PendingGamepadEvent>()
    private var activeSequence: Long? = null
    private var captureStartedAtMillis: Long? = null
    private var matchedDeviceId: Int? = null
    private var pressed = false
    private var released = false
    private var rejection: GamepadEventResult.Rejected? = null
    private var pendingEventsOverflowed = false

    init {
        require(expectedButtonKeyCode > 0) { "The fixture button key code must be positive" }
    }

    /** Starts a capture window using Android InputEvent's uptimeMillis time base. */
    @Synchronized
    fun arm(captureStartedAtMillis: Long) {
        require(captureStartedAtMillis >= 0) { "The capture start time must be nonnegative" }
        reset()
        this.captureStartedAtMillis = captureStartedAtMillis
    }

    @Synchronized
    fun begin(sequence: Long) {
        require(sequence in 1..MAX_SEQUENCE) { "The pulse sequence must be a nonzero uint32" }
        activeSequence = sequence
        matchedDeviceId = null
        pressed = false
        released = false
        rejection = null
        if (captureStartedAtMillis == null) captureStartedAtMillis = Long.MIN_VALUE
        if (pendingEventsOverflowed) {
            rejection = rejected(sequence, GamepadEventIssue.BUFFER_OVERFLOW)
            pendingEvents.clear()
            return
        }
        pendingEvents.toList().forEach { pending ->
            observeForSequence(sequence, pending.association, pending.event)
        }
        pendingEvents.clear()
    }

    /** Buffers events that arrive before firmware returns the accepted pulse sequence. */
    @Synchronized
    fun observeCaptured(
        eventTimeMillis: Long,
        association: GamepadAssociation,
        event: GamepadKeyEventSnapshot?,
    ): GamepadEventResult {
        if (association == GamepadAssociation.IgnoredMotion) {
            return GamepadEventResult.Ignored(GamepadEventIssue.UNSUPPORTED_EVENT)
        }
        val captureStarted = captureStartedAtMillis
            ?: return GamepadEventResult.Ignored(GamepadEventIssue.NO_ACTIVE_SEQUENCE)
        if (eventTimeMillis < captureStarted) {
            return GamepadEventResult.Ignored(GamepadEventIssue.STALE_EVENT)
        }
        if (association == GamepadAssociation.WrongDevice) {
            return GamepadEventResult.Ignored(GamepadEventIssue.WRONG_DEVICE)
        }
        val sequence = activeSequence
        if (sequence != null) return observeForSequence(sequence, association, event)

        if (pendingEvents.size == MAX_PENDING_EVENTS) {
            pendingEventsOverflowed = true
            pendingEvents.clear()
        } else if (!pendingEventsOverflowed) {
            pendingEvents += PendingGamepadEvent(association, event)
        }
        return GamepadEventResult.Pending
    }

    @Synchronized
    fun observe(
        sequence: Long,
        association: GamepadAssociation,
        event: GamepadKeyEventSnapshot?,
    ): GamepadEventResult = observeForSequence(sequence, association, event)

    private fun observeForSequence(
        sequence: Long,
        association: GamepadAssociation,
        event: GamepadKeyEventSnapshot?,
    ): GamepadEventResult {
        if (association == GamepadAssociation.IgnoredMotion) {
            return GamepadEventResult.Ignored(GamepadEventIssue.UNSUPPORTED_EVENT)
        }
        val active = activeSequence ?: return rejected(sequence, GamepadEventIssue.NO_ACTIVE_SEQUENCE)
        if (sequence != active) return rejected(sequence, GamepadEventIssue.STALE_SEQUENCE)
        rejection?.let { return it }

        val deviceId = when (association) {
            GamepadAssociation.Ambiguous -> return rejectActive(GamepadEventIssue.AMBIGUOUS_DEVICE)
            GamepadAssociation.WrongDevice -> return rejectActive(GamepadEventIssue.WRONG_DEVICE)
            GamepadAssociation.IgnoredMotion -> return GamepadEventResult.Ignored(GamepadEventIssue.UNSUPPORTED_EVENT)
            is GamepadAssociation.Matched -> association.deviceId
        }
        if (deviceId <= 0) return rejectActive(GamepadEventIssue.AMBIGUOUS_DEVICE)
        if (matchedDeviceId != null && matchedDeviceId != deviceId) {
            return rejectActive(GamepadEventIssue.WRONG_DEVICE)
        }
        val keyEvent = event ?: return rejectActive(GamepadEventIssue.UNSUPPORTED_EVENT)
        if (keyEvent.repeatCount != 0) return rejectActive(GamepadEventIssue.REPEATED_EVENT)
        if (keyEvent.keyCode != expectedButtonKeyCode) {
            return rejectActive(GamepadEventIssue.WRONG_BUTTON)
        }

        return when (keyEvent.action) {
            GamepadKeyAction.DOWN -> {
                if (pressed) return rejectActive(GamepadEventIssue.DUPLICATE_PRESS)
                matchedDeviceId = deviceId
                pressed = true
                GamepadEventResult.PressAccepted(active, deviceId)
            }
            GamepadKeyAction.UP -> {
                if (!pressed) return rejectActive(GamepadEventIssue.OUT_OF_ORDER)
                if (released) return rejectActive(GamepadEventIssue.DUPLICATE_RELEASE)
                released = true
                GamepadEventResult.ReleaseAccepted(active, deviceId)
            }
            GamepadKeyAction.OTHER -> rejectActive(GamepadEventIssue.UNSUPPORTED_EVENT)
        }
    }

    @Synchronized
    fun finish(sequence: Long): GamepadEventResult {
        val active = activeSequence ?: return rejected(sequence, GamepadEventIssue.NO_ACTIVE_SEQUENCE)
        if (sequence != active) return rejected(sequence, GamepadEventIssue.STALE_SEQUENCE)
        rejection?.let { return it }
        if (!pressed) return rejectActive(GamepadEventIssue.MISSING_PRESS)
        if (!released) return rejectActive(GamepadEventIssue.MISSING_RELEASE)
        return GamepadEventResult.Complete(active, requireNotNull(matchedDeviceId))
    }

    @Synchronized
    fun reset() {
        activeSequence = null
        captureStartedAtMillis = null
        pendingEvents.clear()
        matchedDeviceId = null
        pressed = false
        released = false
        rejection = null
        pendingEventsOverflowed = false
    }

    private fun rejectActive(issue: GamepadEventIssue): GamepadEventResult.Rejected {
        val result = rejected(requireNotNull(activeSequence), issue)
        rejection = result
        return result
    }

    private fun rejected(sequence: Long, issue: GamepadEventIssue) =
        GamepadEventResult.Rejected(sequence, issue)

    private data class PendingGamepadEvent(
        val association: GamepadAssociation,
        val event: GamepadKeyEventSnapshot?,
    )

    private companion object {
        const val MAX_PENDING_EVENTS = 8
        const val MAX_SEQUENCE = 0xFFFF_FFFFL
    }
}
