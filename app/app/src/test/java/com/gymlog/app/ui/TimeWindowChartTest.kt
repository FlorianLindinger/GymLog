package com.gymlog.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWindowChartTest {
    @Test
    fun negativeTrendIsPreservedForClippingButCannotLowerAxisBelowZero() {
        val day = 86_400_000L
        val values = listOf(TimeValue(0L, 2.0), TimeValue(day, 1.0))
        val trend = trendTimeSeries(
            points = values,
            color = Color.Black,
            axis = PlotAxis.LEFT,
            window = ChartWindow(4L * day, endMillis = 4L * day, key = "custom"),
        )
        val range = chartAxisRange((values + trend.points).map(TimeValue::value))

        assertEquals(2.0, trend.points.first().value, 0.0001)
        assertEquals(-2.0, trend.points.last().value, 0.0001)
        assertEquals(0.0, range.min, 0.0)
        assertTrue(range.ticks.all { it >= 0.0 })
    }

    @Test
    fun threeWeekWindowUsesExactlyThreeWeeks() {
        val day = 86_400_000L
        val range = calculateVisibleTimeRange(
            dataStart = 0L,
            dataEnd = 90L * day,
            window = ChartWindow(21L * day, key = "3W"),
        )

        assertEquals(69L * day, range.start)
        assertEquals(90L * day, range.end)
    }

    @Test
    fun allUsesCompleteDataRange() {
        val range = calculateVisibleTimeRange(
            dataStart = 1_000L,
            dataEnd = 9_000L,
            window = ChartWindow(null, key = "ALL"),
        )

        assertEquals(1_000L, range.start)
        assertEquals(9_000L, range.end)
    }

    @Test
    fun customUsesExplicitInterval() {
        val range = calculateVisibleTimeRange(
            dataStart = 0L,
            dataEnd = 10_000L,
            window = ChartWindow(
                durationMillis = 2_000L,
                startMillis = 4_000L,
                endMillis = 6_000L,
                key = "custom",
            ),
        )

        assertEquals(4_000L, range.start)
        assertEquals(6_000L, range.end)
    }

    @Test
    fun trendUsesOnlySelectedIntervalAndReachesBothEdges() {
        val day = 86_400_000L
        val trend = trendTimeSeries(
            points = listOf(
                TimeValue(0L, 100.0),
                TimeValue(10L * day, 0.0),
                TimeValue(11L * day, 1.0),
            ),
            color = Color.Black,
            axis = PlotAxis.LEFT,
            window = ChartWindow(day, endMillis = 11L * day, key = "1W"),
        )

        assertEquals(10L * day, trend.points.first().timestamp)
        assertEquals(11L * day, trend.points.last().timestamp)
        assertEquals(0.0, trend.points.first().value, 0.0001)
        assertEquals(1.0, trend.points.last().value, 0.0001)
    }
}
