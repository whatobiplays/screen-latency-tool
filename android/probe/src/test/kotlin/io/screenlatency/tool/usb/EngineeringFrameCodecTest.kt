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
    fun decodesEveryResponseFromTheSharedGoldenVectors() {
        goldenVectors().forEach { vector ->
            val request = requestFor(vector.name)
            val response = EngineeringFrameCodec.decodeResponse(vector.response, request)

            assertEquals(vector.name, request.requestId, response.requestId)
            assertEquals(vector.name, request.command, response.command)
            assertEquals(vector.name, ProtocolStatus.OK, response.status)
            assertEquals(vector.name, expectedResponsePayloadLength(request.command), response.payload.size)
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

    private fun expectedResponsePayloadLength(command: Int): Int = when (command) {
        0xE001 -> 32
        0xE002 -> 12
        else -> error("No engineering vector for command 0x${command.toString(16)}")
    }

    private fun requestFor(name: String): EngineeringRequest = when (name) {
        "engineering_info_request_0x01020304",
        "engineering_info_completed_wrapped_tick_0x01020304" -> EngineeringRequest(0x01020304L, 0xE001, byteArrayOf())
        "engineering_press_10ms_request_2" -> EngineeringRequest(2, 0xE002, pulsePayload(2, 10))
        "engineering_press_1000ms_request_3" -> EngineeringRequest(3, 0xE002, pulsePayload(3, 1000))
        else -> error("Unknown shared golden vector: $name")
    }

    private fun pulsePayload(tag: Long, durationMs: Int) = byteArrayOf(
        tag.toByte(), (tag ushr 8).toByte(), (tag ushr 16).toByte(), (tag ushr 24).toByte(),
        durationMs.toByte(), (durationMs ushr 8).toByte(), 0, 0,
    )

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
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
