package io.screenlatency.tool.usb

enum class EngineeringEngineState(val wireValue: Int) {
    IDLE(0),
    PRESS_QUEUED(1),
    HOLDING(2),
    RELEASE_QUEUED(3),
    COMPLETE(4),
    RECOVERY(5),
    FAULT(6),
}

enum class EngineeringFault(val wireValue: Int) {
    NONE(0),
    PRESS_TRANSFER_FAILED(1),
    PRESS_TIMEOUT(2),
    SESSION_INVALIDATED(3),
    RELEASE_TRANSFER_FAILED(4),
    RELEASE_TIMEOUT(5),
    RECOVERY_FAILED(6),
    RECOVERY_TIMEOUT(7),
    UNSPECIFIED(255),
}

enum class EngineeringUsbSpeed(val wireValue: Int) {
    UNKNOWN(0),
    LOW(1),
    FULL(2),
    HIGH(3),
}

data class EngineeringInfo(
    val protocolMajor: Int,
    val protocolMinor: Int,
    val firmwareMajor: Int,
    val firmwareMinor: Int,
    val usbSpeed: EngineeringUsbSpeed,
    val serialNumber: Long,
    val sequence: Long,
    val state: EngineeringEngineState,
    val flags: Int,
    val durationMs: Int,
    val usbSessionGeneration: Long,
    val transferGeneration: Long,
    val completionTick: Long,
    val fault: EngineeringFault,
    val completionCounterFrequencyHz: Long = COMPLETION_COUNTER_FREQUENCY_HZ,
) {
    val completionTickValid: Boolean get() = flags and COMPLETION_TICK_VALID != 0
    val fullSpeedVerified: Boolean get() = flags and FULL_SPEED_VERIFIED != 0
    val usbSessionValid: Boolean get() = flags and USB_SESSION_VALID != 0
    val recoveryRequired: Boolean get() = flags and UNMEASURED_RECOVERY_REQUIRED != 0

    companion object {
        const val COMPLETION_COUNTER_FREQUENCY_HZ = 600_000_000L
        const val COMPLETION_TICK_VALID = 1 shl 0
        const val FULL_SPEED_VERIFIED = 1 shl 1
        const val USB_SESSION_VALID = 1 shl 2
        const val UNMEASURED_RECOVERY_REQUIRED = 1 shl 3
        const val KNOWN_FLAGS = 0x0F
    }
}

/** Decodes the exact 32-byte ENGINEERING_INFO payload, including reserved fields. */
object EngineeringInfoCodec {
    const val PAYLOAD_SIZE = 32
    private const val STATE_MASK = 0x07
    private const val STATE_FLAGS_RESERVED_MASK = 0x80

