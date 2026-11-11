package io.screenlatency.tool.usb

import android.content.Context
import android.content.SharedPreferences

/** Stores each fixture's next request ID synchronously so process recreation cannot reuse it. */
internal class AndroidEngineeringRequestIdStateStore(
    context: Context,
    serial: String,
) : EngineeringRequestIdStateStore {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val key = "next_${serial.also { require(SERIAL_PATTERN.matches(it)) }}"

    override fun loadNextRequestId(): Long? {
        if (!preferences.contains(key)) return null
        return try {
            preferences.getLong(key, INVALID_REQUEST_ID)
        } catch (failure: ClassCastException) {
            INVALID_REQUEST_ID
        }
    }

    override fun saveNextRequestId(nextRequestId: Long): Boolean {
        if (nextRequestId !in 1L..EXHAUSTED_REQUEST_ID) return false
        return preferences.edit().putLong(key, nextRequestId).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "engineering_request_id_state_v1"
        const val INVALID_REQUEST_ID = -1L
        const val EXHAUSTED_REQUEST_ID = 0x1_0000_0000L
        val SERIAL_PATTERN = Regex("[0-9A-F]{16}")
    }
}
