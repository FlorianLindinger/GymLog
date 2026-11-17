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
    fun setEstimateIsEpleyAndIgnoresInvalidSets() {
        assertEquals(133.3333333333, setStrengthScore(100.0, 10), 0.000001)
        assertEquals(140.0, setStrengthScore(100.0, 12), 0.000001)
        assertEquals(100.0, setStrengthScore(100.0, 1), 0.0)
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertEquals(0.0, setStrengthScore(it, 10), 0.0)
        }
        assertEquals(0.0, setStrengthScore(100.0, 0), 0.0)
    }

    @Test
    fun sessionScoreAveragesEverySetSoSetCountAloneDoesNotChangeIt() {
        val first = SetRecord(1, 1, 10, 100.0, 0)
        val second = SetRecord(2, 1, 5, 90.0, 1)
        assertEquals(1450.0, trainingPerformance(listOf(first, second), false), 0.0)
        assertEquals(setStrengthScore(100.0, 10), sessionStrengthScore(listOf(first)), 0.0)
        assertEquals(sessionStrengthScore(listOf(first)), sessionStrengthScore(List(5) { first }), 0.000001)
        val mixed = sessionStrengthScore(listOf(first, second))
        assertEquals((setStrengthScore(100.0, 10) + setStrengthScore(90.0, 5)) / 2, mixed, 0.000001)
        assertEquals(mixed, sessionStrengthScore(listOf(second, first)), 0.0)
        // Invalid rows are skipped rather than averaged in as zero.
        assertEquals(sessionStrengthScore(listOf(first)), sessionStrengthScore(listOf(first, first.copy(reps = 0))), 0.0)
        assertEquals(0.0, trainingPerformance(emptyList(), true), 0.0)
    }

    @Test
    fun moreRepsRaiseTheScoreAndAWeakSetLowersIt() {
        val before = List(3) { SetRecord(it.toLong(), 1, 10, 100.0, 0) }
        val moreReps = List(3) { SetRecord(it.toLong(), 1, 12, 100.0, 0) }
        assertEquals(133.3333333333, trainingPerformance(before, true), .000001)
        assertEquals(140.0, trainingPerformance(moreReps, true), .000001)
        org.junit.Assert.assertTrue(trainingPerformance(before.dropLast(1) + before.last().copy(reps = 6), true) <
            trainingPerformance(before, true))
        assertEquals(trainingPerformance(before, true), trainingPerformance(before.take(2), true), .000001)
    }
}