    fun decode(payload: ByteArray): EngineeringInfo {
        if (payload.size != PAYLOAD_SIZE) throw ProtocolViolation("ENGINEERING_INFO payload must be exactly 32 bytes")

        val protocolMajor = u8(payload, 0)
        val protocolMinor = u8(payload, 1)
        if (protocolMajor != 1 || protocolMinor > EngineeringFrameCodec.PROTOCOL_MINOR_1_1) {
            throw ProtocolViolation("ENGINEERING_INFO protocol version is unsupported")
        }
        val firmwareMajor = u8(payload, 2)
        val firmwareMinor = u8(payload, 3)
        val speedValue = u8(payload, 4)
        val usbSpeed = EngineeringUsbSpeed.entries.firstOrNull { it.wireValue == speedValue }
            ?: throw ProtocolViolation("Unknown engineering USB speed $speedValue")
        val stateAndFlags = u8(payload, 5)
        if (stateAndFlags and STATE_FLAGS_RESERVED_MASK != 0) {
            throw ProtocolViolation("ENGINEERING_INFO contains reserved state flag bits")
        }
        val stateValue = stateAndFlags and STATE_MASK
        val state = EngineeringEngineState.entries.firstOrNull { it.wireValue == stateValue }
            ?: throw ProtocolViolation("Unknown engineering engine state $stateValue")
        val flags = stateAndFlags ushr 3
        if (flags and EngineeringInfo.KNOWN_FLAGS.inv() != 0) {
            throw ProtocolViolation("ENGINEERING_INFO contains reserved flag bits")
        }
        val serialNumber = u64(payload, 6)
        val sequence = u32(payload, 14)
        val sessionGeneration = u32(payload, 18)
        val transferGeneration = u32(payload, 22)
        val completionTick = u32(payload, 26)
        val duration = u16(payload, 30)
        val tickValid = flags and EngineeringInfo.COMPLETION_TICK_VALID != 0
        val fullSpeedVerified = flags and EngineeringInfo.FULL_SPEED_VERIFIED != 0
        val sessionValid = flags and EngineeringInfo.USB_SESSION_VALID != 0
        if (!tickValid && completionTick != 0L) {
            throw ProtocolViolation("Completion tick must be zero when its validity flag is clear")
        }
        if (fullSpeedVerified && usbSpeed != EngineeringUsbSpeed.FULL) {
            throw ProtocolViolation("Full-Speed verification requires the Full-Speed enum")
        }
        if (tickValid && (state !in setOf(
                EngineeringEngineState.IDLE,
                EngineeringEngineState.HOLDING,
                EngineeringEngineState.RELEASE_QUEUED,
                EngineeringEngineState.COMPLETE,
                EngineeringEngineState.RECOVERY,
                EngineeringEngineState.FAULT,
            ) || sequence == 0L || !fullSpeedVerified || !sessionValid)
        ) {
            throw ProtocolViolation("Completion tick validity conflicts with state or USB session")
        }
        if (state in setOf(
                EngineeringEngineState.PRESS_QUEUED,
                EngineeringEngineState.HOLDING,
                EngineeringEngineState.RELEASE_QUEUED,
                EngineeringEngineState.COMPLETE,
            ) && (!fullSpeedVerified || !sessionValid)
        ) {
            throw ProtocolViolation("An accepted pulse requires a valid Full-Speed USB session")
        }
        if (sequence == 0L && duration != 0 || sequence != 0L && duration !in 10..1000) {
            throw ProtocolViolation("Engineering pulse sequence and duration are inconsistent")
        }

        return EngineeringInfo(
            protocolMajor = protocolMajor,
            protocolMinor = protocolMinor,
            firmwareMajor = firmwareMajor,
            firmwareMinor = firmwareMinor,
            usbSpeed = usbSpeed,
            serialNumber = serialNumber,
            sequence = sequence,
            state = state,
            flags = flags,
            durationMs = duration,
            usbSessionGeneration = sessionGeneration,
            transferGeneration = transferGeneration,
            completionTick = completionTick,
            fault = if (state == EngineeringEngineState.FAULT) {
                EngineeringFault.UNSPECIFIED
            } else {
                EngineeringFault.NONE
            },
        )
    }

    private fun u8(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 0xFF

    private fun u16(bytes: ByteArray, offset: Int) = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, index -> value or (u8(bytes, offset + index).toLong() shl (index * 8)) }

    private fun u64(bytes: ByteArray, offset: Int): Long =
        (0..7).fold(0L) { value, index -> value or (u8(bytes, offset + index).toLong() shl (index * 8)) }
}

