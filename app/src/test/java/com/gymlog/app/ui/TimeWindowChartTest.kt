package com.gymlog.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeWindowChartTest {
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
}
