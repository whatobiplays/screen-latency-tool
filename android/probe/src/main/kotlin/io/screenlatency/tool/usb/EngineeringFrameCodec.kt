package io.screenlatency.tool.usb

/** A request uses the REF-WIRE-001 20-byte header and an engineering command payload. */
data class EngineeringRequest(
    val requestId: Long,
    val command: Int,
    val payload: ByteArray,
)

/** A validated response whose request ID and command match the outstanding request. */
data class EngineeringResponse(
    val requestId: Long,
    val command: Int,
    val status: ProtocolStatus,
    val payload: ByteArray,
)

/** Status values defined by the shared fixture protocol. */
enum class ProtocolStatus(val wireValue: Int) {
    OK(0),
    UNSUPPORTED_VERSION(1),
    BAD_REQUEST(2),
    INVALID_STATE(3),
    NOT_READY(4),
    HARDWARE_FAULT(5),
    INTEGRITY_FAULT(6),
    BUSY(7),
    RANGE_ERROR(8),
}

open class ProtocolViolation(message: String) : IllegalArgumentException(message)

class StaleResponseException(expected: Long, actual: Long) :
    ProtocolViolation("Response request ID $actual does not match outstanding request $expected")

/** Serializes and validates only the two engineering commands in REF-WIRE-001. */
object EngineeringFrameCodec {
    const val HEADER_SIZE = 20
    const val ENGINEERING_INFO = 0xE001
    const val ENGINEERING_PRESS_PULSE = 0xE002
    const val ENGINEERING_INFO_SIZE = 32
    const val ENGINEERING_PRESS_REQUEST_SIZE = 8
    const val ENGINEERING_PRESS_ACK_SIZE = 12

    private val magic = byteArrayOf(0x53, 0x4C, 0x54, 0x31)

    /** Encodes one validated request with version 1.0 and zero reserved fields. */
    fun encodeRequest(request: EngineeringRequest): ByteArray {
        validateU32(request.requestId, "request ID")
        require(request.command in 0..0xFFFF) { "Command is outside the u16 range" }
        validateRequestPayload(request.command, request.payload)

        val bytes = ByteArray(HEADER_SIZE + request.payload.size)
        magic.copyInto(bytes)
        bytes[4] = 1
        bytes[5] = 0
        bytes[6] = 1
        bytes[7] = 0
        putU32(bytes, 8, request.requestId)
        putU16(bytes, 12, request.command)
        putU16(bytes, 14, ProtocolStatus.OK.wireValue)
        putU32(bytes, 16, request.payload.size.toLong())
        request.payload.copyInto(bytes, HEADER_SIZE)
        return bytes
    }

    /** Encodes the pulse tag, duration, and zero reserved field. */
    fun encodePulseRequest(tag: Long, durationMs: Int): ByteArray {
        validateU32(tag, "pulse tag")
        if (durationMs !in 10..1000) throw ProtocolViolation("Pulse duration must be 10 through 1000 ms")
        return ByteArray(ENGINEERING_PRESS_REQUEST_SIZE).also { payload ->
            putU32(payload, 0, tag)
            putU16(payload, 4, durationMs)
        }
    }

    /** Decodes one complete response and rejects stale, malformed, or unrelated frames. */
    fun decodeResponse(bytes: ByteArray, expected: EngineeringRequest): EngineeringResponse {
        if (expected.requestId == 0L) throw ProtocolViolation("Request ID zero is reserved")
        if (bytes.size < HEADER_SIZE) throw ProtocolViolation("Response is shorter than its header")
        if (!bytes.copyOfRange(0, magic.size).contentEquals(magic)) {
            throw ProtocolViolation("Response magic is not SLT1")
        }
        if (u8(bytes, 4) != 1 || u8(bytes, 5) != 0) {
            throw ProtocolViolation("Response protocol version is not 1.0")
        }
        if (u8(bytes, 6) != 2) throw ProtocolViolation("Frame is not a response")
        if (u8(bytes, 7) != 0) throw ProtocolViolation("Reserved header flags must be zero")

        val requestId = u32(bytes, 8)
        if (requestId != expected.requestId) throw StaleResponseException(expected.requestId, requestId)

        val command = u16(bytes, 12)
        if (command != expected.command) throw ProtocolViolation("Response command does not match request")
        val statusValue = u16(bytes, 14)
        val status = ProtocolStatus.entries.firstOrNull { it.wireValue == statusValue }
            ?: throw ProtocolViolation("Unknown response status $statusValue")
        val payloadLength = u32(bytes, 16)
        if (payloadLength > Int.MAX_VALUE || bytes.size != HEADER_SIZE + payloadLength.toInt()) {
            throw ProtocolViolation("Declared payload length does not match transfer length")
        }

        val expectedLength = expectedResponseLength(command, status)
        if (payloadLength.toInt() != expectedLength) {
            throw ProtocolViolation("Unexpected payload length $payloadLength for command 0x${command.toString(16)}")
        }
        return EngineeringResponse(
            requestId = requestId,
            command = command,
            status = status,
            payload = bytes.copyOfRange(HEADER_SIZE, bytes.size),
        )
    }

    private fun validateRequestPayload(command: Int, payload: ByteArray) {
        when (command) {
            ENGINEERING_INFO -> if (payload.isNotEmpty()) {
                throw ProtocolViolation("ENGINEERING_INFO request payload must be empty")
            }
            ENGINEERING_PRESS_PULSE -> {
                if (payload.size != ENGINEERING_PRESS_REQUEST_SIZE) {
                    throw ProtocolViolation("ENGINEERING_PRESS_PULSE payload must be eight bytes")
                }
                if (u16(payload, 6) != 0) throw ProtocolViolation("ENGINEERING_PRESS_PULSE reserved field must be zero")
                val duration = u16(payload, 4)
                if (duration !in 10..1000) throw ProtocolViolation("Pulse duration must be 10 through 1000 ms")
            }
            else -> throw ProtocolViolation("Unsupported engineering command 0x${command.toString(16)}")
        }
    }

    private fun expectedResponseLength(command: Int, status: ProtocolStatus): Int {
        if (status != ProtocolStatus.OK) return 0
        return when (command) {
            ENGINEERING_INFO -> ENGINEERING_INFO_SIZE
            ENGINEERING_PRESS_PULSE -> ENGINEERING_PRESS_ACK_SIZE
            else -> throw ProtocolViolation("Unsupported engineering command 0x${command.toString(16)}")
        }
    }

    private fun validateU32(value: Long, field: String) {
        if (value !in 1..0xFFFF_FFFFL) throw ProtocolViolation("$field must be a nonzero u32")
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF

    private fun u16(bytes: ByteArray, offset: Int): Int = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, index -> value or (u8(bytes, offset + index).toLong() shl (index * 8)) }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
