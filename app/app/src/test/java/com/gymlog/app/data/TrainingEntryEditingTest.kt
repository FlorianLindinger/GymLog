package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingEntryEditingTest {
    private val sets = listOf(SetRecord(1, 1, 10, 40.0, 100), SetRecord(2, 1, 8, 45.0, 101))

    @Test
    fun editingRepsPreservesRecordedWeightsIncludingAddedSets() {
        assertEquals(40.0, trainingEntryMetric(50.0, TrackingMode.WEIGHT, sets), 0.0)
        assertEquals(45.0, trainingEntryMetric(50.0, TrackingMode.WEIGHT, sets, 1), 0.0)
        assertEquals(45.0, trainingEntryMetric(50.0, TrackingMode.WEIGHT, sets, 2), 0.0)
    }

    @Test
    fun editingMinutesPreservesRecordedLevelOrWatts() {
        listOf(TrackingMode.STRENGTH, TrackingMode.WATTS).forEach { mode ->
            assertEquals(10.0, trainingEntryMetric(20.0, mode, sets), 0.0)
        }
    }

    @Test
    fun newTrainingDayUsesConfiguredMetric() {
        listOf(TrackingMode.WEIGHT, TrackingMode.STRENGTH, TrackingMode.WATTS).forEach { mode ->
            assertEquals(50.0, trainingEntryMetric(50.0, mode, emptyList()), 0.0)
        }
    }
}
