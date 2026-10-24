package com.gymlog.app.data

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseRankingTest {
    private val day = 86_400_000L
    private val cutoff = 10L * day
    private val zone = ZoneOffset.UTC

    @Test
    fun contextUsesLastTwoDistinctExercisesAndExcludesOldTrainings() {
        val old = exercise(1, BodyArea.CHEST, times = listOf(cutoff - 1))
        val previous = exercise(2, BodyArea.BICEPS, times = listOf(cutoff + 2))
        val current = exercise(3, BodyArea.QUADS, times = listOf(cutoff + 3, cutoff + 4, cutoff + 5))

        assertEquals(listOf(3L, 2L), recentTrainingExercises(listOf(old, current, previous), cutoff).map { it.machine.id })
        assertTrue(recentTrainingExercises(listOf(old), cutoff).isEmpty())
    }

    @Test
    fun previousExerciseAffectsRankingEvenWithNoCurrentClash() {
        val current = exercise(1, BodyArea.CHEST, times = listOf(cutoff + 3))
        val previous = exercise(2, BodyArea.BICEPS, times = listOf(cutoff + 2))
        val curl = exercise(3, BodyArea.BICEPS)
        val legs = exercise(4, BodyArea.QUADS)
        val ranked = rankOverviewMachines(listOf(curl, previous, current, legs), cutoff, listOf(current, previous), zone)

        assertEquals(listOf(1L, 4L, 3L, 2L), ranked.map { it.machine.id })
        assertEquals(50, exerciseOverlap(curl, listOf(current, previous)).percent)
        assertEquals(100, exerciseOverlap(curl, listOf(previous)).percent)
    }

    @Test
    fun weightedOverlapConsidersAllRegionsAndIntensity() {
        val recent = exercise(1, BodyArea.CHEST).copy(areas = listOf(AreaAssignment(BodyArea.CHEST, Strength.HALF)))
        val candidate = exercise(2, BodyArea.CHEST).copy(areas = listOf(
            AreaAssignment(BodyArea.CHEST, Strength.FULL), AreaAssignment(BodyArea.QUADS, Strength.FULL),
        ))
        val overlap = exerciseOverlap(candidate, listOf(recent))

        assertEquals(25, overlap.percent)
        assertEquals(setOf(BodyArea.CHEST), overlap.sharedAreas)
        assertEquals(25, exerciseOverlap(exercise(3, BodyArea.CHEST), listOf(
            recent.copy(areas = listOf(AreaAssignment(BodyArea.CHEST, Strength.LIGHT))),
        )).percent)
    }

    @Test
    fun combinedOverlapIsCappedAndRepeatedContextDoesNotDoubleCount() {
        val candidate = exercise(1, BodyArea.CHEST)
        val first = exercise(2, BodyArea.CHEST)
        val second = exercise(3, BodyArea.CHEST)
        assertEquals(100, exerciseOverlap(candidate, listOf(first, second)).percent)
        assertEquals(exerciseOverlap(candidate, listOf(first)), exerciseOverlap(candidate, listOf(first, first)))
    }

    @Test
    fun equalOverlapPrefersFewerLifetimeTrainingDaysBeforePriority() {
        val often = exercise(1, BodyArea.QUADS, times = listOf(day, 2L * day, 3L * day)).copy(
            machine = exercise(1, BodyArea.QUADS).machine.copy(rankingPriority = -20),
        )
        val rare = exercise(2, BodyArea.CALVES, times = listOf(day))
        assertEquals(listOf(2L, 1L), rankOverviewMachines(listOf(often, rare), cutoff, emptyList(), zone).map { it.machine.id })
    }

    @Test
    fun lowerOverlapWinsEvenIfItHasMoreLifetimeTrainings() {
        val current = exercise(1, BodyArea.CHEST)
        val rareConflict = exercise(2, BodyArea.CHEST)
        val frequentLegs = exercise(3, BodyArea.QUADS, times = listOf(day, 2L * day, 3L * day))
        assertEquals(listOf(3L, 2L), rankOverviewMachines(listOf(rareConflict, frequentLegs), cutoff, listOf(current), zone).map { it.machine.id })
    }

    @Test
    fun trainingCountCountsDaysRatherThanSetsAndUsesSixOClockBoundary() {
        val times = listOf("2026-01-02T05:00:00Z", "2026-01-02T05:30:00Z", "2026-01-02T06:30:00Z")
            .map { Instant.parse(it).toEpochMilli() }
        assertEquals(2, exerciseTrainingCount(exercise(1, BodyArea.CHEST, times = times), zone))
    }

    @Test
    fun manualGroupsAndTheirArrowOrderOverrideAutomaticRanking() {
        val current = exercise(1, BodyArea.CHEST, times = listOf(cutoff + 1))
        val topA = manual(exercise(2, BodyArea.CHEST), ExerciseOrderGroup.MANUAL_TOP, 2)
        val topB = manual(exercise(3, BodyArea.CHEST), ExerciseOrderGroup.MANUAL_TOP, 1)
        val auto = exercise(4, BodyArea.QUADS)
        val bottom = manual(exercise(5, BodyArea.CALVES), ExerciseOrderGroup.MANUAL_BOTTOM, 0)
        assertEquals(listOf(1L, 3L, 2L, 4L, 5L), rankOverviewMachines(listOf(bottom, topA, auto, current, topB), cutoff, listOf(current), zone).map { it.machine.id })
    }

    @Test
    fun alreadyDoneAutomaticExercisesFollowUnfinishedOnes() {
        val done = exercise(1, BodyArea.QUADS, times = listOf(cutoff + 1))
        val unfinished = exercise(2, BodyArea.CHEST)
        assertEquals(listOf(2L, 1L), rankOverviewMachines(listOf(done, unfinished), cutoff, emptyList(), zone).map { it.machine.id })
    }

    @Test
    fun missingProfilesAreUnknownAndFollowKnownCandidates() {
        val unknown = exercise(1, BodyArea.CHEST).copy(areas = emptyList())
        val known = exercise(2, BodyArea.CHEST)
        assertNull(exerciseOverlap(unknown, emptyList()).percent)
        assertNull(exerciseOverlap(known, listOf(unknown)).percent)
        assertEquals(listOf(2L, 1L), rankOverviewMachines(listOf(unknown, known), cutoff, emptyList(), zone).map { it.machine.id })
    }

    @Test
    fun missingRecentProfileIsMarkedPartialWithoutPromotingPreviousToCurrent() {
        val unknownCurrent = exercise(1, BodyArea.CHEST).copy(areas = emptyList())
        val previous = exercise(2, BodyArea.CHEST)
        val overlap = exerciseOverlap(exercise(3, BodyArea.CHEST), listOf(unknownCurrent, previous))
        assertTrue(overlap.incomplete)
        assertEquals(50, overlap.percent)
    }

    @Test
    fun hiddenContextIsUsedWithoutAddingHiddenExercisesToVisibleList() {
        val hiddenCurrent = exercise(1, BodyArea.CHEST)
        val chest = exercise(2, BodyArea.CHEST)
        val legs = exercise(3, BodyArea.QUADS)
        assertEquals(listOf(3L, 2L), rankOverviewMachines(listOf(chest, legs), cutoff, listOf(hiddenCurrent), zone).map { it.machine.id })
    }

    private fun manual(summary: MachineSummary, group: ExerciseOrderGroup, order: Int) = summary.copy(
        machine = summary.machine.copy(pinned = group == ExerciseOrderGroup.MANUAL_TOP,
            excludedFromAutoRanking = group == ExerciseOrderGroup.MANUAL_BOTTOM, sortOrder = order),
    )

    private fun exercise(id: Long, area: BodyArea, times: List<Long> = emptyList()) = MachineSummary(
        machine = Machine(id, "Exercise $id", "", "", "", ChartRange.THREE_MONTHS, 0, TrackingMode.WEIGHT, 0.0, id.toInt()),
        areas = listOf(AreaAssignment(area, Strength.FULL)),
        recentSets = times.mapIndexed { index, time -> SetRecord(id * 100 + index, id, 10, 10.0, time) },
        weightChanges = emptyList(), setsLast30Days = 0,
    )
}
