package com.gymlog.app.data

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseRankingTest {
    @Test
    fun addingMuscleRegionsCannotDiluteFullCoreClash() {
        val core = exercise(1, BodyArea.CORE)
        val mixed = core.copy(areas = core.areas + AreaAssignment(BodyArea.CHEST, Strength.FULL))
        val latest = exercise(2, BodyArea.CORE)
        assertEquals(100, exerciseOverlap(core, listOf(latest)).percent)
        assertEquals(exerciseOverlap(core, listOf(latest)).percent, exerciseOverlap(mixed, listOf(latest)).percent)
        val lowCore = mixed.copy(areas = listOf(AreaAssignment(BodyArea.CORE, Strength.LIGHT),
            AreaAssignment(BodyArea.CHEST, Strength.FULL)))
        assertEquals(25, exerciseOverlap(lowCore, listOf(latest)).percent)
    }

    @Test
    fun everyPriorTrainingDayContributesOnceForAnExerciseAcrossAllThreeDays() {
        val now = Instant.parse("2026-11-06T12:00:00Z").toEpochMilli()
        val recent = exercise(1, BodyArea.CHEST, times = listOf(now - day, now - day + 1000,
            now - 2 * day, now - 3 * day, now - 4 * day))
        val prior = priorExerciseExposures(listOf(recent), now, zone)
        assertEquals(listOf(0.4, 0.2, 0.1), prior.map { it.weight })
        assertEquals(70, exerciseOverlap(exercise(2, BodyArea.CHEST), emptyList(), prior).percent)
    }

    @Test
    fun customPreviousDayFatigueChangesWeightsAndZeroDropsThatDay() {
        val now = Instant.parse("2026-11-06T12:00:00Z").toEpochMilli()
        val recent = exercise(1, BodyArea.CHEST, times = listOf(now - day, now - 2 * day, now - 3 * day))
        val custom = priorExerciseExposures(listOf(recent), now, zone, PriorDayFatigue(50, 0, 5))
        assertEquals(listOf(0.5, 0.05), custom.map { it.weight })
        assertEquals(55, exerciseOverlap(exercise(2, BodyArea.CHEST), emptyList(), custom).percent)
        assertTrue(priorExerciseExposures(listOf(recent), now, zone, PriorDayFatigue(0, 0, 0)).isEmpty())
        assertNull(parsePriorDayFatigue("101", "20", "10"))
        assertNull(parsePriorDayFatigue("", "20", "10"))
        assertEquals(PriorDayFatigue(0, 20, 100), parsePriorDayFatigue("0", "20", "100"))
    }

    @Test
    fun extremeBiasAndPriorityCannotCrossAColorBinAndCustomBandsChangeGrouping() {
        val settings = ProgressionSettings(staleDays = 7)
        val conflict = exercise(1, BodyArea.CHEST, times = listOf(40 * day, 45 * day, 50 * day)).let {
            it.copy(machine = it.machine.copy(rankingPriority = -999))
        }
        val clear = exercise(2, BodyArea.QUADS).let { it.copy(machine = it.machine.copy(rankingPriority = 999)) }
        val context = listOf(exercise(9, BodyArea.CHEST).copy(areas = listOf(AreaAssignment(BodyArea.CHEST, Strength.HALF))))
        fun order(bands: FatigueBands) = rankOverviewMachines(listOf(conflict, clear), 60 * day, context,
            zone, 60 * day, progressionSettings = settings, fatigueBands = bands).map { it.machine.id }
        assertEquals(listOf(2L, 1L), order(FatigueBands()))
        assertEquals(listOf(1L, 2L), order(FatigueBands(60, 80, 90)))
    }

    @Test
    fun warningAndFrequencyBoostsAreSmallAndBounded() {
        assertEquals(0.25, overviewOrderBias(3, 3.0, 1), 0.0)
        assertEquals(0.5, overviewOrderBias(3, 3.0, 2), 0.0)
        assertEquals(0.75, overviewOrderBias(0, 3.0, 0), 0.0)
        assertEquals(0.0, overviewOrderBias(3, 3.0, 0), 0.0)
        assertEquals(overviewOrderBias(3, 3.0, 2), overviewOrderBias(30, 3.0, 10), 0.0)
        assertEquals(overviewOrderBias(2, 3.0, 1), overviewOrderBias(1, 3.0, 0), 0.0)
    }

    @Test
    fun activePerExerciseWarningsAreMixedIntoAutomaticOrder() {
        val global = ProgressionSettings(staleDays = 7, volumeWindowDays = 60)
        fun item(id: Long, settings: ProgressionSettings) = exercise(id, BodyArea.CHEST,
            times = listOf(40 * day, 45 * day, 50 * day)).let {
            it.copy(machine = it.machine.copy(progressionSettings = settings))
        }
        val none = item(1, global.copy(staleDays = 0, volumeMinimumIncreasePercent = 0))
        val one = item(2, global.copy(staleDays = 0))
        val two = item(3, global)
        assertEquals(listOf(3L, 2L, 1L), rankOverviewMachines(listOf(none, one, two), 60 * day,
            emptyList(), zone, 60 * day, progressionSettings = global).map { it.machine.id })
        val prioritized = none.copy(machine = none.machine.copy(rankingPriority = -1))
        assertEquals(1L, rankOverviewMachines(listOf(prioritized, one, two), 60 * day,
            emptyList(), zone, 60 * day, progressionSettings = global).first().machine.id)
    }

    @Test
    fun newExerciseStopsReceivingCatchUpPriorityAfterThreeRecentTrainings() {
        val established = exercise(1, BodyArea.CHEST, times = (10L..40L).map { it * day })
        val new = exercise(2, BodyArea.CHEST)
        fun order(candidate: MachineSummary) = rankOverviewMachines(listOf(established, candidate),
            60 * day, emptyList(), zone, 60 * day).map { it.machine.id }
        assertEquals(listOf(2L, 1L), order(new))
        assertEquals(listOf(2L, 1L), order(new.copy(recentSets = established.recentSets.take(2))))
        assertEquals(listOf(1L, 2L), order(new.copy(recentSets = established.recentSets.take(3))))
    }

    @Test fun previousDaysContributeOncePerExerciseWithoutOrderWeighting() {
        val now = Instant.parse("2026-11-06T12:00:00Z").toEpochMilli()
        val yesterday = Instant.parse("2026-11-05T12:00:00Z").toEpochMilli()
        val twoDays = Instant.parse("2026-11-04T12:00:00Z").toEpochMilli()
        val items = listOf(exercise(1, BodyArea.CHEST, times = listOf(yesterday, yesterday + 1000)),
            exercise(2, BodyArea.CHEST, times = listOf(yesterday + 2000)),
            exercise(3, BodyArea.CHEST, times = listOf(twoDays)),
            exercise(4, BodyArea.CHEST, times = listOf(twoDays - day)),
            exercise(5, BodyArea.CHEST, times = listOf(twoDays - 2 * day)))
        val exposures = priorExerciseExposures(items, now, zone)
        assertEquals(4, exposures.size)
        assertEquals(listOf(0.4, 0.4, 0.2, 0.1), exposures.map { it.weight })
        // 0.4 + 0.4 + 0.2 + 0.1 exceeds one full region and is capped.
        assertEquals(100, exerciseOverlap(exercise(99, BodyArea.CHEST), emptyList(), exposures).percent)
        assertEquals(exposures.toSet(), priorExerciseExposures(items.reversed(), now, zone).toSet())
    }
    @Test
    fun olderExercisesKeepTenPercentAndCustomCorePercentIsUsed() {
        val chest = exercise(100, BodyArea.CHEST)
        val core = exercise(101, BodyArea.CORE)
        val context = (1L..8L).map { exercise(it, BodyArea.QUADS) }.toMutableList()
        listOf(100, 50, 25, 13, 10, 10, 10, 10).forEachIndexed { index, expected ->
            val entries = context.toMutableList().also { it[index] = exercise(index.toLong() + 1, BodyArea.CHEST) }
            assertEquals(expected, exerciseOverlap(chest, entries).percent)
        }
        val halfCore = exercise(1, BodyArea.CORE).copy(areas = listOf(AreaAssignment(BodyArea.CORE, Strength.HALF, intensity = 0.75)))
        assertEquals(75, exerciseOverlap(core, listOf(halfCore)).percent)
        assertEquals(100, exerciseOverlap(core, listOf(exercise(1, BodyArea.CORE))).percent)
        assertEquals(10, exerciseOverlap(core, context + exercise(99, BodyArea.CORE)).percent)
    }
    @Test
    fun eachNewlyLoggedExerciseBecomesFirstRegardlessOfAutoPriorityOrManualGroup() {
        val top = manual(exercise(1, BodyArea.CHEST), ExerciseOrderGroup.MANUAL_TOP, 0)
        val automatic = exercise(2, BodyArea.QUADS).let { it.copy(machine = it.machine.copy(rankingPriority = 20)) }
        val bottom = manual(exercise(3, BodyArea.CALVES), ExerciseOrderGroup.MANUAL_BOTTOM, 0)
        val now = cutoff + 100
        fun ranked(items: List<MachineSummary>) = rankOverviewMachines(items, cutoff,
            recentTrainingExercises(items, cutoff), zone, now).map { it.machine.id }
        val firstLog = automatic.copy(recentSets = listOf(SetRecord(1, 2, 10, 40.0, cutoff + 1)))
        assertEquals(2L, ranked(listOf(top, firstLog, bottom)).first())
        val secondLog = bottom.copy(recentSets = listOf(SetRecord(2, 3, 100, 20.0, cutoff + 2)))
        assertEquals(3L, ranked(listOf(top, firstLog, secondLog)).first())
        val changedLog = firstLog.copy(recentSets = listOf(SetRecord(3, 2, 12, 40.0, cutoff + 3)))
        assertEquals(2L, ranked(listOf(top, changedLog, secondLog)).first())
    }

    @Test
    fun recoveryFactorsStayNearOneAndChangeSharedRankingAndColorBins() {
        assertTrue(BodyArea.entries.filter { it != BodyArea.CORE }.all { it.recoveryFactor in 0.9..1.1 })
        val current = exercise(10, BodyArea.CHEST)
        val arms = exercise(11, BodyArea.BICEPS)
        val legs = exercise(12, BodyArea.QUADS)
        val armCandidate = exercise(1, BodyArea.BICEPS)
        val legCandidate = exercise(2, BodyArea.QUADS)
        val context = listOf(current, arms.copy(areas = arms.areas + legs.areas))
        val armOverlap = exerciseOverlap(armCandidate, context).percent
        val legOverlap = exerciseOverlap(legCandidate, context).percent
        assertEquals(53, armOverlap)
        assertEquals(46, legOverlap)
        assertEquals(100, exerciseOverlap(armCandidate, listOf(arms)).percent)
        assertEquals(100, exerciseOverlap(legCandidate, listOf(legs)).percent)
        // Three intervening exercises cross a shared color/ranking bin boundary.
        val olderContext = listOf(current, current.copy(machine = current.machine.copy(id = 20)),
            current.copy(machine = current.machine.copy(id = 21)), context.last())
        val bands = FatigueBands(13, 50, 75)
        assertTrue(exerciseOverlapBin(exerciseOverlap(armCandidate, olderContext).percent, bands) >
            exerciseOverlapBin(exerciseOverlap(legCandidate, olderContext).percent, bands))
        assertEquals(listOf(2L, 1L), rankOverviewMachines(listOf(armCandidate, legCandidate),
            cutoff, olderContext, zone, cutoff, fatigueBands = bands).map { it.machine.id })
    }

    @Test
    fun coreOverlapDecaysAtEightyPercentOfPreviousRate() {
        assertEquals(1.0 / 0.8, BodyArea.CORE.recoveryFactor, 0.0)
        val core = exercise(1, BodyArea.CORE)
        val chest = exercise(2, BodyArea.CHEST)
        val latest = exercise(3, BodyArea.QUADS)
        val combined = exercise(4, BodyArea.CORE).copy(areas = core.areas + chest.areas)
        assertEquals(100, exerciseOverlap(core, listOf(combined)).percent)
        assertEquals(57, exerciseOverlap(core, listOf(latest, combined)).percent)
        assertEquals(50, exerciseOverlap(chest, listOf(latest, combined)).percent)
        val bands = FatigueBands(30, 60, 85)
        assertTrue(exerciseOverlapBin(exerciseOverlap(core, listOf(latest, latest.copy(
            machine = latest.machine.copy(id = 5)), combined)).percent, bands) >
            exerciseOverlapBin(exerciseOverlap(chest, listOf(latest, latest.copy(
                machine = latest.machine.copy(id = 5)), combined)).percent, bands))
    }

    private val day = 86_400_000L
    private val cutoff = 10L * day
    private val zone = ZoneOffset.UTC

    @Test
    fun contextUsesAllDistinctExercisesAndExcludesOldTrainings() {
        val old = exercise(1, BodyArea.CHEST, times = listOf(cutoff - 1))
        val previous = exercise(2, BodyArea.BICEPS, times = listOf(cutoff + 2))
        val current = exercise(3, BodyArea.QUADS, times = listOf(cutoff + 3, cutoff + 4, cutoff + 5))
        val earlier = exercise(4, BodyArea.CALVES, times = listOf(cutoff + 1))

        assertEquals(listOf(3L, 2L), recentTrainingExercises(listOf(old, current, previous), cutoff).map { it.machine.id })
        assertTrue(recentTrainingExercises(listOf(old), cutoff).isEmpty())
        assertEquals(listOf(3L, 2L, 4L), recentTrainingExercises(listOf(old, earlier, current, previous), cutoff).map { it.machine.id })
    }

    @Test
    fun previousExerciseAffectsRankingEvenWithNoCurrentClash() {
        val current = exercise(1, BodyArea.CHEST, times = listOf(cutoff + 3))
        val previous = exercise(2, BodyArea.BICEPS, times = listOf(cutoff + 2))
        val curl = exercise(3, BodyArea.BICEPS)
        val legs = exercise(4, BodyArea.QUADS)
        val ranked = rankOverviewMachines(listOf(curl, previous, current, legs), cutoff, listOf(current, previous), zone, cutoff)

        assertEquals(listOf(1L, 4L, 3L, 2L), ranked.map { it.machine.id })
        assertEquals(53, exerciseOverlap(curl, listOf(current, previous)).percent)
        assertEquals(100, exerciseOverlap(curl, listOf(previous)).percent)
    }

    @Test
    fun weightedOverlapConsidersAllRegionsAndIntensity() {
        val recent = exercise(1, BodyArea.CHEST).copy(areas = listOf(AreaAssignment(BodyArea.CHEST, Strength.HALF)))
        val candidate = exercise(2, BodyArea.CHEST).copy(areas = listOf(
            AreaAssignment(BodyArea.CHEST, Strength.FULL), AreaAssignment(BodyArea.QUADS, Strength.FULL),
        ))
        val overlap = exerciseOverlap(candidate, listOf(recent))

        assertEquals(50, overlap.percent)
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
    fun equalOverlapUsesPriorityBeforeRecentTrainingDays() {
        val often = exercise(1, BodyArea.QUADS, times = listOf(day, 2L * day, 3L * day)).copy(
            machine = exercise(1, BodyArea.QUADS).machine.copy(rankingPriority = -1),
        )
        val rare = exercise(2, BodyArea.CALVES, times = listOf(day))
        // The displayed +1 is stored as -1, so a small increase must beat frequency within this bin.
        assertEquals(listOf(1L, 2L), rankOverviewMachines(listOf(often, rare), cutoff, emptyList(), zone, cutoff).map { it.machine.id })
        val lowerPriority = rare.copy(machine = rare.machine.copy(rankingPriority = 1))
        val normalOften = often.copy(machine = often.machine.copy(rankingPriority = 0))
        assertEquals(listOf(1L, 2L), rankOverviewMachines(listOf(lowerPriority, normalOften), cutoff, emptyList(), zone, cutoff).map { it.machine.id })
        val equalPriority = often.copy(machine = often.machine.copy(rankingPriority = 0))
        assertEquals(listOf(2L, 1L), rankOverviewMachines(listOf(equalPriority, rare), cutoff, emptyList(), zone, cutoff).map { it.machine.id })
    }

    @Test
    fun lowerOverlapWinsEvenIfItHasMoreLifetimeTrainings() {
        val current = exercise(1, BodyArea.CHEST)
        val rareConflict = exercise(2, BodyArea.CHEST).let { it.copy(machine = it.machine.copy(rankingPriority = -20)) }
        val frequentLegs = exercise(3, BodyArea.QUADS, times = listOf(day, 2L * day, 3L * day))
        assertEquals(listOf(3L, 2L), rankOverviewMachines(listOf(rareConflict, frequentLegs), cutoff, listOf(current), zone, cutoff).map { it.machine.id })
    }

    @Test
    fun trainingCountCountsDaysRatherThanSetsAndUsesSixOClockBoundary() {
        val times = listOf("2026-01-02T05:00:00Z", "2026-01-02T05:30:00Z", "2026-01-02T06:30:00Z")
            .map { Instant.parse(it).toEpochMilli() }
        assertEquals(2, exerciseTrainingCount(exercise(1, BodyArea.CHEST, times = times), zone,
            Instant.parse("2026-01-03T12:00:00Z").toEpochMilli()))
    }

    @Test
    fun manualGroupsAndTheirArrowOrderOverrideAutomaticRanking() {
        val current = exercise(1, BodyArea.CHEST, times = listOf(cutoff + 1))
        val topA = manual(exercise(2, BodyArea.CHEST), ExerciseOrderGroup.MANUAL_TOP, 2)
        val topB = manual(exercise(3, BodyArea.CHEST), ExerciseOrderGroup.MANUAL_TOP, 1)
        val auto = exercise(4, BodyArea.QUADS)
        val bottom = manual(exercise(5, BodyArea.CALVES), ExerciseOrderGroup.MANUAL_BOTTOM, 0)
        assertEquals(listOf(1L, 3L, 2L, 4L, 5L), rankOverviewMachines(listOf(bottom, topA, auto, current, topB), cutoff, listOf(current), zone, cutoff).map { it.machine.id })
    }

    @Test
    fun alreadyDoneAutomaticExercisesFollowUnfinishedOnes() {
        val done = exercise(1, BodyArea.QUADS, times = listOf(cutoff + 1))
        val unfinished = exercise(2, BodyArea.CHEST)
        assertEquals(listOf(2L, 1L), rankOverviewMachines(listOf(done, unfinished), cutoff, emptyList(), zone, cutoff).map { it.machine.id })
    }

    @Test
    fun unfinishedManualBottomStaysAboveCompletedExercisesFromEveryGroup() {
        val bottom = manual(exercise(1, BodyArea.CHEST), ExerciseOrderGroup.MANUAL_BOTTOM, 0)
        val doneAuto = exercise(2, BodyArea.QUADS, times = listOf(cutoff + 1))
        val doneTop = manual(exercise(3, BodyArea.CALVES, times = listOf(cutoff + 2)), ExerciseOrderGroup.MANUAL_TOP, 0)
        val doneBottom = manual(exercise(4, BodyArea.BICEPS, times = listOf(cutoff + 3)), ExerciseOrderGroup.MANUAL_BOTTOM, 1)

        assertEquals(listOf(1L, 2L, 3L, 4L), rankOverviewMachines(
            listOf(doneBottom, doneTop, doneAuto, bottom), cutoff, emptyList(), zone, cutoff,
        ).map { it.machine.id })
    }

    @Test
    fun completedExercisesUseLastSetTimeAndIgnoreRankingPriority() {
        val first = exercise(1, BodyArea.CHEST, times = listOf(cutoff + 2))
        val resumed = exercise(2, BodyArea.QUADS, times = listOf(cutoff + 1, cutoff + 3))
        val current = exercise(3, BodyArea.BICEPS, times = listOf(cutoff + 4))

        assertEquals(listOf(3L, 1L, 2L), rankOverviewMachines(
            listOf(resumed, current, first), cutoff, listOf(current, resumed), zone, cutoff,
        ).map { it.machine.id })
    }

    @Test
    fun missingProfilesRemainUnknownButRankAsZeroOverlap() {
        val unknown = exercise(1, BodyArea.CHEST).copy(areas = emptyList())
        val known = exercise(2, BodyArea.CHEST)
        assertNull(exerciseOverlap(unknown, emptyList()).percent)
        assertNull(exerciseOverlap(known, listOf(unknown)).percent)
        assertEquals(listOf(1L, 2L), rankOverviewMachines(listOf(unknown, known), cutoff, emptyList(), zone, cutoff).map { it.machine.id })
        val current = exercise(3, BodyArea.CHEST, times = listOf(cutoff + 1))
        assertEquals(listOf(3L, 1L, 2L), rankOverviewMachines(listOf(known, unknown, current), cutoff, listOf(current), zone, cutoff).map { it.machine.id })
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
    fun olderExercisesContributeWithFixedDecayWithoutNormalization() {
        val candidate = exercise(10, BodyArea.CHEST)
        val unrelated = exercise(1, BodyArea.QUADS)
        val chest = exercise(2, BodyArea.CHEST)
        val earlierChest = exercise(3, BodyArea.CHEST)
        assertEquals(25, exerciseOverlap(candidate, listOf(unrelated, unrelated.copy(machine = unrelated.machine.copy(id = 4)), chest)).percent)
        assertEquals(50, exerciseOverlap(candidate, listOf(unrelated, chest)).percent)
        assertEquals(75, exerciseOverlap(candidate, listOf(unrelated, chest, earlierChest)).percent)
    }

    @Test
    fun fourBinsHaveSharedInclusiveBoundariesIncludingZeroAndFullOverlap() {
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3),
            listOf(0, 24, 25, 49, 50, 74, 75, 100).map { exerciseOverlapBin(it) })
        assertEquals(0, exerciseOverlapBin(null))
    }

    @Test
    fun sameBinUsesTrainingFrequencyRatherThanExactOverlap() {
        val context = exercise(10, BodyArea.CHEST)
        val lowerOverlap = exercise(1, BodyArea.CHEST, times = listOf(day, 2L * day)).copy(areas = listOf(
            AreaAssignment(BodyArea.CHEST, Strength.LIGHT), AreaAssignment(BodyArea.QUADS, Strength.FULL),
            AreaAssignment(BodyArea.CALVES, Strength.LIGHT),
        ))
        val higherOverlap = exercise(2, BodyArea.CHEST, times = listOf(day)).copy(areas = listOf(
            AreaAssignment(BodyArea.CHEST, Strength.LIGHT), AreaAssignment(BodyArea.QUADS, Strength.FULL),
        ))
        assertEquals(exerciseOverlapBin(exerciseOverlap(lowerOverlap, listOf(context)).percent),
            exerciseOverlapBin(exerciseOverlap(higherOverlap, listOf(context)).percent))
        assertEquals(listOf(2L, 1L), rankOverviewMachines(listOf(lowerOverlap, higherOverlap), cutoff, listOf(context), zone, cutoff).map { it.machine.id })
    }

    @Test
    fun trainingFrequencyOnlyCountsPastTwoCalendarMonths() {
        val now = Instant.parse("2026-10-31T12:00:00Z").toEpochMilli()
        val times = listOf("2026-08-30T12:00:00Z", "2026-08-31T05:59:00Z", "2026-08-31T06:00:00Z",
            "2026-10-01T12:00:00Z", "2026-10-01T13:00:00Z", "2026-11-01T12:00:00Z").map { Instant.parse(it).toEpochMilli() }
        assertEquals(2, exerciseTrainingCount(exercise(1, BodyArea.CHEST, times = times), zone, now))
        val oldOften = exercise(1, BodyArea.CHEST, times = times.take(2))
        val recentOnce = exercise(2, BodyArea.CHEST, times = listOf(times[3]))
        assertEquals(listOf(1L, 2L), rankOverviewMachines(listOf(recentOnce, oldOften), now, emptyList(), zone, now).map { it.machine.id })
    }

    @Test
    fun hiddenContextIsUsedWithoutAddingHiddenExercisesToVisibleList() {
        val hiddenCurrent = exercise(1, BodyArea.CHEST)
        val chest = exercise(2, BodyArea.CHEST)
        val legs = exercise(3, BodyArea.QUADS)
        assertEquals(listOf(3L, 2L), rankOverviewMachines(listOf(chest, legs), cutoff, listOf(hiddenCurrent), zone, cutoff).map { it.machine.id })
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
