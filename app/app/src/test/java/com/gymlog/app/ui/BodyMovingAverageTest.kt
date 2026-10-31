package com.gymlog.app.ui

import com.gymlog.app.data.ChartRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMovingAverageTest {
    @Test
    fun trailingWindowsUseEarlierDataWithoutIncludingFutureMeasurements() {
        val points = listOf(TimeValue(0, 0.0), TimeValue(1, 10.0), TimeValue(2, 20.0), TimeValue(5, 50.0), TimeValue(6, 1000.0))
        assertEquals(listOf(TimeValue(2, 10.0), TimeValue(5, 50.0)), bodyMovingAverage(points, VisibleTimeRange(2, 5), 2))
        assertEquals(listOf(TimeValue(2, 10.0), TimeValue(5, 20.0)), bodyMovingAverage(points, VisibleTimeRange(2, 5), 5))
    }

    @Test
    fun duplicateTimestampsShareOneMeanAndInvalidValuesAndGapsAreHandled() {
        val points = listOf(TimeValue(5, 10.0), TimeValue(0, 2.0), TimeValue(0, 4.0), TimeValue(1, 8.0), TimeValue(1, Double.NaN))
        val average = bodyMovingAverage(points, VisibleTimeRange(0, 5), 1)
        assertEquals(listOf(0L, 1L, 5L), average.map { it.timestamp })
        assertEquals(3.0, average[0].value, .000001)
        assertEquals(14.0 / 3, average[1].value, .000001)
        assertEquals(10.0, average[2].value, .000001)
        assertTrue(bodyMovingAverage(emptyList(), VisibleTimeRange(0, 5), 1).isEmpty())
        assertTrue(bodyMovingAverage(points, VisibleTimeRange(6, 10), 1).isEmpty())
    }

    @Test
    fun slidingMeanMatchesIndependentWindowCalculationOnDenseData() {
        val points = (0..1000).map { TimeValue(it * 3L, (it % 17).toDouble()) }
        val average = bodyMovingAverage(points, VisibleTimeRange(1000, 2500), 50).associateBy { it.timestamp }
        listOf(1002L, 1500L, 2499L).forEach { time ->
            val expected = points.filter { it.timestamp in (time - 50)..time }.map { it.value }.average()
            assertEquals(expected, average.getValue(time).value, .000001)
        }
    }

    @Test
    fun historyPercentilesAreIndependentOfExactAllTimeRecordsAndMeans() {
        val points = (0..100).map { TimeValue(it.toLong(), it.toDouble()) } + TimeValue(50, Double.NaN)
        val settings = BodyChartSettings(showAllTimeHigh = true, showAllTimeLow = true,
            showHistoryHigh = true, showHistoryLow = true, showHistoryMean = true, highLowRange = ChartRange.ALL)
        val references = bodyHighLowReferences(points, settings, 100)
        assertEquals(0.0, references[0].low.value, 0.0)
        assertEquals(100.0, references[0].high.value, 0.0)
        assertEquals(5.0, references[1].low.value, 0.0)
        assertEquals(95.0, references[1].high.value, 0.0)
        assertEquals(50.0, references[1].mean!!, 0.0)
        assertTrue(bodyReferenceText(references[1], "kg").contains("(5%)"))
        val exact = bodyHighLowReferences(points, settings.copy(historyPercentile = 0.0), 100)[1]
        assertEquals(0.0, exact.low.value, 0.0)
        assertEquals(100.0, exact.high.value, 0.0)
        assertFalse(bodyReferenceText(exact, "kg").contains("%"))
    }

    @Test
    fun customHistoryPercentilesInterpolateSparseSamplesAndLabelTheirInterval() {
        val settings = BodyChartSettings(historyPercentile = 10.0, showHistoryLow = true, showHistoryHigh = true)
        val reference = bodyHighLowReferences(listOf(TimeValue(0, 0.0), TimeValue(100, 100.0)), settings, 100).single()
        assertEquals(10.0, reference.low.value, .000001)
        assertEquals(90.0, reference.high.value, .000001)
        assertTrue(bodyReferenceText(reference, "kg").startsWith("2mon history:"))
        assertTrue(bodyReferenceText(reference, "kg").contains("(10%)"))
        val single = bodyHighLowReferences(listOf(TimeValue(0, 42.0)), settings, 0).single()
        assertEquals(42.0, single.low.value, 0.0)
        assertEquals(42.0, single.high.value, 0.0)
    }

    @Test
    fun rawAndBothAverageChoicesInheritTogetherAndCanBeOverriddenPerPlot() {
        val global = BodyChartSettings(showRawData = false, periodAveraging = true, averaging = true,
            averagePeriodDays = 14.0, averageFractionPercent = 12.5, historyPercentile = 8.0)
        val local = BodyChartSettings()
        val inherited = resolveBodySettings(local, global)
        assertFalse(inherited.showRawData)
        assertTrue(inherited.periodAveraging && inherited.averaging)
        assertEquals(14.0, inherited.averagePeriodDays, 0.0)
        assertEquals(12.5, inherited.averageFractionPercent, 0.0)
        assertEquals(8.0, inherited.historyPercentile, 0.0)
        val manual = local.copy(globalAveraging = false, globalHighLow = false)
        assertEquals(manual, resolveBodySettings(manual, global))
    }
}
