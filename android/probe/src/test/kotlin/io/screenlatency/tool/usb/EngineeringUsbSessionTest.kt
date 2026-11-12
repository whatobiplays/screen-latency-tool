package io.screenlatency.tool.usb

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringUsbSessionTest {
    @Test
    fun captureWaitsForTheQuietArmBarrierAndIssuesNoVendorTrafficDuringAcquisition() {
        val negotiatedInfo = idleEngineeringInfoResponse().also {
            it[5] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
            it[21] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
        }
        val beginAck = vector("engineering_capture_begin_ascending_v11").also {
            putU64(it, EngineeringFrameCodec.HEADER_SIZE, 2L)
        }
        val (session, connection) = connectedSession(listOf(beginAck), preflightInfo = negotiatedInfo)
        val clock = FakeClock()
        val callbackOrder = mutableListOf<String>()
        var reference: EngineeringCaptureReference? = null
        var readyReference: EngineeringCaptureReference? = null
        var detached = false

        val result = session.runEngineeringCapture(
            polarity = 1,
            rateStage = 1,
            timeoutMs = EngineeringUsbSession.MIN_CAPTURE_TIMEOUT_MS,
            clock = clock,
            sleeper = ProbeSleeper { millis ->
                clock.advance(millis)
                if (readyReference != null && !detached) {
                    detached = true
                    session.invalidate()
                }
            },
            onCaptureAccepted = {
                callbackOrder += "acknowledged"
                reference = it
            },
            onCaptureReadyForStimulus = {
                callbackOrder += "ready"
                readyReference = it
            },
        )

        val interrupted = result as EngineeringCaptureRunResult.Interrupted
        assertEquals(listOf("acknowledged", "ready"), callbackOrder)
        assertEquals(reference, readyReference)
        assertEquals(2L, interrupted.reference?.nonce)
        assertEquals(1L, interrupted.reference?.archiveGeneration)
        assertEquals(1L, interrupted.reference?.sessionGeneration)
        assertTrue(clock.now >= EngineeringUsbSession.CAPTURE_ARM_SETTLE_MS)
        assertTrue(detached)
        assertEquals(listOf(1L, 2L), connection.requestIds)
        assertEquals(listOf(0xE001, 0xE003), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun legacyFixtureNegotiatesMinorZeroAndNeverSendsCaptureCommands() {
        val unsupportedCaptureProbe = emptyStatusResponse(
            vector("engineering_info_request_0x01020304"),
            ProtocolStatus.UNSUPPORTED_VERSION,
        )
        val (session, connection) = connectedSession(
            listOf(idleEngineeringInfoResponse()),
            preflightInfo = unsupportedCaptureProbe,
        )

        assertEquals(
            EngineeringCaptureRunResult.Unsupported,
            session.runEngineeringCapture(1, 1, EngineeringUsbSession.MIN_CAPTURE_TIMEOUT_MS),
        )
        assertEquals(listOf(1L, 2L), connection.requestIds)
        assertEquals(listOf(0xE001, 0xE001), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun lostCaptureAcknowledgementDoesNotRetryBeginOrResetTheRequestId() {
        val negotiatedInfo = idleEngineeringInfoResponse().also {
            it[5] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
            it[21] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
        }
        val (session, connection) = connectedSession(
            listOf(byteArrayOf()),
            preflightInfo = negotiatedInfo,
        )

        val result = session.runEngineeringCapture(
            polarity = 2,
            rateStage = 1,
            timeoutMs = EngineeringUsbSession.MIN_CAPTURE_TIMEOUT_MS,
        )

        val interrupted = result as EngineeringCaptureRunResult.Interrupted
        assertEquals(2L, interrupted.reference?.nonce)
        assertEquals(0L, interrupted.reference?.archiveGeneration)
        assertEquals(0L, interrupted.reference?.sessionGeneration)
        assertEquals(listOf(1L, 2L), connection.requestIds)
        assertEquals(listOf(0xE001, 0xE003), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun missingArchiveAfterQuietCaptureWindowIsUnavailableWithoutVendorTrafficDuringAcquisition() {
        val negotiatedInfo = idleEngineeringInfoResponse().also {
            it[5] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
            it[21] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
        }
        val beginAck = vector("engineering_capture_begin_ascending_v11").also {
            putU64(it, EngineeringFrameCodec.HEADER_SIZE, 2L)
        }
        val unavailable = emptyStatusResponse(
            vector("engineering_capture_status_v11"),
            ProtocolStatus.NOT_READY,
        )
        val (session, connection) = connectedSession(
            listOf(beginAck, unavailable),
            preflightInfo = negotiatedInfo,
        )
        val clock = FakeClock()
        var readyForStimulus = false
        var windowElapsed = false

        val result = session.runEngineeringCapture(
            polarity = 1,
            rateStage = 1,
            timeoutMs = EngineeringUsbSession.MIN_CAPTURE_TIMEOUT_MS,
            clock = clock,
            sleeper = ProbeSleeper { millis ->
                if (readyForStimulus) assertEquals(listOf(0xE001, 0xE003), connection.commands)
                clock.advance(millis)
            },
            onCaptureReadyForStimulus = { readyForStimulus = true },
            onCaptureWindowElapsed = { windowElapsed = it.captureWindowElapsed },
        )

        assertEquals(EngineeringCaptureRunResult.Unavailable, result)
        assertTrue(windowElapsed)
        assertTrue(clock.now >= EngineeringUsbSession.CAPTURE_ARM_SETTLE_MS +
            EngineeringUsbSession.MIN_CAPTURE_TIMEOUT_MS)
        assertEquals(listOf(1L, 2L, 3L), connection.requestIds)
        assertEquals(listOf(0xE001, 0xE003, 0xE004), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun resumedCaptureWaitsQuietlyBeforeAnyVendorRequest() {
        val negotiatedInfo = idleEngineeringInfoResponse().also {
            it[5] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
            it[21] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
        }
        val unavailable = emptyStatusResponse(
            vector("engineering_capture_status_v11"),
            ProtocolStatus.NOT_READY,
        )
        val (session, connection) = connectedSession(
            listOf(unavailable),
            preflightInfo = negotiatedInfo,
        )
        val clock = FakeClock()
        val reference = EngineeringCaptureReference(
            fixtureSerial = "0123456789ABCDEF",
            nonce = 7,
            archiveGeneration = 1,
            sessionGeneration = 1,
            polarity = 2,
            rateStage = 1,
            timeoutMs = EngineeringUsbSession.MIN_CAPTURE_TIMEOUT_MS,
        )
        var elapsedReference: EngineeringCaptureReference? = null

        assertThrows(EngineeringCaptureArchiveUnavailableException::class.java) {
            session.retrieveEngineeringCapture(
                reference = reference,
                clock = clock,
                sleeper = ProbeSleeper { millis ->
                    assertTrue(connection.commands.isEmpty())
                    clock.advance(millis)
                },
                onCaptureWindowElapsed = { elapsedReference = it },
            )
        }

        assertTrue(clock.now >= reference.timeoutMs + EngineeringUsbSession.CAPTURE_ARM_SETTLE_MS)
        assertEquals(true, elapsedReference?.captureWindowElapsed)
        assertEquals(listOf(1L, 2L), connection.requestIds)
        assertEquals(listOf(0xE001, 0xE004), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun interruptedArchiveReadKeepsItsReferenceAndAllocatesOnlyNewRequestIds() {
        val negotiatedInfo = idleEngineeringInfoResponse().also {
            it[5] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
            it[21] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
        }
        val firstChunk = ByteArray(32).also {
            it[0] = 0x53
            it[1] = 0x4C
            it[2] = 0x41
            it[3] = 0x31
        }
        val stateStore = TestRequestIdStateStore(nextRequestId = 1)
        val connection = FakeConnection(
            listOf(
                ScriptedResponse(negotiatedInfo),
                ScriptedResponse(captureStatusResponse(
                    generation = 1,
                    polarity = 1,
                    faultFlags = 1,
                    counts = List(5) { 10_000 },
                )),
                ScriptedResponse(captureReadResponse(1, 0, firstChunk)),
            ),
        )
        val coordinator = UsbProbeCoordinator(
            FakePlatform(connection),
            EngineeringRequestIdSequenceProvider { stateStore },
        )
        val session = openSession(coordinator)
        val reference = EngineeringCaptureReference(
            fixtureSerial = "0123456789ABCDEF",
            nonce = 7,
            archiveGeneration = 1,
            sessionGeneration = 1,
            polarity = 1,
            rateStage = 1,
            timeoutMs = 60_000,
            captureWindowElapsed = true,
        )
        val offsets = mutableListOf<Long>()

        val failure = assertThrows(IllegalStateException::class.java) {
            session.retrieveEngineeringCapture(reference) { _, offset, _ ->
                offsets += offset
                session.invalidate()
            }
        }

        assertTrue(failure.message.orEmpty().contains("detached"))
        assertEquals(listOf(0L), offsets)
        assertEquals(listOf(1L, 2L, 3L), connection.requestIds)
        assertEquals(listOf(0xE001, 0xE004, 0xE004), connection.commands)
        assertEquals(4L, EngineeringRequestIdSequence(stateStore).allocate())
        assertResourcesClosed(connection)
    }

    @Test
    fun rejectsEngineeringSerialMismatchBeforeSendingPulse() {
        val mismatchedInfo = vector("engineering_info_request_0x01020304").also {
            it[26] = (it[26].toInt() xor 1).toByte()
        }
        val (session, connection) = connectedSession(emptyList(), preflightInfo = mismatchedInfo)

        val failure = assertThrows(ProtocolViolation::class.java) {
            session.runEngineeringPulse(
                durationMs = 10,
                timeoutMs = 20,
                pollIntervalMs = 1,
                clock = FakeClock(),
                sleeper = ProbeSleeper {},
            )
        }

        assertTrue(failure.message.orEmpty().contains("serial", ignoreCase = true))
        assertEquals(listOf(0xE001), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun pollsOneRequestSequenceThroughQueueHoldReleaseAndCompletion() {
        val pressAck = vector("engineering_press_10ms_request_2")
        val queued = vector("engineering_info_request_0x01020304")
        val holding = queued.copyOf().also { it[25] = ((it[25].toInt() and 0xC0) or 0x3A).toByte() }
        val releasing = queued.copyOf().also { it[25] = ((it[25].toInt() and 0xC0) or 0x3B).toByte() }
        val complete = vector("engineering_info_completed_wrapped_tick_0x01020304")
        val (session, connection) = connectedSession(listOf(pressAck, queued, holding, releasing, complete))
        val clock = FakeClock().apply { now = -20 }
        val updates = mutableListOf<PulseUpdate>()
        val acceptedSequences = mutableListOf<Long>()

        val result = session.runEngineeringPulse(
            durationMs = 10,
            timeoutMs = 20,
            pollIntervalMs = 1,
            clock = clock,
            sleeper = ProbeSleeper(clock::advance),
            onSequenceAccepted = acceptedSequences::add,
            onProgress = { update, _ -> updates += update },
        )

        assertEquals(PulseRunResult.Completed, result)
        assertEquals(listOf(1L), acceptedSequences)
        assertEquals(
            listOf(PulseUpdate.Queued, PulseUpdate.Holding, PulseUpdate.ReleaseQueued, PulseUpdate.Complete),
            updates,
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), connection.requestIds)
        assertEquals(listOf(0xE001, 0xE002, 0xE001, 0xE001, 0xE001, 0xE001), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun retriesTheSamePulseRequestAfterItsAcknowledgementIsLost() {
        val pressAck = vector("engineering_press_10ms_request_2")
        val queued = vector("engineering_info_request_0x01020304")
        val holding = queued.copyOf().also { it[25] = ((it[25].toInt() and 0xC0) or 0x3A).toByte() }
        val releasing = queued.copyOf().also { it[25] = ((it[25].toInt() and 0xC0) or 0x3B).toByte() }
        val complete = vector("engineering_info_completed_wrapped_tick_0x01020304")
        val (session, connection) = connectedSession(
            listOf(byteArrayOf(), pressAck, queued, holding, releasing, complete),
        )
        val clock = FakeClock().apply { now = -20 }

        val result = session.runEngineeringPulse(
            durationMs = 10,
            timeoutMs = 20,
            pollIntervalMs = 1,
            clock = clock,
            sleeper = ProbeSleeper(clock::advance),
        )

        assertEquals(PulseRunResult.Completed, result)
        assertEquals(listOf(1L, 2L, 2L, 3L, 4L, 5L, 6L), connection.requestIds)
        assertEquals(listOf(0xE001, 0xE002, 0xE002, 0xE001, 0xE001, 0xE001, 0xE001), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun busyPressAcknowledgementPollsTheActivePulseWithoutAcceptingANewSequence() {
        val busy = emptyStatusResponse(vector("engineering_press_10ms_request_2"), ProtocolStatus.BUSY)
        val queued = vector("engineering_info_request_0x01020304")
        val holding = queued.copyOf().also { it[25] = ((it[25].toInt() and 0xC0) or 0x3A).toByte() }
        val releasing = queued.copyOf().also { it[25] = ((it[25].toInt() and 0xC0) or 0x3B).toByte() }
        val complete = vector("engineering_info_completed_wrapped_tick_0x01020304")
        val (session, connection) = connectedSession(listOf(busy, queued, holding, releasing, complete))
        val clock = FakeClock().apply { now = -20 }
        val updates = mutableListOf<PulseUpdate>()
        val acceptedSequences = mutableListOf<Long>()

        val result = session.runEngineeringPulse(
            durationMs = 10,
            timeoutMs = 20,
            pollIntervalMs = 1,
            clock = clock,
            sleeper = ProbeSleeper(clock::advance),
            onSequenceAccepted = acceptedSequences::add,
            onProgress = { update, _ -> updates += update },
        )

        assertEquals(PulseRunResult.Busy, result)
        assertEquals(emptyList<Long>(), acceptedSequences)
        assertEquals(
            listOf(PulseUpdate.Busy, PulseUpdate.Queued, PulseUpdate.Holding, PulseUpdate.ReleaseQueued, PulseUpdate.Complete),
            updates,
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), connection.requestIds)
        assertEquals(listOf(0xE001, 0xE002, 0xE001, 0xE001, 0xE001, 0xE001), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun rejectedConcurrentPulseDoesNotCloseTheActiveOperationConnection() {
        val pressAck = vector("engineering_press_10ms_request_2")
        val queued = vector("engineering_info_request_0x01020304")
        val complete = vector("engineering_info_completed_wrapped_tick_0x01020304")
        val (session, connection) = connectedSession(listOf(pressAck, queued, complete))
        var rejectedWhileActive = false
        connection.beforeBulkOut = {
            if (connection.requestIds.size == 2) {
                assertThrows(IllegalStateException::class.java) { session.runEngineeringPulse(10) }
                assertEquals(0, connection.closeCount)
                assertTrue(!session.isClosed)
                rejectedWhileActive = true
            }
        }
        val clock = FakeClock()
        val result = session.runEngineeringPulse(
            durationMs = 10,
            timeoutMs = 20,
            pollIntervalMs = 1,
            clock = clock,
            sleeper = ProbeSleeper(clock::advance),
        )

        assertTrue(rejectedWhileActive)
        assertEquals(PulseRunResult.Completed, result)
        assertResourcesClosed(connection)
    }

    @Test
    fun requestIdsContinueAcrossCoordinatorRecreationAndLogicalDetach() {
        val responses = listOf(
            vector("engineering_press_10ms_request_2"),
            vector("engineering_info_request_0x01020304"),
            vector("engineering_info_completed_wrapped_tick_0x01020304"),
        )
        val connections = MutableList(4) {
            FakeConnection(listOf(ScriptedResponse(idleEngineeringInfoResponse())) + responses.map(::ScriptedResponse))
        }
        val platform = RotatingFakePlatform(connections)
        val persistedStores = mutableMapOf<String, TestRequestIdStateStore>()
        fun provider() = EngineeringRequestIdSequenceProvider { serial ->
            synchronized(persistedStores) {
                persistedStores.getOrPut(serial) { TestRequestIdStateStore(nextRequestId = 1L) }
            }
        }
        val coordinator = UsbProbeCoordinator(platform, provider())

        runPulse(openSession(coordinator))
        runPulse(openSession(coordinator))
        assertEquals(listOf(1L, 2L, 3L, 4L), platform.openedConnections[0].requestIds)
        assertEquals(listOf(5L, 6L, 7L, 8L), platform.openedConnections[1].requestIds)
        coordinator.onUsbAttached()
        runPulse(openSession(coordinator))
        assertEquals(listOf(9L, 10L, 11L, 12L), platform.openedConnections[2].requestIds)
        coordinator.onUsbDetached()
        coordinator.close()
        val recreatedCoordinator = UsbProbeCoordinator(platform, provider())
        runPulse(openSession(recreatedCoordinator))
        assertEquals(listOf(13L, 14L, 15L, 16L), platform.openedConnections[3].requestIds)
    }

    @Test
    fun retriesBusyInformationResponsesAndStillReportsTerminalFailures() {
        val pressAck = vector("engineering_press_10ms_request_2")
        val busyInfo = emptyStatusResponse(vector("engineering_info_request_0x01020304"), ProtocolStatus.BUSY)
        val complete = vector("engineering_info_completed_wrapped_tick_0x01020304")
        val (busySession, busyConnection) = connectedSession(listOf(pressAck, busyInfo, complete))
        val busyClock = FakeClock()

        assertEquals(
            PulseRunResult.Completed,
            busySession.runEngineeringPulse(10, 10, 1, busyClock, ProbeSleeper(busyClock::advance)),
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), busyConnection.requestIds)
        assertResourcesClosed(busyConnection)

        val errorInfo = emptyStatusResponse(vector("engineering_info_request_0x01020304"), ProtocolStatus.HARDWARE_FAULT)
        val (errorSession, errorConnection) = connectedSession(listOf(pressAck, errorInfo))
        val errorClock = FakeClock()
        assertEquals(
            PulseRunResult.Failed(ProtocolStatus.HARDWARE_FAULT, EngineeringFault.NONE),
            errorSession.runEngineeringPulse(10, 10, 1, errorClock, ProbeSleeper(errorClock::advance)),
        )
        assertResourcesClosed(errorConnection)
    }

    @Test
    fun busyInformationPollingTimesOutWithoutAcceptingOrReleasingAnotherPulse() {
        val busyPress = emptyStatusResponse(vector("engineering_press_10ms_request_2"), ProtocolStatus.BUSY)
        val busyInfo = emptyStatusResponse(vector("engineering_info_request_0x01020304"), ProtocolStatus.BUSY)
        val (session, connection) = connectedSession(listOf(busyPress, busyInfo))
        val clock = FakeClock()
        val acceptedSequences = mutableListOf<Long>()
        val updates = mutableListOf<PulseUpdate>()

        assertEquals(
            PulseRunResult.TimedOut,
            session.runEngineeringPulse(
                durationMs = 10,
                timeoutMs = 2,
                pollIntervalMs = 1,
                clock = clock,
                sleeper = ProbeSleeper(clock::advance),
                onSequenceAccepted = acceptedSequences::add,
                onProgress = { update, _ -> updates += update },
            ),
        )
        assertEquals(emptyList<Long>(), acceptedSequences)
        assertEquals(listOf(PulseUpdate.Busy, PulseUpdate.Busy, PulseUpdate.TimedOut), updates)
        assertEquals(listOf(1L, 2L, 3L), connection.requestIds)
        assertEquals(listOf(0xE001, 0xE002, 0xE001), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun pollsEngineeringInfoThroughFaultRecoveryUntilTheFaultIsRetainedAndRecoveryClears() {
        val pressAck = vector("engineering_press_10ms_request_2")
        val queued = vector("engineering_info_request_0x01020304")
        val recovering = queued.copyOf().also {
            it[25] = 0x7D
            it[42] = 0xD2.toByte()
            it[43] = 0x04
            it[46] = 0xD2.toByte()
            it[47] = 0x04
        }
        val recovered = recovering.copyOf().also {
            it[25] = 0x3E
        }
        val (session, connection) = connectedSession(listOf(pressAck, recovering, recovered))
        val clock = FakeClock()
        val updates = mutableListOf<PulseUpdate>()
        val information = mutableListOf<EngineeringInfo>()

        assertEquals(
            PulseRunResult.RecoveryComplete,
            session.runEngineeringPulse(
                durationMs = 10,
                timeoutMs = 20,
                pollIntervalMs = 1,
                clock = clock,
                sleeper = ProbeSleeper(clock::advance),
                onProgress = { update, info ->
                    updates += update
                    info?.let(information::add)
                },
            ),
        )
        assertEquals(listOf(PulseUpdate.Recovering, PulseUpdate.RecoveryComplete), updates)
        assertEquals(listOf(EngineeringFault.NONE, EngineeringFault.UNSPECIFIED), information.map { it.fault })
        assertEquals(listOf(1234L, 1234L), information.map { it.completionTick })
        assertEquals(listOf(1L, 2L, 3L, 4L), connection.requestIds)
        assertResourcesClosed(connection)
    }

    @Test
    fun timesOutUsingTheInjectedMonotonicClockWithoutWallTimeWaits() {
        val pressAck = vector("engineering_press_10ms_request_2")
        val queued = vector("engineering_info_request_0x01020304")
        val (session, connection) = connectedSession(listOf(pressAck, queued))
        val clock = FakeClock()

        val result = session.runEngineeringPulse(
            durationMs = 10,
            timeoutMs = 5,
            pollIntervalMs = 2,
            clock = clock,
            sleeper = ProbeSleeper(clock::advance),
        )

        assertEquals(PulseRunResult.TimedOut, result)
        assertEquals(5L, clock.now)
        assertEquals(listOf(1L, 2L, 3L, 4L), connection.requestIds)
        assertResourcesClosed(connection)
    }

    @Test
    fun rejectsShortResponsesAndReleasesTheClaim() {
        val shortAck = ScriptedResponse(vector("engineering_press_10ms_request_2").copyOf(19))
        val (session, connection) = connectedScriptedSession(listOf(shortAck))

        assertThrows(ProtocolViolation::class.java) { session.runEngineeringPulse(10) }
        assertResourcesClosed(connection)
    }

    @Test
    fun vendorBulkInTimeoutAfterPressReleasesAndClosesTheSession() {
        val pressAck = ScriptedResponse(vector("engineering_press_10ms_request_2"))
        val (session, connection) = connectedScriptedSession(
            listOf(pressAck, ScriptedResponse(byteArrayOf(), preserveRequestId = true)),
        )
        val clock = FakeClock()

        val failure = assertThrows(IOException::class.java) {
            session.runEngineeringPulse(
                durationMs = 10,
                timeoutMs = 20,
                pollIntervalMs = 1,
                clock = clock,
                sleeper = ProbeSleeper(clock::advance),
            )
        }

        assertTrue(failure.message.orEmpty().contains("returned no response"))
        assertEquals(listOf(1L, 2L, 3L, 3L), connection.requestIds)
        assertResourcesClosed(connection)
    }

    @Test
    fun rejectsStaleRequestIdsAndReleasesTheClaim() {
        val staleAck = ScriptedResponse(
            vector("engineering_press_10ms_request_2").also { putU32(it, 8, 1) },
            preserveRequestId = true,
        )
        val (session, connection) = connectedScriptedSession(listOf(staleAck))

        assertThrows(StaleResponseException::class.java) { session.runEngineeringPulse(10) }
        assertResourcesClosed(connection)
    }

    @Test
    fun cachedInformationFromAnEarlierCoordinatorCannotSatisfyANewRequest() {
        val staleInformation = idleEngineeringInfoResponse().also { putU32(it, 8, 1L) }
        val connection = FakeConnection(
            listOf(ScriptedResponse(staleInformation, preserveRequestId = true)),
        )
        val stateStore = TestRequestIdStateStore(nextRequestId = 21L)
        val provider = EngineeringRequestIdSequenceProvider { stateStore }
        val coordinator = UsbProbeCoordinator(FakePlatform(connection), provider)
        val session = openSession(coordinator)

        assertThrows(StaleResponseException::class.java) {
            session.runEngineeringPulse(durationMs = 10)
        }

        assertEquals(listOf(21L), connection.requestIds)
        assertEquals(listOf(EngineeringFrameCodec.ENGINEERING_INFO), connection.commands)
        assertResourcesClosed(connection)
    }

    @Test
    fun missingPersistedRequestIdsFailBeforeAnyUsbTransfer() {
        val connection = FakeConnection(emptyList())
        val stateStore = TestRequestIdStateStore(nextRequestId = null)
        val coordinator = UsbProbeCoordinator(
            FakePlatform(connection),
            EngineeringRequestIdSequenceProvider { stateStore },
        )
        val session = openSession(coordinator)

        assertThrows(ProtocolViolation::class.java) {
            session.runEngineeringPulse(durationMs = 10)
        }

        assertTrue(connection.requestIds.isEmpty())
        assertTrue(connection.commands.isEmpty())
        assertResourcesClosed(connection)
    }

    @Test
    fun releasesAndClosesWhenUsbReportsAShortWrite() {
        val (session, connection) = connectedSession(listOf(vector("engineering_press_10ms_request_2")))
        connection.beforeBulkOut = {
            if (connection.commands.lastOrNull() == 0xE002) connection.shortWrite = true
        }

        assertThrows(IOException::class.java) { session.runEngineeringPulse(10) }
        assertResourcesClosed(connection)
    }

    @Test
    fun malformedEngineeringInfoClosesTheConnection() {
        val pressAck = vector("engineering_press_10ms_request_2")
        val malformedInfo = vector("engineering_info_request_0x01020304").also { it[48] = 1 }
        val (session, connection) = connectedSession(listOf(pressAck, malformedInfo))
        val clock = FakeClock()

        assertThrows(ProtocolViolation::class.java) {
            session.runEngineeringPulse(10, 10, 1, clock, ProbeSleeper(clock::advance))
        }
        assertResourcesClosed(connection)
    }

    @Test
    fun detachDuringPollingInvalidatesAndClosesTheActiveSession() {
        val pressAck = vector("engineering_press_10ms_request_2")
        val queued = vector("engineering_info_request_0x01020304")
        val connection = FakeConnection(
            listOf(ScriptedResponse(idleEngineeringInfoResponse()), ScriptedResponse(pressAck), ScriptedResponse(queued)),
        )
        val platform = FakePlatform(connection)
        val coordinator = newTestUsbProbeCoordinator(platform)
        val candidate = (coordinator.discover() as ProbeDiscovery.Ready).candidate
        val session = (coordinator.completePermission(candidate, true) as ProbeConnectResult.Connected).session
        val clock = FakeClock()
        var detached = false

        val result = session.runEngineeringPulse(
            durationMs = 10,
            timeoutMs = 10,
            pollIntervalMs = 1,
            clock = clock,
            sleeper = ProbeSleeper { millis ->
                clock.advance(millis)
                if (!detached) {
                    detached = true
                    coordinator.onUsbDetached()
                }
            },
        )

        assertEquals(PulseRunResult.Invalidated, result)
        assertTrue(detached)
        assertResourcesClosed(connection)
    }

    private fun connectedSession(
        responses: List<ByteArray>,
        preflightInfo: ByteArray = idleEngineeringInfoResponse(),
    ): Pair<EngineeringUsbSession, FakeConnection> {
        return connectedScriptedSession(responses.map(::ScriptedResponse), preflightInfo)
    }

    private fun openSession(coordinator: UsbProbeCoordinator): EngineeringUsbSession {
        val candidate = (coordinator.discover() as ProbeDiscovery.Ready).candidate
        return (coordinator.completePermission(candidate, granted = true) as ProbeConnectResult.Connected).session
    }

    private fun runPulse(session: EngineeringUsbSession) {
        val clock = FakeClock()
        assertEquals(
            PulseRunResult.Completed,
            session.runEngineeringPulse(10, 20, 1, clock, ProbeSleeper(clock::advance)),
        )
    }

    private fun connectedScriptedSession(
        responses: List<ScriptedResponse>,
        preflightInfo: ByteArray = idleEngineeringInfoResponse(),
    ): Pair<EngineeringUsbSession, FakeConnection> {
        val connection = FakeConnection(listOf(ScriptedResponse(preflightInfo)) + responses)
        val platform = FakePlatform(connection)
        val coordinator = newTestUsbProbeCoordinator(platform)
        val candidate = (coordinator.discover() as ProbeDiscovery.Ready).candidate
        val result = coordinator.completePermission(candidate, granted = true)
        return (result as ProbeConnectResult.Connected).session to connection
    }

    private fun idleEngineeringInfoResponse(): ByteArray =
        vector("engineering_info_request_0x01020304").also {
            it[25] = (it[25].toInt() and 0xF8).toByte()
            putU32(it, 34, 0)
            putU16(it, 50, 0)
        }

    private fun assertResourcesClosed(connection: FakeConnection) {
        assertEquals(1, connection.releaseCount)
        assertEquals(1, connection.closeCount)
    }

    private class TestRequestIdStateStore(
        private var nextRequestId: Long?,
    ) : EngineeringRequestIdStateStore {
        override fun loadNextRequestId(): Long? = nextRequestId

        override fun saveNextRequestId(nextRequestId: Long): Boolean {
            this.nextRequestId = nextRequestId
            return true
        }
    }

    private fun emptyStatusResponse(response: ByteArray, status: ProtocolStatus): ByteArray =
        response.copyOf(EngineeringFrameCodec.HEADER_SIZE).also {
            putU16(it, 14, status.wireValue)
            putU32(it, 16, 0)
        }

    private fun captureStatusResponse(
        generation: Long,
        polarity: Int,
        faultFlags: Int,
        counts: List<Int> = List(5) { 0 },
    ): ByteArray {
        val payload = ByteArray(EngineeringFrameCodec.ENGINEERING_CAPTURE_STATUS_SIZE)
        putU32(payload, 0, generation)
        payload[4] = 5
        payload[5] = polarity.toByte()
        putU16(payload, 6, faultFlags)
        counts.forEachIndexed { index, count -> putU16(payload, 8 + index * 2, count) }
        putU32(payload, 18, EngineeringCaptureArchiveCodec.ARCHIVE_SIZE.toLong())
        return responseFrame(EngineeringFrameCodec.ENGINEERING_CAPTURE_ARCHIVE, payload)
    }

    private fun captureReadResponse(generation: Long, offset: Long, data: ByteArray): ByteArray {
        val payload = ByteArray(EngineeringFrameCodec.ENGINEERING_CAPTURE_READ_METADATA_SIZE + data.size)
        putU32(payload, 0, generation)
        putU32(payload, 4, offset)
        putU32(payload, 8, EngineeringCaptureArchiveCodec.chunkCrc(generation, offset, data))
        data.copyInto(payload, EngineeringFrameCodec.ENGINEERING_CAPTURE_READ_METADATA_SIZE)
        return responseFrame(EngineeringFrameCodec.ENGINEERING_CAPTURE_ARCHIVE, payload)
    }

    private fun responseFrame(command: Int, payload: ByteArray): ByteArray =
        ByteArray(EngineeringFrameCodec.HEADER_SIZE + payload.size).also { frame ->
            frame[0] = 0x53
            frame[1] = 0x4C
            frame[2] = 0x54
            frame[3] = 0x31
            frame[4] = 1
            frame[5] = EngineeringFrameCodec.PROTOCOL_MINOR_1_1.toByte()
            frame[6] = 2
            putU16(frame, 12, command)
            putU32(frame, 16, payload.size.toLong())
            payload.copyInto(frame, EngineeringFrameCodec.HEADER_SIZE)
        }

    private fun vector(name: String): ByteArray {
        val root = Path.of(requireNotNull(System.getProperty("repositoryRoot")))
        val path = root.resolve("firmware/protocol/engineering-golden-vectors.tsv")
        val columns = Files.readAllLines(path).drop(1).single { it.substringBefore('\t') == name }.split('\t')
        return columns[2].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun putU64(bytes: ByteArray, offset: Int, value: Long) {
        repeat(8) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private class FakeClock : MonotonicClock {
        var now = 0L
        override fun nowMillis() = now
        fun advance(millis: Long) { now += millis }
    }

    private class FakePlatform(private val connection: FakeConnection) : UsbProbePlatform {
        override fun connectedDevices() = listOf(TestUsbFixture.device)
        override fun hasPermission(deviceName: String) = true
        override fun openDevice(deviceName: String) = connection
    }

    private class RotatingFakePlatform(connections: MutableList<FakeConnection>) : UsbProbePlatform {
        private val connections = connections.toMutableList()
        val openedConnections = mutableListOf<FakeConnection>()

        override fun connectedDevices() = listOf(TestUsbFixture.device)
        override fun hasPermission(deviceName: String) = true
        override fun openDevice(deviceName: String) = connections.removeAt(0).also(openedConnections::add)
    }

    private class FakeConnection(responses: List<ScriptedResponse>) : UsbConnectionPort {
        private val responses = responses.toMutableList()
        private var lastResponse: ScriptedResponse? = null
        private var currentRequestId = 0L
        private var currentRequestTag = 0L
        private var currentCommand = 0
        var requestIds = mutableListOf<Long>()
        var commands = mutableListOf<Int>()
        var shortWrite = false
        var beforeBulkOut: (() -> Unit)? = null
        var releaseCount = 0
        var closeCount = 0

        override fun rawDescriptors() = TestUsbFixture.descriptorBytes()
        override fun protectedStrings() = TestUsbFixture.strings
        override fun claimInterface(interfaceNumber: Int, force: Boolean) = interfaceNumber == 1 && !force

        override fun releaseInterface(interfaceNumber: Int): Boolean {
            releaseCount++
            return interfaceNumber == 1
        }

        override fun bulkOut(endpoint: UsbEndpointSnapshot, bytes: ByteArray, timeoutMs: Int): Int {
            currentRequestId = u32(bytes, 8)
            currentCommand = u16(bytes, 12)
            if (currentCommand == EngineeringFrameCodec.ENGINEERING_PRESS_PULSE) {
                currentRequestTag = u32(bytes, EngineeringFrameCodec.HEADER_SIZE)
            }
            requestIds += currentRequestId
            commands += currentCommand
            beforeBulkOut?.invoke()
            return if (shortWrite) bytes.size - 1 else bytes.size
        }

        override fun bulkIn(endpoint: UsbEndpointSnapshot, maxLength: Int, timeoutMs: Int): ByteArray {
            val response = if (responses.isNotEmpty()) responses.removeAt(0).also { lastResponse = it }
                else lastResponse ?: return byteArrayOf()
            val bytes = response.bytes.copyOf()
            if (bytes.isNotEmpty()) {
                if (!response.preserveRequestId) putU32(bytes, 8, currentRequestId)
                if (currentCommand == EngineeringFrameCodec.ENGINEERING_PRESS_PULSE &&
                    bytes.size >= EngineeringFrameCodec.HEADER_SIZE + EngineeringFrameCodec.ENGINEERING_PRESS_ACK_SIZE
                ) {
                    putU32(bytes, EngineeringFrameCodec.HEADER_SIZE, currentRequestTag)
                }
            }
            return bytes
        }

        override fun close() { closeCount++ }

        private fun u16(bytes: ByteArray, offset: Int) =
            (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

        private fun u32(bytes: ByteArray, offset: Int): Long =
            (0..3).fold(0L) { value, index ->
                value or ((bytes[offset + index].toInt() and 0xFF).toLong() shl (index * 8))
            }

        private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
            repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
        }
    }

    private data class ScriptedResponse(val bytes: ByteArray, val preserveRequestId: Boolean = false)
}