data class EngineeringPulseAcknowledgement(
    val requestTag: Long,
    val sequence: Long,
    val state: EngineeringEngineState,
) {
    companion object {
        fun decode(response: EngineeringResponse, expectedTag: Long): EngineeringPulseAcknowledgement {
            if (response.command != EngineeringFrameCodec.ENGINEERING_PRESS_PULSE) {
                throw ProtocolViolation("Response is not ENGINEERING_PRESS_PULSE")
            }
            if (response.status != ProtocolStatus.OK) {
                throw ProtocolViolation("Pulse request was not accepted: ${response.status}")
            }
            if (expectedTag !in 1..0xFFFF_FFFFL ||
                response.payload.size != EngineeringFrameCodec.ENGINEERING_PRESS_ACK_SIZE
            ) {
                throw ProtocolViolation("Press acknowledgement or expected tag is malformed")
            }

            val requestTag = u32(response.payload, 0)
            val sequence = u32(response.payload, 4)
            val stateValue = u8(response.payload, 8)
            val state = EngineeringEngineState.entries.firstOrNull { it.wireValue == stateValue }
                ?: throw ProtocolViolation("Unknown press acknowledgement state $stateValue")
            if (requestTag != expectedTag || sequence == 0L ||
                state != EngineeringEngineState.PRESS_QUEUED ||
                (9 until response.payload.size).any { u8(response.payload, it) != 0 }
            ) {
                throw ProtocolViolation("Press acknowledgement does not match its accepted request")
            }
            return EngineeringPulseAcknowledgement(requestTag, sequence, state)
        }

        private fun u8(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 0xFF

        private fun u32(bytes: ByteArray, offset: Int): Long =
            (0..3).fold(0L) { value, index -> value or (u8(bytes, offset + index).toLong() shl (index * 8)) }
    }
}

sealed interface PulseUpdate {
    data object Queued : PulseUpdate
    data object Holding : PulseUpdate
    data object ReleaseQueued : PulseUpdate
    data object Complete : PulseUpdate
    data object Recovering : PulseUpdate
    data object RecoveryComplete : PulseUpdate
    data object Busy : PulseUpdate
    data object Invalidated : PulseUpdate
    data object TimedOut : PulseUpdate
    data class Failed(val status: ProtocolStatus, val fault: EngineeringFault) : PulseUpdate
}

class StalePulseSequenceException(expected: Long, actual: Long) :
    ProtocolViolation("ENGINEERING_INFO sequence $actual does not match accepted sequence $expected")

/** Tracks one pulse within a USB session and rejects older transfer snapshots. */
class EngineeringPulseTracker(
    private val expectedSequence: Long,
    private val durationMs: Int,
) {
    private var lastMainStateRank = 0
    private var sessionGeneration: Long? = null
    private var lastTransferGeneration: Long? = null
    private var recoveryObserved = false
    private var terminal = false

    init {
        if (expectedSequence !in 1..0xFFFF_FFFFL || durationMs !in 10..1000) {
            throw ProtocolViolation("Pulse tracker requires a valid accepted sequence and duration")
        }
    }

    fun observe(info: EngineeringInfo): PulseUpdate {
        if (terminal) throw ProtocolViolation("Pulse tracker already reached a terminal state")
        if (info.sequence != expectedSequence) throw StalePulseSequenceException(expectedSequence, info.sequence)
        if (info.durationMs != durationMs) throw ProtocolViolation("Pulse duration changed during its sequence")

        val originalSession = sessionGeneration
        if (originalSession != null && originalSession != info.usbSessionGeneration) {
            terminal = true
            return PulseUpdate.Invalidated
        }
        sessionGeneration = info.usbSessionGeneration

        val previousTransferGeneration = lastTransferGeneration
        if (previousTransferGeneration != null &&
            info.transferGeneration < previousTransferGeneration
        ) {
            return finish(PulseUpdate.Invalidated)
        }
        lastTransferGeneration = info.transferGeneration

        if (!info.usbSessionValid) return finish(PulseUpdate.Invalidated)
        if (info.state == EngineeringEngineState.RECOVERY) {
            recoveryObserved = true
            return PulseUpdate.Recovering
        }
        if (!info.fullSpeedVerified) {
            return finish(PulseUpdate.Failed(ProtocolStatus.HARDWARE_FAULT, info.fault))
        }
        if (info.fault != EngineeringFault.NONE &&
            recoveryObserved &&
            info.state == EngineeringEngineState.FAULT &&
            !info.recoveryRequired
        ) {
            return finish(PulseUpdate.RecoveryComplete)
        }
        if (info.fault != EngineeringFault.NONE) {
            return finish(PulseUpdate.Failed(ProtocolStatus.HARDWARE_FAULT, info.fault))
        }

        return when (info.state) {
            EngineeringEngineState.IDLE -> {
                when {
                    info.completionTickValid -> finish(PulseUpdate.Complete)
                    recoveryObserved -> finish(PulseUpdate.RecoveryComplete)
                    else -> throw ProtocolViolation("Accepted pulse returned to idle before completion")
                }
            }
            EngineeringEngineState.PRESS_QUEUED -> updateMainState(1, PulseUpdate.Queued)
            EngineeringEngineState.HOLDING -> {
                if (!info.completionTickValid) throw ProtocolViolation("Holding state lacks a valid press completion tick")
                updateMainState(2, PulseUpdate.Holding)
            }
            EngineeringEngineState.RELEASE_QUEUED -> {
                if (!info.completionTickValid) throw ProtocolViolation("Release state lost the measured press completion tick")
                updateMainState(3, PulseUpdate.ReleaseQueued)
            }
            EngineeringEngineState.COMPLETE -> {
                if (!info.completionTickValid) throw ProtocolViolation("Completed pulse lacks a valid measured press tick")
                updateMainState(4, PulseUpdate.Complete, terminalOnUpdate = true)
            }
            EngineeringEngineState.RECOVERY -> PulseUpdate.Recovering
            EngineeringEngineState.FAULT -> finish(
                PulseUpdate.Failed(ProtocolStatus.HARDWARE_FAULT, info.fault),
            )
        }
    }

    private fun updateMainState(rank: Int, update: PulseUpdate, terminalOnUpdate: Boolean = false): PulseUpdate {
        if (recoveryObserved || rank < lastMainStateRank) {
            throw ProtocolViolation("Engineering pulse state moved backward")
        }
        lastMainStateRank = rank
        if (terminalOnUpdate) return finish(update)
        return update
    }

    private fun finish(update: PulseUpdate): PulseUpdate {
        terminal = true
        return update
    }
}
