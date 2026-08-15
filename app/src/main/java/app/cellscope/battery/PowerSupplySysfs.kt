package app.cellscope.battery

import android.os.BatteryManager
import java.io.File
import java.util.Locale

enum class SysfsProvider(val label: String) {
    DIRECT("Direct sysfs"),
    SHIZUKU("Shizuku / Sui"),
    ROOT("Root"),
}

data class PowerSupplySnapshot(
    val supplies: Map<String, Map<String, String>>,
    val provider: SysfsProvider,
) {
    companion object {
        fun parse(text: String, provider: SysfsProvider): PowerSupplySnapshot {
            val supplies = linkedMapOf<String, MutableMap<String, String>>()
            var current: MutableMap<String, String>? = null
            text.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith(SECTION_PREFIX)) {
                    val name = line.removePrefix(SECTION_PREFIX)
                    current = if (name.matches(SUPPLY_NAME)) {
                        supplies.getOrPut(name) { linkedMapOf() }
                    } else null
                } else {
                    val separator = line.indexOf('=')
                    if (separator > 0 && current != null) {
                        current!![line.substring(0, separator)] = line.substring(separator + 1)
                    }
                }
            }
            return PowerSupplySnapshot(supplies, provider)
        }

        const val SECTION_PREFIX = "@@"
        private val SUPPLY_NAME = Regex("[A-Za-z0-9_.-]+")
    }
}

data class SysfsBatteryData(
    val provider: SysfsProvider,
    val levelPercent: Float?,
    val chargeCounterUah: Long?,
    val currentNowUa: Long?,
    val voltageMv: Int?,
    val temperatureDeciC: Int?,
    val status: Int?,
    val plugSource: Int?,
    val health: Int?,
    val isPresent: Boolean?,
    val chargeFullUah: Long?,
    val chargeFullDesignUah: Long?,
    val chargeVoltageLimitMv: Int?,
    val chargeVoltageDesignLimitMv: Int?,
    val chargeStartThresholdPercent: Int?,
    val chargeEndThresholdPercent: Int?,
    val cycleCount: Int?,
    val voltageOcvMv: Int?,
    val resistanceMicroOhm: Long?,
    val technology: String?,
    val chargeType: String?,
    val powerSupplyType: String?,
    val inputCurrentLimitUa: Long?,
    val inputVoltageLimitMv: Int?,
    val fuelGaugeRawSoc: Long?,
    val chargeCurrentLimitUa: Long?,
    val inputCurrentLimited: Boolean?,
    val aiclComplete: Boolean?,
    val restrictedCharging: Boolean?,
    val batteryChargingEnabled: Boolean?,
    val chargingEnabled: Boolean?,
    val safetyTimerEnabled: Boolean?,
    val chargerOverVoltage: Boolean?,
    val overload: Boolean?,
    val usbOverheat: Boolean?,
    val batteryProfile: String?,
    val batteryIdResistanceOhm: Long?,
    val jeitaCoolDeciC: Int?,
    val jeitaWarmDeciC: Int?,
    val socReportingReady: Boolean?,
    val esrCount: Int?,
    val cycleCountBins: String?,
    val usbPresent: Boolean?,
    val usbOnline: Boolean?,
    val usbCurrentMaxUa: Long?,
    val usbVoltageMaxMv: Int?,
    val usbOtg: Boolean?,
    val usbHealth: String?,
    val dcPresent: Boolean?,
    val dcOnline: Boolean?,
    val dcCurrentMaxUa: Long?,
    val dcChargingEnabled: Boolean?,
    val dcType: String?,
    val parallelPresent: Boolean?,
    val parallelChargingEnabled: Boolean?,
    val parallelStatus: String?,
    val parallelCurrentMaxUa: Long?,
    val parallelChargeCurrentLimitUa: Long?,
    val parallelVoltageMaxMv: Int?,
    val parallelInputCurrentLimited: Boolean?,
)

