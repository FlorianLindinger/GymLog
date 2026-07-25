package com.gymlog.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeWindowChartTest {
    @Test
    fun oneWeekStartsAtLatestWeekAndPansToPreviousWeek() {
        val day = 86_400_000L
        val timeline = calculateChartTimeline(
            dataStart = 0L,
            dataEnd = 90L * day,
            window = ChartWindow(7L * day, key = "1W"),
        )

        assertEquals(83L * day, timeline.initialStart)
        assertEquals(76L * day, panChartViewport(timeline.initialStart, 300f, 300f, timeline))
    }

    @Test
    fun threeWeekWindowReallySpansThreeWeeks() {
        val day = 86_400_000L
        val timeline = calculateChartTimeline(
            dataStart = 0L,
            dataEnd = 90L * day,
            window = ChartWindow(21L * day, key = "3W"),
        )

        assertEquals(21L * day, timeline.viewportDuration)
        assertEquals(69L * day, timeline.initialStart)
    }

    @Test
    fun allUsesCompleteDataRangeAndCannotPan() {
        val timeline = calculateChartTimeline(
            dataStart = 1_000L,
            dataEnd = 9_000L,
            window = ChartWindow(null, key = "ALL"),
        )

        assertEquals(1_000L, timeline.initialStart)
        assertEquals(8_000L, timeline.viewportDuration)
        assertEquals(1_000L, panChartViewport(timeline.initialStart, 300f, 300f, timeline))
    }

    @Test
    fun customRangeStartsAtRequestedHistoricalEnd() {
        val timeline = calculateChartTimeline(
            dataStart = 0L,
            dataEnd = 10_000L,
            window = ChartWindow(durationMillis = 2_000L, initialEnd = 6_000L, key = "custom"),
        )

        assertEquals(4_000L, timeline.initialStart)
    }
}
