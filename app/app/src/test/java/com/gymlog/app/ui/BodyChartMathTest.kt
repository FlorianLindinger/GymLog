package com.gymlog.app.ui

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyChartMathTest {
    @Test
    fun historyAndAllTimeMeansUseOriginalMeasurementsAndHaveIndependentDashedLines() {
        val day = 86_400_000L
        val points = listOf(TimeValue(0, 100.0), TimeValue(50 * day, 10.0), TimeValue(60 * day, 20.0), TimeValue(100 * day, 60.0),
            TimeValue(100 * day, Double.NaN))
        val references = bodyHighLowReferences(points,
            BodyChartSettings(showAllTimeMean = true, showHistoryMean = true), 100 * day)
        assertEquals(2, references.size)
        assertEquals(47.5, references[0].mean!!, 0.0)
        assertEquals(30.0, references[1].mean!!, 0.0)
        references.forEach {
            assertEquals(listOf(BodyReferenceLine(it.mean!!, dashed = true)), it.visibleLines)
            assertEquals(false, it.showHigh)
            assertEquals(false, it.showLow)
        }
        val combined = bodyHighLowReferences(points,
            BodyChartSettings(showHistoryLow = true, showHistoryMean = true, showHistoryHigh = true), 100 * day).single()
        assertEquals(listOf(10.0, 30.0, 60.0), combined.visibleValues)
        assertEquals(listOf(false, true, false), combined.visibleLines.map { it.dashed })
    }

    @Test
    fun historyCanFollowExactPlotRangeInsteadOfSavedSeparateChoice() {
        val points = listOf(TimeValue(0, 100.0), TimeValue(10, 20.0), TimeValue(20, 40.0), TimeValue(21, 200.0))
        val settings = BodyChartSettings(showHistoryLow = true, showHistoryMean = true, showHistoryHigh = true,
            highLowFollowPlot = true, highLowRange = com.gymlog.app.data.ChartRange.CUSTOM, highLowStart = 0, highLowEnd = 21)
        val reference = bodyHighLowReferences(points, settings, 100, VisibleTimeRange(10, 20), "Custom history").single()
        assertEquals("Custom history", reference.label)
        assertEquals(listOf(20.0, 30.0, 40.0), reference.visibleValues)
        val all = bodyHighLowReferences(points, settings, 100, VisibleTimeRange(0, 21), "All history").single()
        assertEquals(90.0, all.mean!!, 0.0)
        val separate = bodyHighLowReferences(points, settings.copy(highLowFollowPlot = false), 100, VisibleTimeRange(10, 20)).single()
        assertEquals(90.0, separate.mean!!, 0.0)
        assertTrue(bodyHighLowReferences(points, settings, 100, VisibleTimeRange(30, 40)).isEmpty())
    }

    @Test
    fun meansAndFollowPlotInheritGloballyWithoutLosingManualReferenceChoices() {
        val local = BodyChartSettings(highLowRange = com.gymlog.app.data.ChartRange.THREE_MONTHS)
        val global = BodyChartSettings(showAllTimeMean = true, showHistoryMean = true, highLowFollowPlot = true)
        val inherited = resolveBodySettings(local, global)
        assertTrue(inherited.showAllTimeMean && inherited.showHistoryMean && inherited.highLowFollowPlot)
        val manual = local.copy(globalHighLow = false)
        assertEquals(manual, resolveBodySettings(manual, global))
        assertEquals(com.gymlog.app.data.ChartRange.THREE_MONTHS, manual.highLowRange)
    }

    @Test
    fun highLowCanShowAllTimeAndDefaultTwoMonthHistoryIndependently() {
        val day = 86_400_000L
        val points = listOf(TimeValue(0, 100.0), TimeValue(39 * day, 50.0), TimeValue(60 * day, 70.0), TimeValue(100 * day, 60.0))
        val settings = BodyChartSettings(showAllTimeHigh = true, showAllTimeLow = true, showHistoryHigh = true, showHistoryLow = true)
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
    fun highAndLowEachIndependentlyControlReferenceValuesIncludingCustomHistory() {
        val points = listOf(TimeValue(0, 90.0), TimeValue(10, 20.0), TimeValue(20, 30.0))
        val base = BodyChartSettings(highLowRange = com.gymlog.app.data.ChartRange.CUSTOM, highLowStart = 10, highLowEnd = 20)
        listOf(base.copy(showAllTimeHigh = true) to listOf(90.0),
            base.copy(showAllTimeLow = true) to listOf(20.0),
            base.copy(showHistoryHigh = true) to listOf(30.0),
            base.copy(showHistoryLow = true) to listOf(20.0)).forEach { (settings, expected) ->
            assertEquals(expected, bodyHighLowReferences(points, settings, 100).single().visibleValues)
        }
        val inherited = resolveBodySettings(base.copy(showAllTimeHigh = true), base.copy(showAllTimeLow = true, showHistoryHigh = true))
        assertEquals(false, inherited.showAllTimeHigh)
        assertTrue(inherited.showAllTimeLow && inherited.showHistoryHigh)
        assertEquals(false, inherited.showHistoryLow)
    }

    @Test
    fun historyHighLowUsesChosenEndAndCustomDatesAndSkipsEmptyWindows() {
        val day = 86_400_000L
        val points = listOf(TimeValue(0, 100.0), TimeValue(50 * day, 70.0), TimeValue(100 * day, 60.0))
        val history = BodyChartSettings(showHistoryHigh = true, showHistoryLow = true)
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
        val global = BodyChartSettings(showFit = true, averaging = true, bins = 20, target = 70.0, acceptance = 1.0, targetEnabled = false)
        val inherited = resolveBodySettings(local, global)
        assertTrue(inherited.showFit && inherited.averaging)
        assertEquals(20, inherited.bins)
        assertEquals(80.0, inherited.target!!, 0.0)
        assertEquals(2.0, inherited.acceptance!!, 0.0)
        assertEquals(false, inherited.targetEnabled)
        assertTrue(resolveBodySettings(local.copy(targetEnabled = false), global.copy(targetEnabled = true)).targetEnabled)
        val manual = local.copy(globalFit = false, globalAveraging = false, globalTarget = false)
        assertEquals(manual, resolveBodySettings(manual, global))
    }

    @Test
    fun summaryStatisticsCanEachBeShownAloneOrAllHidden() {
        val values = listOf(10.0, 20.0).mapIndexed { index, value -> BodyMeasurement(BodyMetric.FAT_PERCENTAGE, value, index.toLong(), "test") }
        val hidden = BodyChartSettings(showIntervalCount = false, showTotalCount = false, showMean = false, showRange = false)
        assertEquals("", bodyPlotSummary(values, 8, hidden, "%"))
        assertEquals("2 in interval", bodyPlotSummary(values, 8, hidden.copy(showIntervalCount = true), "%"))
        assertEquals("8 total", bodyPlotSummary(values, 8, hidden.copy(showTotalCount = true), "%"))
        assertEquals("Mean 15 %", bodyPlotSummary(values, 8, hidden.copy(showMean = true), "%"))
        assertEquals("10\u201320 %", bodyPlotSummary(values, 8, hidden.copy(showRange = true), "%"))
        assertEquals("0 in interval \u00b7 8 total", bodyPlotSummary(emptyList(), 8, BodyChartSettings(), "%"))
        assertEquals("", bodyPlotSummary(emptyList(), 8, hidden.copy(showMean = true, showRange = true), "%"))
    }

    @Test
    fun summaryAndHighLowGlobalDefaultsKeepLocalOverridesAndAxisLimits() {
        val local = BodyChartSettings(showIntervalCount = true, showTotalCount = false, showMean = true, showRange = false,
            showAllTimeHigh = false, showAllTimeLow = false, showHistoryHigh = false, showHistoryLow = false, yMin = 10.0, yMax = 20.0)
        val global = BodyChartSettings(showIntervalCount = false, showTotalCount = true, showMean = false, showRange = true,
            showAllTimeHigh = true, showAllTimeLow = true, showHistoryHigh = true, showHistoryLow = true, highLowRange = com.gymlog.app.data.ChartRange.CUSTOM,
            highLowStart = 10L, highLowEnd = 50L)
        val effective = resolveBodySettings(local, global)
        assertEquals(false, effective.showIntervalCount)
        assertTrue(effective.showTotalCount && effective.showRange)
        assertEquals(false, effective.showMean)
        assertTrue(effective.showAllTimeHigh && effective.showAllTimeLow && effective.showHistoryHigh && effective.showHistoryLow)
        assertEquals(global.highLowRange, effective.highLowRange)
        assertEquals(global.highLowStart, effective.highLowStart)
        assertEquals(global.highLowEnd, effective.highLowEnd)
        assertEquals(local.yMin, effective.yMin)
        assertEquals(local.yMax, effective.yMax)
        val manual = local.copy(globalSummary = false, globalHighLow = false)
        assertEquals(manual, resolveBodySettings(manual, global))
    }

    @Test
    fun estimatesIncludeRemainingWeeksUntilOverOneYear() {
        assertEquals("1 mon. 2 weeks", formatBodyEta(45.0))
        assertEquals("2 mon. 1 week", formatBodyEta(68.0))
        assertEquals("2 weeks", formatBodyEta(16.0))
        assertEquals("12 mon.", formatBodyEta(365.0))
        assertEquals("1 year", formatBodyEta(366.0))
        assertEquals("1 year 1 month", formatBodyEta(396.0))
        assertEquals("2 years", formatBodyEta(731.0))
        assertEquals("9 years 11 months", formatBodyEta(3630.0))
        assertEquals("10 years", formatBodyEta(3653.0))
        assertEquals("1 day", formatBodyEta(0.1))
        assertEquals("\u2014", formatBodyEta(Double.POSITIVE_INFINITY))
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