internal fun PowerSupplySnapshot.batteryData(): SysfsBatteryData? {
    val battery = supplies["battery"]
        ?: supplies.entries.firstOrNull { it.value["POWER_SUPPLY_TYPE"].equals("Battery", true) }?.value
        ?: return null
    val bms = supplies["bms"].orEmpty()
    val usb = supplies["usb"]
        ?: supplies.entries.firstOrNull { it.value["POWER_SUPPLY_TYPE"]?.startsWith("USB", true) == true }?.value
    val dc = supplies["dc"]
    val parallel = supplies["usb-parallel"]

    fun value(key: String): String? = battery[key] ?: bms[key]
    fun long(key: String): Long? = value(key)?.toLongOrNull()
    fun online(supply: Map<String, String>?): Boolean? = supply
        ?.get("POWER_SUPPLY_ONLINE")
        ?.let(::booleanValue)
    fun bool(supply: Map<String, String>?, key: String): Boolean? = supply?.get(key)?.let(::booleanValue)

    val plugSource = when {
        online(usb) == true -> BatteryManager.BATTERY_PLUGGED_USB
        online(dc) == true -> BatteryManager.BATTERY_PLUGGED_WIRELESS
        online(usb) == false && online(dc) == false -> 0
        else -> null
    }

    return SysfsBatteryData(
        provider = provider,
        levelPercent = value("POWER_SUPPLY_CAPACITY")?.toFloatOrNull(),
        chargeCounterUah = long("POWER_SUPPLY_CHARGE_COUNTER"),
        currentNowUa = long("POWER_SUPPLY_CURRENT_NOW"),
        voltageMv = long("POWER_SUPPLY_VOLTAGE_NOW")?.microToMilli(),
        temperatureDeciC = long("POWER_SUPPLY_TEMP")?.toIntExact(),
        status = value("POWER_SUPPLY_STATUS")?.let(::statusValue),
        plugSource = plugSource,
        health = value("POWER_SUPPLY_HEALTH")?.let(::healthValue),
        isPresent = value("POWER_SUPPLY_PRESENT")?.let(::booleanValue),
        chargeFullUah = long("POWER_SUPPLY_CHARGE_FULL"),
        chargeFullDesignUah = long("POWER_SUPPLY_CHARGE_FULL_DESIGN"),
        chargeVoltageLimitMv = (
            long("POWER_SUPPLY_CONSTANT_CHARGE_VOLTAGE")
                ?: long("POWER_SUPPLY_VOLTAGE_MAX")
                ?: long("POWER_SUPPLY_CONSTANT_CHARGE_VOLTAGE_MAX")
            )?.voltageToMilli(),
        chargeVoltageDesignLimitMv = long("POWER_SUPPLY_VOLTAGE_MAX_DESIGN")?.voltageToMilli(),
        chargeStartThresholdPercent = long("POWER_SUPPLY_CHARGE_CONTROL_START_THRESHOLD")?.percent(),
        chargeEndThresholdPercent = (
            long("POWER_SUPPLY_CHARGE_CONTROL_END_THRESHOLD")
                ?: long("POWER_SUPPLY_FULL_LEVEL")
            )?.percent(),
        cycleCount = long("POWER_SUPPLY_CYCLE_COUNT")?.toIntExact(),
        voltageOcvMv = long("POWER_SUPPLY_VOLTAGE_OCV")?.microToMilli(),
        resistanceMicroOhm = long("POWER_SUPPLY_RESISTANCE"),
        technology = value("POWER_SUPPLY_TECHNOLOGY"),
        chargeType = value("POWER_SUPPLY_CHARGE_TYPE"),
        powerSupplyType = usb?.get("POWER_SUPPLY_TYPE"),
        inputCurrentLimitUa = battery["POWER_SUPPLY_INPUT_CURRENT_MAX"]?.toLongOrNull()
            ?: usb?.get("POWER_SUPPLY_CURRENT_MAX")?.toLongOrNull(),
        inputVoltageLimitMv = usb?.get("POWER_SUPPLY_VOLTAGE_MAX")?.toLongOrNull()?.microToMilli(),
        fuelGaugeRawSoc = bms["POWER_SUPPLY_CAPACITY_RAW"]?.toLongOrNull(),
        chargeCurrentLimitUa = battery["POWER_SUPPLY_CONSTANT_CHARGE_CURRENT_MAX"]?.toLongOrNull(),
        inputCurrentLimited = bool(battery, "POWER_SUPPLY_INPUT_CURRENT_LIMITED"),
        aiclComplete = bool(battery, "POWER_SUPPLY_INPUT_CURRENT_SETTLED"),
        restrictedCharging = bool(battery, "POWER_SUPPLY_RESTRICTED_CHARGING"),
        batteryChargingEnabled = bool(battery, "POWER_SUPPLY_BATTERY_CHARGING_ENABLED"),
        chargingEnabled = bool(battery, "POWER_SUPPLY_CHARGING_ENABLED"),
        safetyTimerEnabled = bool(battery, "POWER_SUPPLY_SAFETY_TIMER_ENABLED"),
        chargerOverVoltage = bool(battery, "POWER_SUPPLY_OVER_VCHG"),
        overload = bool(battery, "POWER_SUPPLY_OVERLOAD"),
        usbOverheat = bool(battery, "POWER_SUPPLY_USB_OVERHEAT"),
        batteryProfile = bms["POWER_SUPPLY_BATTERY_TYPE"],
        batteryIdResistanceOhm = bms["POWER_SUPPLY_RESISTANCE_ID"]?.toLongOrNull(),
        jeitaCoolDeciC = bms["POWER_SUPPLY_TEMP_COOL"]?.toLongOrNull()?.toIntExact(),
        jeitaWarmDeciC = bms["POWER_SUPPLY_TEMP_WARM"]?.toLongOrNull()?.toIntExact(),
        socReportingReady = bool(bms, "POWER_SUPPLY_SOC_REPORTING_READY"),
        esrCount = bms["POWER_SUPPLY_ESR_COUNT"]?.toLongOrNull()?.toIntExact(),
        cycleCountBins = bms["CELLSCOPE_CYCLE_COUNT_BINS"]?.cycleBins(),
        usbPresent = bool(usb, "POWER_SUPPLY_PRESENT"),
        usbOnline = online(usb),
        usbCurrentMaxUa = usb?.get("POWER_SUPPLY_CURRENT_MAX")?.toLongOrNull(),
        usbVoltageMaxMv = usb?.get("POWER_SUPPLY_VOLTAGE_MAX")?.toLongOrNull()?.voltageToMilli(),
        usbOtg = bool(usb, "POWER_SUPPLY_USB_OTG"),
        usbHealth = usb?.get("POWER_SUPPLY_HEALTH"),
        dcPresent = bool(dc, "POWER_SUPPLY_PRESENT"),
        dcOnline = online(dc),
        dcCurrentMaxUa = dc?.get("POWER_SUPPLY_CURRENT_MAX")?.toLongOrNull(),
        dcChargingEnabled = bool(dc, "POWER_SUPPLY_CHARGING_ENABLED"),
        dcType = dc?.get("POWER_SUPPLY_TYPE"),
        parallelPresent = bool(parallel, "POWER_SUPPLY_PRESENT"),
        parallelChargingEnabled = bool(parallel, "POWER_SUPPLY_CHARGING_ENABLED"),
        parallelStatus = parallel?.get("POWER_SUPPLY_STATUS"),
        parallelCurrentMaxUa = parallel?.get("POWER_SUPPLY_CURRENT_MAX")?.toLongOrNull(),
        parallelChargeCurrentLimitUa = parallel
            ?.get("POWER_SUPPLY_CONSTANT_CHARGE_CURRENT_MAX")
            ?.toLongOrNull(),
        parallelVoltageMaxMv = parallel?.get("POWER_SUPPLY_VOLTAGE_MAX")?.toLongOrNull()?.voltageToMilli(),
        parallelInputCurrentLimited = bool(parallel, "POWER_SUPPLY_INPUT_CURRENT_LIMITED"),
    )
}

