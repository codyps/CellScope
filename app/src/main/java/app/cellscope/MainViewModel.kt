package app.cellscope

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cellscope.battery.BatteryReader
import app.cellscope.data.BatteryReading
import app.cellscope.data.BatterySample
import app.cellscope.data.GapReason
import app.cellscope.data.TimelineGap
import app.cellscope.data.TimelineRange
import app.cellscope.recording.RecordingPreferences
import app.cellscope.recording.RecordingService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CellScopeApplication
    private val dao = app.database.batteryDao()
    private val reader = BatteryReader(app)
    private val preferences = RecordingPreferences(app)

    private val _liveReading = MutableStateFlow<BatteryReading?>(null)
    val liveReading: StateFlow<BatteryReading?> = _liveReading.asStateFlow()

    private val _recordingEnabled = MutableStateFlow(preferences.recordingEnabled)
    val recordingEnabled: StateFlow<Boolean> = _recordingEnabled.asStateFlow()

    private val _sampleIntervalMs = MutableStateFlow(preferences.sampleIntervalMs)
    val sampleIntervalMs: StateFlow<Long> = _sampleIntervalMs.asStateFlow()

    private val _collapseGaps = MutableStateFlow(preferences.collapseGaps)
    val collapseGaps: StateFlow<Boolean> = _collapseGaps.asStateFlow()

    private val _timelineRange = MutableStateFlow(TimelineRange.DAY)
    val timelineRange: StateFlow<TimelineRange> = _timelineRange.asStateFlow()

    private val rangeStart = _timelineRange.map { range ->
        range.durationMs?.let { System.currentTimeMillis() - it } ?: 0L
    }
    val samples = rangeStart.flatMapLatest(dao::observeSamplesSince)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val gaps = rangeStart.flatMapLatest { dao.observeGapsSince(it, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sampleCount = dao.observeSampleCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            while (isActive) {
                _liveReading.value = withContext(Dispatchers.IO) { reader.read() }
                delay(1_000)
            }
        }
    }

    fun ensureRecording() {
        if (preferences.recordingEnabled) RecordingService.ensureRunning(app)
    }

    fun setRecordingEnabled(enabled: Boolean) {
        preferences.recordingEnabled = enabled
        _recordingEnabled.value = enabled
        if (enabled) RecordingService.ensureRunning(app) else RecordingService.disable(app)
    }

    fun setInterval(intervalMs: Long) {
        preferences.sampleIntervalMs = intervalMs
        _sampleIntervalMs.value = preferences.sampleIntervalMs
    }

    fun setCollapseGaps(collapse: Boolean) {
        preferences.collapseGaps = collapse
        _collapseGaps.value = collapse
    }

    fun setTimelineRange(range: TimelineRange) {
        _timelineRange.value = range
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteSamples()
            dao.deleteGaps()
        }
    }

    suspend fun csv(samples: List<BatterySample>, gaps: List<TimelineGap>): String = withContext(Dispatchers.Default) {
        buildString {
            appendLine("# CellScope continuous timeline CSV v2")
            gaps.forEach { gap ->
                appendLine("# gap,${iso(gap.startedAtMs)},${gap.endedAtMs?.let(::iso).orEmpty()},${GapReason.label(gap.reason)}")
            }
            appendLine("timestamp_iso,elapsed_ms,level_percent,charge_uah,current_now_ua,current_average_ua,energy_nwh,voltage_mv,temperature_deci_c,status,plug_source,health,present")
            samples.forEach { sample ->
                appendLine(listOf(
                    iso(sample.wallTimeMs),
                    sample.elapsedRealtimeMs,
                    sample.levelPercent.orEmpty(),
                    sample.chargeCounterUah.orEmpty(),
                    sample.currentNowUa.orEmpty(),
                    sample.currentAverageUa.orEmpty(),
                    sample.energyCounterNwh.orEmpty(),
                    sample.voltageMv.orEmpty(),
                    sample.temperatureDeciC.orEmpty(),
                    sample.status,
                    sample.plugSource,
                    sample.health,
                    sample.isPresent,
                ).joinToString(","))
            }
        }
    }

    private fun iso(timestamp: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        Locale.US,
    ).format(Date(timestamp))

    private fun Any?.orEmpty(): String = this?.toString() ?: ""
}
