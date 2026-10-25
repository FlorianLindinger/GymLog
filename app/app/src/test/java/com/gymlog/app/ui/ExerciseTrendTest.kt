package com.gymlog.app.ui

import com.gymlog.app.data.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseTrendTest {
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
