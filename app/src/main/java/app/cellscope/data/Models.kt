package app.cellscope.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "battery_samples",
    indices = [Index("wallTimeMs"), Index("elapsedRealtimeMs")],
)
data class BatterySample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wallTimeMs: Long,
    val elapsedRealtimeMs: Long,
    val levelPercent: Float?,
    val chargeCounterUah: Long?,
    val currentNowUa: Long?,
    val currentAverageUa: Long?,
    val energyCounterNwh: Long?,
    val voltageMv: Int?,
    val temperatureDeciC: Int?,
    val status: Int,
    val plugSource: Int,
    val health: Int,
    val isPresent: Boolean,
    val chargeFullUah: Long? = null,
    val chargeFullDesignUah: Long? = null,
    val chargeVoltageLimitMv: Int? = null,
    val chargeVoltageDesignLimitMv: Int? = null,
    val chargeStartThresholdPercent: Int? = null,
    val chargeEndThresholdPercent: Int? = null,
    val cycleCount: Int? = null,
    val voltageOcvMv: Int? = null,
    val resistanceMicroOhm: Long? = null,
    val technology: String? = null,
    val chargeType: String? = null,
    val powerSupplyType: String? = null,
    val inputCurrentLimitUa: Long? = null,
    val inputVoltageLimitMv: Int? = null,
    val fuelGaugeRawSoc: Long? = null,
    val chargeCurrentLimitUa: Long? = null,
    val inputCurrentLimited: Boolean? = null,
    val aiclComplete: Boolean? = null,
    val restrictedCharging: Boolean? = null,
    val batteryChargingEnabled: Boolean? = null,
    val chargingEnabled: Boolean? = null,
    val safetyTimerEnabled: Boolean? = null,
    val chargerOverVoltage: Boolean? = null,
    val overload: Boolean? = null,
    val usbOverheat: Boolean? = null,
    val batteryProfile: String? = null,
    val batteryIdResistanceOhm: Long? = null,
    val jeitaCoolDeciC: Int? = null,
    val jeitaWarmDeciC: Int? = null,
    val socReportingReady: Boolean? = null,
    val esrCount: Int? = null,
    val cycleCountBins: String? = null,
    val usbPresent: Boolean? = null,
    val usbOnline: Boolean? = null,
    val usbCurrentMaxUa: Long? = null,
    val usbVoltageMaxMv: Int? = null,
    val usbOtg: Boolean? = null,
    val usbHealth: String? = null,
    val dcPresent: Boolean? = null,
    val dcOnline: Boolean? = null,
    val dcCurrentMaxUa: Long? = null,
    val dcChargingEnabled: Boolean? = null,
    val dcType: String? = null,
    val parallelPresent: Boolean? = null,
    val parallelChargingEnabled: Boolean? = null,
    val parallelStatus: String? = null,
    val parallelCurrentMaxUa: Long? = null,
    val parallelChargeCurrentLimitUa: Long? = null,
    val parallelVoltageMaxMv: Int? = null,
    val parallelInputCurrentLimited: Boolean? = null,
    val sysfsProvider: String? = null,
    val sysfsFallbackFields: String? = null,
)

@Entity(tableName = "timeline_gaps", indices = [Index("startedAtMs")])
data class TimelineGap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val reason: String,
    val details: String? = null,
)

object GapReason {
    const val RECORDING_DISABLED = "RECORDING_DISABLED"
    const val RECORDER_INTERRUPTED = "RECORDER_INTERRUPTED"

    fun label(reason: String): String = when (reason) {
        RECORDING_DISABLED -> "Recording disabled"
        RECORDER_INTERRUPTED -> "Phone off or recorder unavailable"
        else -> "Unknown interruption"
    }
}

