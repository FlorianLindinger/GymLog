package com.gymlog.app.ui

import com.gymlog.app.data.ChartRange
import com.gymlog.app.data.Machine
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.SetRecord
import com.gymlog.app.data.TrackingMode
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingTimerTest {
    @Test
    fun wakesAtDisplayedMinuteBoundaryInsteadOfPollingEveryThirtySeconds() {
        assertEquals(60_000L, nextTrainingTimerDelay(180_000, 180_000, 0))
        assertEquals(45_000L, nextTrainingTimerDelay(195_000, 180_000, 0))
        assertEquals(1L, nextTrainingTimerDelay(239_999, 180_000, 0))
    }

    @Test
    fun stopsExactlyAtIdleEvenBetweenMinuteBoundaries() {
        assertEquals(15_000L, nextTrainingTimerDelay(1_800_000, 15_000, 0))
        assertNull(nextTrainingTimerDelay(1_815_000, 15_000, 0))
        assertNull(nextTrainingTimerDelay(1_900_000, 15_000, 0))
        assertNull(nextTrainingTimerDelay(0, null, null))
    }

    @Test
    fun shortSessionsStayLiveUntilThirtyMinuteBreakEvenIfLiveClockPassesOneHour() {
        val last = 50L * 60_000L
        assertEquals(true, trainingTimerActive(last + 29L * 60_000L, last, 0))
        assertEquals(false, trainingTimerActive(last + 30L * 60_000L, last, 0))
        assertEquals(1L, nextTrainingTimerDelay(last + 30L * 60_000L - 1, last, 0))
        assertNull(nextTrainingTimerDelay(last + 30L * 60_000L, last, 0))
    }

    @Test
    fun oneHourBoundaryKeepsTenMinuteTimeoutAndNewEntryResumesClock() {
        val hour = 60L * 60_000L
        assertEquals(hour - 1 + 30L * 60_000L, trainingIdleDeadline(hour - 1, 0))
        assertEquals(hour + 10L * 60_000L, trainingIdleDeadline(hour, 0))
        assertEquals(true, trainingTimerActive(hour + 10L * 60_000L - 1, hour, 0))
        assertEquals(false, trainingTimerActive(hour + 10L * 60_000L, hour, 0))
        assertEquals(true, trainingTimerActive(hour + 11L * 60_000L, hour + 11L * 60_000L, 0))
        assertEquals(false, trainingTimerActive(hour, null, null))
    }

    @Test
    fun snapshotUsesLatestTrainingDayAndCountsEachExerciseOnce() {
        val morning = Instant.parse("2026-10-31T06:00:00Z").toEpochMilli()
        val snapshot = trainingTimerSnapshot(listOf(
            exercise(1, TrackingMode.WEIGHT, listOf(morning - 1, morning, morning + 100)),
            exercise(2, TrackingMode.WEIGHT, listOf(morning + 60_000)),
            exercise(3, TrackingMode.WEIGHT, listOf(morning - 60_000)),
        ), ZoneOffset.UTC)
        assertEquals(morning, snapshot.first)
        assertEquals(morning + 60_000, snapshot.last)
        assertEquals(morning - 9 * 60_000, snapshot.estimatedStart)
        assertEquals(2, snapshot.completedExercises)
    }

    @Test
    fun timedExerciseKeepsItsRecordedMinutesForEstimatedStart() {
        listOf(TrackingMode.DURATION, TrackingMode.STRENGTH, TrackingMode.WATTS).forEach { mode ->
            val snapshot = trainingTimerSnapshot(listOf(exercise(1, mode, listOf(1_000_000))), ZoneOffset.UTC)
            assertEquals(1_000_000L - 10 * 60_000, snapshot.estimatedStart)
        }
        assertEquals(TrainingTimerSnapshot(), trainingTimerSnapshot(emptyList(), ZoneOffset.UTC))
    }

    private fun exercise(id: Long, mode: TrackingMode, times: List<Long>) = MachineSummary(
        machine = Machine(id, "Exercise $id", "", "", "", ChartRange.THREE_MONTHS, 0, mode, 10.0, 0),
        areas = emptyList(),
        recentSets = times.mapIndexed { index, time -> SetRecord(id * 100 + index, id, 10, 10.0, time) },
        weightChanges = emptyList(), setsLast30Days = 0,
    )
}