object PowerSupplySnapshotIo {
    private const val ROOT = "/sys/class/power_supply"
    private const val MAX_SNAPSHOT_CHARS = 128 * 1024
    private val OPTIONAL_ATTRIBUTES = listOf(
        "constant_charge_voltage",
        "constant_charge_voltage_max",
        "voltage_max",
        "voltage_max_design",
        "charge_full",
        "charge_full_design",
        "charge_control_limit",
        "charge_control_limit_max",
        "charge_control_start_threshold",
        "charge_control_end_threshold",
        "full_level",
        "over_vchg",
        "overload",
        "usb_overheat",
    )

    fun readDirect(provider: SysfsProvider = SysfsProvider.DIRECT): PowerSupplySnapshot? {
        val root = File(ROOT)
        val output = StringBuilder()
        root.listFiles().orEmpty().sortedBy(File::getName).forEach { supply ->
            val name = supply.name
            if (!name.matches(Regex("[A-Za-z0-9_.-]+"))) return@forEach
            val uevent = File(supply, "uevent")
            val text = runCatching { uevent.readText() }.getOrNull() ?: return@forEach
            if (output.length + text.length > MAX_SNAPSHOT_CHARS) return@forEach
            output.append(PowerSupplySnapshot.SECTION_PREFIX).appendLine(name)
            output.appendLine(text.trim())
            OPTIONAL_ATTRIBUTES.forEach { attribute ->
                val value = runCatching { File(supply, attribute).readText().trim() }.getOrNull()
                    ?.takeIf { it.isNotEmpty() && '\n' !in it && '\r' !in it }
                    ?: return@forEach
                val line = "POWER_SUPPLY_${attribute.uppercase(Locale.US)}=$value\n"
                if (output.length + line.length <= MAX_SNAPSHOT_CHARS) output.append(line)
            }
            if (name == "bms") {
                appendCycleCountBins(output, File(supply, "device/cycle_counts_bins"))
            }
        }
        return output.takeIf { it.isNotEmpty() }
            ?.toString()
            ?.let { PowerSupplySnapshot.parse(it, provider) }
    }

