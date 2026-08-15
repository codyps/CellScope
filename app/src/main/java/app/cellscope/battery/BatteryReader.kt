package app.cellscope.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import app.cellscope.data.BatteryReading

class BatteryReader(
    private val context: Context,
    private val sysfsAccess: PrivilegedSysfsAccess? = null,
) {
    private val manager = context.getSystemService(BatteryManager::class.java)

    fun read(): BatteryReading {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val apiLevel = if (level >= 0 && scale > 0) level * 100f / scale else {
            intProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.toFloat()
        }
        val apiCharge = intProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val apiCurrent = intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val apiVoltage = intent.validExtra(BatteryManager.EXTRA_VOLTAGE)
        val apiTemperature = intent.validExtra(BatteryManager.EXTRA_TEMPERATURE)
        val apiStatus = intent.validExtra(BatteryManager.EXTRA_STATUS)
            ?.takeUnless { it == BatteryManager.BATTERY_STATUS_UNKNOWN }
        val apiHealth = intent.validExtra(BatteryManager.EXTRA_HEALTH)
            ?.takeUnless { it == BatteryManager.BATTERY_HEALTH_UNKNOWN }
        val apiPresent = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_PRESENT) }
            ?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
        val apiPlug = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_PLUGGED) }
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val apiTechnology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        val sysfs = sysfsAccess?.readSnapshot()?.batteryData()
        val fallbacks = buildSet {
            if (apiLevel == null && sysfs?.levelPercent != null) add("level")
            if (apiCharge == null && sysfs?.chargeCounterUah != null) add("charge_counter")
            if (apiCurrent == null && sysfs?.currentNowUa != null) add("current_now")
            if (apiVoltage == null && sysfs?.voltageMv != null) add("voltage")
            if (apiTemperature == null && sysfs?.temperatureDeciC != null) add("temperature")
            if (apiStatus == null && sysfs?.status != null) add("status")
            if (apiHealth == null && sysfs?.health != null) add("health")
            if (apiPresent == null && sysfs?.isPresent != null) add("present")
            if (apiPlug == null && sysfs?.plugSource != null) add("plug_source")
            if (apiTechnology == null && sysfs?.technology != null) add("technology")
        }

        return BatteryReading(
            wallTimeMs = System.currentTimeMillis(),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            levelPercent = apiLevel ?: sysfs?.levelPercent,
            chargeCounterUah = apiCharge ?: sysfs?.chargeCounterUah,
            currentNowUa = apiCurrent ?: sysfs?.currentNowUa,
            currentAverageUa = intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
            energyCounterNwh = longProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER),
            voltageMv = apiVoltage ?: sysfs?.voltageMv,
            temperatureDeciC = apiTemperature ?: sysfs?.temperatureDeciC,
            status = apiStatus ?: sysfs?.status ?: BatteryManager.BATTERY_STATUS_UNKNOWN,
            plugSource = apiPlug ?: sysfs?.plugSource ?: 0,
            health = apiHealth ?: sysfs?.health ?: BatteryManager.BATTERY_HEALTH_UNKNOWN,
            isPresent = apiPresent ?: sysfs?.isPresent ?: false,
            chargeFullUah = sysfs?.chargeFullUah,
            chargeFullDesignUah = sysfs?.chargeFullDesignUah,
            chargeVoltageLimitMv = sysfs?.chargeVoltageLimitMv,
            chargeVoltageDesignLimitMv = sysfs?.chargeVoltageDesignLimitMv,
            chargeStartThresholdPercent = sysfs?.chargeStartThresholdPercent,
            chargeEndThresholdPercent = sysfs?.chargeEndThresholdPercent,
            cycleCount = sysfs?.cycleCount,
            voltageOcvMv = sysfs?.voltageOcvMv,
            resistanceMicroOhm = sysfs?.resistanceMicroOhm,
            technology = apiTechnology ?: sysfs?.technology,
            chargeType = sysfs?.chargeType,
            powerSupplyType = sysfs?.powerSupplyType,
            inputCurrentLimitUa = sysfs?.inputCurrentLimitUa,
            inputVoltageLimitMv = sysfs?.inputVoltageLimitMv,
            fuelGaugeRawSoc = sysfs?.fuelGaugeRawSoc,
            chargeCurrentLimitUa = sysfs?.chargeCurrentLimitUa,
            inputCurrentLimited = sysfs?.inputCurrentLimited,
            aiclComplete = sysfs?.aiclComplete,
            restrictedCharging = sysfs?.restrictedCharging,
            batteryChargingEnabled = sysfs?.batteryChargingEnabled,
            chargingEnabled = sysfs?.chargingEnabled,
            safetyTimerEnabled = sysfs?.safetyTimerEnabled,
            chargerOverVoltage = sysfs?.chargerOverVoltage,
            overload = sysfs?.overload,
            usbOverheat = sysfs?.usbOverheat,
            batteryProfile = sysfs?.batteryProfile,
            batteryIdResistanceOhm = sysfs?.batteryIdResistanceOhm,
            jeitaCoolDeciC = sysfs?.jeitaCoolDeciC,
            jeitaWarmDeciC = sysfs?.jeitaWarmDeciC,
            socReportingReady = sysfs?.socReportingReady,
            esrCount = sysfs?.esrCount,
            cycleCountBins = sysfs?.cycleCountBins,
            usbPresent = sysfs?.usbPresent,
            usbOnline = sysfs?.usbOnline,
            usbCurrentMaxUa = sysfs?.usbCurrentMaxUa,
            usbVoltageMaxMv = sysfs?.usbVoltageMaxMv,
            usbOtg = sysfs?.usbOtg,
            usbHealth = sysfs?.usbHealth,
            dcPresent = sysfs?.dcPresent,
            dcOnline = sysfs?.dcOnline,
            dcCurrentMaxUa = sysfs?.dcCurrentMaxUa,
            dcChargingEnabled = sysfs?.dcChargingEnabled,
            dcType = sysfs?.dcType,
            parallelPresent = sysfs?.parallelPresent,
            parallelChargingEnabled = sysfs?.parallelChargingEnabled,
            parallelStatus = sysfs?.parallelStatus,
            parallelCurrentMaxUa = sysfs?.parallelCurrentMaxUa,
            parallelChargeCurrentLimitUa = sysfs?.parallelChargeCurrentLimitUa,
            parallelVoltageMaxMv = sysfs?.parallelVoltageMaxMv,
            parallelInputCurrentLimited = sysfs?.parallelInputCurrentLimited,
            sysfsProvider = sysfs?.provider?.name,
            sysfsFallbackFields = fallbacks,
        )
    }

    private fun intProperty(id: Int): Long? = manager.getIntProperty(id)
        .takeUnless { it == Int.MIN_VALUE }
        ?.toLong()

    private fun longProperty(id: Int): Long? = manager.getLongProperty(id)
        .takeUnless { it == Long.MIN_VALUE }

    private fun Intent?.validExtra(name: String): Int? = this
        ?.takeIf { it.hasExtra(name) }
        ?.getIntExtra(name, Int.MIN_VALUE)
        ?.takeUnless { it == Int.MIN_VALUE }
}
