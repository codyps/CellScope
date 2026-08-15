package app.cellscope

import app.cellscope.data.BatterySample
import app.cellscope.data.ChartMetric
import app.cellscope.data.GapReason
import app.cellscope.data.TimelineGap
import app.cellscope.data.valueFor
import app.cellscope.battery.PowerSupplySnapshot
import app.cellscope.battery.SysfsProvider
import app.cellscope.battery.batteryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryTest {
    @Test
    fun pixelPowerSupplySnapshotMapsUnitsAndDetails() {
        val snapshot = PowerSupplySnapshot.parse(
            """
                @@battery
                POWER_SUPPLY_STATUS=Full
                POWER_SUPPLY_PRESENT=1
                POWER_SUPPLY_CAPACITY=100
                POWER_SUPPLY_HEALTH=Good
                POWER_SUPPLY_TECHNOLOGY=Li-ion
                POWER_SUPPLY_CHARGE_TYPE=Taper
                POWER_SUPPLY_CURRENT_NOW=-63323
                POWER_SUPPLY_TEMP=310
                POWER_SUPPLY_VOLTAGE_NOW=4403355
                POWER_SUPPLY_INPUT_CURRENT_MAX=3000000
                POWER_SUPPLY_INPUT_CURRENT_LIMITED=0
                POWER_SUPPLY_INPUT_CURRENT_SETTLED=1
                POWER_SUPPLY_CONSTANT_CHARGE_CURRENT_MAX=1600000
                POWER_SUPPLY_RESTRICTED_CHARGING=0
                POWER_SUPPLY_BATTERY_CHARGING_ENABLED=1
                POWER_SUPPLY_CHARGING_ENABLED=1
                POWER_SUPPLY_SAFETY_TIMER_ENABLED=1
                POWER_SUPPLY_OVER_VCHG=0
                POWER_SUPPLY_OVERLOAD=0
                POWER_SUPPLY_USB_OVERHEAT=0
                POWER_SUPPLY_CHARGE_COUNTER=3533665
                POWER_SUPPLY_CHARGE_FULL=3532000
                POWER_SUPPLY_VOLTAGE_MAX=4400
                POWER_SUPPLY_VOLTAGE_MAX_DESIGN=4400000
                POWER_SUPPLY_CHARGE_CONTROL_START_THRESHOLD=70
                POWER_SUPPLY_CHARGE_CONTROL_END_THRESHOLD=80
                POWER_SUPPLY_CYCLE_COUNT=2553
                @@bms
                POWER_SUPPLY_CHARGE_FULL_DESIGN=3532000
                POWER_SUPPLY_CAPACITY_RAW=9999
                POWER_SUPPLY_RESISTANCE_ID=22213
                POWER_SUPPLY_BATTERY_TYPE=3
                POWER_SUPPLY_TEMP_COOL=100
                POWER_SUPPLY_TEMP_WARM=470
                POWER_SUPPLY_SOC_REPORTING_READY=1
                POWER_SUPPLY_ESR_COUNT=7
                CELLSCOPE_CYCLE_COUNT_BINS=811 1091 1316 1502 1787 7573 4028 2317
                POWER_SUPPLY_VOLTAGE_OCV=4377721
                POWER_SUPPLY_RESISTANCE=146118
                @@usb
                POWER_SUPPLY_ONLINE=1
                POWER_SUPPLY_VOLTAGE_MAX=5100000
                POWER_SUPPLY_CURRENT_MAX=3000000
                POWER_SUPPLY_TYPE=USB_CDP
                POWER_SUPPLY_PRESENT=1
                POWER_SUPPLY_USB_OTG=0
                POWER_SUPPLY_HEALTH=Good
                @@dc
                POWER_SUPPLY_PRESENT=0
                POWER_SUPPLY_ONLINE=0
                POWER_SUPPLY_CHARGING_ENABLED=1
                POWER_SUPPLY_CURRENT_MAX=1500000
                POWER_SUPPLY_TYPE=Wipower
                @@usb-parallel
                POWER_SUPPLY_PRESENT=1
                POWER_SUPPLY_CHARGING_ENABLED=0
                POWER_SUPPLY_STATUS=Not charging
                POWER_SUPPLY_CURRENT_MAX=0
                POWER_SUPPLY_CONSTANT_CHARGE_CURRENT_MAX=1000000
                POWER_SUPPLY_VOLTAGE_MAX=4450
                POWER_SUPPLY_INPUT_CURRENT_LIMITED=0
            """.trimIndent(),
            SysfsProvider.SHIZUKU,
        ).batteryData()!!

        assertEquals(4_403, snapshot.voltageMv)
        assertEquals(4_377, snapshot.voltageOcvMv)
        assertEquals(-63_323L, snapshot.currentNowUa)
        assertEquals(3_532_000L, snapshot.chargeFullDesignUah)
        assertEquals(4_400, snapshot.chargeVoltageLimitMv)
        assertEquals(4_400, snapshot.chargeVoltageDesignLimitMv)
        assertEquals(70, snapshot.chargeStartThresholdPercent)
        assertEquals(80, snapshot.chargeEndThresholdPercent)
        assertEquals(146_118L, snapshot.resistanceMicroOhm)
        assertEquals("USB_CDP", snapshot.powerSupplyType)
        assertEquals(5_100, snapshot.inputVoltageLimitMv)
        assertEquals(9_999L, snapshot.fuelGaugeRawSoc)
        assertEquals(1_600_000L, snapshot.chargeCurrentLimitUa)
        assertEquals(false, snapshot.inputCurrentLimited)
        assertEquals(true, snapshot.aiclComplete)
        assertEquals("3", snapshot.batteryProfile)
        assertEquals(22_213L, snapshot.batteryIdResistanceOhm)
        assertEquals(100, snapshot.jeitaCoolDeciC)
        assertEquals(470, snapshot.jeitaWarmDeciC)
        assertEquals("811 1091 1316 1502 1787 7573 4028 2317", snapshot.cycleCountBins)
        assertEquals(true, snapshot.usbOnline)
        assertEquals(1_500_000L, snapshot.dcCurrentMaxUa)
        assertEquals(1_000_000L, snapshot.parallelChargeCurrentLimitUa)
        assertEquals(4_450, snapshot.parallelVoltageMaxMv)
    }

    @Test
    fun durationLabelsAreReadable() {
        assertEquals("0s", durationLabel(0))
        assertEquals("1m 05s", durationLabel(65_000))
        assertEquals("2h 03m", durationLabel(7_380_000))
        assertEquals("2d 03h", durationLabel(183_600_000))
    }

    @Test
    fun downsamplingPreservesShortSpikes() {
        val values = (0L until 1_000L).map { it to if (it == 501L) 100f else 1f }
        val sampled = downsampleTelemetry(values, 100)

        assertTrue(sampled.size <= 100)
        assertTrue(sampled.any { it.second == 100f })
        assertEquals(values.first(), sampled.first())
    }

    @Test
    fun missingIntervalBecomesAnInferredGap() {
        val samples = listOf(sample(0, 100f), sample(10_000, 99f), sample(100_000, 98f))
        val gaps = effectiveTimelineGaps(samples, emptyList(), 10_000, 100_000)

        assertEquals(1, gaps.size)
        assertEquals(20_000L, gaps.single().startedAtMs)
        assertEquals(100_000L, gaps.single().endedAtMs)
        assertEquals(GapReason.RECORDER_INTERRUPTED, gaps.single().reason)
    }

    @Test
    fun storedGapPreventsDuplicateInference() {
        val samples = listOf(sample(0, 100f), sample(100_000, 98f))
        val stored = TimelineGap(
            startedAtMs = 10_000,
            endedAtMs = 100_000,
            reason = GapReason.RECORDING_DISABLED,
        )

        assertEquals(listOf(stored), effectiveTimelineGaps(samples, listOf(stored), 10_000, 100_000))
    }

    @Test
    fun collapsedGapIsShorterButStillPresent() {
        val samples = listOf(sample(0, 100f), sample(1_000_000, 90f))
        val gap = TimelineGap(1, 10_000, 990_000, GapReason.RECORDER_INTERRUPTED)

        val expanded = buildTimelineLayout(samples, ChartMetric.LEVEL, listOf(gap), false, 10_000)
        val collapsed = buildTimelineLayout(samples, ChartMetric.LEVEL, listOf(gap), true, 10_000)

        assertTrue(collapsed.end - collapsed.start < expanded.end - expanded.start)
        assertTrue(collapsed.gaps.single().second > collapsed.gaps.single().first)
        assertEquals(2, collapsed.segments.size)
    }

    @Test
    fun extendedStoredMetricsHaveChartUnits() {
        val sample = sample(0, 100f).copy(
            chargeFullUah = 3_532_000,
            cycleCount = 2_553,
            voltageOcvMv = 4_377,
            resistanceMicroOhm = 146_118,
            chargeVoltageLimitMv = 4_400,
            chargeEndThresholdPercent = 80,
            chargeCurrentLimitUa = 1_600_000,
            usbCurrentMaxUa = 3_000_000,
            inputCurrentLimited = true,
            parallelVoltageMaxMv = 4_400,
        )

        assertEquals(3_532f, sample.valueFor(ChartMetric.FULL_CHARGE))
        assertEquals(2_553f, sample.valueFor(ChartMetric.CYCLE_COUNT))
        assertEquals(4.377f, sample.valueFor(ChartMetric.OCV))
        assertEquals(146.118f, sample.valueFor(ChartMetric.RESISTANCE))
        assertEquals(4.4f, sample.valueFor(ChartMetric.CHARGE_VOLTAGE_LIMIT))
        assertEquals(80f, sample.valueFor(ChartMetric.CHARGE_END_THRESHOLD))
        assertEquals(1_600f, sample.valueFor(ChartMetric.CHARGE_CURRENT_LIMIT))
        assertEquals(3_000f, sample.valueFor(ChartMetric.USB_CURRENT_LIMIT))
        assertEquals(1f, sample.valueFor(ChartMetric.INPUT_CURRENT_LIMITED))
        assertEquals(4.4f, sample.valueFor(ChartMetric.PARALLEL_VOLTAGE_LIMIT))
    }

    @Test
    fun categoricalMetricsPlotEachObservedState() {
        val samples = listOf(
            sample(0, 100f).copy(chargeType = "Trickle"),
            sample(10_000, 99f).copy(chargeType = "Fast"),
            sample(20_000, 98f).copy(chargeType = "Trickle"),
        )

        val layout = buildTimelineLayout(samples, ChartMetric.CHARGE_TYPE, emptyList(), false, 10_000)

        assertEquals(listOf("Trickle", "Fast"), layout.categories)
        assertEquals(listOf(0f, 1f, 0f), layout.segments.flatten().map { it.second })
    }

    @Test
    fun metricsWithLateValuesStillUseTheWholeTimeline() {
        val samples = listOf(
            sample(0, 100f),
            sample(10_000, 99f).copy(voltageMv = 4_000),
        )

        val layout = buildTimelineLayout(samples, ChartMetric.VOLTAGE, emptyList(), false, 10_000)

        assertEquals(0, layout.start)
        assertEquals(10_000, layout.end)
    }

    private fun sample(time: Long, level: Float) = BatterySample(
        wallTimeMs = time,
        elapsedRealtimeMs = time,
        levelPercent = level,
        chargeCounterUah = null,
        currentNowUa = null,
        currentAverageUa = null,
        energyCounterNwh = null,
        voltageMv = null,
        temperatureDeciC = null,
        status = 0,
        plugSource = 0,
        health = 0,
        isPresent = true,
    )
}
