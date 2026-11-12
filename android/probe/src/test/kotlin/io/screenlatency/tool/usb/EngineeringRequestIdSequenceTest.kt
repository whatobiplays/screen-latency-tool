package io.screenlatency.tool.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringRequestIdSequenceTest {
    @Test
    fun missingStateFailsClosedUntilOwnerConfirmsCompleteFirmwareRestart() {
        val store = MemoryRequestIdStateStore(nextRequestId = null)
        val sequence = EngineeringRequestIdSequence(store)

        assertThrows(ProtocolViolation::class.java) { sequence.allocate() }
        assertEquals(null, store.nextRequestId)

        assertTrue(sequence.initializeMissingAfterOwnerConfirmedFirmwareRestart())

        assertEquals(1L, sequence.allocate())
        assertEquals(2L, store.nextRequestId)
    }

    @Test
    fun corruptStateFailsClosedUntilOwnerConfirmsCompleteFirmwareRestart() {
        val store = MemoryRequestIdStateStore(nextRequestId = 0L)
        val sequence = EngineeringRequestIdSequence(store)

        assertThrows(ProtocolViolation::class.java) { sequence.allocate() }
        assertEquals(0L, store.nextRequestId)

        assertTrue(sequence.initializeMissingAfterOwnerConfirmedFirmwareRestart())

        assertEquals(1L, sequence.allocate())
        assertEquals(2L, store.nextRequestId)
    }

    @Test
    fun coordinatorRecreationAndRestartConfirmationKeepPersistedIdsIncreasing() {
        val store = MemoryRequestIdStateStore(nextRequestId = 7L)
        val firstProcessSequence = EngineeringRequestIdSequence(store)

        assertEquals(7L, firstProcessSequence.allocate())
        assertEquals(8L, firstProcessSequence.allocate())

        val restartedStore = MemoryRequestIdStateStore(nextRequestId = store.nextRequestId)
        val restartedProcessSequence = EngineeringRequestIdSequence(restartedStore)
        assertTrue(restartedProcessSequence.initializeMissingAfterOwnerConfirmedFirmwareRestart())

        assertEquals(9L, restartedProcessSequence.allocate())
        assertEquals(9L, store.nextRequestId)
        assertEquals(10L, restartedStore.nextRequestId)
    }

    @Test
    fun replacementFixtureDoesNotInheritAnotherSerialsRequestIds() {
        val stores = mutableMapOf<String, MemoryRequestIdStateStore>(
            "fixture-a" to MemoryRequestIdStateStore(nextRequestId = 19L),
        )
        val provider = EngineeringRequestIdSequenceProvider { serial ->
            synchronized(stores) {
                stores.getOrPut(serial) { MemoryRequestIdStateStore(nextRequestId = null) }
            }
        }

        assertEquals(19L, provider.forFixture("fixture-a").allocate())
        assertThrows(ProtocolViolation::class.java) {
            provider.forFixture("fixture-b").allocate()
        }
        assertEquals(20L, stores.getValue("fixture-a").nextRequestId)
        assertEquals(null, stores.getValue("fixture-b").nextRequestId)
    }

    @Test
    fun independentEngineeringClientsForOneFixtureShareThePersistedHighWaterMark() {
        val store = MemoryRequestIdStateStore(nextRequestId = 77L)
        val firstClient = EngineeringRequestIdSequence(store)
        val archiveClient = EngineeringRequestIdSequence(store)

        assertEquals(77L, firstClient.allocate())
        assertEquals(78L, archiveClient.allocate())
        assertEquals(79L, firstClient.allocate())
        assertEquals(80L, store.nextRequestId)
    }

    @Test
    fun failedPersistenceDoesNotReturnOrReuseAnUncommittedRequestId() {
        val store = MemoryRequestIdStateStore(nextRequestId = 41L, persistSucceeds = false)
        val sequence = EngineeringRequestIdSequence(store)

        assertThrows(ProtocolViolation::class.java) { sequence.allocate() }

        assertEquals(41L, store.nextRequestId)
        store.persistSucceeds = true
        assertEquals(41L, sequence.allocate())
        assertEquals(42L, store.nextRequestId)
    }

    @Test
    fun exhaustionIsPersistedWithoutWrappingIntoTheCurrentFirmwareEpoch() {
        val store = MemoryRequestIdStateStore(nextRequestId = 0xFFFF_FFFFL)
        val sequence = EngineeringRequestIdSequence(store)

        assertEquals(0xFFFF_FFFFL, sequence.allocate())
        assertEquals(0x1_0000_0000L, store.nextRequestId)
        assertThrows(ProtocolViolation::class.java) { sequence.allocate() }
    }

    private class MemoryRequestIdStateStore(
        var nextRequestId: Long?,
        var persistSucceeds: Boolean = true,
    ) : EngineeringRequestIdStateStore {
        override fun loadNextRequestId(): Long? = nextRequestId

        override fun saveNextRequestId(nextRequestId: Long): Boolean {
            if (!persistSucceeds) return false
            this.nextRequestId = nextRequestId
            return true
        }
    }
}
