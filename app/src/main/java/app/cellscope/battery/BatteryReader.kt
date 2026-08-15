package app.cellscope.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import app.cellscope.data.BatteryReading

class BatteryReader(private val context: Context) {
    private val manager = context.getSystemService(BatteryManager::class.java)

    fun read(): BatteryReading {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return BatteryReading(
            wallTimeMs = System.currentTimeMillis(),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            levelPercent = if (level >= 0 && scale > 0) level * 100f / scale else null,
            chargeCounterUah = intProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentNowUa = intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            currentAverageUa = intProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
            energyCounterNwh = longProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER),
            voltageMv = intent.validExtra(BatteryManager.EXTRA_VOLTAGE),
            temperatureDeciC = intent.validExtra(BatteryManager.EXTRA_TEMPERATURE),
            status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                ?: BatteryManager.BATTERY_STATUS_UNKNOWN,
            plugSource = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
            health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                ?: BatteryManager.BATTERY_HEALTH_UNKNOWN,
            isPresent = intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) ?: false,
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
