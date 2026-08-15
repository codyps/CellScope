package app.cellscope.recording

import android.content.Context
import androidx.core.content.edit

class RecordingPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var recordingEnabled: Boolean
        get() = preferences.getBoolean(KEY_RECORDING_ENABLED, true)
        set(value) = preferences.edit { putBoolean(KEY_RECORDING_ENABLED, value) }

    var sampleIntervalMs: Long
        get() = preferences.getLong(KEY_INTERVAL, DEFAULT_INTERVAL_MS).coerceIn(1_000, 60_000)
        set(value) = preferences.edit { putLong(KEY_INTERVAL, value.coerceIn(1_000, 60_000)) }

    var collapseGaps: Boolean
        get() = preferences.getBoolean(KEY_COLLAPSE_GAPS, false)
        set(value) = preferences.edit { putBoolean(KEY_COLLAPSE_GAPS, value) }

    companion object {
        private const val NAME = "cellscope_settings"
        private const val KEY_RECORDING_ENABLED = "recording_enabled"
        private const val KEY_INTERVAL = "sample_interval_ms"
        private const val KEY_COLLAPSE_GAPS = "collapse_gaps"
        const val DEFAULT_INTERVAL_MS = 10_000L
    }
}