data class BatteryReading(
    val wallTimeMs: Long,
    val elapsedRealtimeMs: Long,
    val levelPercent: Float?,
    val chargeCounterUah: Long?,
    val currentNowUa: Long?,
    val currentAverageUa: Long?,
    val energyCounterNwh: Long?,
    val voltageMv: Int?,
    val temperatureDeciC: Int?,
    val status: Int,
    val plugSource: Int,
    val health: Int,
    val isPresent: Boolean,
    val chargeFullUah: Long? = null,
    val chargeFullDesignUah: Long? = null,
    val chargeVoltageLimitMv: Int? = null,
    val chargeVoltageDesignLimitMv: Int? = null,
    val chargeStartThresholdPercent: Int? = null,
    val chargeEndThresholdPercent: Int? = null,
    val cycleCount: Int? = null,
    val voltageOcvMv: Int? = null,
    val resistanceMicroOhm: Long? = null,
    val technology: String? = null,
    val chargeType: String? = null,
    val powerSupplyType: String? = null,
    val inputCurrentLimitUa: Long? = null,
    val inputVoltageLimitMv: Int? = null,
    val fuelGaugeRawSoc: Long? = null,
    val chargeCurrentLimitUa: Long? = null,
    val inputCurrentLimited: Boolean? = null,
    val aiclComplete: Boolean? = null,
    val restrictedCharging: Boolean? = null,
    val batteryChargingEnabled: Boolean? = null,
    val chargingEnabled: Boolean? = null,
    val safetyTimerEnabled: Boolean? = null,
    val chargerOverVoltage: Boolean? = null,
    val overload: Boolean? = null,
    val usbOverheat: Boolean? = null,
    val batteryProfile: String? = null,
    val batteryIdResistanceOhm: Long? = null,
    val jeitaCoolDeciC: Int? = null,
    val jeitaWarmDeciC: Int? = null,
    val socReportingReady: Boolean? = null,
    val esrCount: Int? = null,
    val cycleCountBins: String? = null,
    val usbPresent: Boolean? = null,
    val usbOnline: Boolean? = null,
    val usbCurrentMaxUa: Long? = null,
    val usbVoltageMaxMv: Int? = null,
    val usbOtg: Boolean? = null,
    val usbHealth: String? = null,
    val dcPresent: Boolean? = null,
    val dcOnline: Boolean? = null,
    val dcCurrentMaxUa: Long? = null,
    val dcChargingEnabled: Boolean? = null,
    val dcType: String? = null,
    val parallelPresent: Boolean? = null,
    val parallelChargingEnabled: Boolean? = null,
    val parallelStatus: String? = null,
    val parallelCurrentMaxUa: Long? = null,
    val parallelChargeCurrentLimitUa: Long? = null,
    val parallelVoltageMaxMv: Int? = null,
    val parallelInputCurrentLimited: Boolean? = null,
    val sysfsProvider: String? = null,
    val sysfsFallbackFields: Set<String> = emptySet(),
) {
    fun asSample() = BatterySample(
        wallTimeMs = wallTimeMs,
        elapsedRealtimeMs = elapsedRealtimeMs,
        levelPercent = levelPercent,
        chargeCounterUah = chargeCounterUah,
        currentNowUa = currentNowUa,
        currentAverageUa = currentAverageUa,
        energyCounterNwh = energyCounterNwh,
        voltageMv = voltageMv,
        temperatureDeciC = temperatureDeciC,
        status = status,
        plugSource = plugSource,
        health = health,
        isPresent = isPresent,
        chargeFullUah = chargeFullUah,
        chargeFullDesignUah = chargeFullDesignUah,
        chargeVoltageLimitMv = chargeVoltageLimitMv,
        chargeVoltageDesignLimitMv = chargeVoltageDesignLimitMv,
        chargeStartThresholdPercent = chargeStartThresholdPercent,
        chargeEndThresholdPercent = chargeEndThresholdPercent,
        cycleCount = cycleCount,
        voltageOcvMv = voltageOcvMv,
        resistanceMicroOhm = resistanceMicroOhm,
        technology = technology,
        chargeType = chargeType,
        powerSupplyType = powerSupplyType,
        inputCurrentLimitUa = inputCurrentLimitUa,
        inputVoltageLimitMv = inputVoltageLimitMv,
        fuelGaugeRawSoc = fuelGaugeRawSoc,
        chargeCurrentLimitUa = chargeCurrentLimitUa,
        inputCurrentLimited = inputCurrentLimited,
        aiclComplete = aiclComplete,
        restrictedCharging = restrictedCharging,
        batteryChargingEnabled = batteryChargingEnabled,
        chargingEnabled = chargingEnabled,
        safetyTimerEnabled = safetyTimerEnabled,
        chargerOverVoltage = chargerOverVoltage,
        overload = overload,
        usbOverheat = usbOverheat,
        batteryProfile = batteryProfile,
        batteryIdResistanceOhm = batteryIdResistanceOhm,
        jeitaCoolDeciC = jeitaCoolDeciC,
        jeitaWarmDeciC = jeitaWarmDeciC,
        socReportingReady = socReportingReady,
        esrCount = esrCount,
        cycleCountBins = cycleCountBins,
        usbPresent = usbPresent,
        usbOnline = usbOnline,
        usbCurrentMaxUa = usbCurrentMaxUa,
        usbVoltageMaxMv = usbVoltageMaxMv,
        usbOtg = usbOtg,
        usbHealth = usbHealth,
        dcPresent = dcPresent,
        dcOnline = dcOnline,
        dcCurrentMaxUa = dcCurrentMaxUa,
        dcChargingEnabled = dcChargingEnabled,
        dcType = dcType,
        parallelPresent = parallelPresent,
        parallelChargingEnabled = parallelChargingEnabled,
        parallelStatus = parallelStatus,
        parallelCurrentMaxUa = parallelCurrentMaxUa,
        parallelChargeCurrentLimitUa = parallelChargeCurrentLimitUa,
        parallelVoltageMaxMv = parallelVoltageMaxMv,
        parallelInputCurrentLimited = parallelInputCurrentLimited,
        sysfsProvider = sysfsProvider,
        sysfsFallbackFields = sysfsFallbackFields.takeIf { it.isNotEmpty() }?.sorted()?.joinToString("|"),
    )
}

