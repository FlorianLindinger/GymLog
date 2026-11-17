package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingPerformanceTest {
    @Test
    fun tenMinuteEstimateHasAnExactAnchorAndModestDurationAdjustment() {
        assertEquals(100.0, estimatedTenMinuteIntensity(100.0, 10.0), 0.0)
        assertEquals(104.2465760841, estimatedTenMinuteIntensity(100.0, 20.0), 0.000001)
        assertEquals(95.9264119325, estimatedTenMinuteIntensity(100.0, 5.0), 0.000001)
        assertEquals(6.254794565, estimatedTenMinuteIntensity(6.0, 20.0), 0.000001)
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertEquals(0.0, estimatedTenMinuteIntensity(it, 10.0), 0.0)
            assertEquals(0.0, estimatedTenMinuteIntensity(100.0, it), 0.0)
        }
    }

    @Test
    fun timedPerformanceSelectsBestAdjustedEntryWithoutAddingSeparateEfforts() {
        val entries = listOf(SetRecord(1, 1, 100, 20.0, 0), SetRecord(2, 1, 105, 5.0, 1))
        listOf(TrackingMode.WATTS, TrackingMode.STRENGTH).forEach { mode ->
            assertEquals(104.2465760841, trainingPerformance(entries, false, mode), 0.000001)
            assertEquals(trainingPerformance(entries, false, mode), trainingPerformance(entries + entries, true, mode), 0.0)
            assertEquals(0.0, trainingPerformance(emptyList(), false, mode), 0.0)
        }
        assertEquals(25.0, trainingPerformance(entries, true, TrackingMode.DURATION), 0.0)
    }

    @Test
    fun setScoreWeightsLoadMoreStronglyThanRepetitionsAndIgnoresInvalidSets() {
        assertEquals(125.8925411794, setStrengthScore(100.0, 10), 0.000001)
        assertEquals(100.0, setStrengthScore(100.0, 1), 0.0)
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertEquals(0.0, setStrengthScore(it, 10), 0.0)
        }
        assertEquals(0.0, setStrengthScore(100.0, 0), 0.0)
    }

    @Test
    fun everySetContributesFullyWithoutABestSetBonusCap() {
        val first = SetRecord(1, 1, 10, 100.0, 0)
        val second = SetRecord(2, 1, 5, 90.0, 1)
        val firstScore = setStrengthScore(100.0, 10)
        assertEquals(1450.0, trainingPerformance(listOf(first, second), false), 0.0)
        assertEquals(firstScore, sessionStrengthScore(listOf(first)), 0.0)
        assertEquals(firstScore * 2, sessionStrengthScore(listOf(first, first)), 0.000001)
        val score = sessionStrengthScore(listOf(first, second))
        assertEquals(firstScore + setStrengthScore(90.0, 5), score, 0.000001)
        assertEquals(firstScore * 100, sessionStrengthScore(List(100) { first }), 0.000001)
        assertEquals(score, sessionStrengthScore(listOf(second, first)), 0.0)
        assertEquals(firstScore, sessionStrengthScore(listOf(first, first.copy(reps = 0))), 0.0)
        assertEquals(0.0, trainingPerformance(emptyList(), true), 0.0)
    }

    @Test
    fun ordinaryLoadProgressionCanImproveScoreEvenWhenVolumeFallsButMuchLessWorkCanLowerIt() {
        val before = List(3) { SetRecord(it.toLong(), 1, 10, 100.0, 0) }
        val after = List(3) { SetRecord(it.toLong(), 1, 8, 105.0, 0) }
        assertEquals(377.6776235383, trainingPerformance(before, true), .000001)
        assertEquals(387.8104902036, trainingPerformance(after, true), .000001)
        org.junit.Assert.assertTrue(trainingPerformance(after, false) < trainingPerformance(before, false))
        org.junit.Assert.assertTrue(trainingPerformance(after, true) > trainingPerformance(before, true))
        org.junit.Assert.assertTrue(trainingPerformance(after.take(1), true) < trainingPerformance(before, true))
        org.junit.Assert.assertTrue(trainingPerformance(after.map { it.copy(reps = 1) }, true) < trainingPerformance(before, true))
    }
}
