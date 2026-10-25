package com.gymlog.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodySyncTest {
    @Test
    fun automaticSyncOnlyRunsWhenMissingOrStale() {
        assertTrue(shouldAutoSyncBody(null, 0))
        assertEquals(false, shouldAutoSyncBody(1_000, 2_000))
        assertEquals(false, shouldAutoSyncBody(1_000, 900_999))
        assertTrue(shouldAutoSyncBody(1_000, 901_000))
        assertTrue(shouldAutoSyncBody(1_000, 0))
    }

    @Test
    fun binaryWeightMatchingPreservesNearestEarlierAndDuplicateTieBehavior() {
        val times = listOf(10L, 10L, 20L, 30L)
        assertEquals(0, nearestBodyMeasurementIndex(times, 10, 10))
        assertEquals(0, nearestBodyMeasurementIndex(times, 15, 10))
        assertEquals(2, nearestBodyMeasurementIndex(times, 16, 10))
        assertEquals(0, nearestBodyMeasurementIndex(times, 0, 10))
        assertEquals(3, nearestBodyMeasurementIndex(times, 40, 10))
        assertEquals(null, nearestBodyMeasurementIndex(times, 41, 10))
        assertEquals(null, nearestBodyMeasurementIndex(emptyList(), 10, 10))
        val dense = (0L..10_000L).map { it * 100 }
        for (target in listOf(0L, 49L, 50L, 51L, 500_050L, 1_000_001L)) {
            val expected = dense.indices.minBy { kotlin.math.abs(dense[it] - target) }
            assertEquals(expected, nearestBodyMeasurementIndex(dense, target, 100))
        }
    }
}
