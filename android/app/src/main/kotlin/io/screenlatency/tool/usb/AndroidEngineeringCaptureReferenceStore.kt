package io.screenlatency.tool.usb

import android.content.Context
import android.content.SharedPreferences

/** Retains the last engineering archive identity so retrieval can resume after process loss. */
internal class AndroidEngineeringCaptureReferenceStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun save(reference: EngineeringCaptureReference): Boolean {
        if (!SERIAL_PATTERN.matches(reference.fixtureSerial) ||
            reference.nonce !in 1L..U32_MAX ||
            !validGenerationPair(reference.archiveGeneration, reference.sessionGeneration) ||
            reference.polarity !in 1..2 || reference.rateStage !in 1..2 ||
            reference.timeoutMs !in 1L..86_400_000L
        ) {
            return false
        }
        return preferences.edit()
            .putLong(key(reference.fixtureSerial, FIELD_NONCE), reference.nonce)
            .putLong(key(reference.fixtureSerial, FIELD_ARCHIVE_GENERATION), reference.archiveGeneration)
            .putLong(key(reference.fixtureSerial, FIELD_SESSION_GENERATION), reference.sessionGeneration)
            .putInt(key(reference.fixtureSerial, FIELD_POLARITY), reference.polarity)
            .putInt(key(reference.fixtureSerial, FIELD_RATE_STAGE), reference.rateStage)
            .putLong(key(reference.fixtureSerial, FIELD_TIMEOUT), reference.timeoutMs)
            .putBoolean(key(reference.fixtureSerial, FIELD_WINDOW_ELAPSED), reference.captureWindowElapsed)
            .commit()
    }

    fun load(serial: String): EngineeringCaptureReference? {
        if (!SERIAL_PATTERN.matches(serial)) return null
        val nonce = getLong(serial, FIELD_NONCE) ?: return null
        val archiveGeneration = getLong(serial, FIELD_ARCHIVE_GENERATION) ?: return null
        val sessionGeneration = getLong(serial, FIELD_SESSION_GENERATION) ?: return null
        val polarity = getInt(serial, FIELD_POLARITY) ?: return null
        val rateStage = getInt(serial, FIELD_RATE_STAGE) ?: return null
        val timeout = getLong(serial, FIELD_TIMEOUT) ?: return null
        val windowElapsed = getBoolean(serial, FIELD_WINDOW_ELAPSED) ?: false
        if (nonce !in 1L..U32_MAX ||
            !validGenerationPair(archiveGeneration, sessionGeneration) ||
            polarity !in 1..2 || rateStage !in 1..2 || timeout !in 1L..86_400_000L
        ) {
            return null
        }
        return EngineeringCaptureReference(
            fixtureSerial = serial,
            nonce = nonce,
            archiveGeneration = archiveGeneration,
            sessionGeneration = sessionGeneration,
            polarity = polarity,
            rateStage = rateStage,
            timeoutMs = timeout,
            captureWindowElapsed = windowElapsed,
        )
    }

    fun clear(serial: String, nonce: Long): Boolean {
        if (!SERIAL_PATTERN.matches(serial) || nonce !in 1L..U32_MAX) return false
        if (getLong(serial, FIELD_NONCE) != nonce) return false
        return preferences.edit().remove(key(serial, FIELD_NONCE))
            .remove(key(serial, FIELD_ARCHIVE_GENERATION))
            .remove(key(serial, FIELD_SESSION_GENERATION))
            .remove(key(serial, FIELD_POLARITY))
            .remove(key(serial, FIELD_RATE_STAGE))
            .remove(key(serial, FIELD_TIMEOUT))
            .remove(key(serial, FIELD_WINDOW_ELAPSED))
            .commit()
    }

    private fun getLong(serial: String, field: String): Long? = try {
        val fieldKey = key(serial, field)
        if (preferences.contains(fieldKey)) preferences.getLong(fieldKey, INVALID_LONG) else null
    } catch (_: ClassCastException) {
        null
    }

    private fun getInt(serial: String, field: String): Int? = try {
        val fieldKey = key(serial, field)
        if (preferences.contains(fieldKey)) preferences.getInt(fieldKey, INVALID_INT) else null
    } catch (_: ClassCastException) {
        null
    }

    private fun getBoolean(serial: String, field: String): Boolean? = try {
        val fieldKey = key(serial, field)
        if (preferences.contains(fieldKey)) preferences.getBoolean(fieldKey, false) else null
    } catch (_: ClassCastException) {
        null
    }

    private fun key(serial: String, field: String) = "${serial}_$field"

    private fun validGenerationPair(archive: Long, session: Long): Boolean =
        (archive == 0L && session == 0L) || (archive in 1L..U32_MAX && session in 1L..U32_MAX)

    private companion object {
        const val PREFERENCES_NAME = "engineering_capture_reference_v1"
        const val FIELD_NONCE = "nonce"
        const val FIELD_ARCHIVE_GENERATION = "archive_generation"
        const val FIELD_SESSION_GENERATION = "session_generation"
        const val FIELD_POLARITY = "polarity"
        const val FIELD_RATE_STAGE = "rate_stage"
        const val FIELD_TIMEOUT = "timeout_ms"
        const val FIELD_WINDOW_ELAPSED = "capture_window_elapsed"
        const val INVALID_LONG = -1L
        const val INVALID_INT = -1
        const val U32_MAX = 0xFFFF_FFFFL
        val SERIAL_PATTERN = Regex("[0-9A-F]{16}")
    }
}
