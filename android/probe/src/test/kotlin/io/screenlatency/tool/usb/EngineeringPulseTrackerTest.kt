package io.screenlatency.tool.usb

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringPulseTrackerTest {
    @Test
    fun decodesEngineeringInfoWithItsExactPayloadContract() {
        val queued = info("engineering_info_request_0x01020304")
        val completed = info("engineering_info_completed_wrapped_tick_0x01020304")

        assertEquals(1, queued.protocolMajor)
        assertEquals(0, queued.protocolMinor)
        assertEquals(0, queued.firmwareMajor)
        assertEquals(1, queued.firmwareMinor)
        assertEquals(EngineeringUsbSpeed.FULL, queued.usbSpeed)
        assertEquals(0x0123456789ABCDEFL, queued.serialNumber)
        assertEquals(1L, queued.sequence)
        assertEquals(EngineeringEngineState.PRESS_QUEUED, queued.state)
        assertTrue(queued.fullSpeedVerified)
        assertTrue(queued.usbSessionValid)
        assertFalse(queued.completionTickValid)
        assertEquals(0L, queued.completionTick)
        assertEquals(600_000_000L, queued.completionCounterFrequencyHz)
        assertEquals(EngineeringEngineState.COMPLETE, completed.state)
        assertTrue(completed.completionTickValid)
        assertEquals(0xF1234567L, completed.completionTick)
        assertEquals(600_000_000L, completed.completionCounterFrequencyHz)
    }

    @Test
    fun acceptsTheRetainedCompletionTickAfterTheTerminalSnapshotReturnsIdle() {
        val tracker = trackerFromSharedAcknowledgement()
        val idleWithCompletion = EngineeringInfoCodec.decode(
            infoPayload(state = 0, flags = 0x07, tick = 1234),
        )

        assertTrue(idleWithCompletion.completionTickValid)
        assertEquals(1234L, idleWithCompletion.completionTick)
        assertEquals(600_000_000L, idleWithCompletion.completionCounterFrequencyHz)
        assertEquals(PulseUpdate.Complete, tracker.observe(idleWithCompletion))
    }

    @Test
    fun rejectsMalformedEngineeringInfoVersionsStatesFlagsSpeedsAndLengths() {
        val vector = infoPayload("engineering_info_request_0x01020304")
        val malformed = listOf(
            vector.copyOf(31),
            vector.copyOf().also { it[0] = 2 },
            vector.copyOf().also { it[4] = 4 },
            vector.copyOf().also { it[4] = 3 },
            vector.copyOf().also { it[5] = (it[5].toInt() and 0xF8 or 7).toByte() },
            vector.copyOf().also { it[5] = (it[5].toInt() or 0x80).toByte() },
            vector.copyOf().also { putU32(it, 26, 1) },
            vector.copyOf().also { putU16(it, 30, 9) },
        )

        malformed.forEach { payload ->
            assertThrows(ProtocolViolation::class.java) { EngineeringInfoCodec.decode(payload) }
        }
    }

    @Test
    fun reportsSessionInvalidationWhenFullSpeedEnumIsNotVerified() {
        val tracker = trackerFromSharedAcknowledgement()
        val invalidated = EngineeringInfoCodec.decode(
            infoPayload(state = 6, flags = 0),
        )

        assertEquals(EngineeringUsbSpeed.FULL, invalidated.usbSpeed)
        assertFalse(invalidated.fullSpeedVerified)
        assertFalse(invalidated.usbSessionValid)
        assertEquals(PulseUpdate.Invalidated, tracker.observe(invalidated))
    }

    @Test
    fun decodesTheAcceptedSequenceFromTheSharedPressAcknowledgement() {
        val response = response("engineering_press_10ms_request_2")
        val acknowledgement = EngineeringPulseAcknowledgement.decode(response, expectedTag = 2)

        assertEquals(2L, acknowledgement.requestTag)
        assertEquals(1L, acknowledgement.sequence)
        assertEquals(EngineeringEngineState.PRESS_QUEUED, acknowledgement.state)
    }

    @Test
    fun rejectsMalformedAcknowledgementStateAndReservedBytes() {
        val request = requestFor("engineering_press_10ms_request_2")
        val valid = response("engineering_press_10ms_request_2")
        val wrongState = valid.copy(payload = valid.payload.copyOf().also { it[8] = 2 })
        val wrongTag = valid.copy(payload = valid.payload.copyOf().also { it[0] = 3 })
        val reserved = valid.copy(payload = valid.payload.copyOf().also { it[9] = 1 })

        assertThrows(ProtocolViolation::class.java) {
            EngineeringPulseAcknowledgement.decode(wrongState, expectedTag = 2)
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringPulseAcknowledgement.decode(wrongTag, expectedTag = 2)
        }
        assertThrows(ProtocolViolation::class.java) {
            EngineeringPulseAcknowledgement.decode(reserved, expectedTag = 2)
        }
        assertEquals(2L, request.requestId)
    }

    @Test
    fun tracksQueuedHoldingReleaseAndCompletionForOneAcceptedSequence() {
        val tracker = trackerFromSharedAcknowledgement()
        assertEquals(PulseUpdate.Queued, tracker.observe(info("engineering_info_request_0x01020304")))
        assertEquals(
            PulseUpdate.Holding,
            tracker.observe(EngineeringInfoCodec.decode(infoPayload(state = 2, flags = 0x07, tick = 1234, transferGeneration = 2))),
        )
        assertEquals(
            PulseUpdate.ReleaseQueued,
            tracker.observe(EngineeringInfoCodec.decode(infoPayload(state = 3, flags = 0x07, tick = 1234, transferGeneration = 3))),
        )
        assertEquals(
            PulseUpdate.Complete,
            tracker.observe(EngineeringInfoCodec.decode(infoPayload(state = 4, flags = 0x07, tick = 1234, transferGeneration = 3))),
        )
    }

    @Test
    fun invalidatesCompletionFromAnOlderTransferGeneration() {
        val tracker = trackerFromSharedAcknowledgement()
        val release = EngineeringInfoCodec.decode(
            infoPayload(state = 3, flags = 0x07, tick = 1234, transferGeneration = 3),
        )
        val staleCompletion = EngineeringInfoCodec.decode(
            infoPayload(state = 4, flags = 0x07, tick = 1234, transferGeneration = 2),
        )

        assertEquals(PulseUpdate.ReleaseQueued, tracker.observe(release))
        assertEquals(PulseUpdate.Invalidated, tracker.observe(staleCompletion))
    }

    @Test
    fun rejectsAnInformationSnapshotForAnotherPulseSequence() {
        val tracker = trackerFromSharedAcknowledgement()
        val stale = infoPayload("engineering_info_request_0x01020304").also { putU32(it, 14, 2) }

        assertThrows(StalePulseSequenceException::class.java) {
            tracker.observe(EngineeringInfoCodec.decode(stale))
        }
    }

    @Test
    fun reportsRecoveryCompleteWhenItClearsRecoveryButRetainsThePulseFault() {
        val tracker = trackerFromSharedAcknowledgement()
        val recovering = EngineeringInfoCodec.decode(
            infoPayload(
                state = 5,
                flags = 0x0F,
                tick = 1234,
            ),
        )
        val recovered = EngineeringInfoCodec.decode(
            infoPayload(
                state = 6,
                flags = 0x07,
                tick = 1234,
            ),
        )

        assertEquals(EngineeringFault.UNSPECIFIED, recovered.fault)
        assertEquals(1234L, recovered.completionTick)
        assertEquals(PulseUpdate.Recovering, tracker.observe(recovering))
        assertEquals(PulseUpdate.RecoveryComplete, tracker.observe(recovered))
    }

    @Test
    fun preservesSuccessfulPressTickWhileReleaseRecoveryReportsItsFault() {
        val tracker = trackerFromSharedAcknowledgement()
        val recovery = EngineeringInfoCodec.decode(
            infoPayload(state = 5, flags = 0x0F, tick = 1234),
        )
        val fault = EngineeringInfoCodec.decode(
            infoPayload(state = 6, flags = 0x0F, tick = 1234),
        )

        assertTrue(recovery.completionTickValid)
        assertEquals(1234L, recovery.completionTick)
        assertEquals(PulseUpdate.Recovering, tracker.observe(recovery))
        assertEquals(
            PulseUpdate.Failed(ProtocolStatus.HARDWARE_FAULT, EngineeringFault.UNSPECIFIED),
            tracker.observe(fault),
        )
    }

    @Test
    fun keepsPollingPulseStateWhenThePreviousCommandWasBusyOrFailed() {
        val busyTracker = trackerFromSharedAcknowledgement()
        val queuedAfterBusy = EngineeringInfoCodec.decode(
            infoPayload(state = 1, flags = 0x06),
        )
        val failedCommandTracker = trackerFromSharedAcknowledgement()
        val holdingAfterFailure = EngineeringInfoCodec.decode(
            infoPayload(state = 2, flags = 0x07, tick = 1234),
        )

        assertEquals(PulseUpdate.Queued, busyTracker.observe(queuedAfterBusy))
        assertEquals(PulseUpdate.Holding, failedCommandTracker.observe(holdingAfterFailure))
    }

    @Test
    fun treatsLostUsbSessionAsInvalidated() {
        val tracker = trackerFromSharedAcknowledgement()
        val disconnected = EngineeringInfoCodec.decode(infoPayload(state = 5, flags = 0x0A))

        assertEquals(PulseUpdate.Invalidated, tracker.observe(disconnected))
    }

    private fun trackerFromSharedAcknowledgement(): EngineeringPulseTracker {
        val acknowledgement = EngineeringPulseAcknowledgement.decode(
            response("engineering_press_10ms_request_2"),
            expectedTag = 2,
        )
        return EngineeringPulseTracker(acknowledgement.sequence, durationMs = 10)
    }

    private fun info(name: String) = EngineeringInfoCodec.decode(infoPayload(name))

    private fun infoPayload(name: String): ByteArray = response(name).payload

    private fun response(name: String): EngineeringResponse {
        val vector = vector(name)
        val request = requestFor(name)
        return EngineeringFrameCodec.decodeResponse(vector.response, request)
    }

    private fun requestFor(name: String): EngineeringRequest = when (name) {
        "engineering_info_request_0x01020304",
        "engineering_info_completed_wrapped_tick_0x01020304" -> EngineeringRequest(0x01020304L, 0xE001, byteArrayOf())
        "engineering_press_10ms_request_2" -> EngineeringRequest(
            2,
            0xE002,
            EngineeringFrameCodec.encodePulseRequest(tag = 2, durationMs = 10),
        )
        else -> error("No test request mapping for $name")
    }

    private fun vector(name: String): GoldenVector {
        val repositoryRoot = Path.of(requireNotNull(System.getProperty("repositoryRoot")))
        val path = repositoryRoot.resolve("firmware/protocol/engineering-golden-vectors.tsv")
        val row = Files.readAllLines(path).drop(1).single { it.substringBefore('\t') == name }
        val columns = row.split('\t')
        return GoldenVector(columns[1].hexBytes(), columns[2].hexBytes())
    }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun infoPayload(
        state: Int = 1,
        flags: Int = 0x06,
        tick: Long = 0,
        transferGeneration: Long = 2,
    ): ByteArray = ByteArray(32).also { bytes ->
        bytes[0] = 1
        bytes[1] = 0
        bytes[2] = 0
        bytes[3] = 1
        bytes[4] = EngineeringUsbSpeed.FULL.wireValue.toByte()
        bytes[5] = (state or (flags shl 3)).toByte()
        putU64(bytes, 6, 0x0123456789ABCDEFL)
        putU32(bytes, 14, 1)
        putU32(bytes, 18, 1)
        putU32(bytes, 22, transferGeneration)
        putU32(bytes, 26, tick)
        putU16(bytes, 30, 10)
    }

    private fun putU64(bytes: ByteArray, offset: Int, value: Long) {
        repeat(8) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private data class GoldenVector(val request: ByteArray, val response: ByteArray)
}
