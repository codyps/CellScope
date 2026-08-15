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
import app.cellscope.update.UpdateState
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
    private val dao by lazy { app.database.batteryDao() }
    private val reader by lazy { BatteryReader(app, app.sysfsAccess) }
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
    val samples by lazy {
        rangeStart.flatMapLatest(dao::observeSamplesSince)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }
    val gaps by lazy {
        rangeStart.flatMapLatest { dao.observeGapsSince(it, System.currentTimeMillis()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }
    val sampleCount by lazy {
        dao.observeSampleCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    }
    val sysfsAccess by lazy { app.sysfsAccess.state }
    val updateState: StateFlow<UpdateState> = app.updates.state

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

    fun requestShizukuAccess() {
        app.sysfsAccess.requestShizukuPermission()
    }

    fun setRootAccess(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { app.sysfsAccess.setRootEnabled(enabled) }
    }

    fun checkForUpdates() = app.updates.checkNow()

    suspend fun csv(samples: List<BatterySample>, gaps: List<TimelineGap>): String = withContext(Dispatchers.Default) {
        buildString {
            appendLine("# CellScope continuous timeline CSV v5")
            gaps.forEach { gap ->
                appendLine("# gap,${iso(gap.startedAtMs)},${gap.endedAtMs?.let(::iso).orEmpty()},${GapReason.label(gap.reason)}")
            }
            appendLine("timestamp_iso,elapsed_ms,level_percent,charge_uah,current_now_ua,current_average_ua,energy_nwh,voltage_mv,temperature_deci_c,status,plug_source,health,present,charge_full_uah,charge_full_design_uah,charge_voltage_limit_mv,charge_voltage_design_limit_mv,charge_start_threshold_percent,charge_end_threshold_percent,cycle_count,voltage_ocv_mv,resistance_micro_ohm,technology,charge_type,power_supply_type,input_current_limit_ua,input_voltage_limit_mv,fuel_gauge_raw_soc,charge_current_limit_ua,input_current_limited,aicl_complete,restricted_charging,battery_charging_enabled,charging_enabled,safety_timer_enabled,charger_over_voltage,overload,usb_overheat,battery_profile,battery_id_resistance_ohm,jeita_cool_deci_c,jeita_warm_deci_c,soc_reporting_ready,esr_count,cycle_count_bins,usb_present,usb_online,usb_current_max_ua,usb_voltage_max_mv,usb_otg,usb_health,dc_present,dc_online,dc_current_max_ua,dc_charging_enabled,dc_type,parallel_present,parallel_charging_enabled,parallel_status,parallel_current_max_ua,parallel_charge_current_limit_ua,parallel_voltage_max_mv,parallel_input_current_limited,sysfs_provider,sysfs_fallback_fields")
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
                    sample.chargeFullUah.orEmpty(),
                    sample.chargeFullDesignUah.orEmpty(),
                    sample.chargeVoltageLimitMv.orEmpty(),
                    sample.chargeVoltageDesignLimitMv.orEmpty(),
                    sample.chargeStartThresholdPercent.orEmpty(),
                    sample.chargeEndThresholdPercent.orEmpty(),
                    sample.cycleCount.orEmpty(),
                    sample.voltageOcvMv.orEmpty(),
                    sample.resistanceMicroOhm.orEmpty(),
                    sample.technology.csv(),
                    sample.chargeType.csv(),
                    sample.powerSupplyType.csv(),
                    sample.inputCurrentLimitUa.orEmpty(),
                    sample.inputVoltageLimitMv.orEmpty(),
                    sample.fuelGaugeRawSoc.orEmpty(),
                    sample.chargeCurrentLimitUa.orEmpty(),
                    sample.inputCurrentLimited.orEmpty(),
                    sample.aiclComplete.orEmpty(),
                    sample.restrictedCharging.orEmpty(),
                    sample.batteryChargingEnabled.orEmpty(),
                    sample.chargingEnabled.orEmpty(),
                    sample.safetyTimerEnabled.orEmpty(),
                    sample.chargerOverVoltage.orEmpty(),
                    sample.overload.orEmpty(),
                    sample.usbOverheat.orEmpty(),
                    sample.batteryProfile.csv(),
                    sample.batteryIdResistanceOhm.orEmpty(),
                    sample.jeitaCoolDeciC.orEmpty(),
                    sample.jeitaWarmDeciC.orEmpty(),
                    sample.socReportingReady.orEmpty(),
                    sample.esrCount.orEmpty(),
                    sample.cycleCountBins.csv(),
                    sample.usbPresent.orEmpty(),
                    sample.usbOnline.orEmpty(),
                    sample.usbCurrentMaxUa.orEmpty(),
                    sample.usbVoltageMaxMv.orEmpty(),
                    sample.usbOtg.orEmpty(),
                    sample.usbHealth.csv(),
                    sample.dcPresent.orEmpty(),
                    sample.dcOnline.orEmpty(),
                    sample.dcCurrentMaxUa.orEmpty(),
                    sample.dcChargingEnabled.orEmpty(),
                    sample.dcType.csv(),
                    sample.parallelPresent.orEmpty(),
                    sample.parallelChargingEnabled.orEmpty(),
                    sample.parallelStatus.csv(),
                    sample.parallelCurrentMaxUa.orEmpty(),
                    sample.parallelChargeCurrentLimitUa.orEmpty(),
                    sample.parallelVoltageMaxMv.orEmpty(),
                    sample.parallelInputCurrentLimited.orEmpty(),
                    sample.sysfsProvider.orEmpty(),
                    sample.sysfsFallbackFields.orEmpty(),
                ).joinToString(","))
            }
        }
    }

    private fun iso(timestamp: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        Locale.US,
    ).format(Date(timestamp))

    private fun Any?.orEmpty(): String = this?.toString() ?: ""
    private fun String?.csv(): String = this?.let { "\"${it.replace("\"", "\"\"")}\"" } ?: ""
}
