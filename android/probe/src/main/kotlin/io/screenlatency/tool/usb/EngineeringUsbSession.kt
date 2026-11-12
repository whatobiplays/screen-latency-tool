package io.screenlatency.tool.usb

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

fun interface MonotonicClock {
    fun nowMillis(): Long
}

fun interface ProbeSleeper {
    fun sleep(millis: Long)
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowMillis(): Long = System.nanoTime() / 1_000_000L
}

object ThreadProbeSleeper : ProbeSleeper {
    override fun sleep(millis: Long) = Thread.sleep(millis)
}

sealed interface PulseRunResult {
    data object Completed : PulseRunResult
    data object Busy : PulseRunResult
    data object Invalidated : PulseRunResult
    data object TimedOut : PulseRunResult
    data object RecoveryComplete : PulseRunResult
    data class Rejected(val status: ProtocolStatus) : PulseRunResult
    data class Failed(val status: ProtocolStatus, val fault: EngineeringFault) : PulseRunResult
}

data class EngineeringCaptureReference(
    val fixtureSerial: String,
    val nonce: Long,
    val archiveGeneration: Long,
    val sessionGeneration: Long,
    val polarity: Int,
    val rateStage: Int,
    val timeoutMs: Long,
    val captureWindowElapsed: Boolean = false,
)

sealed interface EngineeringCaptureRunResult {
    data class Complete(val archive: EngineeringCaptureArchive) : EngineeringCaptureRunResult
    data class Incomplete(val archive: EngineeringCaptureArchive) : EngineeringCaptureRunResult
    data object Unsupported : EngineeringCaptureRunResult
    data class Rejected(val status: ProtocolStatus) : EngineeringCaptureRunResult
    data class Interrupted(val reference: EngineeringCaptureReference?) : EngineeringCaptureRunResult
    data object Unavailable : EngineeringCaptureRunResult
}

class EngineeringCaptureArchiveUnavailableException : IOException(
    "The fixture no longer retains this capture archive",
)

