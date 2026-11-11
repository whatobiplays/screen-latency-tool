package io.screenlatency.tool.usb

/** Durable high-water storage for one validated fixture serial number. */
interface EngineeringRequestIdStateStore {
    fun loadNextRequestId(): Long?
    fun saveNextRequestId(nextRequestId: Long): Boolean
}

/** Allocates request IDs only after the next value has been stored successfully. */
class EngineeringRequestIdSequence(
    private val stateStore: EngineeringRequestIdStateStore,
) {
    fun allocate(): Long = synchronized(PROCESS_LOCK) {
        val next = try {
            stateStore.loadNextRequestId()
        } catch (failure: Exception) {
            throw ProtocolViolation("Could not load the fixture request ID state")
        } ?: throw ProtocolViolation(
            "No trusted request ID state exists for this fixture; refusing USB commands",
        )

        if (next !in FIRST_REQUEST_ID..EXHAUSTED_REQUEST_ID) {
            throw ProtocolViolation("The fixture request ID state is invalid; refusing USB commands")
        }
        if (next == EXHAUSTED_REQUEST_ID) {
            throw ProtocolViolation("The fixture request ID sequence is exhausted")
        }

        val following = next + 1
        val persisted = try {
            stateStore.saveNextRequestId(following)
        } catch (failure: Exception) {
            false
        }
        if (!persisted) {
            throw ProtocolViolation("Could not persist the next fixture request ID; no USB command was sent")
        }
        next
    }

    /** Initializes missing or corrupt state after an owner confirms a complete fixture firmware restart. */
    fun initializeMissingAfterOwnerConfirmedFirmwareRestart(): Boolean = synchronized(PROCESS_LOCK) {
        val current = try {
            stateStore.loadNextRequestId()
        } catch (failure: Exception) {
            null
        }
        if (current in FIRST_REQUEST_ID..EXHAUSTED_REQUEST_ID) return@synchronized true
        try {
            stateStore.saveNextRequestId(FIRST_REQUEST_ID)
        } catch (failure: Exception) {
            false
        }
    }

    private companion object {
        /** Serializes allocations even when a USB coordinator is recreated in the same process. */
        val PROCESS_LOCK = Any()
        const val FIRST_REQUEST_ID = 1L
        const val EXHAUSTED_REQUEST_ID = 0x1_0000_0000L
    }
}

/** Creates independent ID sequences for fixtures with different protected serial numbers. */
class EngineeringRequestIdSequenceProvider(
    private val stateStoreForSerial: (String) -> EngineeringRequestIdStateStore,
) {
    fun forFixture(serial: String): EngineeringRequestIdSequence {
        require(serial.isNotBlank()) { "A validated fixture serial is required" }
        return EngineeringRequestIdSequence(stateStoreForSerial(serial))
    }

    /** Trusts explicit owner confirmation; USB detach events never establish this boundary. */
    fun initializeMissingAfterOwnerConfirmedFirmwareRestart(serial: String): Boolean =
        forFixture(serial).initializeMissingAfterOwnerConfirmedFirmwareRestart()
}
