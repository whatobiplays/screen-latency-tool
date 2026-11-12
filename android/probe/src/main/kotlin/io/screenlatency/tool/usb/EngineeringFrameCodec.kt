package io.screenlatency.tool.usb

/** A request uses the REF-WIRE-001 20-byte header and an engineering command payload. */
data class EngineeringRequest(
    val requestId: Long,
    val command: Int,
    val payload: ByteArray,
    val protocolMinor: Int = 0,
)

/** A validated response whose request ID and command match the outstanding request. */
data class EngineeringResponse(
    val requestId: Long,
    val command: Int,
    val status: ProtocolStatus,
    val payload: ByteArray,
    val protocolMinor: Int,
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

/** Serializes the compatible production commands and the 1.1 capture commands. */
object EngineeringFrameCodec {
    const val HEADER_SIZE = 20
    const val MAX_FRAME_SIZE = 64
    const val PROTOCOL_MINOR_1_0 = 0
    const val PROTOCOL_MINOR_1_1 = 1
    const val ENGINEERING_INFO = 0xE001
    const val ENGINEERING_PRESS_PULSE = 0xE002
    const val ENGINEERING_BEGIN_CAPTURE = 0xE003
    const val ENGINEERING_CAPTURE_ARCHIVE = 0xE004
    const val ENGINEERING_INFO_SIZE = 32
    const val ENGINEERING_PRESS_REQUEST_SIZE = 8
    const val ENGINEERING_PRESS_ACK_SIZE = 12
    const val ENGINEERING_BEGIN_CAPTURE_REQUEST_SIZE = 16
    const val ENGINEERING_BEGIN_CAPTURE_ACK_SIZE = 20
    const val ENGINEERING_CAPTURE_ARCHIVE_REQUEST_SIZE = 12
    const val ENGINEERING_CAPTURE_STATUS_SIZE = 24
    const val ENGINEERING_CAPTURE_READ_METADATA_SIZE = 12
    const val ENGINEERING_CAPTURE_RELEASE_SIZE = 8
    const val CAPTURE_ARCHIVE_SIZE = 400_128L
    const val CAPTURE_CHUNK_SIZE = 32

    const val CAPTURE_STATUS = 1
    const val CAPTURE_READ = 2
    const val CAPTURE_RELEASE = 3

    private val magic = byteArrayOf(0x53, 0x4C, 0x54, 0x31)

    /** Encodes one validated request with a supported minor version and no reserved bits. */
    fun encodeRequest(request: EngineeringRequest): ByteArray {
        validateU32(request.requestId, "request ID")
        require(request.command in 0..0xFFFF) { "Command is outside the u16 range" }
        if (request.protocolMinor !in PROTOCOL_MINOR_1_0..PROTOCOL_MINOR_1_1) {
            throw ProtocolViolation("Protocol minor version is unsupported")
        }
        if (request.command in setOf(ENGINEERING_BEGIN_CAPTURE, ENGINEERING_CAPTURE_ARCHIVE) &&
            request.protocolMinor != PROTOCOL_MINOR_1_1
        ) {
            throw ProtocolViolation("Capture commands require protocol minor 1.1")
        }
        if (request.command == ENGINEERING_BEGIN_CAPTURE &&
            (request.payload.size != ENGINEERING_BEGIN_CAPTURE_REQUEST_SIZE ||
                u64(request.payload, 0) != request.requestId)
        ) {
            throw ProtocolViolation("Capture nonce must equal its request ID")
        }
        validateRequestPayload(request.command, request.payload)

        val bytes = ByteArray(HEADER_SIZE + request.payload.size)
        if (bytes.size > MAX_FRAME_SIZE) throw ProtocolViolation("Engineering frame exceeds 64 bytes")
        magic.copyInto(bytes)
        bytes[4] = 1
        bytes[5] = request.protocolMinor.toByte()
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
        val responseMinor = u8(bytes, 5)
        if (u8(bytes, 4) != 1 || responseMinor !in PROTOCOL_MINOR_1_0..PROTOCOL_MINOR_1_1) {
            throw ProtocolViolation("Response protocol version is unsupported")
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
        if (responseMinor != expected.protocolMinor &&
            !(expected.protocolMinor == PROTOCOL_MINOR_1_1 &&
                responseMinor == PROTOCOL_MINOR_1_0 &&
                status == ProtocolStatus.UNSUPPORTED_VERSION &&
                expected.command in setOf(ENGINEERING_INFO, ENGINEERING_PRESS_PULSE))
        ) {
            throw ProtocolViolation("Response protocol minor does not match the request")
        }
        val payloadLength = u32(bytes, 16)
        if (bytes.size > MAX_FRAME_SIZE || payloadLength > Int.MAX_VALUE ||
            bytes.size != HEADER_SIZE + payloadLength.toInt()
        ) {
            throw ProtocolViolation("Declared payload length does not match transfer length")
        }

        val expectedLength = expectedResponseLength(expected, status)
        if (payloadLength.toInt() != expectedLength) {
            throw ProtocolViolation("Unexpected payload length $payloadLength for command 0x${command.toString(16)}")
        }
        return EngineeringResponse(
            requestId = requestId,
            command = command,
            status = status,
            payload = bytes.copyOfRange(HEADER_SIZE, bytes.size),
            protocolMinor = responseMinor,
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
            ENGINEERING_BEGIN_CAPTURE -> {
                if (payload.size != ENGINEERING_BEGIN_CAPTURE_REQUEST_SIZE) {
                    throw ProtocolViolation("ENGINEERING_BEGIN_CAPTURE payload must be 16 bytes")
                }
                val nonce = u64(payload, 0)
                val samplesPerChannel = u16(payload, 8)
                val polarity = u8(payload, 10)
                val rateStage = u8(payload, 11)
                val timeoutMs = u32(payload, 12)
                if (nonce == 0L || samplesPerChannel != 10_000 || polarity !in 1..2 ||
                    rateStage !in 1..2 || timeoutMs !in 1_000..86_400_000L
                ) {
                    throw ProtocolViolation("ENGINEERING_BEGIN_CAPTURE fields are outside their valid ranges")
                }
            }
            ENGINEERING_CAPTURE_ARCHIVE -> {
                if (payload.size != ENGINEERING_CAPTURE_ARCHIVE_REQUEST_SIZE) {
                    throw ProtocolViolation("ENGINEERING_CAPTURE_ARCHIVE payload must be 12 bytes")
                }
                val operation = u8(payload, 0)
                val generation = u32(payload, 2)
                val offset = u32(payload, 6)
                val length = u8(payload, 10)
                if (u8(payload, 1) != 0 || u8(payload, 11) != 0) {
                    throw ProtocolViolation("ENGINEERING_CAPTURE_ARCHIVE reserved fields must be zero")
                }
                when (operation) {
                    CAPTURE_STATUS -> if (offset != 0L || length != 0) {
                        throw ProtocolViolation("Capture status request fields are invalid")
                    }
                    CAPTURE_READ -> {
                        if (generation == 0L || offset > CAPTURE_ARCHIVE_SIZE ||
                            offset % CAPTURE_CHUNK_SIZE != 0L ||
                            (offset == CAPTURE_ARCHIVE_SIZE && length != 0) ||
                            (offset < CAPTURE_ARCHIVE_SIZE && length != CAPTURE_CHUNK_SIZE)
                        ) {
                            throw ProtocolViolation("Capture read must use an aligned 32-byte chunk or the zero-length EOF request")
                        }
                    }
                    CAPTURE_RELEASE -> if (generation == 0L || offset != 0L || length != 0) {
                        throw ProtocolViolation("Capture release request fields are invalid")
                    }
                    else -> throw ProtocolViolation("Unknown capture archive operation")
                }
            }
            else -> throw ProtocolViolation("Unsupported engineering command 0x${command.toString(16)}")
        }
    }

    private fun expectedResponseLength(request: EngineeringRequest, status: ProtocolStatus): Int {
        if (status != ProtocolStatus.OK) return 0
        return when (request.command) {
            ENGINEERING_INFO -> ENGINEERING_INFO_SIZE
            ENGINEERING_PRESS_PULSE -> ENGINEERING_PRESS_ACK_SIZE
            ENGINEERING_BEGIN_CAPTURE -> ENGINEERING_BEGIN_CAPTURE_ACK_SIZE
            ENGINEERING_CAPTURE_ARCHIVE -> when (u8(request.payload, 0)) {
                CAPTURE_STATUS -> ENGINEERING_CAPTURE_STATUS_SIZE
                CAPTURE_READ -> ENGINEERING_CAPTURE_READ_METADATA_SIZE + u8(request.payload, 10)
                CAPTURE_RELEASE -> ENGINEERING_CAPTURE_RELEASE_SIZE
                else -> throw ProtocolViolation("Unknown capture archive operation")
            }
            else -> throw ProtocolViolation("Unsupported engineering command 0x${request.command.toString(16)}")
        }
    }

    private fun validateU32(value: Long, field: String) {
        if (value !in 1..0xFFFF_FFFFL) throw ProtocolViolation("$field must be a nonzero u32")
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xFF

    private fun u16(bytes: ByteArray, offset: Int): Int = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, index -> value or (u8(bytes, offset + index).toLong() shl (index * 8)) }

    private fun u64(bytes: ByteArray, offset: Int): Long =
        (0..7).fold(0L) { value, index -> value or (u8(bytes, offset + index).toLong() shl (index * 8)) }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
