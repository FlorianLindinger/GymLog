package com.gymlog.app.ui

import com.gymlog.app.data.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseTrendTest {
    @Test
    fun weightChartUsesPerformanceOnLeftAndUnlabelledRepetitions() {
        val axes = exerciseChartAxes(TrackingMode.WEIGHT)
        assertEquals(PlotAxis.LEFT, axes.performance)
        assertEquals(PlotAxis.RIGHT, axes.load)
        assertEquals(PlotAxis.INDEPENDENT, axes.repetitions)
        val points = listOf(TimeValue(0, 10.0), TimeValue(1000, 20.0))
        val raw = listOf(TimeSeries(points, androidx.compose.ui.graphics.Color.Red, axes.repetitions),
            TimeSeries(points, androidx.compose.ui.graphics.Color.Green, axes.load),
            TimeSeries(points, androidx.compose.ui.graphics.Color.Blue, axes.performance))
        val fits = exerciseChartTrendSeries(raw, ChartWindow(null, key = "all"),
            unfittedAxis = axes.repetitions, performanceAxis = axes.performance)
        assertEquals(listOf(PlotAxis.RIGHT, PlotAxis.LEFT), fits.map { it.axis })
    }

    @Test
    fun timedIntensityChartsUsePerformanceOnLeftAndIndependentMinutesWithoutAFit() {
        val points = listOf(TimeValue(0, 10.0), TimeValue(1000, 20.0))
        listOf(TrackingMode.STRENGTH, TrackingMode.WATTS).forEach { mode ->
            val axes = exerciseChartAxes(mode)
            assertEquals(PlotAxis.LEFT, axes.performance)
            assertEquals(PlotAxis.RIGHT, axes.repetitions) // Timed records store level/watts here.
            assertEquals(PlotAxis.INDEPENDENT, axes.load) // Timed records store minutes here.
            val raw = listOf(TimeSeries(points, androidx.compose.ui.graphics.Color.Red, axes.repetitions),
                TimeSeries(points, androidx.compose.ui.graphics.Color.Green, axes.load),
                TimeSeries(points, androidx.compose.ui.graphics.Color.Blue, axes.performance))
            val fits = exerciseChartTrendSeries(raw, ChartWindow(null, key = "all"),
                unfittedAxis = axes.load, performanceAxis = axes.performance)
            assertEquals(listOf(PlotAxis.RIGHT, PlotAxis.LEFT), fits.map { it.axis })
        }
    }

    @Test
    fun exerciseFitsExcludeRepsAndMinutesButRetainLoadAndPerformance() {
        val points = listOf(TimeValue(0, 1.0), TimeValue(1000, 2.0))
        val raw = listOf(TimeSeries(points, androidx.compose.ui.graphics.Color.Red, PlotAxis.LEFT),
            TimeSeries(points, androidx.compose.ui.graphics.Color.Green, PlotAxis.RIGHT),
            TimeSeries(points, androidx.compose.ui.graphics.Color.Blue, PlotAxis.INDEPENDENT))
        val fits = exerciseChartTrendSeries(raw, ChartWindow(null, key = "all"))
        assertEquals(listOf(PlotAxis.LEFT, PlotAxis.RIGHT), fits.map { it.axis })
        assertTrue(fits.all { it.dashed && !it.showPoints })
        assertTrue(exerciseChartTrendSeries(raw.takeLast(1), ChartWindow(null, key = "minutes")).isEmpty())
    }

    @Test
    fun historySeparatorColorsTrackActualRecordedLoadInEveryMode() {
        TrackingMode.entries.forEach { mode ->
            val machine = com.gymlog.app.data.Machine(1, "Exercise", "", "", "", com.gymlog.app.data.ChartRange.ALL,
                0, mode, 999.0, 0)
            val entries = listOf(com.gymlog.app.data.SetRecord(1, 1, 10, 10.0, 1000),
                com.gymlog.app.data.SetRecord(2, 1, 20, 20.0, 2000),
                com.gymlog.app.data.SetRecord(3, 1, 15, 15.0, 3000))
            val summary = com.gymlog.app.data.MachineSummary(machine, emptyList(), entries, emptyList(), 0)
            val text = sessionPatternText(summary)
            assertEquals(listOf(androidx.compose.ui.graphics.Color(0xFFEF5350), androidx.compose.ui.graphics.Color(0xFF66BB6A)),
                text.spanStyles.map { it.item.color })
            assertTrue(text.spanStyles.all { text.text.substring(it.start, it.end) == "|" })
            val sameMetric = if (mode == TrackingMode.STRENGTH || mode == TrackingMode.WATTS)
                entries.map { it.copy(reps = 10) } else entries.map { it.copy(weight = 10.0) }
            assertTrue(sessionPatternText(summary.copy(recentSets = sameMetric)).spanStyles.isEmpty())
            // Simultaneous edits use record ID, regardless of input list order.
            val tied = listOf(entries[0], entries[1], entries[2].copy(performedAt = 2000))
            assertEquals(sessionPatternText(summary.copy(recentSets = tied)),
                sessionPatternText(summary.copy(recentSets = tied.reversed())))
        }
    }

    @Test fun newSessionUsesLatestBatchInsteadOfStaleSavedWheelsOrOlderBatches() {
        val machine = com.gymlog.app.data.Machine(1, "Biceps", "", "", "", com.gymlog.app.data.ChartRange.ALL,
            0, TrackingMode.WEIGHT, 40.0, 0)
        val entries = listOf(com.gymlog.app.data.SetRecord(1, 1, 5, 40.0, 1000),
            com.gymlog.app.data.SetRecord(2, 1, 9, 40.0, 2000), com.gymlog.app.data.SetRecord(3, 1, 8, 40.0, 2001))
        val summary = com.gymlog.app.data.MachineSummary(machine, emptyList(), entries, emptyList(), 0, setWheelValues = listOf(5, 5, 0, 0, 0))
        assertEquals(listOf(9, 8), previousTrainingUnitReps(summary, emptyList()))
        assertEquals(listOf(9, 8, 0, 0, 0), initialWheelReps(summary, emptyList()))
        assertEquals(listOf(9, 0, 8, 0, 0), initialWheelReps(summary.copy(setWheelValues = listOf(9, 0, 8, 0, 0)), emptyList()))
    }

    @Test fun bodyEntryDateUsesLocalCalendarDateAndRejectsInvalidOrFutureDates() {
        val zone = java.time.ZoneId.of("Europe/Berlin")
        val now = java.time.Instant.parse("2026-11-06T12:30:00Z").toEpochMilli()
        val selected = manualBodyTimestamp("2026-10-24", now, zone)!!
        assertEquals("2026-10-24T13:30+02:00[Europe/Berlin]", java.time.Instant.ofEpochMilli(selected).atZone(zone).toString())
        assertEquals(now, manualBodyTimestamp("2026-11-06", now, zone))
        assertEquals(null, manualBodyTimestamp("2026-11-07", now, zone))
        assertEquals(null, manualBodyTimestamp("bad date", now, zone))
    }
    @Test
    fun overviewTimedHistoryHasColoredSeparatorsAndNoMinuteSuffix() {
        listOf(TrackingMode.STRENGTH, TrackingMode.WATTS, TrackingMode.DURATION).forEach { mode ->
            val machine = com.gymlog.app.data.Machine(1, "Exercise", "", "", "", com.gymlog.app.data.ChartRange.ALL,
                0, mode, 100.0, 0)
            val entries = listOf(com.gymlog.app.data.SetRecord(1, 1, 100, 20.0, 1000),
                com.gymlog.app.data.SetRecord(2, 1, 120, 10.0, 2000))
            val summary = com.gymlog.app.data.MachineSummary(machine, emptyList(), entries, emptyList(), 0)
            val history = sessionPatternText(summary)
            assertEquals("Last: 10 | 20", history.text)
            assertEquals(1, history.spanStyles.size)
        }
    }

    @Test
    fun currentWheelsRefreshAfterChangingARecentEntryButKeepMatchingEmptySlots() {
        val machine = com.gymlog.app.data.Machine(1, "Exercise", "", "", "", com.gymlog.app.data.ChartRange.ALL,
            0, TrackingMode.WEIGHT, 50.0, 0)
        val entries = listOf(com.gymlog.app.data.SetRecord(1, 1, 12, 40.0, 100), com.gymlog.app.data.SetRecord(2, 1, 8, 40.0, 101))
        val summary = com.gymlog.app.data.MachineSummary(machine, emptyList(), entries, emptyList(), 0,
            setWheelValues = listOf(10, 0, 8, 0, 0))
        assertEquals(listOf(12, 8, 0, 0, 0), initialWheelReps(summary, entries))
        val matching = summary.copy(setWheelValues = listOf(12, 0, 8, 0, 0))
        assertEquals(listOf(12, 0, 8, 0, 0), initialWheelReps(matching, entries))
    }

    @Test
    fun chartHelpExplainsTheSelectedTimedMetricInsteadOfWeightTraining() {
        val level = trainingMetricExplanation(TrackingMode.STRENGTH)
        assertEquals("Estimated 10-minute level", level.title)
        assertTrue(level.text.contains("6.25") && level.text.contains("same machine"))
        val watts = trainingMetricExplanation(TrackingMode.WATTS)
        assertEquals("Estimated 10-minute power", watts.title)
        assertTrue(watts.text.contains("104.25 W"))
        listOf(level, watts).forEach { help ->
            assertTrue(!help.text.contains("reps") && !help.text.contains("sets") && !help.text.contains("1RM"))
            assertTrue(help.text.contains("left axis") && help.text.contains("right axis"))
            assertTrue(help.text.contains("Minutes use an independent, unlabelled scale"))
        }
        assertEquals(TRAINING_METRIC_EXPLANATION, trainingMetricExplanation(TrackingMode.WEIGHT).text)
        assertEquals(TRAINING_METRIC_EXPLANATION, trainingMetricExplanation().text)
    }

    @Test
    fun legendUsesTwoSignificantDigitsAndMonthlyRateFromSelectedInterval() {
        val day = 86_400_000L
        val points = listOf(TimeValue(0, 100.0), TimeValue(10 * day, 20.0), TimeValue(20 * day, 19.3))
        val window = ChartWindow(10 * day, endMillis = 20 * day, key = "test")
        assertEquals("-2.1/mon.", formatMonthlyTrend(points, window))
        assertEquals("", formatMonthlyTrend(listOf(points.last()), window))
    }

    @Test
    fun powerAndRecordedMinutesHaveSeparateUnits() {
        assertEquals("120 W", formatMetric(120.0, TrackingMode.WATTS))
        assertEquals("120 W | 20 min", formatTrainingEntry(20.0, TrackingMode.WATTS, 120))
    }
}
