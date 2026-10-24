package com.gymlog.app.ui

import com.gymlog.app.data.HistorySet
import com.gymlog.app.data.TrackingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsScreenTest {
    private val zone = ZoneOffset.UTC
    private val firstDate = LocalDate.of(2026, 1, 1)
    private val lastDate = LocalDate.of(2026, 1, 30)

    @Test
    fun hoursPerTrainingOmitsRestDaysAndCombinesAllExercises() {
        val points = trainingHoursPerTraining(
            listOf(
                set("2026-01-04T11:45:00Z"),
                set("2026-01-01T11:30:00Z", machineId = 2),
                set("2026-01-04T11:00:00Z"),
                set("2026-01-01T10:00:00Z"),
            ),
            firstDate, lastDate, zone,
        )

        assertEquals(listOf(1.5, 0.75), points.map(TimeValue::value))
        assertEquals(Instant.parse("2026-01-04T11:45:00Z").toEpochMilli(), points.last().timestamp)
    }

    @Test
    fun overnightSetsRemainOneTraining() {
        val points = trainingHoursPerTraining(
            listOf(set("2026-01-01T23:30:00Z"), set("2026-01-02T01:00:00Z")),
            firstDate, firstDate, zone,
        )

        assertEquals(listOf(1.5), points.map(TimeValue::value))
    }

    @Test
    fun sixOClockBoundaryStartsAnotherTraining() {
        val points = trainingHoursPerTraining(
            listOf(set("2026-01-02T05:30:00Z"), set("2026-01-02T06:30:00Z")),
            firstDate, lastDate, zone,
        )

        assertEquals(listOf(0.0, 0.0), points.map(TimeValue::value))
    }

    @Test
    fun selectedDatesExcludeOtherTrainingsButKeepSingleSetTraining() {
        val points = trainingHoursPerTraining(
            listOf(
                set("2025-12-31T10:00:00Z"),
                set("2026-01-01T10:00:00Z"),
                set("2026-01-30T10:00:00Z"),
                set("2026-01-30T11:00:00Z"),
                set("2026-01-31T10:00:00Z"),
            ),
            firstDate, lastDate, zone,
        )

        assertEquals(listOf(0.0, 1.0), points.map(TimeValue::value))
    }

    private fun set(time: String, machineId: Long = 1L) = HistorySet(
        id = 0L,
        machineId = machineId,
        machineName = "Exercise $machineId",
        trackingMode = TrackingMode.WEIGHT,
        reps = 10,
        weight = 20.0,
        performedAt = Instant.parse(time).toEpochMilli(),
    )
}
