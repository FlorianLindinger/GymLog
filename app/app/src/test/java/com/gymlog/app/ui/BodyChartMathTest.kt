package com.gymlog.app.ui

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyChartMathTest {
    @Test
    fun highLowCanShowAllTimeAndDefaultTwoMonthHistoryIndependently() {
        val day = 86_400_000L
        val points = listOf(TimeValue(0, 100.0), TimeValue(39 * day, 50.0), TimeValue(60 * day, 70.0), TimeValue(100 * day, 60.0))
        val settings = BodyChartSettings(showAllTimeHighLow = true, showHistoryHighLow = true)
        val references = bodyHighLowReferences(points, settings, 100 * day)
        assertEquals(2, references.size)
        assertEquals(100.0, references[0].high.value, 0.0)
        assertEquals(50.0, references[0].low.value, 0.0)
        assertEquals("2M history", references[1].label)
        assertEquals(70.0, references[1].high.value, 0.0)
        assertEquals(50.0, references[1].low.value, 0.0)
        assertEquals(emptyList<BodyHighLowReference>(), bodyHighLowReferences(points, BodyChartSettings(), 100 * day))
    }

    @Test
    fun historyHighLowUsesChosenEndAndCustomDatesAndSkipsEmptyWindows() {
        val day = 86_400_000L
        val points = listOf(TimeValue(0, 100.0), TimeValue(50 * day, 70.0), TimeValue(100 * day, 60.0))
        val history = BodyChartSettings(showHistoryHighLow = true)
        assertEquals(100.0, bodyHighLowReferences(points, history, 50 * day).single().high.value, 0.0)
        val custom = history.copy(highLowRange = com.gymlog.app.data.ChartRange.CUSTOM, highLowStart = 50 * day, highLowEnd = 100 * day)
        val reference = bodyHighLowReferences(points, custom, 200 * day).single()
        assertEquals(60.0, reference.low.value, 0.0)
        assertEquals(70.0, reference.high.value, 0.0)
        assertTrue(bodyHighLowReferences(points, history, 200 * day).isEmpty())
        assertTrue(bodyHighLowReferences(emptyList(), history, 100 * day).isEmpty())
    }

    @Test
    fun binsUseSelectedIntervalSkipEmptyBinsAndIncludeEnd() {
        val result = averageTimeBins(listOf(
            TimeValue(-1, 100.0), TimeValue(0, 2.0), TimeValue(9, 4.0),
            TimeValue(80, 8.0), TimeValue(100, 12.0), TimeValue(101, 100.0),
        ), VisibleTimeRange(0, 100), 10)
        assertEquals(listOf(3.0, 8.0, 12.0), result.map { it.value })
        assertEquals(listOf(4L, 80L, 100L), result.map { it.timestamp })
        assertEquals(emptyList<TimeValue>(), averageTimeBins(emptyList(), VisibleTimeRange(0, 100), 20))
    }

    @Test
    fun globalControlsResolveIndependentlyAndManualValuesSurvive() {
        val local = BodyChartSettings(showFit = false, averaging = false, bins = 7, target = 80.0,
            acceptance = 2.0, targetEnabled = true)
        val global = BodyChartSettings(showFit = true, averaging = true, bins = 20)
        val target = BodyChartSettings(target = 70.0, acceptance = 1.0, targetEnabled = false)
        val inherited = resolveBodySettings(local, global, target)
        assertTrue(inherited.showFit && inherited.averaging)
        assertEquals(20, inherited.bins)
        assertEquals(70.0, inherited.target!!, 0.0)
        assertEquals(false, inherited.targetEnabled)
        val manual = local.copy(globalFit = false, globalAveraging = false, globalTarget = false)
        assertEquals(manual, resolveBodySettings(manual, global, target))
    }

    @Test
    fun estimatesKeepTwelveMonthsThenYearsAndMonthsUntilTenYears() {
        assertEquals("12 months", formatBodyEta(365.0))
        assertEquals("1 year 1 month", formatBodyEta(396.0))
        assertEquals("2 years", formatBodyEta(731.0))
        assertEquals("9 years 11 months", formatBodyEta(3630.0))
        assertEquals("10 years", formatBodyEta(3653.0))
        assertEquals("1 day", formatBodyEta(0.1))
    }

    @Test
    fun datesUseEnglishMonthsEvenWithGermanLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            val timestamp = LocalDate.of(2026, 10, 25).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            assertEquals("25 Oct 26", formatChartDate(timestamp))
        } finally { Locale.setDefault(original) }
    }

    @Test
    fun calendarTicksRespectMonthBoundariesAndDaylightSaving() {
        val zone = ZoneId.of("Europe/Berlin")
        fun time(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
        val range = VisibleTimeRange(time("2026-01-15"), time("2026-12-20"))
        val ticks = calendarTimeTicks(range, zone)
        assertEquals(11, ticks.size)
        assertTrue(ticks.all { java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfMonth == 1 })
        val days = calendarTimeTicks(VisibleTimeRange(time("2026-10-23"), time("2026-10-27")), zone)
        assertEquals(5, days.size)
        assertTrue(days.zipWithNext().any { (a, b) -> b.timestamp - a.timestamp == 25L * 60 * 60 * 1000 })
    }
}