enum class ChartMetric(
    val title: String,
    val unit: String,
    val isExtended: Boolean = false,
    val isBinary: Boolean = false,
    val isCategorical: Boolean = false,
) {
    LEVEL("Battery level", "%"),
    VOLTAGE("Voltage", "V"),
    CURRENT("Current", "mA"),
    AVERAGE_CURRENT("Average current", "mA"),
    CHARGE("Remaining charge", "mAh"),
    ENERGY("Energy counter", "Wh"),
    TEMPERATURE("Temperature", "°C"),
    POWER("Estimated power", "W"),
    STATUS("Battery status", "state", isCategorical = true),
    PLUG_SOURCE("Plug source", "state", isCategorical = true),
    HEALTH("Battery health", "state", isCategorical = true),
    PRESENT("Battery present", "0/1", isBinary = true),
    FULL_CHARGE("Learned full threshold", "mAh", true),
    DESIGN_CHARGE("Design full threshold", "mAh", true),
    CHARGE_VOLTAGE_LIMIT("Charge voltage threshold", "V", true),
    CHARGE_VOLTAGE_DESIGN_LIMIT("Design voltage threshold", "V", true),
    CHARGE_START_THRESHOLD("Charge start threshold", "%", true),
    CHARGE_END_THRESHOLD("Charge end threshold", "%", true),
    CYCLE_COUNT("Cycle count", "cycles", true),
    OCV("Open-circuit voltage", "V", true),
    RESISTANCE("Internal resistance", "mΩ", true),
    FUEL_GAUGE_RAW_SOC("Fuel-gauge raw SOC", "raw", true),
    TECHNOLOGY("Technology", "state", true, isCategorical = true),
    CHARGE_TYPE("Charge type", "state", true, isCategorical = true),
    POWER_SUPPLY_TYPE("Power source", "state", true, isCategorical = true),
    INPUT_CURRENT_LIMIT("Input current limit", "mA", true),
    INPUT_VOLTAGE_LIMIT("Input voltage limit", "V", true),
    CHARGE_CURRENT_LIMIT("Charge current limit", "mA", true),
    INPUT_CURRENT_LIMITED("Input current limited", "0/1", true, true),
    AICL_COMPLETE("AICL complete", "0/1", true, true),
    RESTRICTED_CHARGING("Restricted charging", "0/1", true, true),
    BATTERY_CHARGING_ENABLED("Battery charging enabled", "0/1", true, true),
    INPUT_CHARGING_ENABLED("Input charging enabled", "0/1", true, true),
    SAFETY_TIMER_ENABLED("Safety timer enabled", "0/1", true, true),
    CHARGER_OVER_VOLTAGE("Charger over-voltage", "0/1", true, true),
    OVERLOAD("Overload", "0/1", true, true),
    USB_OVERHEAT("USB overheat", "0/1", true, true),
    BATTERY_PROFILE("Battery profile", "state", true, isCategorical = true),
    BATTERY_ID_RESISTANCE("Battery ID resistance", "kΩ", true),
    JEITA_COOL("JEITA cool boundary", "°C", true),
    JEITA_WARM("JEITA warm boundary", "°C", true),
    SOC_REPORTING_READY("SOC reporting ready", "0/1", true, true),
    ESR_COUNT("ESR update count", "updates", true),
    CYCLE_COUNT_BINS("Cycle depth bins", "state", true, isCategorical = true),
    USB_PRESENT("USB present", "0/1", true, true),
    USB_ONLINE("USB online", "0/1", true, true),
    USB_CURRENT_LIMIT("USB current limit", "mA", true),
    USB_VOLTAGE_LIMIT("USB voltage limit", "V", true),
    USB_OTG("USB OTG active", "0/1", true, true),
    USB_HEALTH("USB health", "state", true, isCategorical = true),
    DC_PRESENT("DC present", "0/1", true, true),
    DC_ONLINE("DC online", "0/1", true, true),
    DC_CURRENT_LIMIT("DC current limit", "mA", true),
    DC_CHARGING_ENABLED("DC charging enabled", "0/1", true, true),
    DC_TYPE("DC source type", "state", true, isCategorical = true),
    PARALLEL_PRESENT("Parallel charger present", "0/1", true, true),
    PARALLEL_CHARGING_ENABLED("Parallel charging enabled", "0/1", true, true),
    PARALLEL_STATUS("Parallel status", "state", true, isCategorical = true),
    PARALLEL_CURRENT_LIMIT("Parallel current limit", "mA", true),
    PARALLEL_CHARGE_CURRENT_LIMIT("Parallel charge limit", "mA", true),
    PARALLEL_VOLTAGE_LIMIT("Parallel voltage limit", "V", true),
    PARALLEL_INPUT_CURRENT_LIMITED("Parallel input limited", "0/1", true, true),
    SYSFS_PROVIDER("Sysfs provider", "state", true, isCategorical = true),
    SYSFS_FALLBACK_FIELDS("API fields filled by sysfs", "state", true, isCategorical = true),
}

