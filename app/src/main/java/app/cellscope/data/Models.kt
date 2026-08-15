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
    )
}

enum class ChartMetric(val title: String, val unit: String) {
    LEVEL("Battery level", "%"),
    VOLTAGE("Voltage", "V"),
    CURRENT("Current", "mA"),
    CHARGE("Remaining charge", "mAh"),
    TEMPERATURE("Temperature", "°C"),
    POWER("Estimated power", "W"),
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
    ChartMetric.CHARGE -> chargeCounterUah?.div(1_000f)
    ChartMetric.TEMPERATURE -> temperatureDeciC?.div(10f)
    ChartMetric.POWER -> if (voltageMv != null && currentNowUa != null) {
        voltageMv * currentNowUa / 1_000_000_000f
    } else null
}
