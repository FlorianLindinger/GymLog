package com.gymlog.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlanAndTrainingActionsTest {
    @Test fun fatigueColorBoundariesPersistAndRoundTripBackupWithLegacyDefaults() {
        assertEquals(FatigueBands(), repo.loadFatigueBands())
        val bands = FatigueBands(10, 40, 80)
        repo.saveFatigueBands(bands)
        assertEquals(bands, GymRepository(ApplicationProvider.getApplicationContext<Context>()).loadFatigueBands())
        val backup = repo.exportJson()
        repo.saveFatigueBands(FatigueBands())
        repo.importJson(backup)
        assertEquals(bands, repo.loadFatigueBands())
        val legacy = org.json.JSONObject(backup)
        legacy.getJSONObject("settings").remove("fatigueBands")
        repo.importJson(legacy.toString())
        assertEquals(FatigueBands(), repo.loadFatigueBands())
        repo.saveFatigueBands(bands)
        repo.createFreshDatabase()
        assertEquals(FatigueBands(), repo.loadFatigueBands())
    }

    @Test fun customRegionPercentagesSurviveStoragePlanFilesDuplicationAndBackup() {
        val id = repo.addMachine("Abs", 40.0, TrackingMode.WEIGHT)
        repo.saveAreas(id, listOf(AreaAssignment(BodyArea.CORE, Strength.FULL, intensity = 1.5),
            AreaAssignment(BodyArea.SHOULDERS, Strength.LIGHT, intensity = 0.375)))
        fun checkValues() { assertEquals(listOf(1.5, 0.375), repo.loadMachines().single().areas.sortedBy { it.area.ordinal }.map { it.intensity }.sortedDescending()) }
        checkValues()
        val plan = repo.activeTrainingPlanId()
        val file = repo.exportTrainingPlan()
        repo.importTrainingPlan(file); checkValues()
        repo.setActiveTrainingPlan(repo.duplicateTrainingPlan(plan)); checkValues()
        val backup = repo.exportJson()
        repo.importJson(backup); checkValues()
    }
    private lateinit var repo: GymRepository
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-02T12:00:00Z")
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = GymRepository(context, GymClock(Clock.fixed(now, zone), zone))
    }
    @Test fun deleteLastTrainingUsesLatestRecordedDayNotTodayAndUndoRestoresExactlyThatPlan() {
        val plan = repo.activeTrainingPlanId()
        val machine = repo.addMachine("Bench", 40.0, TrackingMode.WEIGHT)
        val older = Instant.parse("2026-10-30T12:00:00Z").toEpochMilli()
        val last = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli()
        val earlyNextMorning = Instant.parse("2026-11-02T04:59:00Z").toEpochMilli()
        repo.addHistoricalSet(machine, 10, 40.0, older)
        repo.addHistoricalSet(machine, 8, 40.0, last)
        repo.addHistoricalSet(machine, 6, 40.0, earlyNextMorning)
        val otherPlan = repo.createTrainingPlan("Other")
        val other = repo.addMachine("Run", 0.0, TrackingMode.DURATION)
        repo.addHistoricalSet(other, 1, 20.0, now.toEpochMilli())
        repo.setActiveTrainingPlan(plan)
        val window = repo.lastTrainingWindow()!!
        assertTrue(last in window.startInclusive until window.endExclusive)
        assertTrue(earlyNextMorning in window.startInclusive until window.endExclusive)
        val journal = repo.deleteLastTrainingWithJournal(window)!!
        assertEquals(listOf(older), repo.loadMachines().single().recentSets.map { it.performedAt })
        repo.setActiveTrainingPlan(otherPlan)
        assertEquals(1, repo.loadMachines().single().recentSets.size)
        repo.restoreDeletion(journal)
        repo.setActiveTrainingPlan(plan)
        assertEquals(setOf(older, last, earlyNextMorning), repo.loadMachines().single().recentSets.map { it.performedAt }.toSet())
    }
    @Test fun emptyAndStaleDeletionRequestsCannotDeleteAnotherTraining() {
        assertNull(repo.lastTrainingWindow()); assertNull(repo.deleteLastTrainingWithJournal())
        val id = repo.addMachine("Bench", 40.0, TrackingMode.WEIGHT)
        repo.addHistoricalSet(id, 10, 40.0, now.minusSeconds(86400).toEpochMilli())
        val old = repo.lastTrainingWindow()!!
        repo.addHistoricalSet(id, 10, 40.0, now.toEpochMilli())
        assertNull(repo.deleteLastTrainingWithJournal(old))
        assertEquals(2, repo.loadMachines().single().recentSets.size)
    }
    @Test fun planFileRoundTripAddsNewPlanWithoutHistoryOrReplacingExistingData() {
        val original = repo.activeTrainingPlanId()
        repo.renameTrainingPlan(original, "My routine"); repo.saveTrainingPlanNotes(original, "Notes", true)
        val machine = repo.addMachine("Bike", 120.0, TrackingMode.WATTS, "Intervals", listOf(AreaAssignment(BodyArea.QUADS, Strength.FULL)))
        repo.changeRankingPriority(machine, -1)
        repo.addHistoricalSet(machine, 120, 20.0, now.toEpochMilli())
        val file = repo.exportTrainingPlan()
        assertFalse(file.contains("performedAt")); assertFalse(file.contains("weightChanges"))
        val imported = repo.importTrainingPlan(file)
        assertNotEquals(original, imported); assertEquals(2, repo.loadTrainingPlans().size)
        val copy = repo.loadMachines().single()
        assertEquals(TrackingMode.WATTS, copy.machine.trackingMode)
        assertEquals(120.0, copy.machine.currentWeight, 0.0); assertEquals(-1, copy.machine.rankingPriority)
        assertEquals(BodyArea.QUADS, copy.areas.single().area)
        assertTrue(copy.recentSets.isEmpty()); assertTrue(copy.weightChanges.isEmpty())
        repo.setActiveTrainingPlan(original); assertEquals(1, repo.loadMachines().single().recentSets.size)
        val before = repo.loadTrainingPlans()
        try { repo.importTrainingPlan(file.replace("WATTS", "INVALID")); fail("Invalid file accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repo.loadTrainingPlans())
        assertEquals(original, repo.activeTrainingPlanId())
    }
    @Test fun trainingWindowFollowsSixAmAndCalendarDaysAcrossDst() {
        val clock = GymClock(Clock.fixed(now, zone), zone)
        val window = clock.trainingWindow(Instant.parse("2026-10-24T12:00:00Z").toEpochMilli())
        assertEquals(25L * 3600000L, window.endExclusive - window.startInclusive)
    }
}
