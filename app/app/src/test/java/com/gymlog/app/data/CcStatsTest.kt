package com.gymlog.app.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CcStatsTest {
    // Wednesday 2026-11-18, 12:00 UTC.
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val clock = GymClock(Clock.fixed(now, ZoneOffset.UTC))
    private val day = 86_400_000L
    private var nextId = 1L

    private fun exercise(id: Long, name: String, areas: List<AreaAssignment>, vararg days: Pair<Int, List<Pair<Int, Double>>>) =
        MachineSummary(
            Machine(id, name, "", "", "", ChartRange.ALL, 0, TrackingMode.WEIGHT, 0.0, 0), areas,
            days.flatMap { (daysAgo, sets) -> sets.mapIndexed { index, (reps, weight) ->
                SetRecord(nextId++, id, reps, weight, now.toEpochMilli() - daysAgo * day - 3_600_000L + index * 60_000L)
            } }, emptyList(), 0,
        )

    @Test fun streakSessionsAndWeekdaysCountTrainingDays() {
        val bench = exercise(1, "Bench", listOf(AreaAssignment(BodyArea.CHEST, Strength.FULL)),
            0 to listOf(10 to 100.0, 10 to 100.0), 7 to listOf(10 to 100.0), 14 to listOf(10 to 100.0), 35 to listOf(10 to 100.0))
        val stats = buildCcStats(listOf(bench), CcPeriod.TWELVE_WEEKS, clock)
        assertEquals(4, stats.sessions.size)
        // Three consecutive weeks up to this week; the gap before the oldest training ends the streak.
        assertEquals(3, stats.currentStreakWeeks)
        assertEquals(3, stats.longestStreakWeeks)
        assertEquals(4, stats.weekdayCounts[2])
        assertEquals(12, stats.weeks.size)
        assertEquals(1, stats.weeks.last().trainings)
        assertEquals(2, stats.weeks.last().sets)
        assertEquals(1.0, stats.sessions.last().minutes!!, 1e-9)
        assertNull(stats.sessions.first().minutes)
    }

    @Test fun momentumRecordsAndNextPrComeFromDailyEstimates() {
        val squat = exercise(1, "Squat", listOf(AreaAssignment(BodyArea.QUADS, Strength.FULL)),
            28 to listOf(5 to 100.0), 21 to listOf(5 to 102.5), 14 to listOf(5 to 105.0), 7 to listOf(5 to 107.5), 0 to listOf(5 to 110.0))
        val stats = buildCcStats(listOf(squat), CcPeriod.TWELVE_WEEKS, clock)
        val lift = stats.lifts.single()
        assertEquals(CcMomentum.RISING, lift.momentum)
        assertEquals(setStrengthScore(110.0, 5), lift.best, 1e-9)
        assertEquals(0L, lift.daysSinceBest)
        assertEquals(5, lift.typicalReps)
        assertEquals(112.5, lift.nextPrWeight, 1e-9)
        // The first training is a baseline, not a record.
        assertEquals(4, stats.records.size)
        assertTrue(stats.records.first().gainPercent > 0)
    }

    @Test fun shortHistoryIsNewAndFlatHistoryIsFlat() {
        val fresh = exercise(1, "Row", emptyList(), 3 to listOf(10 to 50.0), 0 to listOf(10 to 60.0))
        val flat = exercise(2, "Press", emptyList(), 30 to listOf(10 to 50.0), 15 to listOf(10 to 50.0), 0 to listOf(10 to 50.0))
        val lifts = buildCcStats(listOf(fresh, flat), CcPeriod.TWELVE_WEEKS, clock).lifts.associateBy { it.name }
        assertEquals(CcMomentum.NEW, lifts.getValue("Row").momentum)
        assertNull(lifts.getValue("Row").monthlyTrendPercent)
        assertEquals(CcMomentum.FLAT, lifts.getValue("Press").momentum)
    }

    @Test fun balanceSplitAndRepMixUseRegionIntensities() {
        val bench = exercise(1, "Bench", listOf(AreaAssignment(BodyArea.CHEST, Strength.FULL), AreaAssignment(BodyArea.TRICEPS, Strength.HALF)),
            1 to listOf(5 to 100.0, 8 to 90.0, 15 to 60.0, 8 to 90.0))
        val row = exercise(2, "Row", listOf(AreaAssignment(BodyArea.UPPER_BACK, Strength.FULL)), 40 to listOf(10 to 70.0))
        val stats = buildCcStats(listOf(bench, row), CcPeriod.TWELVE_WEEKS, clock)
        assertEquals(1.0, stats.regions.first { it.area == BodyArea.CHEST }.weeklySets, 1e-9)
        assertEquals(0.5, stats.regions.first { it.area == BodyArea.TRICEPS }.weeklySets, 1e-9)
        assertEquals(40L, stats.regions.first { it.area == BodyArea.UPPER_BACK }.daysSinceTrained)
        assertEquals(6.0, stats.split.push, 1e-9)
        assertEquals(1.0, stats.split.pull, 1e-9)
        assertEquals(CcRepMix(1, 3, 1), stats.repMix)
    }

    @Test fun nextPrRoundsUpToAPlateStepThatBeatsTheBest() {
        val best = setStrengthScore(100.0, 10)
        assertEquals(102.5, nextPrWeight(best, 10), 1e-9)
        assertTrue(setStrengthScore(nextPrWeight(best, 8), 8) > best)
        assertEquals(102.5, nextPrWeight(100.0, 1), 1e-9)
    }
}
