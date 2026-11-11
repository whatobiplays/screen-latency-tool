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

    private fun exchange(request: EngineeringRequest): EngineeringResponse {
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
        const val MAX_FRAME_SIZE = EngineeringFrameCodec.HEADER_SIZE + EngineeringInfoCodec.PAYLOAD_SIZE
        private const val MAX_ACK_ATTEMPTS = 2
    }
}
