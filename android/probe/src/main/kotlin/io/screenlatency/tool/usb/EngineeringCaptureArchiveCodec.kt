package io.screenlatency.tool.usb

import java.security.MessageDigest

data class EngineeringCaptureArchive(
    val generation: Long,
    val nonce: Long,
    val sessionGeneration: Long,
    val polarity: Int,
    val rateStage: Int,
    val complete: Boolean,
    val faultFlags: Int,
    val counts: List<Int>,
    val tickHz: Long,
    val timeoutMs: Long,
    val maximumServiceDwtCycles: Long,
    val maximumServiceLatencyTicks: Int,
    val rawBytes: ByteArray,
) {
    fun timestamp(channel: Int, sample: Int): Long {
        require(channel in 0 until EngineeringCaptureArchiveCodec.CHANNEL_COUNT &&
            sample in 0 until EngineeringCaptureArchiveCodec.SAMPLES_PER_CHANNEL)
        val index = EngineeringCaptureArchiveCodec.HEADER_SIZE +
            ((channel * EngineeringCaptureArchiveCodec.SAMPLES_PER_CHANNEL + sample) *
                EngineeringCaptureArchiveCodec.SAMPLE_SIZE)
        return EngineeringCaptureArchiveCodec.readU64(rawBytes, index)
    }
}

/** Validates E004 chunks and the fixed little-endian engineering capture archive. */
object EngineeringCaptureArchiveCodec {
    const val HEADER_SIZE = 128
    const val CHANNEL_COUNT = 5
    const val SAMPLES_PER_CHANNEL = 10_000
    const val SAMPLE_SIZE = 8
    const val SAMPLE_DATA_SIZE = CHANNEL_COUNT * SAMPLES_PER_CHANNEL * SAMPLE_SIZE
    const val ARCHIVE_SIZE = HEADER_SIZE + SAMPLE_DATA_SIZE
    const val CHUNK_SIZE = 32
    const val COMPLETE_FLAG = 1
    const val INCOMPLETE_FLAG = 2

    fun decodeChunk(
        payload: ByteArray,
        expectedGeneration: Long,
        expectedOffset: Long,
        expectedLength: Int,
    ): ByteArray {
        if (expectedGeneration !in 1..0xFFFF_FFFFL || expectedOffset !in 0..ARCHIVE_SIZE.toLong()) {
            throw ProtocolViolation("Capture chunk identity is outside its valid range")
        }
        if (expectedOffset % CHUNK_SIZE != 0L ||
            (expectedOffset == ARCHIVE_SIZE.toLong() && expectedLength != 0) ||
            (expectedOffset < ARCHIVE_SIZE && expectedLength != CHUNK_SIZE)
        ) {
            throw ProtocolViolation("Capture chunk request is not aligned or has the wrong length")
        }
        if (payload.size != EngineeringFrameCodec.ENGINEERING_CAPTURE_READ_METADATA_SIZE + expectedLength) {
            throw ProtocolViolation("Capture chunk response length does not match the request")
        }

        val generation = readU32(payload, 0)
        val offset = readU32(payload, 4)
        val crc = readU32(payload, 8)
        val data = payload.copyOfRange(12, payload.size)
        if (generation != expectedGeneration || offset != expectedOffset) {
            throw ProtocolViolation("Capture chunk generation or offset changed during retrieval")
        }
        val expectedCrc = chunkCrc(generation, offset, data)
        if (crc != expectedCrc) throw ProtocolViolation("Capture chunk CRC-32 mismatch")
        return data
    }

