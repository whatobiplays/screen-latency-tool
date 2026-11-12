package io.screenlatency.tool.usb

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringCaptureArchiveCodecTest {
    @Test
    fun verifiesChunkCrcIdentityAlignmentAndExplicitEof() {
        val generation = 3L
        val data = ByteArray(EngineeringCaptureArchiveCodec.CHUNK_SIZE) { it.toByte() }
        val response = ByteArray(12 + data.size).also { payload ->
            putU32(payload, 0, generation)
            putU32(payload, 4, 32)
            putU32(payload, 8, EngineeringCaptureArchiveCodec.chunkCrc(generation, 32, data))
            data.copyInto(payload, 12)
        }

        assertArrayEquals(data, EngineeringCaptureArchiveCodec.decodeChunk(response, generation, 32, 32))
        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeChunk(response, generation + 1, 32, 32)
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeChunk(response.copyOf().also { it[8] = (it[8].toInt() xor 1).toByte() }, generation, 32, 32)
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeChunk(response.copyOf(43), generation, 32, 32)
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeChunk(response, generation, 1, 32)
        }

        val eof = ByteArray(12).also { payload ->
            putU32(payload, 0, generation)
            putU32(payload, 4, EngineeringCaptureArchiveCodec.ARCHIVE_SIZE.toLong())
            putU32(payload, 8, EngineeringCaptureArchiveCodec.chunkCrc(generation, EngineeringCaptureArchiveCodec.ARCHIVE_SIZE.toLong(), byteArrayOf()))
        }
        assertArrayEquals(byteArrayOf(), EngineeringCaptureArchiveCodec.decodeChunk(
            eof, generation, EngineeringCaptureArchiveCodec.ARCHIVE_SIZE.toLong(), 0,
        ))
    }

    @Test
    fun validatesArchiveHeaderHashCompletenessAndIdentity() {
        val bytes = validArchive()
        val archive = EngineeringCaptureArchiveCodec.decodeArchive(bytes, 7, 41, 9)
        assertEquals(7L, archive.generation)
        assertEquals(41L, archive.nonce)
        assertEquals(9L, archive.sessionGeneration)
        assertEquals(2, archive.polarity)
        assertEquals(2, archive.rateStage)
        assertEquals(true, archive.complete)
        assertEquals(List(5) { 10_000 }, archive.counts)
        assertEquals(150_000_000L, archive.tickHz)
        assertEquals(0x0102030405060708L, archive.timestamp(0, 0))

        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeArchive(bytes, expectedGeneration = 8)
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeArchive(bytes.copyOf(bytes.size - 1))
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeArchive(bytes.copyOf().also { it[88] = (it[88].toInt() xor 1).toByte() })
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeArchive(bytes.copyOf().also { it[37] = 3 })
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeArchive(bytes.copyOf().also { putU16(it, 76, 9_999) })
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeArchive(bytes.copyOf().also { it[124] = (it[124].toInt() xor 1).toByte() })
        }
    }

    @Test
    fun incompleteArchiveCarriesVerifiedStatusFaultsAndCounts() {
        val bytes = validArchive().also {
            it[39] = EngineeringCaptureArchiveCodec.INCOMPLETE_FLAG.toByte()
            putU32(it, 124, crc32(it, 0, 124))
        }

        val archive = EngineeringCaptureArchiveCodec.decodeArchive(bytes, faultFlags = 1)
        assertEquals(false, archive.complete)
        assertEquals(1, archive.faultFlags)
        assertEquals(10_000, archive.counts.first())
        assertThrows(ProtocolViolation::class.java) {
            EngineeringCaptureArchiveCodec.decodeArchive(bytes)
        }
    }

    @Test
    fun allSixCaptureGoldenFramesFitAndHaveValidProtocolLengths() {
        val root = Path.of(requireNotNull(System.getProperty("repositoryRoot")))
        val vectors = Files.readAllLines(root.resolve("firmware/protocol/engineering-golden-vectors.tsv"))
            .drop(1).filter { it.isNotBlank() }
        val captureRows = vectors.filter { it.substringBefore('\t').startsWith("engineering_capture_") }
        assertEquals(6, captureRows.size)
        captureRows.forEach { row ->
            val columns = row.split('\t')
            val request = columns[1].hex()
            val response = columns[2].hex()
            assertTrue(columns[0], maxOf(request.size, response.size) <= 64)
            val requestModel = vectorRequest(columns[0], request)
            val decoded = EngineeringFrameCodec.decodeResponse(response, requestModel)
            assertEquals(columns[0], requestModel.protocolMinor, decoded.protocolMinor)
        }
    }

    private fun validArchive(): ByteArray {
        val bytes = ByteArray(EngineeringCaptureArchiveCodec.ARCHIVE_SIZE)
        byteArrayOf(0x53, 0x4C, 0x41, 0x31).copyInto(bytes)
        putU16(bytes, 4, 1)
        putU16(bytes, 6, 128)
        putU32(bytes, 8, 7)
        putU64(bytes, 12, 41)
        putU32(bytes, 20, 9)
        bytes[36] = 5
        bytes[37] = 2
        bytes[38] = 2
        bytes[39] = 1
        putU16(bytes, 40, 10_000)
        putU16(bytes, 42, 8)
        putU32(bytes, 44, 150_000_000)
        putU32(bytes, 48, 600_000_000)
        putU32(bytes, 52, 60_000)
        putU64(bytes, 128, 0x0102030405060708L)
        repeat(5) { putU16(bytes, 76 + it * 2, 10_000) }
        val sampleHash = MessageDigest.getInstance("SHA-256").digest(bytes.copyOfRange(128, bytes.size))
        sampleHash.copyInto(bytes, 88)
        putU32(bytes, 120, EngineeringCaptureArchiveCodec.ARCHIVE_SIZE.toLong())
        putU32(bytes, 124, crc32(bytes, 0, 124))
        return bytes
    }

    private fun vectorRequest(@Suppress("UNUSED_PARAMETER") name: String, bytes: ByteArray): EngineeringRequest {
        val requestId = u32(bytes, 8)
        val command = u16(bytes, 12)
        val payload = bytes.copyOfRange(20, bytes.size)
        return EngineeringRequest(requestId, command, payload, bytes[5].toInt() and 0xFF)
    }

    private fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun u16(bytes: ByteArray, offset: Int) = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, index -> value or ((bytes[offset + index].toInt() and 0xFF).toLong() shl (8 * index)) }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun putU64(bytes: ByteArray, offset: Int, value: Long) {
        repeat(8) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }

    private fun crc32(bytes: ByteArray, offset: Int, length: Int): Long {
        var crc = 0xFFFF_FFFFL
        repeat(length) { index ->
            crc = crc xor (bytes[offset + index].toInt() and 0xFF).toLong()
            repeat(8) { crc = if ((crc and 1L) != 0L) (crc ushr 1) xor 0xEDB8_8320L else crc ushr 1 }
        }
        return crc xor 0xFFFF_FFFFL
    }
}
