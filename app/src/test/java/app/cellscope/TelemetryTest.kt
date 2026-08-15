package app.cellscope

import app.cellscope.data.BatterySample
import app.cellscope.data.ChartMetric
import app.cellscope.data.GapReason
import app.cellscope.data.TimelineGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryTest {
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