    fun shellScript(): String = """
        for d in /sys/class/power_supply/*; do
          [ -r "${'$'}d/uevent" ] || continue
          n=${'$'}{d##*/}
          echo "@@${'$'}n"
          cat "${'$'}d/uevent"
          for a in ${OPTIONAL_ATTRIBUTES.joinToString(" ")}; do
            [ -r "${'$'}d/${'$'}a" ] || continue
            k=${'$'}(echo "${'$'}a" | tr '[:lower:]' '[:upper:]')
            v=${'$'}(cat "${'$'}d/${'$'}a" 2>/dev/null) || continue
            case "${'$'}v" in *'\n'*|*'\r'*) continue;; esac
            echo "POWER_SUPPLY_${'$'}k=${'$'}v"
          done
          if [ "${'$'}n" = bms ] && [ -r "${'$'}d/device/cycle_counts_bins" ]; then
            v=${'$'}(cat "${'$'}d/device/cycle_counts_bins" 2>/dev/null) || continue
            echo "CELLSCOPE_CYCLE_COUNT_BINS=${'$'}v"
          fi
        done
    """.trimIndent()

    private fun appendCycleCountBins(output: StringBuilder, file: File) {
        val value = runCatching { file.readText().trim().cycleBins() }.getOrNull() ?: return
        val line = "CELLSCOPE_CYCLE_COUNT_BINS=$value\n"
        if (output.length + line.length <= MAX_SNAPSHOT_CHARS) output.append(line)
    }
}

private fun booleanValue(value: String): Boolean? = when (value.trim().lowercase(Locale.US)) {
    "1", "y", "yes", "true" -> true
    "0", "n", "no", "false" -> false
    else -> null
}

private fun statusValue(value: String): Int? = when (value.trim().lowercase(Locale.US)) {
    "charging" -> BatteryManager.BATTERY_STATUS_CHARGING
    "discharging" -> BatteryManager.BATTERY_STATUS_DISCHARGING
    "not charging" -> BatteryManager.BATTERY_STATUS_NOT_CHARGING
    "full" -> BatteryManager.BATTERY_STATUS_FULL
    "unknown" -> BatteryManager.BATTERY_STATUS_UNKNOWN
    else -> null
}

private fun healthValue(value: String): Int? = when (value.trim().lowercase(Locale.US)) {
    "good" -> BatteryManager.BATTERY_HEALTH_GOOD
    "overheat" -> BatteryManager.BATTERY_HEALTH_OVERHEAT
    "dead" -> BatteryManager.BATTERY_HEALTH_DEAD
    "over voltage" -> BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE
    "unspecified failure" -> BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE
    "cold" -> BatteryManager.BATTERY_HEALTH_COLD
    "unknown" -> BatteryManager.BATTERY_HEALTH_UNKNOWN
    else -> null
}

private fun Long.microToMilli(): Int? = (this / 1_000L).toIntExact()
private fun Long.voltageToMilli(): Int? = (if (this >= 100_000L) this / 1_000L else this).toIntExact()
private fun Long.percent(): Int? = takeIf { it in 0L..100L }?.toInt()
private fun Long.toIntExact(): Int? = takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
private fun String.cycleBins(): String? {
    val bins = trim().split(Regex("\\s+")).mapNotNull(String::toLongOrNull)
    return bins.takeIf { it.size == 8 && it.all { value -> value >= 0 } }?.joinToString(" ")
}