/** Owns vendor interface 1 and polls one accepted pulse using a single request-ID sequence. */
class EngineeringUsbSession internal constructor(
    val identity: ValidatedFixture,
    private val connection: UsbConnectionPort,
    private val bulkOutEndpoint: UsbEndpointSnapshot,
    private val bulkInEndpoint: UsbEndpointSnapshot,
    private val requestIds: EngineeringRequestIdSequence,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val invalidated = AtomicBoolean(false)
    private val operationStarted = AtomicBoolean(false)

    val isClosed: Boolean get() = closed.get()

    /** Runs one bounded pulse operation and closes the claim when its owner finishes. */
    fun runEngineeringPulse(
        durationMs: Int,
        timeoutMs: Long = DEFAULT_PULSE_TIMEOUT_MS,
        pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        clock: MonotonicClock = SystemMonotonicClock,
        sleeper: ProbeSleeper = ThreadProbeSleeper,
        onSequenceAccepted: (Long) -> Unit = {},
        onProgress: (PulseUpdate, EngineeringInfo?) -> Unit = { _, _ -> },
    ): PulseRunResult {
        var ownsOperation = false
        try {
            if (durationMs !in 10..1000 || timeoutMs <= 0 || pollIntervalMs <= 0) {
                throw ProtocolViolation("Pulse duration, timeout, and poll interval are outside their valid ranges")
            }
            if (!operationStarted.compareAndSet(false, true)) {
                throw IllegalStateException("A USB session can run only one pulse operation")
            }
            ownsOperation = true
            ensureOpen()

            val identityResponse = exchange(
                EngineeringRequest(
                    requestId = allocateRequestId(),
                    command = EngineeringFrameCodec.ENGINEERING_INFO,
                    payload = byteArrayOf(),
                ),
            )
            if (identityResponse.status != ProtocolStatus.OK) {
                onProgress(PulseUpdate.Failed(identityResponse.status, EngineeringFault.NONE), null)
                return PulseRunResult.Rejected(identityResponse.status)
            }
            val identityInfo = EngineeringInfoCodec.decode(identityResponse.payload)
            val expectedSerialNumber = identity.serial.toULong(16).toLong()
            if (identityInfo.serialNumber != expectedSerialNumber) {
                throw ProtocolViolation("ENGINEERING_INFO serial does not match protected USB serial")
            }

        val pulseTag = allocateRequestId()
        val pressRequest = EngineeringRequest(
            requestId = pulseTag,
                command = EngineeringFrameCodec.ENGINEERING_PRESS_PULSE,
            payload = EngineeringFrameCodec.encodePulseRequest(
                tag = pulseTag,
                durationMs = durationMs,
            ),
            )
            val pressResponse = exchange(pressRequest)
            if (pressResponse.status == ProtocolStatus.BUSY) {
                onProgress(PulseUpdate.Busy, null)
                return pollActivePulseAfterBusy(timeoutMs, pollIntervalMs, clock, sleeper, onProgress)
            }
            if (pressResponse.status != ProtocolStatus.OK) {
                onProgress(PulseUpdate.Failed(pressResponse.status, EngineeringFault.NONE), null)
                return PulseRunResult.Rejected(pressResponse.status)
            }

        val acknowledgement = EngineeringPulseAcknowledgement.decode(
            pressResponse,
            expectedTag = pulseTag,
        )
            onSequenceAccepted(acknowledgement.sequence)
            val tracker = EngineeringPulseTracker(acknowledgement.sequence, durationMs)
            val start = clock.nowMillis()

            while (true) {
                ensureOpen()
            val elapsed = clock.nowMillis() - start
            if (elapsed >= timeoutMs) return timeout(onProgress)
            val remaining = timeoutMs - elapsed
                sleeper.sleep(min(pollIntervalMs, remaining))
                ensureOpen()
            if (clock.nowMillis() - start >= timeoutMs) return timeout(onProgress)

                val informationRequest = EngineeringRequest(
                    requestId = allocateRequestId(),
                    command = EngineeringFrameCodec.ENGINEERING_INFO,
                    payload = byteArrayOf(),
                )
                val response = exchange(informationRequest)
            if (clock.nowMillis() - start >= timeoutMs) return timeout(onProgress)
                if (response.status == ProtocolStatus.BUSY) {
                    onProgress(PulseUpdate.Busy, null)
                    continue
                }
                if (response.status != ProtocolStatus.OK) {
                    onProgress(PulseUpdate.Failed(response.status, EngineeringFault.NONE), null)
                    return PulseRunResult.Failed(response.status, EngineeringFault.NONE)
                }

                val info = EngineeringInfoCodec.decode(response.payload)
                val update = tracker.observe(info)
                onProgress(update, info)
                when (update) {
                    PulseUpdate.Complete -> return PulseRunResult.Completed
                    PulseUpdate.Invalidated -> return PulseRunResult.Invalidated
                    PulseUpdate.TimedOut -> return PulseRunResult.TimedOut
                    PulseUpdate.RecoveryComplete -> return PulseRunResult.RecoveryComplete
                    is PulseUpdate.Failed -> return PulseRunResult.Failed(update.status, update.fault)
                    else -> Unit
                }
            }
        } catch (failure: Exception) {
            if (invalidated.get()) return PulseRunResult.Invalidated
            throw failure
        } finally {
            if (ownsOperation) close()
        }
    }

    /** Negotiates 1.1, starts one bounded capture, waits without vendor traffic, and downloads verified bytes. */
    fun runEngineeringCapture(
        polarity: Int,
        rateStage: Int,
        timeoutMs: Long,
        retrievalTimeoutMs: Long = DEFAULT_CAPTURE_RETRIEVAL_TIMEOUT_MS,
        clock: MonotonicClock = SystemMonotonicClock,
        sleeper: ProbeSleeper = ThreadProbeSleeper,
        onCapturePrepared: (EngineeringCaptureReference) -> Unit = {},
        onCaptureAccepted: (EngineeringCaptureReference) -> Unit = {},
        onCaptureReadyForStimulus: (EngineeringCaptureReference) -> Unit = {},
        onCaptureWindowElapsed: (EngineeringCaptureReference) -> Unit = {},
        onChunkReceived: (EngineeringCaptureReference, Long, ByteArray) -> Unit = { _, _, _ -> },
    ): EngineeringCaptureRunResult {
        if (polarity !in 1..2 || rateStage !in 1..2 ||
            timeoutMs !in MIN_CAPTURE_TIMEOUT_MS..86_400_000L ||
            retrievalTimeoutMs !in 1..86_400_000L
        ) {
            throw ProtocolViolation("Capture polarity, rate stage, or timeout is outside its valid range")
        }
        if (!operationStarted.compareAndSet(false, true)) {
            throw IllegalStateException("A USB session can run only one engineering operation")
        }
        var reference: EngineeringCaptureReference? = null
        var pendingNonce: Long? = null
        try {
            ensureOpen()
            if (!negotiateCaptureProtocol()) return EngineeringCaptureRunResult.Unsupported

            val requestId = allocateRequestId()
            pendingNonce = requestId
            val preparedReference = EngineeringCaptureReference(
                fixtureSerial = identity.serial,
                nonce = requestId,
                archiveGeneration = 0,
                sessionGeneration = 0,
                polarity = polarity,
                rateStage = rateStage,
                timeoutMs = timeoutMs,
            )
            reference = preparedReference
            onCapturePrepared(preparedReference)
            val begin = EngineeringRequest(
                requestId = requestId,
                command = EngineeringFrameCodec.ENGINEERING_BEGIN_CAPTURE,
                payload = beginCapturePayload(requestId, polarity, rateStage, timeoutMs),
                protocolMinor = EngineeringFrameCodec.PROTOCOL_MINOR_1_1,
            )
            // A lost BEGIN acknowledgement must not trigger a vendor OUT retry
            // after firmware may have completed the IN transfer and armed capture.
            val response = exchange(begin, retryOnEmptyResponse = false)
            if (response.status != ProtocolStatus.OK) return EngineeringCaptureRunResult.Rejected(response.status)
            if (response.payload.size != EngineeringFrameCodec.ENGINEERING_BEGIN_CAPTURE_ACK_SIZE ||
                readU64(response.payload, 0) != requestId ||
                readU32(response.payload, 8) == 0L ||
                readU32(response.payload, 12) == 0L ||
                (response.payload[16].toInt() and 0xFF) != 1 ||
                (response.payload[17].toInt() and 0xFF) != polarity ||
                readU16(response.payload, 18) != EngineeringCaptureArchiveCodec.SAMPLES_PER_CHANNEL
            ) {
                throw ProtocolViolation("Capture acknowledgement does not match the requested run")
            }
            reference = EngineeringCaptureReference(
                fixtureSerial = identity.serial,
                nonce = requestId,
                archiveGeneration = readU32(response.payload, 8),
                sessionGeneration = readU32(response.payload, 12),
                polarity = polarity,
                rateStage = rateStage,
                timeoutMs = timeoutMs,
            )
            onCaptureAccepted(reference)

            // The fixture runner may start stimulus only after this quiet arm
            // interval. No vendor requests are issued during either wait.
            waitWithoutVendorTraffic(CAPTURE_ARM_SETTLE_MS, clock, sleeper)
            onCaptureReadyForStimulus(reference)
            waitWithoutVendorTraffic(timeoutMs, clock, sleeper)
            reference = reference.copy(captureWindowElapsed = true)
            onCaptureWindowElapsed(reference)
            val archive = downloadCaptureArchive(reference, retrievalTimeoutMs, clock, onChunkReceived)
            return if (archive.complete) {
                EngineeringCaptureRunResult.Complete(archive)
            } else {
                EngineeringCaptureRunResult.Incomplete(archive)
            }
        } catch (_: EngineeringCaptureArchiveUnavailableException) {
            return EngineeringCaptureRunResult.Unavailable
        } catch (failure: Exception) {
            if (invalidated.get()) {
                return EngineeringCaptureRunResult.Interrupted(
                    reference ?: pendingNonce?.let {
                        EngineeringCaptureReference(identity.serial, it, 0, 0, polarity, rateStage, timeoutMs)
                    },
                )
            }
            if (pendingNonce != null && failure is IOException) {
                return EngineeringCaptureRunResult.Interrupted(
                    reference ?: EngineeringCaptureReference(
                        identity.serial, pendingNonce, 0, 0, polarity, rateStage, timeoutMs,
                    ),
                )
            }
            throw failure
        } finally {
            close()
        }
    }

    /** Resumes raw retrieval by fixture, boot-retained archive generation, nonce, and original session. */
    fun retrieveEngineeringCapture(
        reference: EngineeringCaptureReference,
        retrievalTimeoutMs: Long = DEFAULT_CAPTURE_RETRIEVAL_TIMEOUT_MS,
        clock: MonotonicClock = SystemMonotonicClock,
        sleeper: ProbeSleeper = ThreadProbeSleeper,
        onCaptureWindowElapsed: (EngineeringCaptureReference) -> Unit = {},
        onChunkReceived: (EngineeringCaptureReference, Long, ByteArray) -> Unit = { _, _, _ -> },
    ): EngineeringCaptureArchive {
        if (!operationStarted.compareAndSet(false, true)) {
            throw IllegalStateException("A USB session can run only one engineering operation")
        }
        try {
            ensureOpen()
            requireReference(reference, allowPendingGeneration = true)
            val readyReference = if (reference.captureWindowElapsed) {
                reference
            } else {
                waitWithoutVendorTraffic(reference.timeoutMs + CAPTURE_ARM_SETTLE_MS,
                    clock, sleeper)
                reference.copy(captureWindowElapsed = true).also(onCaptureWindowElapsed)
            }
            // A reconnect can happen while the fixture is still acquiring. Even
            // ENGINEERING_INFO is vendor traffic and would abort that run, so
            // defer protocol negotiation until the conservative quiet window ends.
            if (!negotiateCaptureProtocol()) throw ProtocolViolation("Fixture does not support capture protocol 1.1")
            return downloadCaptureArchive(readyReference, retrievalTimeoutMs, clock, onChunkReceived)
        } finally {
            close()
        }
    }

    /** Releases the single retained archive only after a caller has retained its validated bytes. */
    fun releaseEngineeringCapture(reference: EngineeringCaptureReference): Boolean {
        if (!operationStarted.compareAndSet(false, true)) {
            throw IllegalStateException("A USB session can run only one engineering operation")
        }
        try {
            ensureOpen()
            requireReference(reference)
            if (!negotiateCaptureProtocol()) return false
            val request = EngineeringRequest(
                requestId = allocateRequestId(),
                command = EngineeringFrameCodec.ENGINEERING_CAPTURE_ARCHIVE,
                payload = archivePayload(EngineeringFrameCodec.CAPTURE_RELEASE,
                    reference.archiveGeneration, 0, 0),
                protocolMinor = EngineeringFrameCodec.PROTOCOL_MINOR_1_1,
            )
            return exchange(request).status == ProtocolStatus.OK
        } finally {
            close()
        }
    }

    private fun pollActivePulseAfterBusy(
        timeoutMs: Long,
        pollIntervalMs: Long,
        clock: MonotonicClock,
        sleeper: ProbeSleeper,
        onProgress: (PulseUpdate, EngineeringInfo?) -> Unit,
    ): PulseRunResult {
        val start = clock.nowMillis()
        var tracker: EngineeringPulseTracker? = null

        while (true) {
            ensureOpen()
            val elapsed = clock.nowMillis() - start
            if (elapsed >= timeoutMs) return timeout(onProgress)
            val remaining = timeoutMs - elapsed
            sleeper.sleep(min(pollIntervalMs, remaining))
            ensureOpen()
            if (clock.nowMillis() - start >= timeoutMs) return timeout(onProgress)

            val informationRequest = EngineeringRequest(
                requestId = allocateRequestId(),
                command = EngineeringFrameCodec.ENGINEERING_INFO,
                payload = byteArrayOf(),
            )
            val response = exchange(informationRequest)
            if (clock.nowMillis() - start >= timeoutMs) return timeout(onProgress)
            if (response.status == ProtocolStatus.BUSY) {
                onProgress(PulseUpdate.Busy, null)
                continue
            }
            if (response.status != ProtocolStatus.OK) {
                onProgress(PulseUpdate.Failed(response.status, EngineeringFault.NONE), null)
                return PulseRunResult.Failed(response.status, EngineeringFault.NONE)
            }

            val information = EngineeringInfoCodec.decode(response.payload)
            val activeTracker = tracker ?: run {
                if (information.sequence == 0L) {
                    throw ProtocolViolation("BUSY press response had no active pulse to observe")
                }
                EngineeringPulseTracker(information.sequence, information.durationMs)
                    .also { tracker = it }
            }
            val update = activeTracker.observe(information)
            onProgress(update, information)
            when (update) {
                PulseUpdate.Complete -> return PulseRunResult.Busy
                PulseUpdate.Busy -> Unit
                PulseUpdate.Invalidated -> return PulseRunResult.Invalidated
                PulseUpdate.TimedOut -> return PulseRunResult.TimedOut
                PulseUpdate.RecoveryComplete -> return PulseRunResult.RecoveryComplete
                is PulseUpdate.Failed -> return PulseRunResult.Failed(update.status, update.fault)
                else -> Unit
            }
        }
    }

    /** Releases the claimed vendor interface, then closes its connection. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            runCatching { connection.releaseInterface(FixtureIdentityValidator.VENDOR_INTERFACE) }
        } finally {
            runCatching { connection.close() }
        }
    }

    fun invalidate() {
        invalidated.set(true)
        close()
    }

    private fun timeout(onProgress: (PulseUpdate, EngineeringInfo?) -> Unit): PulseRunResult.TimedOut {
        onProgress(PulseUpdate.TimedOut, null)
        return PulseRunResult.TimedOut
    }

    private fun exchange(
        request: EngineeringRequest,
        retryOnEmptyResponse: Boolean = true,
    ): EngineeringResponse {
        ensureOpen()
        val outbound = EngineeringFrameCodec.encodeRequest(request)
        val written = connection.bulkOut(bulkOutEndpoint, outbound, TRANSFER_TIMEOUT_MS)
        if (written != outbound.size) throw IOException("Short USB bulk OUT transfer: $written/${outbound.size}")
        repeat(MAX_ACK_ATTEMPTS) { attempt ->
            ensureOpen()
            val inbound = connection.bulkIn(bulkInEndpoint, MAX_FRAME_SIZE, TRANSFER_TIMEOUT_MS)
            if (inbound.isNotEmpty()) {
                if (inbound.size > MAX_FRAME_SIZE) {
                    throw ProtocolViolation("USB response exceeds the engineering frame limit")
                }
                return EngineeringFrameCodec.decodeResponse(inbound, request)
            }
            if (!retryOnEmptyResponse) {
                throw IOException("USB bulk IN returned no response; capture BEGIN was not retried")
            }
            if (attempt + 1 == MAX_ACK_ATTEMPTS) {
                throw IOException("USB bulk IN returned no response after the exact-request retry")
            }
            ensureOpen()
            val retryWritten = connection.bulkOut(bulkOutEndpoint, outbound, TRANSFER_TIMEOUT_MS)
            if (retryWritten != outbound.size) {
                throw IOException("Short USB bulk OUT retry: $retryWritten/${outbound.size}")
            }
        }
        throw IOException("USB bulk IN returned no response")
    }

    private fun negotiateCaptureProtocol(): Boolean {
        val captureProbe = EngineeringRequest(
            requestId = allocateRequestId(),
            command = EngineeringFrameCodec.ENGINEERING_INFO,
            payload = byteArrayOf(),
            protocolMinor = EngineeringFrameCodec.PROTOCOL_MINOR_1_1,
        )
        val captureResponse = exchange(captureProbe)
        if (captureResponse.status == ProtocolStatus.OK) {
            val info = EngineeringInfoCodec.decode(captureResponse.payload)
            verifyFixtureIdentity(info)
            if (info.protocolMinor != EngineeringFrameCodec.PROTOCOL_MINOR_1_1) {
                throw ProtocolViolation("Fixture response did not negotiate protocol minor 1.1")
            }
            return true
        }
        if (captureResponse.status != ProtocolStatus.UNSUPPORTED_VERSION || captureResponse.protocolMinor != 0) {
            throw ProtocolViolation("Fixture protocol negotiation failed: ${captureResponse.status}")
        }

        // A legacy fixture gets a new monotonically allocated ID for its 1.0 fallback probe.
        val legacyProbe = EngineeringRequest(
            requestId = allocateRequestId(),
            command = EngineeringFrameCodec.ENGINEERING_INFO,
            payload = byteArrayOf(),
            protocolMinor = EngineeringFrameCodec.PROTOCOL_MINOR_1_0,
        )
        val legacyResponse = exchange(legacyProbe)
        if (legacyResponse.status != ProtocolStatus.OK) return false
        val info = EngineeringInfoCodec.decode(legacyResponse.payload)
        verifyFixtureIdentity(info)
        if (info.protocolMinor != EngineeringFrameCodec.PROTOCOL_MINOR_1_0) {
            throw ProtocolViolation("Legacy fixture returned an inconsistent protocol version")
        }
        return false
    }

    private fun verifyFixtureIdentity(info: EngineeringInfo) {
        val expectedSerialNumber = identity.serial.toULong(16).toLong()
        if (info.serialNumber != expectedSerialNumber) {
            throw ProtocolViolation("ENGINEERING_INFO serial does not match protected USB serial")
        }
    }

    private fun downloadCaptureArchive(
        reference: EngineeringCaptureReference,
        retrievalTimeoutMs: Long,
        clock: MonotonicClock,
        onChunkReceived: (EngineeringCaptureReference, Long, ByteArray) -> Unit,
    ): EngineeringCaptureArchive {
        if (retrievalTimeoutMs !in 1..86_400_000L) {
            throw ProtocolViolation("Capture retrieval timeout is outside its valid range")
        }
        requireReference(reference, allowPendingGeneration = true)
        val start = clock.nowMillis()
        fun checkDeadline() {
            ensureOpen()
            if (clock.nowMillis() - start >= retrievalTimeoutMs) {
                throw IOException("Capture archive retrieval exceeded its deadline")
            }
        }

        val statusRequest = EngineeringRequest(
            requestId = allocateRequestId(),
            command = EngineeringFrameCodec.ENGINEERING_CAPTURE_ARCHIVE,
            payload = archivePayload(EngineeringFrameCodec.CAPTURE_STATUS, 0, 0, 0),
            protocolMinor = EngineeringFrameCodec.PROTOCOL_MINOR_1_1,
        )
        checkDeadline()
        val status = exchange(statusRequest)
        if (status.status == ProtocolStatus.NOT_READY) {
            throw EngineeringCaptureArchiveUnavailableException()
        }
        if (status.status != ProtocolStatus.OK || status.payload.size != EngineeringFrameCodec.ENGINEERING_CAPTURE_STATUS_SIZE) {
            throw ProtocolViolation("Capture archive status is unavailable: ${status.status}")
        }
        val generation = readU32(status.payload, 0)
        val state = status.payload[4].toInt() and 0xFF
        val statusPolarity = status.payload[5].toInt() and 0xFF
        val faultFlags = readU16(status.payload, 6)
        val counts = (0 until 5).map { readU16(status.payload, 8 + it * 2) }
        if (generation == 0L ||
            (reference.archiveGeneration != 0L && generation != reference.archiveGeneration) ||
            state !in 4..5 || statusPolarity != reference.polarity ||
            readU32(status.payload, 18) != EngineeringCaptureArchiveCodec.ARCHIVE_SIZE.toLong() ||
            readU16(status.payload, 22) != 0 || counts.any { it !in 0..10_000 } ||
            (state == 4 && (faultFlags != 0 || counts.any { it != 10_000 })) ||
            (state == 5 && faultFlags == 0)
        ) {
            throw ProtocolViolation("Capture archive is incomplete, stale, or still active")
        }

        val archiveBytes = ByteArray(EngineeringCaptureArchiveCodec.ARCHIVE_SIZE)
        var offset = 0L
        while (offset < archiveBytes.size) {
            checkDeadline()
            val request = EngineeringRequest(
                requestId = allocateRequestId(),
                command = EngineeringFrameCodec.ENGINEERING_CAPTURE_ARCHIVE,
                payload = archivePayload(EngineeringFrameCodec.CAPTURE_READ,
                    generation, offset, EngineeringCaptureArchiveCodec.CHUNK_SIZE),
                protocolMinor = EngineeringFrameCodec.PROTOCOL_MINOR_1_1,
            )
            val response = exchange(request)
            if (response.status != ProtocolStatus.OK) {
                throw IOException("Capture archive read failed at offset $offset: ${response.status}")
            }
            val chunk = EngineeringCaptureArchiveCodec.decodeChunk(
                response.payload, generation, offset, EngineeringCaptureArchiveCodec.CHUNK_SIZE,
            )
            chunk.copyInto(archiveBytes, offset.toInt())
            onChunkReceived(reference.copy(archiveGeneration = generation), offset, chunk.copyOf())
            offset += chunk.size
        }

        checkDeadline()
        val eofRequest = EngineeringRequest(
            requestId = allocateRequestId(),
            command = EngineeringFrameCodec.ENGINEERING_CAPTURE_ARCHIVE,
            payload = archivePayload(EngineeringFrameCodec.CAPTURE_READ,
                generation, EngineeringCaptureArchiveCodec.ARCHIVE_SIZE.toLong(), 0),
            protocolMinor = EngineeringFrameCodec.PROTOCOL_MINOR_1_1,
        )
        val eofResponse = exchange(eofRequest)
        if (eofResponse.status != ProtocolStatus.OK) throw ProtocolViolation("Capture archive EOF could not be verified")
        EngineeringCaptureArchiveCodec.decodeChunk(
            eofResponse.payload, generation,
            EngineeringCaptureArchiveCodec.ARCHIVE_SIZE.toLong(), 0,
        )

        val archive = EngineeringCaptureArchiveCodec.decodeArchive(
            archiveBytes,
            expectedGeneration = generation,
            expectedNonce = reference.nonce,
            expectedSessionGeneration = reference.sessionGeneration.takeIf { it != 0L },
            faultFlags = faultFlags,
        )
        if (archive.counts != counts || archive.faultFlags != faultFlags ||
            archive.complete != (state == 4) ||
            archive.polarity != reference.polarity || archive.rateStage != reference.rateStage ||
            archive.timeoutMs != reference.timeoutMs
        ) {
            throw ProtocolViolation("Capture status and archive header disagree")
        }
        return archive
    }

    private fun waitWithoutVendorTraffic(
        waitMs: Long,
        clock: MonotonicClock,
        sleeper: ProbeSleeper,
    ) {
        val start = clock.nowMillis()
        while (clock.nowMillis() - start < waitMs) {
            ensureOpen()
            val remaining = waitMs - (clock.nowMillis() - start)
            sleeper.sleep(min(CAPTURE_WAIT_SLICE_MS, remaining))
        }
        ensureOpen()
    }

    private fun requireReference(
        reference: EngineeringCaptureReference,
        allowPendingGeneration: Boolean = false,
    ) {
        val knownGeneration = reference.archiveGeneration in 1..0xFFFF_FFFFL &&
            reference.sessionGeneration in 1..0xFFFF_FFFFL
        val pendingGeneration = allowPendingGeneration && reference.archiveGeneration == 0L &&
            reference.sessionGeneration == 0L
        if (reference.fixtureSerial != identity.serial || reference.nonce !in 1..0xFFFF_FFFFL ||
            (!knownGeneration && !pendingGeneration) ||
            reference.polarity !in 1..2 || reference.rateStage !in 1..2 ||
            reference.timeoutMs !in 1..86_400_000L
        ) {
            throw ProtocolViolation("Capture reference belongs to another fixture or has invalid generations")
        }
    }

    private fun beginCapturePayload(nonce: Long, polarity: Int, rateStage: Int, timeoutMs: Long): ByteArray =
        ByteArray(EngineeringFrameCodec.ENGINEERING_BEGIN_CAPTURE_REQUEST_SIZE).also { payload ->
            putU64(payload, 0, nonce)
            putU16(payload, 8, EngineeringCaptureArchiveCodec.SAMPLES_PER_CHANNEL)
            payload[10] = polarity.toByte()
            payload[11] = rateStage.toByte()
            putU32(payload, 12, timeoutMs)
        }

    private fun archivePayload(operation: Int, generation: Long, offset: Long, length: Int): ByteArray =
        ByteArray(EngineeringFrameCodec.ENGINEERING_CAPTURE_ARCHIVE_REQUEST_SIZE).also { payload ->
            payload[0] = operation.toByte()
            putU32(payload, 2, generation)
            putU32(payload, 6, offset)
            payload[10] = length.toByte()
        }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, index -> value or ((bytes[offset + index].toInt() and 0xFF).toLong() shl (8 * index)) }

    private fun readU64(bytes: ByteArray, offset: Int): Long =
        (0..7).fold(0L) { value, index -> value or ((bytes[offset + index].toInt() and 0xFF).toLong() shl (8 * index)) }

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

    private fun allocateRequestId(): Long = requestIds.allocate()

    private fun ensureOpen() {
        if (invalidated.get()) throw SessionInvalidatedException()
        if (closed.get()) throw IllegalStateException("USB vendor session is closed")
    }

    private class SessionInvalidatedException : IllegalStateException("USB device was detached or re-enumerated")

    companion object {
        const val DEFAULT_PULSE_TIMEOUT_MS = 5_000L
        const val DEFAULT_POLL_INTERVAL_MS = 20L
        const val TRANSFER_TIMEOUT_MS = 500
        const val MAX_FRAME_SIZE = EngineeringFrameCodec.MAX_FRAME_SIZE
        const val DEFAULT_CAPTURE_RETRIEVAL_TIMEOUT_MS = 1_800_000L
        const val MIN_CAPTURE_TIMEOUT_MS = 1_000L
        const val CAPTURE_ARM_SETTLE_MS = 500L
        private const val CAPTURE_WAIT_SLICE_MS = 100L
        private const val MAX_ACK_ATTEMPTS = 2
    }
}