    fun decodeArchive(
        bytes: ByteArray,
        expectedGeneration: Long? = null,
        expectedNonce: Long? = null,
        expectedSessionGeneration: Long? = null,
        faultFlags: Int = 0,
    ): EngineeringCaptureArchive {
        if (bytes.size != ARCHIVE_SIZE) throw ProtocolViolation("Capture archive must be exactly 400,128 bytes")
        if (!bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x53, 0x4C, 0x41, 0x31))) {
            throw ProtocolViolation("Capture archive magic is not SLA1")
        }
        if (readU16(bytes, 4) != 1 || readU16(bytes, 6) != HEADER_SIZE) {
            throw ProtocolViolation("Capture archive format or header size is unsupported")
        }

        val generation = readU32(bytes, 8)
        val nonce = readU64(bytes, 12)
        val sessionGeneration = readU32(bytes, 20)
        val channelCount = u8(bytes, 36)
        val polarity = u8(bytes, 37)
        val rateStage = u8(bytes, 38)
        val completion = u8(bytes, 39)
        val samplesPerChannel = readU16(bytes, 40)
        val sampleSize = readU16(bytes, 42)
        val tickHz = readU32(bytes, 44)
        val timeoutMs = readU32(bytes, 52)
        val maximumServiceDwtCycles = readU32(bytes, 56)
        val maximumServiceLatencyTicks = readU16(bytes, 86)
        val counts = (0 until CHANNEL_COUNT).map { readU16(bytes, 76 + it * 2) }
        val archiveLength = readU32(bytes, 120)
        val headerCrc = readU32(bytes, 124)

        if (generation == 0L || nonce == 0L || sessionGeneration == 0L ||
            (expectedGeneration != null && generation != expectedGeneration) ||
            (expectedNonce != null && nonce != expectedNonce) ||
            (expectedSessionGeneration != null && sessionGeneration != expectedSessionGeneration)
        ) {
            throw ProtocolViolation("Capture archive identity does not match the accepted run")
        }
        if (channelCount != CHANNEL_COUNT || samplesPerChannel != SAMPLES_PER_CHANNEL ||
            sampleSize != SAMPLE_SIZE || polarity !in 1..2 || rateStage !in 1..2 ||
            completion !in COMPLETE_FLAG..INCOMPLETE_FLAG || tickHz == 0L ||
            timeoutMs !in 1_000..86_400_000L || archiveLength != ARCHIVE_SIZE.toLong() ||
            faultFlags !in 0..0xFFFF || counts.any { it !in 0..SAMPLES_PER_CHANNEL } ||
            (completion == COMPLETE_FLAG &&
                (counts.any { it != SAMPLES_PER_CHANNEL } || faultFlags != 0)) ||
            (completion == INCOMPLETE_FLAG && faultFlags == 0)
        ) {
            throw ProtocolViolation("Capture archive metadata violates the fixed format")
        }
        if (crc32(bytes, 0, 124) != headerCrc) throw ProtocolViolation("Capture archive header CRC-32 mismatch")

        val expectedHash = bytes.copyOfRange(88, 120)
        val actualHash = MessageDigest.getInstance("SHA-256")
            .digest(bytes.copyOfRange(HEADER_SIZE, bytes.size))
        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            throw ProtocolViolation("Capture archive sample SHA-256 mismatch")
        }

        return EngineeringCaptureArchive(
            generation = generation,
            nonce = nonce,
            sessionGeneration = sessionGeneration,
            polarity = polarity,
            rateStage = rateStage,
            complete = completion == COMPLETE_FLAG,
            faultFlags = faultFlags,
            counts = counts,
            tickHz = tickHz,
            timeoutMs = timeoutMs,
            maximumServiceDwtCycles = maximumServiceDwtCycles,
            maximumServiceLatencyTicks = maximumServiceLatencyTicks,
            rawBytes = bytes.copyOf(),
        )
    }

    fun chunkCrc(generation: Long, offset: Long, data: ByteArray): Long {
        val crcInput = ByteArray(9 + data.size)
        putU32(crcInput, 0, generation)
        putU32(crcInput, 4, offset)
        crcInput[8] = data.size.toByte()
        data.copyInto(crcInput, 9)
        return crc32(crcInput, 0, crcInput.size)
    }

    private fun crc32(bytes: ByteArray, offset: Int, length: Int): Long {
        var crc = 0xFFFF_FFFFL
        repeat(length) { index ->
            crc = crc xor u8(bytes, offset + index).toLong()
            repeat(8) {
                crc = if ((crc and 1L) != 0L) (crc ushr 1) xor 0xEDB8_8320L else crc ushr 1
            }
        }
        return crc xor 0xFFFF_FFFFL
    }

    private fun u8(bytes: ByteArray, offset: Int) = bytes[offset].toInt() and 0xFF

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, index -> value or (u8(bytes, offset + index).toLong() shl (8 * index)) }

    internal fun readU64(bytes: ByteArray, offset: Int): Long =
        (0..7).fold(0L) { value, index -> value or (u8(bytes, offset + index).toLong() shl (8 * index)) }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (8 * index)).toByte() }
    }
}
