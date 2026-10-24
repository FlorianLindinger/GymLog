package com.gymlog.app.ui

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAxisTest {
    @Test
    fun axesCoverDataWithFourToSixEvenlySpacedRoundTicks() {
        val samples = listOf(
            listOf(0.0, 7.0),
            listOf(43.2, 51.7),
            listOf(0.0123, 0.0437),
            listOf(1_500.0, 7_500.0),
            listOf(72.0, 72.0),
            listOf(1.23, 1.24),
            listOf(0.0, 0.3),
        )

        samples.forEach { values ->
            val range = chartAxisRange(values)
            assertTrue("Tick count for $values: ${range.ticks}", range.ticks.size in 4..6)
            assertTrue(range.min >= 0.0)
            assertTrue(range.min <= values.min())
            assertTrue(range.max >= values.max())
            val labels = range.ticks.map(::formatChartAxisValue)
            val steps = labels.map(::BigDecimal).zipWithNext { a, b -> b.subtract(a).stripTrailingZeros() }
            assertTrue("Uneven labels: $labels", steps.all { it == steps.first() })
            val normalizedStep = steps.first().unscaledValue().toInt()
            assertTrue("Not a round step: $labels", normalizedStep in listOf(1, 2, 25, 5))
        }
    }

    @Test
    fun zeroAndMissingDataUseReadableNonnegativeAxis() {
        listOf(emptyList(), listOf(0.0), listOf(-10.0), listOf(Double.NaN, Double.POSITIVE_INFINITY)).forEach { values ->
            val range = chartAxisRange(values)
            assertEquals(listOf(0.0, 0.25, 0.5, 0.75, 1.0), range.ticks)
        }
    }

    @Test
    fun negativeTrendCannotExpandAxisBelowZero() {
        val range = chartAxisRange(listOf(10.0, 5.0, -200.0))

        assertEquals(0.0, range.min, 0.0)
        assertTrue(range.max >= 10.0)
        assertTrue(range.ticks.all { it >= 0.0 })
    }

    @Test
    fun positiveDataKeepsUsefulZoomInsteadOfAlwaysStartingAtZero() {
        val range = chartAxisRange(listOf(78.5, 79.0, 80.0))

        assertTrue(range.min > 0.0)
        assertTrue(range.min <= 78.5)
        assertTrue(range.max >= 80.0)
    }

    @Test
    fun labelsPreserveQuarterStepsWithoutRoundingToUnevenTenths() {
        assertEquals("10.25", formatChartAxisValue(10.25))
        assertEquals("10.5", formatChartAxisValue(10.5))
        assertEquals("10.75", formatChartAxisValue(10.75))
        assertEquals("10", formatChartAxisValue(10.0))
        assertEquals("0", formatChartAxisValue(-0.0))
    }
}
