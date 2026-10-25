package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingPerformanceTest {
    @Test
    fun wathanEstimateUsesLoadAndRepsWithRecordedSinglesPreserved() {
        assertEquals(134.7466994817, estimatedOneRepMax(100.0, 10), 0.000001)
        assertEquals(100.0, estimatedOneRepMax(100.0, 1), 0.0)
        assertEquals(0.0, estimatedOneRepMax(0.0, 10), 0.0)
        assertEquals(0.0, estimatedOneRepMax(100.0, 0), 0.0)
        assertEquals(0.0, estimatedOneRepMax(Double.NaN, 10), 0.0)
    }

    @Test
    fun extraSetsIncreaseVolumeButDoNotInflateBestSetOneRepMax() {
        val first = SetRecord(1, 1, 10, 100.0, 0)
        val second = SetRecord(2, 1, 5, 90.0, 1)
        assertEquals(1450.0, trainingPerformance(listOf(first, second), false), 0.0)
        assertEquals(estimatedOneRepMax(100.0, 10), trainingPerformance(listOf(first, second, first), true), 0.0)
        assertEquals(0.0, trainingPerformance(emptyList(), true), 0.0)
    }
}
