package io.screenlatency.tool.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidEngineeringCaptureReferenceStoreTest {
    @Test
    fun pendingAndAcceptedReferencesSurviveStoreRecreationUntilExplicitRelease() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PREFERENCES_NAME, 0).edit().clear().commit()
        val pending = reference(archiveGeneration = 0, sessionGeneration = 0)

        assertTrue(AndroidEngineeringCaptureReferenceStore(context).save(pending))
        assertEquals(pending, AndroidEngineeringCaptureReferenceStore(context).load(pending.fixtureSerial))

        val accepted = pending.copy(archiveGeneration = 9, sessionGeneration = 12)
        assertTrue(AndroidEngineeringCaptureReferenceStore(context).save(accepted))
        val recreated = AndroidEngineeringCaptureReferenceStore(context)
        assertEquals(accepted, recreated.load(accepted.fixtureSerial))
        val elapsed = accepted.copy(captureWindowElapsed = true)
        assertTrue(recreated.save(elapsed))
        assertEquals(elapsed, AndroidEngineeringCaptureReferenceStore(context).load(elapsed.fixtureSerial))
        assertFalse(recreated.clear(accepted.fixtureSerial, accepted.nonce + 1))
        assertTrue(recreated.clear(accepted.fixtureSerial, accepted.nonce))
        assertNull(recreated.load(accepted.fixtureSerial))
    }

    @Test
    fun rejectsInvalidFixtureAndGenerationPairs() {
        val context = RuntimeEnvironment.getApplication()
        val store = AndroidEngineeringCaptureReferenceStore(context)

        assertFalse(store.save(reference(serial = "not-a-serial")))
        assertFalse(store.save(reference(archiveGeneration = 1, sessionGeneration = 0)))
        assertNull(store.load("not-a-serial"))
    }

    private fun reference(
        serial: String = "0123456789ABCDEF",
        archiveGeneration: Long = 0,
        sessionGeneration: Long = 0,
    ) = EngineeringCaptureReference(
        fixtureSerial = serial,
        nonce = 44,
        archiveGeneration = archiveGeneration,
        sessionGeneration = sessionGeneration,
        polarity = 2,
        rateStage = 1,
        timeoutMs = 60_000,
    )

    private companion object {
        const val PREFERENCES_NAME = "engineering_capture_reference_v1"
    }
}
