package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingEntryEditingTest {
    @Test
    fun changingHistoricalSetsKeepsIdsWeightsAndDates() {
        val original = listOf(SetRecord(10, 1, 10, 40.0, 100), SetRecord(11, 1, 8, 45.0, 101))
        assertEquals(listOf(original[0].copy(reps = 12), original[1].copy(reps = 6)), editedRecordedSets(original, listOf(12, 6, 0, 0, 0)))
        assertEquals(listOf(original[1].copy(reps = 9)), editedRecordedSets(original, listOf(0, 9, 0, 0, 0)))
        assertEquals(listOf(original[0], original[1], original[1].copy(id = 0, reps = 7)), editedRecordedSets(original, listOf(10, 8, 7)))
        assertEquals(emptyList<SetRecord>(), editedRecordedSets(original, listOf(0, 0, 0, 0, 0)))
    }

    @Test
    fun unchangedImportedEntryCanKeepMoreThanFiveSetsAndFifteenReps() {
        val original = (1..7).map { SetRecord(it.toLong(), 1, 20 + it, 40.0 + it, 100L + it) }
        assertEquals(original, editedRecordedSets(original, original.map { it.reps }))
    }

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
