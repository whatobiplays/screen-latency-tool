package io.screenlatency.tool.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidEngineeringRequestIdStateStoreTest {
    @Test
    fun committedCounterSurvivesProviderAndCoordinatorRecreation() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PREFERENCES_NAME, 0).edit().clear().commit()
        val fixtureSerial = "0123456789ABCDEF"
        val firstProvider = provider(context)

        assertThrows(ProtocolViolation::class.java) {
            firstProvider.forFixture(fixtureSerial).allocate()
        }
        assertTrue(firstProvider.initializeMissingAfterOwnerConfirmedFirmwareRestart(fixtureSerial))
        assertEquals(1L, firstProvider.forFixture(fixtureSerial).allocate())
        assertEquals(2L, firstProvider.forFixture(fixtureSerial).allocate())

        val recreatedProvider = provider(context)

        assertEquals(3L, recreatedProvider.forFixture(fixtureSerial).allocate())
    }

    @Test
    fun requestIdStateIsScopedToTheValidatedFixtureSerial() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PREFERENCES_NAME, 0).edit().clear().commit()
        val provider = provider(context)
        val fixtureA = "0123456789ABCDEF"
        val fixtureB = "FEDCBA9876543210"

        assertTrue(provider.initializeMissingAfterOwnerConfirmedFirmwareRestart(fixtureA))
        assertEquals(1L, provider.forFixture(fixtureA).allocate())
        assertThrows(ProtocolViolation::class.java) {
            provider.forFixture(fixtureB).allocate()
        }
        assertTrue(provider.initializeMissingAfterOwnerConfirmedFirmwareRestart(fixtureB))
        assertEquals(1L, provider.forFixture(fixtureB).allocate())
        assertEquals(2L, provider.forFixture(fixtureA).allocate())
    }

    private fun provider(context: android.content.Context) =
        EngineeringRequestIdSequenceProvider { serial ->
            AndroidEngineeringRequestIdStateStore(context, serial)
        }

    private companion object {
        const val PREFERENCES_NAME = "engineering_request_id_state_v1"
    }
}
