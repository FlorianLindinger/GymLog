package com.gymlog.app.ui

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAxisTest {
    @Test
    fun minorTicksUseReadableSubdivisionsAndStayInsideBoundsWithoutDuplicatingMajors() {
        val range = AxisRange(9.6, 20.4, listOf(10.0, 12.5, 15.0, 17.5, 20.0))
        val minor = minorChartTicks(range)
        assertEquals(listOf(10.5, 11.0, 11.5, 12.0), minor.take(4))
        assertTrue(minor.all { it > range.min && it < range.max && it !in range.ticks })
        assertEquals(listOf(0.5, 1.0, 1.5), minorChartTicks(AxisRange(0.0, 2.0, listOf(0.0, 2.0))))
        val small = minorChartTicks(AxisRange(.1, .2, listOf(.1, .15, .2)))
        assertTrue(small.contains(.11) && small.contains(.19))
        assertTrue(minorChartTicks(AxisRange(0.0, 1.0, listOf(0.0))).isEmpty())
    }
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

    @Test
    fun bodyRangeKeepsRoundTicksInsideCompactDataBounds() {
        listOf(listOf(10.0, 20.0), listOf(9.8, 20.2)).forEach { data ->
            val range = chartAxisRange(data, compact = true)
            assertEquals(listOf(10.0, 12.5, 15.0, 17.5, 20.0), range.ticks)
            assertTrue(range.min <= data.min() && range.max >= data.max())
            assertTrue(range.min > 9.0 && range.max < 21.0)
            assertTrue(range.ticks.first() > range.min && range.ticks.last() < range.max)
        }
    }

    @Test
    fun bodyLimitsCanBeForcedIndependentlyAndClipOutlyingData() {
        val data = listOf(10.0, 20.0)
        val minimum = chartAxisRange(data, compact = true, yMin = 12.0)
        assertEquals(12.0, minimum.min, 0.0)
        assertTrue(minimum.max > 20.0 && minimum.max < 21.0)
        val maximum = chartAxisRange(data, compact = true, yMax = 18.0)
        assertEquals(18.0, maximum.max, 0.0)
        assertTrue(maximum.min < 10.0 && maximum.min > 9.0)
        val both = chartAxisRange(data, compact = true, yMin = 11.0, yMax = 19.0)
        assertEquals(11.0, both.min, 0.0)
        assertEquals(19.0, both.max, 0.0)
        assertTrue(both.ticks.all { it in 11.0..19.0 })
        listOf(chartAxisRange(data, yMin = 30.0), chartAxisRange(data, yMax = 5.0),
            chartAxisRange(listOf(72.0), compact = true), chartAxisRange(emptyList(), compact = true)).forEach { range ->
            assertTrue(range.min >= 0 && range.span > 0)
            assertTrue(range.ticks.size in 2..7)
        }
    }
}
