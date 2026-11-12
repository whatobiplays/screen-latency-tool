package io.screenlatency.tool.usb

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EngineeringFrameCodecTest {
    @Test
    fun encodesEveryRequestFromTheSharedGoldenVectors() {
        goldenVectors().forEach { vector ->
            assertArrayEquals(vector.name, EngineeringFrameCodec.encodeRequest(requestFor(vector.name)), vector.request)
        }
    }

    @Test
    fun encodesPulseTagDurationAndReservedFieldInEightBytes() {
        val request = EngineeringRequest(
            requestId = 2,
            command = EngineeringFrameCodec.ENGINEERING_PRESS_PULSE,
            payload = pulsePayload(tag = 0x11223344L, durationMs = 10),
        )

        assertArrayEquals(
            byteArrayOf(0x44, 0x33, 0x22, 0x11, 10, 0, 0, 0),
            EngineeringFrameCodec.encodeRequest(request).copyOfRange(EngineeringFrameCodec.HEADER_SIZE, 28),
        )
    }

    @Test
    fun productionEngineeringCommandsRemainAvailableAtBothSupportedMinors() {
        val legacyInfo = requestFor("engineering_info_request_0x01020304")
        val captureMinorInfo = legacyInfo.copy(protocolMinor = EngineeringFrameCodec.PROTOCOL_MINOR_1_1)
        val infoV10 = EngineeringFrameCodec.encodeRequest(legacyInfo)
        val infoV11 = EngineeringFrameCodec.encodeRequest(captureMinorInfo)
        assertArrayEquals(infoV10.copyOfRange(6, infoV10.size), infoV11.copyOfRange(6, infoV11.size))

        val legacyPulse = requestFor("engineering_press_10ms_request_2")
        val captureMinorPulse = legacyPulse.copy(protocolMinor = EngineeringFrameCodec.PROTOCOL_MINOR_1_1)
        val pulseV10 = EngineeringFrameCodec.encodeRequest(legacyPulse)
        val pulseV11 = EngineeringFrameCodec.encodeRequest(captureMinorPulse)
        assertArrayEquals(pulseV10.copyOfRange(6, pulseV10.size), pulseV11.copyOfRange(6, pulseV11.size))

        val infoResponseV11 = goldenVector("engineering_info_request_0x01020304").response.copyOf().also {
            it[5] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
            it[21] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
        }
        assertEquals(
            EngineeringFrameCodec.PROTOCOL_MINOR_1_1,
            EngineeringFrameCodec.decodeResponse(infoResponseV11, captureMinorInfo).protocolMinor,
        )

        val pulseResponseV11 = goldenVector(legacyPulse).response.copyOf().also {
            it[5] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
        }
        assertEquals(
            EngineeringFrameCodec.PROTOCOL_MINOR_1_1,
            EngineeringFrameCodec.decodeResponse(pulseResponseV11, captureMinorPulse).protocolMinor,
        )
    }

    @Test
    fun rejectsPulseRequestsWithReservedBytesOrOutOfRangeDurations() {
        val valid = requestFor("engineering_press_10ms_request_2")

        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.encodeRequest(
                valid.copy(payload = valid.payload.copyOf().also { it[7] = 1 }),
            )
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.encodeRequest(
                valid.copy(payload = pulsePayload(tag = 2, durationMs = 9)),
            )
        }
    }

    @Test
    fun enforcesCaptureNonceChunkAlignmentAndZeroLengthEofLimits() {
        val begin = requestFor("engineering_capture_begin_ascending_v11")
        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.encodeRequest(
                begin.copy(payload = begin.payload.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }),
            )
        }

        val read = requestFor("engineering_capture_read_32_v11")
        listOf(
            read.copy(payload = read.payload.copyOf().also { it[6] = 1 }),
            read.copy(payload = read.payload.copyOf().also { it[10] = 33 }),
            read.copy(payload = read.payload.copyOf().also { it[1] = 1 }),
        ).forEach { invalid ->
            assertThrows(ProtocolViolation::class.java) {
                EngineeringFrameCodec.encodeRequest(invalid)
            }
        }

        val eof = requestFor("engineering_capture_read_eof_v11")
        assertEquals(EngineeringFrameCodec.MAX_FRAME_SIZE, goldenVector(read).response.size)
        assertEquals(EngineeringFrameCodec.ENGINEERING_CAPTURE_READ_METADATA_SIZE,
            EngineeringFrameCodec.decodeResponse(
            goldenVector(eof).response, eof,
        ).payload.size)
        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.encodeRequest(
                eof.copy(payload = eof.payload.copyOf().also { it[10] = 32 }),
            )
        }
    }

    @Test
    fun decodesEveryResponseFromTheSharedGoldenVectors() {
        goldenVectors().forEach { vector ->
            val request = requestFor(vector.name)
            val response = EngineeringFrameCodec.decodeResponse(vector.response, request)

            assertEquals(vector.name, request.requestId, response.requestId)
            assertEquals(vector.name, request.command, response.command)
            assertEquals(vector.name, ProtocolStatus.OK, response.status)
            assertEquals(vector.name, expectedResponsePayloadLength(request), response.payload.size)
        }
    }

    @Test
    fun rejectsAResponseForAnOlderRequestId() {
        val request = requestFor("engineering_press_10ms_request_2")
        val stale = goldenVector(request).response.copyOf().also { putU32(it, 8, 3L) }

        assertThrows(StaleResponseException::class.java) {
            EngineeringFrameCodec.decodeResponse(stale, request)
        }
    }

    @Test
    fun rejectsUnsupportedProtocolVersions() {
        val request = requestFor("engineering_info_request_0x01020304")
        val response = goldenVector("engineering_info_request_0x01020304").response.copyOf().also { it[4] = 2 }

        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.decodeResponse(response, request)
        }
    }

    @Test
    fun rejectsReservedZeroRequestIds() {
        val valid = requestFor("engineering_info_request_0x01020304")
        val zeroId = valid.copy(requestId = 0)

        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.encodeRequest(zeroId)
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.decodeResponse(byteArrayOf(), zeroId)
        }
    }

    @Test
    fun rejectsNonzeroReservedHeaderFlags() {
        val request = requestFor("engineering_info_request_0x01020304")
        val response = goldenVector("engineering_info_request_0x01020304").response.copyOf().also { it[7] = 1 }

        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.decodeResponse(response, request)
        }
    }

    @Test
    fun rejectsTruncatedHeaders() {
        val request = requestFor("engineering_press_10ms_request_2")
        val response = goldenVector(request).response.copyOf(19)

        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.decodeResponse(response, request)
        }
    }

    @Test
    fun rejectsDeclaredPayloadLengthsThatDisagreeWithTheTransfer() {
        val request = requestFor("engineering_press_10ms_request_2")
        val response = goldenVector(request).response.copyOf().also { putU32(it, 16, 7L) }

        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.decodeResponse(response, request)
        }
    }

    @Test
    fun rejectsTrailingBytesAfterACompleteResponse() {
        val request = requestFor("engineering_press_10ms_request_2")
        val response = goldenVector(request).response + byteArrayOf(0)

        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.decodeResponse(response, request)
        }
    }

    @Test
    fun rejectsUnknownStatusesAndWrongPayloadLengths() {
        val request = requestFor("engineering_press_10ms_request_2")
        val unknownStatus = goldenVector(request).response.copyOf().also { putU16(it, 14, 9) }
        val wrongPayload = goldenVector(request).response.copyOf().also { putU32(it, 16, 7L) }

        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.decodeResponse(unknownStatus, request)
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringFrameCodec.decodeResponse(wrongPayload, request)
        }
    }

    private fun goldenVector(requestName: String): GoldenVector = goldenVectors().single { it.name == requestName }

    private fun goldenVector(request: EngineeringRequest): GoldenVector =
        goldenVectors().single { vector ->
            val expected = requestFor(vector.name)
            expected.requestId == request.requestId &&
                expected.command == request.command &&
                expected.payload.contentEquals(request.payload)
        }

    private fun expectedResponsePayloadLength(request: EngineeringRequest): Int = when (request.command) {
        0xE001 -> 32
        0xE002 -> 12
        0xE003 -> 20
        0xE004 -> when (request.payload[0].toInt() and 0xFF) {
            EngineeringFrameCodec.CAPTURE_STATUS -> EngineeringFrameCodec.ENGINEERING_CAPTURE_STATUS_SIZE
            EngineeringFrameCodec.CAPTURE_READ -> EngineeringFrameCodec.ENGINEERING_CAPTURE_READ_METADATA_SIZE +
                (request.payload[10].toInt() and 0xFF)
            EngineeringFrameCodec.CAPTURE_RELEASE -> EngineeringFrameCodec.ENGINEERING_CAPTURE_RELEASE_SIZE
            else -> error("Unknown archive operation")
        }
        else -> error("No engineering vector for command 0x${request.command.toString(16)}")
    }

    private fun requestFor(name: String): EngineeringRequest = when (name) {
        "engineering_info_request_0x01020304",
        "engineering_info_completed_wrapped_tick_0x01020304" -> EngineeringRequest(0x01020304L, 0xE001, byteArrayOf())
        "engineering_press_10ms_request_2" -> EngineeringRequest(2, 0xE002, pulsePayload(2, 10))
        "engineering_press_1000ms_request_3" -> EngineeringRequest(3, 0xE002, pulsePayload(3, 1000))
        "engineering_capture_begin_ascending_v11" -> EngineeringRequest(
            0x01020304L, 0xE003, beginCapturePayload(0x01020304L, polarity = 1, rateStage = 1, timeoutMs = 60_000), 1,
        )
        "engineering_capture_begin_descending_v11" -> EngineeringRequest(
            0x01020309L, 0xE003, beginCapturePayload(0x01020309L, polarity = 2, rateStage = 2, timeoutMs = 60_000), 1,
        )
        "engineering_capture_status_v11" -> EngineeringRequest(0x01020305L, 0xE004, archivePayload(1, 0, 0, 0), 1)
        "engineering_capture_read_32_v11" -> EngineeringRequest(0x01020306L, 0xE004, archivePayload(2, 1, 0, 32), 1)
        "engineering_capture_read_eof_v11" -> EngineeringRequest(0x01020307L, 0xE004, archivePayload(2, 1, 400_128, 0), 1)
        "engineering_capture_release_v11" -> EngineeringRequest(0x01020308L, 0xE004, archivePayload(3, 1, 0, 0), 1)
        else -> error("Unknown shared golden vector: $name")
    }

    private fun beginCapturePayload(nonce: Long, polarity: Int, rateStage: Int, timeoutMs: Int) =
        ByteArray(16).also { payload ->
            putU64(payload, 0, nonce)
            putU16(payload, 8, 10_000)
            payload[10] = polarity.toByte()
            payload[11] = rateStage.toByte()
            putU32(payload, 12, timeoutMs.toLong())
        }

    private fun archivePayload(operation: Int, generation: Long, offset: Long, length: Int) =
        ByteArray(12).also { payload ->
            payload[0] = operation.toByte()
            putU32(payload, 2, generation)
            putU32(payload, 6, offset)
            payload[10] = length.toByte()
        }

    private fun pulsePayload(tag: Long, durationMs: Int) = byteArrayOf(
        tag.toByte(), (tag ushr 8).toByte(), (tag ushr 16).toByte(), (tag ushr 24).toByte(),
        durationMs.toByte(), (durationMs ushr 8).toByte(), 0, 0,
    )

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU64(bytes: ByteArray, offset: Int, value: Long) {
        repeat(8) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun goldenVectors(): List<GoldenVector> {
        val repositoryRoot = Path.of(requireNotNull(System.getProperty("repositoryRoot")))
        val path = repositoryRoot.resolve("firmware/protocol/engineering-golden-vectors.tsv")
        return Files.readAllLines(path).drop(1).filter(String::isNotBlank).map { row ->
            val columns = row.split('\t')
            require(columns.size == 3) { "Malformed shared golden-vector row" }
            GoldenVector(columns[0], columns[1].hexBytes(), columns[2].hexBytes())
        }
    }

    private fun String.hexBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private data class GoldenVector(val name: String, val request: ByteArray, val response: ByteArray)
}
