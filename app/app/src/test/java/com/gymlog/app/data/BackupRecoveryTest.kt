package com.gymlog.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gymlog.app.ui.loadUiState
import com.gymlog.app.ui.loadUiStateResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupRecoveryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-02T12:00:00Z").toEpochMilli()
    private val clock = GymClock(Clock.fixed(Instant.ofEpochMilli(now), zone), zone)
    private lateinit var repo: GymRepository

    @Before fun setup() { repo = GymRepository(context, clock) }

    @Test fun invalidLateRowsLeaveWorkoutDataAndEveryPreferenceUnchanged() {
        val first = repo.addMachine("Bench", 40.0, TrackingMode.WEIGHT)
        val second = repo.addMachine("Other", 20.0, TrackingMode.WEIGHT)
        repo.addSets(first, listOf(10, 0, 8, 0, 0), 40.0)
        repo.addHistoricalSet(second, 10, 20.0, now)
        val before = repo.exportJson()
        val prefs = context.getSharedPreferences(GYMLOG_PREFERENCES, Context.MODE_PRIVATE)
        val beforePrefs = prefs.all.toMap()
        for (corruption in listOf("name", "area", "set", "wheels")) {
            val broken = JSONObject(before)
            broken.getJSONObject("settings").put("progressStaleDays", 99)
                .put("fatigueBands", JSONObject().put("yellowFrom", 10).put("orangeFrom", 40).put("redFrom", 80))
            val last = broken.getJSONArray("machines").getJSONObject(1)
            when (corruption) {
                "name" -> last.remove("name")
                "area" -> last.put("areas", JSONArray().put(JSONObject().put("area", "INVALID").put("strength", 1.0)))
                "set" -> last.getJSONArray("sets").getJSONObject(0).remove("weight")
                "wheels" -> last.put("setWheelValues", JSONArray(listOf(10, -1, 0, 0, 0)))
            }
            assertTrue("Must reject $corruption", runCatching { repo.importJson(broken.toString()) }.isFailure)
            assertEquals("Preferences changed after $corruption failure", beforePrefs, prefs.all)
            assertEquals("Database changed after $corruption failure", before, repo.exportJson())
        }
    }

    @Test fun backupRestoresTrendsAndWheelSlotsForRemappedAndPreservedIds() {
        val first = repo.addMachine("Bench", 40.0, TrackingMode.WEIGHT)
        val second = repo.addMachine("Other", 20.0, TrackingMode.WEIGHT)
        repo.addSets(first, listOf(10, 0, 8, 0, 0), 40.0)
        repo.addSets(second, listOf(6, 7, 0, 0, 0), 20.0)
        repo.setShowTrend(first, false)
        val backup = repo.exportJson()
        for (preserveIds in listOf(false, true)) {
            repo.importJson(backup, preserveIds)
            val restored = repo.loadMachines().associateBy { it.machine.name }
            val bench = restored.getValue("Bench")
            val other = restored.getValue("Other")
            assertFalse(bench.machine.showTrend)
            assertTrue(other.machine.showTrend)
            assertEquals(listOf(10, 0, 8, 0, 0), bench.setWheelValues)
            assertEquals(listOf(6, 7, 0, 0, 0), other.setWheelValues)
            if (preserveIds) assertEquals(first, bench.machine.id) else assertNotEquals(first, bench.machine.id)
        }
        val legacy = JSONObject(backup)
        for (index in 0 until legacy.getJSONArray("machines").length()) {
            legacy.getJSONArray("machines").getJSONObject(index).apply {
                remove("showTrend"); remove("setWheelValues")
            }
        }
        repo.importJson(legacy.toString(), preserveIds = true)
        assertTrue(repo.loadMachines().all { it.machine.showTrend && it.setWheelValues == null })
    }

    @Test fun unsupportedUpgradePreservesRowsAndVersionAndReturnsRecoveryError() {
        context.openOrCreateDatabase(GYMLOG_DATABASE, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL("CREATE TABLE machines(id INTEGER PRIMARY KEY,name TEXT)")
            database.execSQL("INSERT INTO machines VALUES(7,'Keep this exercise')")
            database.execSQL("CREATE TABLE sets(id INTEGER PRIMARY KEY,machine_id INTEGER,reps INTEGER)")
            database.execSQL("INSERT INTO sets VALUES(12,7,10)")
            database.version = 8
        }
        val result = repo.loadUiStateResult()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("preserved"))
        context.openOrCreateDatabase(GYMLOG_DATABASE, Context.MODE_PRIVATE, null).use { database ->
            assertEquals(8, database.version)
            database.rawQuery("SELECT name FROM machines WHERE id=7", null).use {
                assertTrue(it.moveToFirst()); assertEquals("Keep this exercise", it.getString(0))
            }
            database.rawQuery("SELECT reps FROM sets WHERE id=12", null).use {
                assertTrue(it.moveToFirst()); assertEquals(10, it.getInt(0))
            }
        }
    }

    @Test fun supportedVersionNineMigrationPreservesRowsAndPinnedOrdering() {
        context.openOrCreateDatabase(GYMLOG_DATABASE, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL("CREATE TABLE machines(id INTEGER PRIMARY KEY,name TEXT,pinned INTEGER)")
            database.execSQL("INSERT INTO machines VALUES(7,'Keep this exercise',1)")
            database.version = 9
        }
        val helper = GymDatabase(context)
        try {
            val database = helper.writableDatabase
            assertEquals(11, database.version)
            database.rawQuery("SELECT name,order_group,show_trend,progression_settings FROM machines WHERE id=7", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("Keep this exercise", it.getString(0))
                assertEquals("MANUAL_TOP", it.getString(1))
                assertEquals(1, it.getInt(2))
                assertTrue(it.isNull(3))
            }
        } finally {
            helper.close()
        }
    }

    @Test fun hiddenExercisesFromOtherPlansAffectFatigueButDoNotEnterTheActiveList() {
        val core = listOf(AreaAssignment(BodyArea.CORE, Strength.FULL))
        val original = repo.addMachine("Abs A", 40.0, TrackingMode.WEIGHT, assignments = core)
        repo.setMachineHidden(original, true)
        repo.addHistoricalSet(original, 10, 40.0, now - 1000)
        repo.addHistoricalSet(original, 10, 40.0, now - 86_400_000)
        repo.addHistoricalSet(original, 10, 40.0, now - 4 * 86_400_000L)
        repo.addHistoricalSet(original, 10, 40.0, now + 1000)
        repo.createTrainingPlan("Other plan")
        val abs = repo.addMachine("Abs B", 40.0, TrackingMode.WEIGHT, assignments = core)
        val legs = repo.addMachine("Legs", 40.0, TrackingMode.WEIGHT,
            assignments = listOf(AreaAssignment(BodyArea.QUADS, Strength.FULL)))
        repo.changeRankingPriority(abs, -20)
        val state = repo.loadUiState()
        assertEquals(setOf(abs, legs), state.machines.map { it.machine.id }.toSet())
        assertEquals(listOf(now - 1000, now - 86_400_000), state.fatigueExercises.single().recentSets.map { it.performedAt })
        val recent = recentTrainingExercises(state.fatigueExercises, clock.trainingDayStart())
        val prior = priorExerciseExposures(state.fatigueExercises, now, zone)
        val candidate = state.machines.first { it.machine.id == abs }
        assertEquals(100, exerciseOverlap(candidate, recent, prior).percent)
        assertEquals(listOf(legs, abs), rankOverviewMachines(state.machines, clock.trainingDayStart(), recent,
            zone, now, prior, state.progressionSettings, state.fatigueBands).map { it.machine.id })
        assertEquals(30, exerciseOverlap(candidate, emptyList(), prior).percent)
    }
}
