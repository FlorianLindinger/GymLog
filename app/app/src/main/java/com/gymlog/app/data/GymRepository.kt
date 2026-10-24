package com.gymlog.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class GymRepository(context: Context, private val gymClock: GymClock = GymClock()) {
    private val appContext = context.applicationContext
    private var db = GymDatabase(appContext)
    private val preferences = appContext.getSharedPreferences(GYMLOG_PREFERENCES, Context.MODE_PRIVATE)
    private val exerciseReads by lazy { ExerciseReadRepository({ db.readableDatabase }, gymClock::now, ::loadSetWheelValues) }

    fun createFreshDatabase() {
        runCatching { db.close() }
        check(appContext.deleteDatabase(GYMLOG_DATABASE) || !appContext.getDatabasePath(GYMLOG_DATABASE).exists()) {
            "The old GymLog database could not be removed"
        }
        check(
            preferences.edit()
                .remove("active_training_plan")
                .remove("progress_stale_weeks")
                .remove("progress_stale_trainings")
                .remove("progress_volume_minimum_percent")
                .commit(),
        ) { "GymLog settings could not be reset" }
        db = GymDatabase(appContext)
        db.writableDatabase
    }

    fun automaticBackupEnabled(): Boolean = automaticBackupEnabled(appContext)

    fun backupSyncStatus(): BackupSyncStatus {
        val enabled = automaticBackupEnabled()
        val lastBackup = preferences.getLong(LAST_AUTOMATIC_BACKUP_AT, 0L).takeIf { it > 0L }
        val databaseChangedAt = listOf(
            appContext.getDatabasePath(GYMLOG_DATABASE),
            appContext.getDatabasePath("$GYMLOG_DATABASE-wal"),
        ).maxOfOrNull { it.lastModified() } ?: 0L
        return BackupSyncStatus(enabled, lastBackup, lastBackup == null || databaseChangedAt > lastBackup)
    }

    fun accentColor(): Long {
        if (preferences.contains("accent_color_argb")) return preferences.getLong("accent_color_argb", DEFAULT_ACCENT_COLOR)
        return when (preferences.getString("accent_color", null)) {
            "RED" -> 0xFFEF5350
            else -> DEFAULT_ACCENT_COLOR
        }
    }

    fun setAccentColor(color: Long) {
        preferences.edit().putLong("accent_color_argb", color).remove("accent_color").apply()
    }

    fun setAutomaticBackupEnabled(enabled: Boolean) {
        setAutomaticBackupEnabled(appContext, enabled)
    }

    fun loadProgressionSettings(): ProgressionSettings = ProgressionSettings(
        staleWeeks = preferences.getInt("progress_stale_weeks", 4),
        staleTrainings = preferences.getInt("progress_stale_trainings", 5),
        volumeMinimumIncreasePercent = preferences.getInt("progress_volume_minimum_percent", 5),
    )

    fun saveProgressionSettings(settings: ProgressionSettings) {
        preferences.edit()
            .putInt("progress_stale_weeks", settings.staleWeeks.coerceAtLeast(0))
            .putInt("progress_stale_trainings", settings.staleTrainings.coerceAtLeast(0))
            .putInt("progress_volume_minimum_percent", settings.volumeMinimumIncreasePercent.coerceAtLeast(0))
            .apply()
    }

    fun loadTrainingPlans(): List<TrainingPlan> {
        val database = db.writableDatabase
        ensureTrainingPlan(database)
        val result = mutableListOf<TrainingPlan>()
        database.rawQuery("SELECT id,name,sort_order,notes,notes_pinned FROM training_plans ORDER BY sort_order,id", null).use { cursor ->
            while (cursor.moveToNext()) result += TrainingPlan(
                cursor.getLong(0),
                cursor.getString(1),
                cursor.getInt(2),
                cursor.getString(3),
                cursor.getInt(4) != 0,
            )
        }
        return result
    }

    fun activeTrainingPlanId(): Long = ensureTrainingPlan(db.writableDatabase)

    fun setActiveTrainingPlan(id: Long) {
        val exists = db.readableDatabase.rawQuery("SELECT 1 FROM training_plans WHERE id=?", arrayOf(id.toString()))
            .use { it.moveToFirst() }
        if (exists) preferences.edit().putLong("active_training_plan", id).apply()
    }

    fun createTrainingPlan(name: String): Long {
        val database = db.writableDatabase
        val nextOrder = database.rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM training_plans", null)
            .use { it.moveToFirst(); it.getInt(0) }
        val id = database.insertOrThrow("training_plans", null, ContentValues().apply {
            put("name", uniquePlanName(database, name))
            put("sort_order", nextOrder)
        })
        setActiveTrainingPlan(id)
        return id
    }

    fun createDemoTrainingPlan(): Long {
        data class DemoExercise(
            val name: String,
            val title: String,
            val weight: Double,
            val areas: List<Pair<BodyArea, Strength>>,
        )
        val exercises = listOf(
            DemoExercise("Lat Pulldown", "Pull", 67.5, listOf(BodyArea.UPPER_BACK to Strength.FULL, BodyArea.BICEPS to Strength.HALF)),
            DemoExercise("Leg Press", "Legs", 150.0, listOf(BodyArea.QUADS to Strength.FULL, BodyArea.GLUTES to Strength.HALF)),
            DemoExercise("Bench Press", "Push", 72.5, listOf(BodyArea.CHEST to Strength.FULL, BodyArea.TRICEPS to Strength.HALF, BodyArea.SHOULDERS to Strength.HALF)),
            DemoExercise("Cable Crunch", "Core", 45.0, listOf(BodyArea.CORE to Strength.FULL)),
            DemoExercise("Shoulder Press", "Push", 42.5, listOf(BodyArea.SHOULDERS to Strength.FULL, BodyArea.TRICEPS to Strength.HALF)),
            DemoExercise("Chest Fly", "Push", 55.0, listOf(BodyArea.CHEST to Strength.FULL, BodyArea.SHOULDERS to Strength.HALF)),
        )
        val database = db.writableDatabase
        val now = gymClock.now()
        val nextOrder = database.rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM training_plans", null)
            .use { it.moveToFirst(); it.getInt(0) }
        val planId = database.transaction {
            val newPlanId = database.insertOrThrow("training_plans", null, ContentValues().apply {
                put("name", uniquePlanName(database, "Demo - Halfway Training"))
                put("sort_order", nextOrder)
                put("notes", "Demo data: three of six exercises are complete in the active training.")
                put("notes_pinned", 1)
            })
            exercises.forEachIndexed { index, exercise ->
                val machineId = database.insertOrThrow("machines", null, ContentValues().apply {
                    put("plan_id", newPlanId)
                    put("name", exercise.name)
                    put("title_note", exercise.title)
                    put("notes", "Sample exercise generated for UI and workflow testing.")
                    put("pause_time", "90 sec")
                    put("chart_window", ChartRange.THREE_MONTHS.storageValue)
                    put("trailing_average", 3)
                    put("tracking_mode", TrackingMode.WEIGHT.name)
                    put("current_weight", exercise.weight)
                    put("sort_order", index)
                    put("pinned", if (index == 0) 1 else 0)
                })
                exercise.areas.forEach { (area, strength) ->
                    database.insertOrThrow("machine_areas", null, ContentValues().apply {
                        put("machine_id", machineId)
                        put("area", area.name)
                        put("strength", strength.value)
                    })
                }
                val startWeight = exercise.weight - 10.0
                addWeightHistory(database, machineId, startWeight, now - 11L * 7L * 24L * 60L * 60L * 1000L)
                addWeightHistory(database, machineId, exercise.weight - 5.0, now - 5L * 7L * 24L * 60L * 60L * 1000L)
                addWeightHistory(database, machineId, exercise.weight, now - 8L * 24L * 60L * 60L * 1000L)
                repeat(10) { sessionIndex ->
                    val sessionTime = now - (10L - sessionIndex) * 7L * 24L * 60L * 60L * 1000L + index * 12L * 60L * 1000L
                    val sessionWeight = startWeight + (sessionIndex / 3) * 2.5
                    listOf(10, 10, 8).forEachIndexed { setIndex, reps ->
                        database.insertOrThrow("sets", null, ContentValues().apply {
                            put("machine_id", machineId)
                            put("reps", reps + if (sessionIndex % 3 == 2 && setIndex == 0) 1 else 0)
                            put("weight", sessionWeight)
                            put("performed_at", sessionTime + setIndex * 25L)
                        })
                    }
                }
                if (index < exercises.size / 2) {
                    val currentTime = now - (55L - index * 20L) * 60L * 1000L
                    listOf(10, 10, 9).forEachIndexed { setIndex, reps ->
                        database.insertOrThrow("sets", null, ContentValues().apply {
                            put("machine_id", machineId)
                            put("reps", reps)
                            put("weight", exercise.weight)
                            put("performed_at", currentTime + setIndex * 25L)
                        })
                    }
                }
            }
            newPlanId
        }
        setActiveTrainingPlan(planId)
        return planId
    }

    fun duplicateTrainingPlan(id: Long): Long {
        val database = db.writableDatabase
        val source = loadTrainingPlans().firstOrNull { it.id == id } ?: error("Training plan does not exist")
        val nextOrder = database.rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM training_plans", null)
            .use { it.moveToFirst(); it.getInt(0) }
        return database.transaction {
            val newPlanId = database.insertOrThrow("training_plans", null, ContentValues().apply {
                put("name", uniquePlanName(database, "${source.name} - Copy"))
                put("sort_order", nextOrder)
                put("notes", source.notes)
                put("notes_pinned", if (source.notesPinned) 1 else 0)
            })
            loadMachinesForPlan(id).forEach { summary ->
                val machine = summary.machine
                val newMachineId = database.insertOrThrow("machines", null, ContentValues().apply {
                    put("plan_id", newPlanId)
                    put("name", machine.name)
                    put("title_note", machine.titleNote)
                    put("notes", machine.notes)
                    put("pause_time", machine.pauseTime)
                    put("chart_window", machine.chartWindow.storageValue)
                    put("trailing_average", machine.trailingAverage)
                    put("tracking_mode", machine.trackingMode.name)
                    put("current_weight", machine.currentWeight)
                    put("sort_order", machine.sortOrder)
                    put("pinned", if (machine.pinned) 1 else 0)
                    put("order_group", machine.orderGroup.name)
                    put("ranking_priority", machine.rankingPriority)
                    put("show_trend", if (machine.showTrend) 1 else 0)
                })
                summary.areas.forEach { assignment ->
                    database.insertOrThrow("machine_areas", null, ContentValues().apply {
                        put("machine_id", newMachineId)
                        put("area", assignment.area.name)
                        put("strength", assignment.strength.value)
                    })
                }
                addWeightHistory(database, newMachineId, machine.currentWeight, gymClock.now())
            }
            newPlanId
        }
    }

    fun renameTrainingPlan(id: Long, name: String) {
        val database = db.writableDatabase
        database.update("training_plans", ContentValues().apply { put("name", uniquePlanName(database, name, id)) }, "id=?", arrayOf(id.toString()))
    }

    fun saveTrainingPlanNotes(id: Long, notes: String, pinned: Boolean) {
        db.writableDatabase.update(
            "training_plans",
            ContentValues().apply {
                put("notes", notes.trim())
                put("notes_pinned", if (pinned) 1 else 0)
            },
            "id=?",
            arrayOf(id.toString()),
        )
    }

    fun deleteTrainingPlan(id: Long): Boolean {
        val database = db.writableDatabase
        val plans = loadTrainingPlans()
        if (plans.size <= 1 || plans.none { it.id == id }) return false
        database.transaction {
            database.delete("machines", "plan_id=?", arrayOf(id.toString()))
            database.delete("training_plans", "id=?", arrayOf(id.toString()))
        }
        if (preferences.getLong("active_training_plan", -1L) == id) {
            preferences.edit().putLong("active_training_plan", plans.first { it.id != id }.id).apply()
        }
        return true
    }

    fun loadMachines(): List<MachineSummary> = loadMachinesForPlan(ensureTrainingPlan(db.readableDatabase))

    private fun loadMachinesForPlan(planId: Long): List<MachineSummary> {
        migrateLegacyMachineState()
        return exerciseReads.loadForPlan(planId)
    }
    private fun migrateLegacyMachineState() {
        if (preferences.getBoolean("machine_state_in_sqlite", false)) return
        val database = db.writableDatabase
        database.transaction {
            database.rawQuery("SELECT id,pinned FROM machines", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val values = ContentValues()
                    val excluded = preferences.getBoolean("ranking_excluded_$id", false)
                    values.put("order_group", when {
                        cursor.getInt(1) != 0 -> ExerciseOrderGroup.MANUAL_TOP.name
                        excluded -> ExerciseOrderGroup.MANUAL_BOTTOM.name
                        else -> ExerciseOrderGroup.AUTOMATIC.name
                    })
                    values.put("ranking_priority", preferences.getInt("ranking_priority_$id", 0))
                    values.put("show_trend", if (preferences.getBoolean("show_trend_$id", true)) 1 else 0)
                    values.put("hidden", if (preferences.getBoolean("hidden_machine_$id", false)) 1 else 0)
                    database.update("machines", values, "id=?", arrayOf(id.toString()))
                }
            }
        }
        check(preferences.edit().putBoolean("machine_state_in_sqlite", true).commit()) { "Could not migrate exercise settings" }
    }
    fun addMachine(
        name: String,
        weight: Double,
        trackingMode: TrackingMode,
        titleNote: String = "",
        assignments: List<AreaAssignment> = emptyList(),
    ): Long {
        val database = db.writableDatabase
        val planId = ensureTrainingPlan(database)
        return database.transaction {
            val nextOrder = database.rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM machines WHERE plan_id=?", arrayOf(planId.toString()))
                .use { it.moveToFirst(); it.getInt(0) }
            val id = database.insertOrThrow("machines", null, ContentValues().apply {
                put("name", capitalizeName(name))
                put("plan_id", planId)
                put("notes", "")
                put("title_note", titleNote.trim())
                put("tracking_mode", trackingMode.name)
                put("current_weight", weight)
                put("sort_order", nextOrder)
            })
            addWeightHistory(database, id, weight, gymClock.now())
            assignments.forEach { assignment ->
                database.insertOrThrow("machine_areas", null, ContentValues().apply {
                    put("machine_id", id)
                    put("area", assignment.area.name)
                    put("strength", assignment.strength.value)
                })
            }
            id
        }
    }

    fun updateMachine(id: Long, name: String, titleNote: String, notes: String, pauseTime: String) {
        db.writableDatabase.update("machines", ContentValues().apply {
            put("name", capitalizeName(name))
            put("title_note", titleNote.trim())
            put("notes", notes.trim())
            put("pause_time", pauseTime.trim())
        }, "id=?", arrayOf(id.toString()))
    }

    fun renameMachine(id: Long, name: String) {
        db.writableDatabase.update("machines", ContentValues().apply { put("name", capitalizeName(name)) }, "id=?", arrayOf(id.toString()))
    }

    fun setMachineTitleNote(id: Long, note: String) {
        db.writableDatabase.update("machines", ContentValues().apply { put("title_note", note.trim()) }, "id=?", arrayOf(id.toString()))
    }

    fun setMachineNotes(id: Long, notes: String) {
        db.writableDatabase.update("machines", ContentValues().apply { put("notes", notes.trim()) }, "id=?", arrayOf(id.toString()))
    }

    fun isMachineHidden(id: Long): Boolean = db.readableDatabase.rawQuery(
        "SELECT hidden FROM machines WHERE id=?", arrayOf(id.toString()),
    ).use { it.moveToFirst() && it.getInt(0) != 0 }

    fun setMachineHidden(id: Long, hidden: Boolean) {
        db.writableDatabase.update("machines", ContentValues().apply { put("hidden", if (hidden) 1 else 0) }, "id=?", arrayOf(id.toString()))
    }

    fun showHiddenExercises(): Boolean = preferences.getBoolean("show_hidden_exercises", false)

    fun setShowHiddenExercises(show: Boolean) {
        preferences.edit().putBoolean("show_hidden_exercises", show).apply()
    }

    fun setExerciseOrderGroup(id: Long, group: ExerciseOrderGroup) {
        db.writableDatabase.update("machines", ContentValues().apply {
            put("order_group", group.name)
            put("pinned", if (group == ExerciseOrderGroup.MANUAL_TOP) 1 else 0)
        }, "id=?", arrayOf(id.toString()))
    }

    fun setChartWindow(id: Long, window: String) {
        db.writableDatabase.update("machines", ContentValues().apply {
            put("chart_window", ChartRange.fromStorage(window).storageValue)
        }, "id=?", arrayOf(id.toString()))
    }

    fun setTrailingAverage(id: Long, amount: Int) {
        db.writableDatabase.update("machines", ContentValues().apply { put("trailing_average", amount) }, "id=?", arrayOf(id.toString()))
    }

    fun changeRankingPriority(id: Long, delta: Int) {
        db.writableDatabase.execSQL("UPDATE machines SET ranking_priority=MIN(20,MAX(-20,ranking_priority + ?)) WHERE id=?", arrayOf(delta, id))
    }

    fun setShowTrend(id: Long, show: Boolean) {
        db.writableDatabase.update("machines", ContentValues().apply { put("show_trend", if (show) 1 else 0) }, "id=?", arrayOf(id.toString()))
    }    fun setAreaColor(area: BodyArea, color: Long) {
        db.writableDatabase.insertWithOnConflict("area_colors", null, ContentValues().apply {
            put("area", area.name)
            put("color", color)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadAreaColors(): Map<BodyArea, Long> = BodyArea.entries
        .associateWith { areaColor(db.readableDatabase, it) }

    fun loadHistory(): List<HistorySet> {
        val result = mutableListOf<HistorySet>()
        val planId = ensureTrainingPlan(db.readableDatabase)
        db.readableDatabase.rawQuery(
            "SELECT s.id,s.machine_id,m.name,m.tracking_mode,s.reps,s.weight,s.performed_at FROM sets s JOIN machines m ON m.id=s.machine_id WHERE m.plan_id=? ORDER BY s.performed_at DESC,s.id DESC",
            arrayOf(planId.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += HistorySet(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), TrackingMode.fromStorage(cursor.getString(3)), cursor.getInt(4), cursor.getDouble(5), cursor.getLong(6))
            }
        }
        return result
    }

    fun changeWeight(id: Long, weight: Double) {
        val database = db.writableDatabase
        val current = database.rawQuery(
            "SELECT current_weight FROM machines WHERE id=?",
            arrayOf(id.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getDouble(0) else return }
        if (current == weight) return
        database.transaction {
            database.update("machines", ContentValues().apply { put("current_weight", weight) }, "id=?", arrayOf(id.toString()))
            addWeightHistory(database, id, weight, gymClock.now())
        }
    }

    fun changeMetricForCurrentTrainingUnit(id: Long, value: Double) {
        val database = db.writableDatabase
        val now = gymClock.now()
        database.transaction {
            val machine = database.rawQuery(
                "SELECT tracking_mode,current_weight FROM machines WHERE id=?",
                arrayOf(id.toString()),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) to cursor.getDouble(1) else null }
                ?: return@transaction
            val (trackingMode, currentValue) = machine
            if (currentValue == value) return@transaction
            database.update("machines", ContentValues().apply { put("current_weight", value) }, "id=?", arrayOf(id.toString()))
            addWeightHistory(database, id, value, now)
            val setValues = ContentValues().apply {
                if (trackingMode == TrackingMode.STRENGTH.name) put("reps", value.toInt().coerceAtLeast(1)) else put("weight", value)
            }
            database.update(
                "sets",
                setValues,
                "machine_id=? AND performed_at>=?",
                arrayOf(id.toString(), trainingDayStart(now).toString()),
            )
        }
    }

    fun addSet(machineId: Long, reps: Int, weight: Double) {
        db.writableDatabase.insertOrThrow("sets", null, ContentValues().apply {
            put("machine_id", machineId)
            put("reps", reps)
            put("weight", weight)
            put("performed_at", gymClock.now())
        })
    }

    fun addHistoricalSet(machineId: Long, reps: Int, weight: Double, performedAt: Long) {
        db.writableDatabase.insertOrThrow("sets", null, ContentValues().apply {
            put("machine_id", machineId)
            put("reps", reps.coerceAtLeast(1))
            put("weight", weight.coerceAtLeast(0.0))
            put("performed_at", performedAt)
        })
    }

    fun addTrainingMinutes(machineId: Long, minutes: Int) {
        require(minutes > 0) { "Training duration must be greater than zero" }
        val database = db.writableDatabase
        val recordedLevel = database.rawQuery(
            "SELECT tracking_mode,current_weight FROM machines WHERE id=?",
            arrayOf(machineId.toString()),
        ).use { cursor ->
            require(cursor.moveToFirst()) { "Exercise does not exist" }
            if (TrackingMode.fromStorage(cursor.getString(0)) == TrackingMode.STRENGTH) cursor.getDouble(1).roundToInt().coerceAtLeast(1) else 1
        }
        val now = gymClock.now()
        database.transaction {
            database.delete(
                "sets",
                "machine_id=? AND performed_at>=?",
                arrayOf(machineId.toString(), trainingDayStart(now).toString()),
            )
            database.insertOrThrow("sets", null, ContentValues().apply {
                put("machine_id", machineId)
                put("reps", recordedLevel)
                put("weight", minutes.toDouble())
                put("performed_at", now)
            })
        }
    }

    fun addSets(machineId: Long, reps: List<Int>, weight: Double) {
        val database = db.writableDatabase
        val now = gymClock.now()
        database.transaction {
            database.delete(
                "sets",
                "machine_id=? AND performed_at>=?",
                arrayOf(machineId.toString(), trainingDayStart(now).toString()),
            )
            reps.filter { it > 0 }.forEachIndexed { index, amount ->
                database.insertOrThrow("sets", null, ContentValues().apply {
                    put("machine_id", machineId)
                    put("reps", amount)
                    put("weight", weight)
                    put("performed_at", now + index)
                })
            }
        }
        preferences.edit().putString(setWheelPreference(machineId), reps.take(5).joinToString(",")).apply()
    }

    private fun loadSetWheelValues(machineId: Long): List<Int>? = preferences
        .getString(setWheelPreference(machineId), null)
        ?.split(',')
        ?.mapNotNull(String::toIntOrNull)
        ?.takeIf { it.size == 5 }

    private fun setWheelPreference(machineId: Long): String = "set_wheels_$machineId"

    fun deleteSetWithJournal(id: Long): DeletionJournal? = deleteSetsWithJournal(listOf(id))

    fun deleteSetsWithJournal(ids: List<Long>): DeletionJournal? {
        if (ids.isEmpty()) return null
        val placeholders = ids.joinToString(",") { "?" }
        val args = ids.map(Long::toString).toTypedArray()
        val database = db.writableDatabase
        val journal = setsJournal(database, "id IN ($placeholders)", args) ?: return null
        database.delete("sets", "id IN ($placeholders)", args)
        return journal
    }

    fun deleteExerciseDayWithJournal(machineId: Long, startInclusive: Long, endExclusive: Long): DeletionJournal? {
        val database = db.writableDatabase
        val args = arrayOf(machineId.toString(), startInclusive.toString(), endExclusive.toString())
        val journal = setsJournal(database, "machine_id=? AND performed_at>=? AND performed_at<?", args) ?: return null
        database.delete("sets", "machine_id=? AND performed_at>=? AND performed_at<?", args)
        return journal
    }

    fun deleteTrainingDayWithJournal(startInclusive: Long, endExclusive: Long): DeletionJournal? {
        val planId = activeTrainingPlanId()
        val database = db.writableDatabase
        val args = arrayOf(startInclusive.toString(), endExclusive.toString(), planId.toString())
        val where = "performed_at>=? AND performed_at<? AND machine_id IN (SELECT id FROM machines WHERE plan_id=?)"
        val journal = setsJournal(database, where, args) ?: return null
        database.delete("sets", where, args)
        return journal
    }

    fun deleteCurrentExerciseSetsWithJournal(machineId: Long): DeletionJournal? {
        val database = db.writableDatabase
        val args = arrayOf(machineId.toString(), trainingDayStart(gymClock.now()).toString())
        val journal = setsJournal(database, "machine_id=? AND performed_at>=?", args) ?: return null
        database.delete("sets", "machine_id=? AND performed_at>=?", args)
        return journal
    }

    fun deleteCurrentTrainingWithJournal(): DeletionJournal? {
        val planId = activeTrainingPlanId()
        val database = db.writableDatabase
        val args = arrayOf(trainingDayStart(gymClock.now()).toString(), planId.toString())
        val where = "performed_at>=? AND machine_id IN (SELECT id FROM machines WHERE plan_id=?)"
        val journal = setsJournal(database, where, args) ?: return null
        database.delete("sets", where, args)
        return journal
    }

    fun deleteMachineWithJournal(id: Long): DeletionJournal? {
        val database = db.writableDatabase
        val machine = machineJournalObject(database, id) ?: return null
        database.delete("machines", "id=?", arrayOf(id.toString()))
        return DeletionJournal(JSONObject().put("kind", "machine").put("machine", machine).toString())
    }

    fun deleteTrainingPlanWithJournal(id: Long): DeletionJournal? {
        val database = db.writableDatabase
        val plans = loadTrainingPlans()
        val plan = plans.firstOrNull { it.id == id } ?: return null
        if (plans.size <= 1) return null
        val machines = JSONArray()
        database.rawQuery("SELECT id FROM machines WHERE plan_id=? ORDER BY id", arrayOf(id.toString())).use { cursor ->
            while (cursor.moveToNext()) machineJournalObject(database, cursor.getLong(0))?.let(machines::put)
        }
        database.transaction {
            database.delete("machines", "plan_id=?", arrayOf(id.toString()))
            database.delete("training_plans", "id=?", arrayOf(id.toString()))
        }
        if (preferences.getLong("active_training_plan", -1L) == id) {
            preferences.edit().putLong("active_training_plan", plans.first { it.id != id }.id).apply()
        }
        return DeletionJournal(JSONObject().put("kind", "plan").put("plan", planJournalObject(plan)).put("machines", machines).toString())
    }

    fun restoreDeletion(journal: DeletionJournal) {
        val root = JSONObject(journal.payload)
        val database = db.writableDatabase
        database.transaction {
            when (root.getString("kind")) {
                "sets" -> restoreSets(database, root.getJSONArray("sets"))
                "machine" -> restoreMachine(database, root.getJSONObject("machine"))
                "plan" -> {
                    val plan = root.getJSONObject("plan")
                    database.insertOrThrow("training_plans", null, ContentValues().apply {
                        put("id", plan.getLong("id")); put("name", plan.getString("name")); put("sort_order", plan.getInt("sortOrder")); put("notes", plan.getString("notes")); put("notes_pinned", if (plan.getBoolean("notesPinned")) 1 else 0)
                    })
                    val machines = root.getJSONArray("machines")
                    for (index in 0 until machines.length()) restoreMachine(database, machines.getJSONObject(index))
                }
            }
        }
    }

    private fun setsJournal(database: SQLiteDatabase, where: String, args: Array<String>): DeletionJournal? {
        val sets = JSONArray()
        database.rawQuery("SELECT id,machine_id,reps,weight,performed_at FROM sets WHERE $where", args).use { cursor ->
            while (cursor.moveToNext()) sets.put(JSONObject().put("id", cursor.getLong(0)).put("machineId", cursor.getLong(1)).put("reps", cursor.getInt(2)).put("weight", cursor.getDouble(3)).put("performedAt", cursor.getLong(4)))
        }
        return if (sets.length() == 0) null else DeletionJournal(JSONObject().put("kind", "sets").put("sets", sets).toString())
    }

    private fun restoreSets(database: SQLiteDatabase, sets: JSONArray) {
        for (index in 0 until sets.length()) {
            val set = sets.getJSONObject(index)
            database.insertOrThrow("sets", null, ContentValues().apply {
                put("id", set.getLong("id")); put("machine_id", set.getLong("machineId")); put("reps", set.getInt("reps")); put("weight", set.getDouble("weight")); put("performed_at", set.getLong("performedAt"))
            })
        }
    }

    private fun planJournalObject(plan: TrainingPlan): JSONObject = JSONObject().put("id", plan.id).put("name", plan.name).put("sortOrder", plan.sortOrder).put("notes", plan.notes).put("notesPinned", plan.notesPinned)

    private fun machineJournalObject(database: SQLiteDatabase, id: Long): JSONObject? {
        val machine = database.rawQuery("SELECT id,plan_id,name,title_note,notes,pause_time,chart_window,trailing_average,tracking_mode,current_weight,sort_order,pinned,order_group,ranking_priority,show_trend,hidden FROM machines WHERE id=?", arrayOf(id.toString())).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            JSONObject().put("id", cursor.getLong(0)).put("planId", cursor.getLong(1)).put("name", cursor.getString(2)).put("titleNote", cursor.getString(3)).put("notes", cursor.getString(4)).put("pauseTime", cursor.getString(5)).put("chartWindow", cursor.getString(6)).put("trailingAverage", cursor.getInt(7)).put("trackingMode", cursor.getString(8)).put("currentWeight", cursor.getDouble(9)).put("sortOrder", cursor.getInt(10)).put("pinned", cursor.getInt(11)).put("orderGroup", cursor.getString(12)).put("rankingPriority", cursor.getInt(13)).put("showTrend", cursor.getInt(14)).put("hidden", cursor.getInt(15))
        } ?: return null
        machine.put("areas", JSONArray()).put("sets", JSONArray()).put("weights", JSONArray())
        database.rawQuery("SELECT area,strength FROM machine_areas WHERE machine_id=?", arrayOf(id.toString())).use { cursor -> while (cursor.moveToNext()) machine.getJSONArray("areas").put(JSONObject().put("area", cursor.getString(0)).put("strength", cursor.getDouble(1))) }
        database.rawQuery("SELECT id,reps,weight,performed_at FROM sets WHERE machine_id=?", arrayOf(id.toString())).use { cursor -> while (cursor.moveToNext()) machine.getJSONArray("sets").put(JSONObject().put("id", cursor.getLong(0)).put("reps", cursor.getInt(1)).put("weight", cursor.getDouble(2)).put("performedAt", cursor.getLong(3))) }
        database.rawQuery("SELECT id,weight,changed_at FROM weight_history WHERE machine_id=?", arrayOf(id.toString())).use { cursor -> while (cursor.moveToNext()) machine.getJSONArray("weights").put(JSONObject().put("id", cursor.getLong(0)).put("weight", cursor.getDouble(1)).put("changedAt", cursor.getLong(2))) }
        return machine
    }

    private fun restoreMachine(database: SQLiteDatabase, machine: JSONObject) {
        database.insertOrThrow("machines", null, ContentValues().apply {
            put("id", machine.getLong("id")); put("plan_id", machine.getLong("planId")); put("name", machine.getString("name")); put("title_note", machine.getString("titleNote")); put("notes", machine.getString("notes")); put("pause_time", machine.getString("pauseTime")); put("chart_window", machine.getString("chartWindow")); put("trailing_average", machine.getInt("trailingAverage")); put("tracking_mode", machine.getString("trackingMode")); put("current_weight", machine.getDouble("currentWeight")); put("sort_order", machine.getInt("sortOrder")); put("pinned", machine.getInt("pinned")); put("order_group", machine.getString("orderGroup")); put("ranking_priority", machine.getInt("rankingPriority")); put("show_trend", machine.getInt("showTrend")); put("hidden", machine.getInt("hidden"))
        })
        val areas = machine.getJSONArray("areas"); for (index in 0 until areas.length()) { val area = areas.getJSONObject(index); database.insertOrThrow("machine_areas", null, ContentValues().apply { put("machine_id", machine.getLong("id")); put("area", area.getString("area")); put("strength", area.getDouble("strength")) }) }
        val sets = machine.getJSONArray("sets"); for (index in 0 until sets.length()) { val set = sets.getJSONObject(index); database.insertOrThrow("sets", null, ContentValues().apply { put("id", set.getLong("id")); put("machine_id", machine.getLong("id")); put("reps", set.getInt("reps")); put("weight", set.getDouble("weight")); put("performed_at", set.getLong("performedAt")) }) }
        val weights = machine.getJSONArray("weights"); for (index in 0 until weights.length()) { val weight = weights.getJSONObject(index); database.insertOrThrow("weight_history", null, ContentValues().apply { put("id", weight.getLong("id")); put("machine_id", machine.getLong("id")); put("weight", weight.getDouble("weight")); put("changed_at", weight.getLong("changedAt")) }) }
    }
    fun deleteSet(id: Long) {
        db.writableDatabase.delete("sets", "id=?", arrayOf(id.toString()))
    }

    fun updateSet(id: Long, reps: Int, weight: Double) {
        db.writableDatabase.update("sets", ContentValues().apply {
            put("reps", reps.coerceAtLeast(1))
            put("weight", weight.coerceAtLeast(0.0))
        }, "id=?", arrayOf(id.toString()))
    }

    fun deleteExerciseDay(machineId: Long, startInclusive: Long, endExclusive: Long) {
        db.writableDatabase.delete(
            "sets",
            "machine_id=? AND performed_at>=? AND performed_at<?",
            arrayOf(machineId.toString(), startInclusive.toString(), endExclusive.toString()),
        )
    }

    fun deleteTrainingDay(startInclusive: Long, endExclusive: Long) {
        val planId = activeTrainingPlanId()
        db.writableDatabase.delete(
            "sets",
            "performed_at>=? AND performed_at<? AND machine_id IN (SELECT id FROM machines WHERE plan_id=?)",
            arrayOf(startInclusive.toString(), endExclusive.toString(), planId.toString()),
        )
    }

    fun deleteCurrentExerciseSets(machineId: Long) {
        db.writableDatabase.delete("sets", "machine_id=? AND performed_at>=?", arrayOf(machineId.toString(), trainingDayStart(gymClock.now()).toString()))
    }

    fun deleteCurrentTraining() {
        val planId = activeTrainingPlanId()
        val cutoff = trainingDayStart(gymClock.now())
        db.writableDatabase.delete(
            "sets",
            "performed_at>=? AND machine_id IN (SELECT id FROM machines WHERE plan_id=?)",
            arrayOf(cutoff.toString(), planId.toString()),
        )
    }

    fun saveAreas(machineId: Long, assignments: List<AreaAssignment>) {
        val database = db.writableDatabase
        database.transaction {
            database.delete("machine_areas", "machine_id=?", arrayOf(machineId.toString()))
            assignments.forEach { assignment ->
                database.insertOrThrow("machine_areas", null, ContentValues().apply {
                    put("machine_id", machineId)
                    put("area", assignment.area.name)
                    put("strength", assignment.strength.value)
                })
            }
        }
    }

    fun moveMachineWithinOrderGroup(machineId: Long, direction: Int) {
        val machines = loadMachines().map { it.machine }
        val group = machines.firstOrNull { it.id == machineId }?.orderGroup ?: return
        if (group == ExerciseOrderGroup.AUTOMATIC) return
        val ordered = machines.filter { it.orderGroup == group }.sortedBy { it.sortOrder }.toMutableList()
        val from = ordered.indexOfFirst { it.id == machineId }
        val to = from + direction
        if (from < 0 || to !in ordered.indices) return
        val first = ordered[from]
        val second = ordered[to]
        val database = db.writableDatabase
        database.transaction {
            database.update("machines", ContentValues().apply { put("sort_order", second.sortOrder) }, "id=?", arrayOf(first.id.toString()))
            database.update("machines", ContentValues().apply { put("sort_order", first.sortOrder) }, "id=?", arrayOf(second.id.toString()))
        }
    }

    fun deleteMachine(id: Long) {
        db.writableDatabase.delete("machines", "id=?", arrayOf(id.toString()))
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("format", "GymLog backup")
        root.put("version", 5)
        root.put("exportedAt", gymClock.now())
        root.put("settings", JSONObject().apply {
            put("weightUnit", "kg")
            loadProgressionSettings().let {
                put("progressStaleWeeks", it.staleWeeks)
                put("progressStaleTrainings", it.staleTrainings)
                put("progressVolumeMinimumPercent", it.volumeMinimumIncreasePercent)
            }
            put("areaColors", JSONObject().apply {
                BodyArea.entries.forEach { area -> put(area.name, areaColor(db.readableDatabase, area)) }
            })
        })
        val plans = loadTrainingPlans()
        root.put("activePlanId", activeTrainingPlanId())
        root.put("plans", JSONArray().apply {
            plans.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("sortOrder", it.sortOrder)
                        .put("notes", it.notes)
                        .put("notesPinned", it.notesPinned),
                )
            }
        })
        root.put("machines", JSONArray().apply {
            plans.forEach { plan ->
            loadMachinesForPlan(plan.id).forEach { summary ->
                put(JSONObject().apply {
                    put("id", summary.machine.id)
                    put("planId", plan.id)
                    put("name", summary.machine.name)
                    put("titleNote", summary.machine.titleNote)
                    put("notes", summary.machine.notes)
                    put("pauseTime", summary.machine.pauseTime)
                    put("chartWindow", summary.machine.chartWindow.storageValue)
                    put("trailingAverage", summary.machine.trailingAverage)
                    put("trackingMode", summary.machine.trackingMode.name)
                    put("currentWeight", summary.machine.currentWeight)
                    put("sortOrder", summary.machine.sortOrder)
                    put("pinned", summary.machine.pinned)
                    put("rankingPriority", summary.machine.rankingPriority)
                    put("excludedFromAutoRanking", summary.machine.excludedFromAutoRanking)
                    put("hidden", isMachineHidden(summary.machine.id))
                    put("areas", JSONArray().apply {
                        summary.areas.forEach { put(JSONObject().put("area", it.area.name).put("strength", it.strength.value)) }
                    })
                    put("sets", JSONArray().apply {
                        loadSets(db.readableDatabase, summary.machine.id, null).forEach {
                            put(JSONObject().apply {
                                put("id", it.id)
                                if (summary.machine.trackingMode == TrackingMode.STRENGTH) {
                                    put("recordedLevel", it.reps)
                                    put("trainingMinutes", it.weight)
                                } else {
                                    put("reps", it.reps)
                                    put("weight", it.weight)
                                }
                                put("performedAt", it.performedAt)
                            })
                        }
                    })
                    put("weightChanges", JSONArray().apply {
                        summary.weightChanges.forEach {
                            put(JSONObject().put("weight", it.weight).put("changedAt", it.changedAt))
                        }
                    })
                })
            }
            }
        })
        return root.toString(2)
    }

    fun importJson(json: String, preserveIds: Boolean = false) {
        val root = JSONObject(json)
        val version = root.optInt("version", -1)
        require(version == 5) { "Outdated GymLog backup format $version; this WIP build accepts format 5 only" }
        val machines = root.getJSONArray("machines")
        root.optJSONObject("settings")?.let { settings ->
            saveProgressionSettings(ProgressionSettings(
                staleWeeks = settings.optInt("progressStaleWeeks", 4),
                staleTrainings = settings.optInt("progressStaleTrainings", 5),
                volumeMinimumIncreasePercent = settings.optInt("progressVolumeMinimumPercent", 5),
            ))
        }
        val database = db.writableDatabase
        val importedPlanIds = mutableMapOf<Long, Long>()
        database.transaction {
            database.delete("machines", null, null)
            database.delete("training_plans", null, null)
            val plans = root.getJSONArray("plans")
            for (planIndex in 0 until plans.length()) {
                val plan = plans.getJSONObject(planIndex)
                val newId = database.insertOrThrow("training_plans", null, ContentValues().apply {
                    if (preserveIds) put("id", plan.getLong("id"))
                    put("name", plan.getString("name"))
                    put("sort_order", plan.getInt("sortOrder"))
                    put("notes", plan.optString("notes"))
                    put("notes_pinned", if (plan.optBoolean("notesPinned")) 1 else 0)
                })
                importedPlanIds[plan.getLong("id")] = newId
            }
            val targetPlanId = importedPlanIds[root.getLong("activePlanId")]
                ?: importedPlanIds.values.firstOrNull()
                ?: error("Backup contains no training plans")
            database.delete("area_colors", null, null)
            val colors = root.optJSONObject("settings")?.optJSONObject("areaColors")
            if (colors != null) {
                BodyArea.entries.filter { colors.has(it.name) }.forEach { area ->
                    database.insertOrThrow("area_colors", null, ContentValues().apply {
                        put("area", area.name)
                        put("color", colors.getLong(area.name))
                    })
                }
            }
            for (index in 0 until machines.length()) {
                val item = machines.getJSONObject(index)
                val trackingMode = TrackingMode.fromStorage(item.getString("trackingMode")).name
                val orderGroup = when {
                    item.optBoolean("pinned") -> ExerciseOrderGroup.MANUAL_TOP
                    item.optBoolean("excludedFromAutoRanking") -> ExerciseOrderGroup.MANUAL_BOTTOM
                    else -> ExerciseOrderGroup.AUTOMATIC
                }
                val id = database.insertOrThrow("machines", null, ContentValues().apply {
                    if (preserveIds) put("id", item.getLong("id"))
                    put("plan_id", importedPlanIds[item.getLong("planId")] ?: targetPlanId)
                    put("name", capitalizeName(item.getString("name")))
                    put("title_note", item.optString("titleNote"))
                    put("notes", item.optString("notes"))
                    put("pause_time", item.optString("pauseTime"))
                    put("chart_window", ChartRange.fromStorage(item.optString("chartWindow", ChartRange.THREE_MONTHS.storageValue)).storageValue)
                    put("trailing_average", item.optInt("trailingAverage", 0))
                    put("tracking_mode", trackingMode)
                    put("current_weight", item.getDouble("currentWeight"))
                    put("sort_order", item.optInt("sortOrder", index))
                    put("pinned", if (orderGroup == ExerciseOrderGroup.MANUAL_TOP) 1 else 0)
                    put("order_group", orderGroup.name)
                    put("ranking_priority", item.optInt("rankingPriority", 0))
                    put("show_trend", if (item.optBoolean("showTrend", true)) 1 else 0)
                    put("hidden", if (item.optBoolean("hidden")) 1 else 0)
                })
                val areas = item.optJSONArray("areas") ?: JSONArray()
                for (areaIndex in 0 until areas.length()) {
                    val area = areas.getJSONObject(areaIndex)
                    database.insertOrThrow("machine_areas", null, ContentValues().apply {
                        put("machine_id", id)
                        put("area", BodyArea.valueOf(area.getString("area")).name)
                        put("strength", area.getDouble("strength"))
                    })
                }
                val sets = item.optJSONArray("sets") ?: JSONArray()
                for (setIndex in 0 until sets.length()) {
                    val set = sets.getJSONObject(setIndex)
                    database.insertOrThrow("sets", null, ContentValues().apply {
                        if (preserveIds && set.has("id")) put("id", set.getLong("id"))
                        put("machine_id", id)
                        put("reps", if (trackingMode == TrackingMode.STRENGTH.name) set.getInt("recordedLevel") else set.getInt("reps"))
                        put("weight", if (trackingMode == TrackingMode.STRENGTH.name) set.getDouble("trainingMinutes") else set.getDouble("weight"))
                        put("performed_at", set.getLong("performedAt"))
                    })
                }
                val changes = item.optJSONArray("weightChanges") ?: JSONArray()
                for (changeIndex in 0 until changes.length()) {
                    val change = changes.getJSONObject(changeIndex)
                    addWeightHistory(database, id, change.getDouble("weight"), change.getLong("changedAt"))
                }
                if (changes.length() == 0) addWeightHistory(database, id, item.getDouble("currentWeight"), gymClock.now())
                preferences.edit()
                    .putInt("ranking_priority_$id", item.optInt("rankingPriority", 0))
                    .putBoolean("ranking_excluded_$id", item.optBoolean("excludedFromAutoRanking", false))
                    .putBoolean("hidden_machine_$id", item.optBoolean("hidden", false))
                    .apply()
            }
        }
        val activePlanId = importedPlanIds[root.getLong("activePlanId")]
            ?: importedPlanIds.values.first()
        preferences.edit().putLong("active_training_plan", activePlanId).apply()
    }

    private fun loadAreas(database: SQLiteDatabase, id: Long): List<AreaAssignment> {
        val result = mutableListOf<AreaAssignment>()
        database.rawQuery("SELECT area,strength FROM machine_areas WHERE machine_id=? ORDER BY area", arrayOf(id.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val stored = runCatching { BodyArea.valueOf(cursor.getString(0)) }.getOrNull() ?: continue
                result += AreaAssignment(
                    stored,
                    Strength.fromStorage(cursor.getDouble(1)),
                    areaColor(database, stored),
                )
            }
        }
        return result
    }

    private fun loadSets(database: SQLiteDatabase, id: Long, limit: Int?): List<SetRecord> {
        val result = mutableListOf<SetRecord>()
        val limitClause = limit?.let { " LIMIT $it" } ?: ""
        database.rawQuery(
            "SELECT id,machine_id,reps,weight,performed_at FROM sets WHERE machine_id=? ORDER BY performed_at DESC,id DESC$limitClause",
            arrayOf(id.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) result += SetRecord(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2), cursor.getDouble(3), cursor.getLong(4))
        }
        return result
    }

    private fun loadWeights(database: SQLiteDatabase, id: Long): List<WeightChange> {
        val result = mutableListOf<WeightChange>()
        database.rawQuery("SELECT weight,changed_at FROM weight_history WHERE machine_id=? ORDER BY changed_at DESC,id DESC", arrayOf(id.toString())).use { cursor ->
            while (cursor.moveToNext()) result += WeightChange(cursor.getDouble(0), cursor.getLong(1))
        }
        return result
    }

    private fun countRecentSets(database: SQLiteDatabase, id: Long): Int {
        val cutoff = gymClock.now() - 30L * 24 * 60 * 60 * 1000
        return database.rawQuery(
            "SELECT COUNT(*) FROM sets WHERE machine_id=? AND performed_at>=?",
            arrayOf(id.toString(), cutoff.toString()),
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
    }

    private fun addWeightHistory(database: SQLiteDatabase, machineId: Long, weight: Double, timestamp: Long) {
        database.insertOrThrow("weight_history", null, ContentValues().apply {
            put("machine_id", machineId)
            put("weight", weight)
            put("changed_at", timestamp)
        })
    }

    private fun areaColor(database: SQLiteDatabase, area: BodyArea): Long = database.rawQuery(
        "SELECT color FROM area_colors WHERE area=?",
        arrayOf(area.name),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else area.color }

    private fun capitalizeName(name: String): String = name.trim().replaceFirstChar {
        if (it.isLowerCase()) it.titlecase() else it.toString()
    }

    /** A training day runs from 06:00 to 05:59, so late-night sessions remain one training. */
    private fun trainingDayStart(timestamp: Long): Long = gymClock.trainingDayStart(timestamp)
    private fun ensureTrainingPlan(database: SQLiteDatabase): Long {
        val preferred = preferences.getLong("active_training_plan", -1L)
        if (preferred > 0 && database.rawQuery("SELECT 1 FROM training_plans WHERE id=?", arrayOf(preferred.toString())).use { it.moveToFirst() }) return preferred
        var id = database.rawQuery("SELECT id FROM training_plans ORDER BY sort_order,id LIMIT 1", null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L }
        if (id < 0) id = database.insertOrThrow("training_plans", null, ContentValues().apply { put("name", "Default"); put("sort_order", 0) })
        preferences.edit().putLong("active_training_plan", id).apply()
        return id
    }

    private fun uniquePlanName(database: SQLiteDatabase, requested: String, excludingId: Long? = null): String {
        val base = requested.trim().ifBlank { "Training plan" }
        var candidate = base
        var suffix = 2
        while (database.rawQuery(
                "SELECT 1 FROM training_plans WHERE LOWER(name)=LOWER(?)${if (excludingId == null) "" else " AND id<>?"}",
                if (excludingId == null) arrayOf(candidate) else arrayOf(candidate, excludingId.toString()),
            ).use { it.moveToFirst() }
        ) candidate = "$base $suffix".also { suffix++ }
        return candidate
    }
}

internal const val GYMLOG_DATABASE = "gymlog.db"
private const val DEFAULT_ACCENT_COLOR = 0xFF64B5F6
private const val TRAINING_UNIT_MILLIS = 6L * 60L * 60L * 1000L