enum class TimelineRange(val title: String, val durationMs: Long?) {
    DAY("24h", 24 * 60 * 60 * 1_000L),
    WEEK("7d", 7 * 24 * 60 * 60 * 1_000L),
    MONTH("30d", 30 * 24 * 60 * 60 * 1_000L),
    ALL("All", null),
}

fun BatterySample.valueFor(metric: ChartMetric): Float? = when (metric) {
    ChartMetric.LEVEL -> levelPercent
    ChartMetric.VOLTAGE -> voltageMv?.div(1_000f)
    ChartMetric.CURRENT -> currentNowUa?.div(1_000f)
    ChartMetric.AVERAGE_CURRENT -> currentAverageUa?.div(1_000f)
    ChartMetric.CHARGE -> chargeCounterUah?.div(1_000f)
    ChartMetric.ENERGY -> energyCounterNwh?.div(1_000_000_000f)
    ChartMetric.TEMPERATURE -> temperatureDeciC?.div(10f)
    ChartMetric.POWER -> if (voltageMv != null && currentNowUa != null) {
        voltageMv * currentNowUa / 1_000_000_000f
    } else null
    ChartMetric.STATUS,
    ChartMetric.PLUG_SOURCE,
    ChartMetric.HEALTH,
    -> null
    ChartMetric.PRESENT -> isPresent.asChartValue()
    ChartMetric.FULL_CHARGE -> chargeFullUah?.div(1_000f)
    ChartMetric.DESIGN_CHARGE -> chargeFullDesignUah?.div(1_000f)
    ChartMetric.CHARGE_VOLTAGE_LIMIT -> chargeVoltageLimitMv?.div(1_000f)
    ChartMetric.CHARGE_VOLTAGE_DESIGN_LIMIT -> chargeVoltageDesignLimitMv?.div(1_000f)
    ChartMetric.CHARGE_START_THRESHOLD -> chargeStartThresholdPercent?.toFloat()
    ChartMetric.CHARGE_END_THRESHOLD -> chargeEndThresholdPercent?.toFloat()
    ChartMetric.CYCLE_COUNT -> cycleCount?.toFloat()
    ChartMetric.OCV -> voltageOcvMv?.div(1_000f)
    ChartMetric.RESISTANCE -> resistanceMicroOhm?.div(1_000f)
    ChartMetric.FUEL_GAUGE_RAW_SOC -> fuelGaugeRawSoc?.toFloat()
    ChartMetric.TECHNOLOGY,
    ChartMetric.CHARGE_TYPE,
    ChartMetric.POWER_SUPPLY_TYPE,
    -> null
    ChartMetric.INPUT_CURRENT_LIMIT -> inputCurrentLimitUa?.div(1_000f)
    ChartMetric.INPUT_VOLTAGE_LIMIT -> inputVoltageLimitMv?.div(1_000f)
    ChartMetric.CHARGE_CURRENT_LIMIT -> chargeCurrentLimitUa?.div(1_000f)
    ChartMetric.INPUT_CURRENT_LIMITED -> inputCurrentLimited.asChartValue()
    ChartMetric.AICL_COMPLETE -> aiclComplete.asChartValue()
    ChartMetric.RESTRICTED_CHARGING -> restrictedCharging.asChartValue()
    ChartMetric.BATTERY_CHARGING_ENABLED -> batteryChargingEnabled.asChartValue()
    ChartMetric.INPUT_CHARGING_ENABLED -> chargingEnabled.asChartValue()
    ChartMetric.SAFETY_TIMER_ENABLED -> safetyTimerEnabled.asChartValue()
    ChartMetric.CHARGER_OVER_VOLTAGE -> chargerOverVoltage.asChartValue()
    ChartMetric.OVERLOAD -> overload.asChartValue()
    ChartMetric.USB_OVERHEAT -> usbOverheat.asChartValue()
    ChartMetric.BATTERY_PROFILE -> null
    ChartMetric.BATTERY_ID_RESISTANCE -> batteryIdResistanceOhm?.div(1_000f)
    ChartMetric.JEITA_COOL -> jeitaCoolDeciC?.div(10f)
    ChartMetric.JEITA_WARM -> jeitaWarmDeciC?.div(10f)
    ChartMetric.SOC_REPORTING_READY -> socReportingReady.asChartValue()
    ChartMetric.ESR_COUNT -> esrCount?.toFloat()
    ChartMetric.CYCLE_COUNT_BINS -> null
    ChartMetric.USB_PRESENT -> usbPresent.asChartValue()
    ChartMetric.USB_ONLINE -> usbOnline.asChartValue()
    ChartMetric.USB_CURRENT_LIMIT -> usbCurrentMaxUa?.div(1_000f)
    ChartMetric.USB_VOLTAGE_LIMIT -> usbVoltageMaxMv?.div(1_000f)
    ChartMetric.USB_OTG -> usbOtg.asChartValue()
    ChartMetric.USB_HEALTH -> null
    ChartMetric.DC_PRESENT -> dcPresent.asChartValue()
    ChartMetric.DC_ONLINE -> dcOnline.asChartValue()
    ChartMetric.DC_CURRENT_LIMIT -> dcCurrentMaxUa?.div(1_000f)
    ChartMetric.DC_CHARGING_ENABLED -> dcChargingEnabled.asChartValue()
    ChartMetric.DC_TYPE -> null
    ChartMetric.PARALLEL_PRESENT -> parallelPresent.asChartValue()
    ChartMetric.PARALLEL_CHARGING_ENABLED -> parallelChargingEnabled.asChartValue()
    ChartMetric.PARALLEL_STATUS -> null
    ChartMetric.PARALLEL_CURRENT_LIMIT -> parallelCurrentMaxUa?.div(1_000f)
    ChartMetric.PARALLEL_CHARGE_CURRENT_LIMIT -> parallelChargeCurrentLimitUa?.div(1_000f)
    ChartMetric.PARALLEL_VOLTAGE_LIMIT -> parallelVoltageMaxMv?.div(1_000f)
    ChartMetric.PARALLEL_INPUT_CURRENT_LIMITED -> parallelInputCurrentLimited.asChartValue()
    ChartMetric.SYSFS_PROVIDER,
    ChartMetric.SYSFS_FALLBACK_FIELDS,
    -> null
}

fun BatterySample.categoryFor(metric: ChartMetric): String? = when (metric) {
    ChartMetric.STATUS -> status.toString()
    ChartMetric.PLUG_SOURCE -> plugSource.toString()
    ChartMetric.HEALTH -> health.toString()
    ChartMetric.TECHNOLOGY -> technology
    ChartMetric.CHARGE_TYPE -> chargeType
    ChartMetric.POWER_SUPPLY_TYPE -> powerSupplyType
    ChartMetric.BATTERY_PROFILE -> batteryProfile
    ChartMetric.CYCLE_COUNT_BINS -> cycleCountBins
    ChartMetric.USB_HEALTH -> usbHealth
    ChartMetric.DC_TYPE -> dcType
    ChartMetric.PARALLEL_STATUS -> parallelStatus
    ChartMetric.SYSFS_PROVIDER -> sysfsProvider
    ChartMetric.SYSFS_FALLBACK_FIELDS -> sysfsFallbackFields
    else -> null
}

fun BatterySample.hasValueFor(metric: ChartMetric): Boolean =
    valueFor(metric) != null || categoryFor(metric) != null

private fun Boolean?.asChartValue(): Float? = this?.let { if (it) 1f else 0f }
