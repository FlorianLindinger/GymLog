package com.gymlog.app.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsDashboardTest {
    private val clock = GymClock(Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC))
    private val period = DashboardPeriod(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-22"))
    private val settings = ProgressionSettings(staleWeeks = 0, volumeMinimumIncreasePercent = 0)

    private fun set(id: Long, time: String, weight: Double = 10.0, reps: Int = 10) =
        SetRecord(id, 1, reps, weight, Instant.parse(time).toEpochMilli())

    private fun exercise(id: Long, mode: TrackingMode = TrackingMode.WEIGHT, vararg sets: SetRecord) = MachineSummary(
        Machine(id, "Exercise $id", "", "", "", ChartRange.ALL, 0, mode, 10.0, id.toInt()),
        emptyList(), sets.map { it.copy(machineId = id) }, emptyList(), 0,
    )

    @Test
    fun equalCalendarPeriodsAndSixOClockBoundaryAreAppliedToAllTotals() {
        assertEquals(3L, period.days)
        assertEquals(DashboardPeriod(LocalDate.parse("2026-10-17"), LocalDate.parse("2026-10-19")), period.previous())
        val machine = exercise(1, TrackingMode.WEIGHT,
            set(1, "2026-10-19T23:00:00Z"), set(2, "2026-10-20T05:59:00Z"),
            set(3, "2026-10-20T06:00:00Z"), set(4, "2026-10-22T23:00:00Z"),
            set(5, "2026-10-23T06:00:00Z"), set(6, "2026-10-26T12:00:00Z"))
        val result = buildStatsDashboard(listOf(machine), period, settings, clock)
        assertEquals(2, result.current.sessions)
        assertEquals(1, result.previous.sessions)
        assertEquals(2, result.current.weightSets)
        assertEquals(200.0, result.current.volume, 0.0)
        assertEquals(200.0, result.previous.volume, 0.0)
        assertEquals(listOf(period.first, period.last), result.exercises.single().days.map { it.date })
        assertEquals(2L, result.exercises.single().daysSinceLast)
    }

    @Test
    fun cardioDoesNotBecomeOneRepMaxOrWeightVolumeAndExtraSetsDoNotInflateStrength() {
        val weight = exercise(1, TrackingMode.WEIGHT, set(1, "2026-10-20T12:00:00Z", 100.0), set(2, "2026-10-20T12:01:00Z", 100.0))
        val level = exercise(2, TrackingMode.STRENGTH, set(3, "2026-10-20T13:00:00Z", 20.0, 7))
        val watts = exercise(3, TrackingMode.WATTS, set(4, "2026-10-20T13:30:00Z", 25.0, 120))
        val result = buildStatsDashboard(listOf(weight, level, watts), period, settings, clock)
        assertEquals(2, result.current.weightSets)
        assertEquals(2000.0, result.current.volume, 0.0)
        assertEquals(45.0, result.current.cardioMinutes, 0.0)
        assertEquals(estimatedOneRepMax(100.0, 10), result.exercises[0].latest!!, 0.0)
        assertEquals(7.0, result.exercises[1].latest!!, 0.0)
        assertEquals(120.0, result.exercises[2].latest!!, 0.0)
        assertEquals("Peak power", result.exercises[2].label)
        assertNull(result.exercises[1].changePercent)
    }

    @Test
    fun progressUsesPreviousPeriodsLatestAndRecordsRequireBeatingAllEarlierHistory() {
        val machine = exercise(1, TrackingMode.WEIGHT,
            set(1, "2026-10-17T12:00:00Z", 60.0), set(2, "2026-10-19T12:00:00Z", 50.0),
            set(3, "2026-10-20T12:00:00Z", 52.0), set(4, "2026-10-22T12:00:00Z", 55.0))
        val result = buildStatsDashboard(listOf(machine), period, settings, clock)
        assertEquals(10.0, result.exercises.single().changePercent!!, 0.000001)
        assertEquals(10.0, result.medianStrengthChange!!, 0.000001)
        assertFalse(result.exercises.single().isRecord)
        assertTrue(result.records.isEmpty())
        val improved = machine.copy(recentSets = machine.recentSets + set(5, "2026-10-22T15:00:00Z", 65.0))
        assertTrue(buildStatsDashboard(listOf(improved), period, settings, clock).exercises.single().isRecord)
    }

    @Test
    fun firstTrainingsAndUnknownDurationsDoNotProduceFakeRecordsOrPercentages() {
        val machine = exercise(1, TrackingMode.WEIGHT, set(1, "2026-10-20T12:00:00Z"))
        val result = buildStatsDashboard(listOf(machine), period, settings, clock)
        assertNull(result.current.averageMinutes)
        assertEquals(0, result.current.knownDurationCount)
        assertNull(result.exercises.single().changePercent)
        assertFalse(result.exercises.single().isRecord)
        assertNull(result.medianStrengthChange)
        assertNull(dashboardPercentChange(10.0, 0.0))
        assertNull(dashboardPercentChange(10.0, null))
        val empty = buildStatsDashboard(emptyList(), period, settings, clock)
        assertEquals(0, empty.current.sessions)
        assertEquals(0.0, empty.current.volume, 0.0)
        assertTrue(empty.regions.isEmpty())
    }

    @Test
    fun firstTrainingWithinRangeIsFallbackBaselineAndRegionWorkloadUsesSets() {
        val machine = exercise(1, TrackingMode.WEIGHT, set(1, "2026-10-20T12:00:00Z", 50.0),
            set(2, "2026-10-22T12:00:00Z", 55.0)).copy(areas = listOf(
                AreaAssignment(BodyArea.CHEST, Strength.FULL), AreaAssignment(BodyArea.BICEPS, Strength.HALF)))
        val result = buildStatsDashboard(listOf(machine), period, settings, clock)
        assertEquals(10.0, result.exercises.single().changePercent!!, 0.000001)
        assertFalse(result.exercises.single().isRecord)
        assertEquals(2.0, result.regions.first { it.area == BodyArea.CHEST }.weightedSets, 0.0)
        assertEquals(1.0, result.regions.first { it.area == BodyArea.BICEPS }.weightedSets, 0.0)
    }

    @Test
    fun ongoingSundayIsNotMistakenForACompleteWeek() {
        val week = DashboardPeriod(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25"))
        assertFalse(buildStatsDashboard(emptyList(), week, settings, clock).weeks.single().complete)
        val monday = GymClock(Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC))
        assertTrue(buildStatsDashboard(emptyList(), week, settings, monday).weeks.single().complete)
    }

    @Test
    fun unconfiguredSettingsEnableOneRepMaxByDefault() {
        assertTrue(ProgressionSettings().useOneRepMax)
    }
}
