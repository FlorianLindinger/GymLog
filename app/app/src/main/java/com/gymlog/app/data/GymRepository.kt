package com.gymlog.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class GymRepository(context: Context, internal val gymClock: GymClock = GymClock()) {
    internal val appContext: Context = context.applicationContext
    internal var db = GymDatabase(appContext)
        private set
    internal val preferences = appContext.getSharedPreferences(GYMLOG_PREFERENCES, Context.MODE_PRIVATE)
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
                .remove("progress_stale_days")
                .remove("progress_stale_trainings")
                .remove("progress_volume_minimum_percent")
                .remove("progress_volume_window_trainings")
                .remove("progress_volume_window_days")
                .remove("progress_use_one_rep_max")
                .remove("fatigue_yellow_from").remove("fatigue_orange_from").remove("fatigue_red_from")
                .remove("fatigue_prior_1").remove("fatigue_prior_2").remove("fatigue_prior_3")
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

    fun loadFatigueBands(): FatigueBands = fatigueBandsOrDefault(
        preferences.getInt("fatigue_yellow_from", 25), preferences.getInt("fatigue_orange_from", 50),
        preferences.getInt("fatigue_red_from", 75))

    fun saveFatigueBands(bands: FatigueBands) {
        preferences.edit().putInt("fatigue_yellow_from", bands.yellowFrom)
            .putInt("fatigue_orange_from", bands.orangeFrom).putInt("fatigue_red_from", bands.redFrom).apply()
    }

    fun loadPriorDayFatigue(): PriorDayFatigue = priorDayFatigueOrDefault(
        preferences.getInt("fatigue_prior_1", 40), preferences.getInt("fatigue_prior_2", 20),
        preferences.getInt("fatigue_prior_3", 10))

    fun savePriorDayFatigue(prior: PriorDayFatigue) {
        preferences.edit().putInt("fatigue_prior_1", prior.yesterday)
            .putInt("fatigue_prior_2", prior.twoDaysAgo).putInt("fatigue_prior_3", prior.threeDaysAgo).apply()
    }

    fun loadProgressionSettings(): ProgressionSettings = ProgressionSettings(
        staleDays = migratedStaleDays(
            if (preferences.contains("progress_stale_days")) preferences.getInt("progress_stale_days", 60) else null,
            if (preferences.contains("progress_stale_weeks")) preferences.getInt("progress_stale_weeks", 4) else null),
        volumeWindowDays = migratedTrendWindowDays(
            if (preferences.contains("progress_volume_window_days")) preferences.getInt("progress_volume_window_days", 60) else null,
            if (preferences.contains("progress_volume_window_trainings")) preferences.getInt("progress_volume_window_trainings", 5) else null),
        volumeMinimumIncreasePercent = preferences.getInt("progress_volume_minimum_percent", 5),
        useOneRepMax = preferences.getBoolean("progress_use_one_rep_max", true),
    )

    fun saveProgressionSettings(settings: ProgressionSettings) {
        preferences.edit()
            .putInt("progress_stale_days", settings.staleDays.coerceAtLeast(0))
            .remove("progress_stale_weeks")
            .remove("progress_stale_trainings")
            .putInt("progress_volume_window_days", settings.volumeWindowDays.coerceAtLeast(0))
            .remove("progress_volume_window_trainings")
            .putInt("progress_volume_minimum_percent", settings.volumeMinimumIncreasePercent.coerceAtLeast(0))
            .putBoolean("progress_use_one_rep_max", settings.useOneRepMax)
            .apply()
    }

    fun saveExerciseProgressionSettings(id: Long, settings: ProgressionSettings?) {
        db.writableDatabase.update("machines", ContentValues().apply {
            put("progression_settings", settings?.toJson()?.toString())
        }, "id=?", arrayOf(id.toString()))
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
                    put("progression_settings", machine.progressionSettings?.toJson()?.toString())
                    put("show_trend", if (machine.showTrend) 1 else 0)
                })
                summary.areas.forEach { assignment ->
                    database.insertOrThrow("machine_areas", null, ContentValues().apply {
                        put("machine_id", newMachineId)
                        put("area", assignment.area.name)
                        put("strength", assignment.intensity)
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

    fun loadMachines(): List<MachineSummary> = loadMachinesForPlan(ensureTrainingPlan(db.readableDatabase))

    fun loadFatigueExercises(): List<MachineSummary> {
        migrateLegacyMachineState()
        val now = gymClock.now()
        val since = java.time.Instant.ofEpochMilli(gymClock.trainingDayStart(now)).atZone(gymClock.zoneId)
            .minusDays(3).toInstant().toEpochMilli()
        return exerciseReads.loadForFatigue(since, now)
    }

    internal fun loadMachinesForPlan(planId: Long): List<MachineSummary> {
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
                    if (preferences.contains("ranking_excluded_$id")) values.put("order_group", when {
                        cursor.getInt(1) != 0 -> ExerciseOrderGroup.MANUAL_TOP.name
                        excluded -> ExerciseOrderGroup.MANUAL_BOTTOM.name
                        else -> ExerciseOrderGroup.AUTOMATIC.name
                    })
                    if (preferences.contains("ranking_priority_$id")) values.put("ranking_priority", preferences.getInt("ranking_priority_$id", 0))
                    if (preferences.contains("show_trend_$id")) values.put("show_trend", if (preferences.getBoolean("show_trend_$id", true)) 1 else 0)
                    if (preferences.contains("hidden_machine_$id")) values.put("hidden", if (preferences.getBoolean("hidden_machine_$id", false)) 1 else 0)
                    if (values.size() > 0) database.update("machines", values, "id=?", arrayOf(id.toString()))
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
                    put("strength", assignment.intensity)
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

    fun changeRankingPriority(id: Long, delta: Int) {
        db.writableDatabase.execSQL("UPDATE machines SET ranking_priority=MIN(20,MAX(-20,ranking_priority + ?)) WHERE id=?", arrayOf(delta, id))
    }

    fun setShowTrend(id: Long, show: Boolean) {
        db.writableDatabase.update("machines", ContentValues().apply { put("show_trend", if (show) 1 else 0) }, "id=?", arrayOf(id.toString()))
    }

    fun setAreaColor(area: BodyArea, color: Long) {
        db.writableDatabase.insertWithOnConflict("area_colors", null, ContentValues().apply {
            put("area", area.name)
            put("color", color)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadAreaColors(): Map<BodyArea, Long> = BodyArea.entries
        .associateWith { areaColor(db.readableDatabase, it) }

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
            val (trackingMode, _) = machine
            database.update("machines", ContentValues().apply { put("current_weight", value) }, "id=?", arrayOf(id.toString()))
            addWeightHistory(database, id, value, now)
            val setValues = ContentValues().apply {
                if (TrackingMode.fromStorage(trackingMode).usesResistanceLevel()) put("reps", value.toInt().coerceAtLeast(1)) else put("weight", value)
            }
            database.update(
                "sets",
                setValues,
                "machine_id=? AND performed_at>=?",
                arrayOf(id.toString(), trainingDayStart(now).toString()),
            )
        }
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
        val (mode, recordedLevel) = database.rawQuery(
            "SELECT tracking_mode,current_weight FROM machines WHERE id=?",
            arrayOf(machineId.toString()),
        ).use { cursor ->
            require(cursor.moveToFirst()) { "Exercise does not exist" }
            val mode = TrackingMode.fromStorage(cursor.getString(0))
            mode to if (mode.usesResistanceLevel()) cursor.getDouble(1).roundToInt().coerceAtLeast(1) else 1
        }
        val now = gymClock.now()
        database.transaction {
            val currentSets = loadSets(database, machineId, null, trainingDayStart(now)).sortedBy { it.performedAt }
            val entryLevel = if (mode.usesResistanceLevel()) {
                trainingEntryMetric(recordedLevel.toDouble(), mode, currentSets).toInt()
            } else recordedLevel
            if (mode == TrackingMode.DURATION) database.update("machines", ContentValues().apply {
                put("current_weight", minutes.toDouble())
            }, "id=?", arrayOf(machineId.toString()))
            database.delete(
                "sets",
                "machine_id=? AND performed_at>=?",
                arrayOf(machineId.toString(), trainingDayStart(now).toString()),
            )
            database.insertOrThrow("sets", null, ContentValues().apply {
                put("machine_id", machineId)
                put("reps", entryLevel)
                put("weight", minutes.toDouble())
                put("performed_at", now)
            })
        }
    }

    fun addSets(machineId: Long, reps: List<Int>, weight: Double) {
        val database = db.writableDatabase
        val now = gymClock.now()
        database.transaction {
            val currentSets = loadSets(database, machineId, null, trainingDayStart(now)).sortedBy { it.performedAt }
            database.delete(
                "sets",
                "machine_id=? AND performed_at>=?",
                arrayOf(machineId.toString(), trainingDayStart(now).toString()),
            )
            reps.filter { it > 0 }.forEachIndexed { index, amount ->
                database.insertOrThrow("sets", null, ContentValues().apply {
                    put("machine_id", machineId)
                    put("reps", amount)
                    put("weight", trainingEntryMetric(weight, TrackingMode.WEIGHT, currentSets, index))
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

    internal fun setWheelPreference(machineId: Long): String = "set_wheels_$machineId"

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

    fun lastTrainingWindow(): TrainingWindow? {
        val planId = activeTrainingPlanId()
        return db.readableDatabase.rawQuery(
            "SELECT MAX(performed_at) FROM sets WHERE performed_at<=? AND machine_id IN (SELECT id FROM machines WHERE plan_id=?)",
            arrayOf(gymClock.now().toString(), planId.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) gymClock.trainingWindow(cursor.getLong(0)) else null
        }
    }

    fun deleteLastTrainingWithJournal(expected: TrainingWindow? = null): DeletionJournal? = db.writableDatabase.transaction {
        val latest = lastTrainingWindow() ?: return@transaction null
        if (expected != null && latest != expected) return@transaction null
        deleteTrainingDayWithJournal(latest.startInclusive, latest.endExclusive)
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
        val machine = database.rawQuery("SELECT id,plan_id,name,title_note,notes,pause_time,chart_window,trailing_average,tracking_mode,current_weight,sort_order,pinned,order_group,ranking_priority,show_trend,hidden,progression_settings FROM machines WHERE id=?", arrayOf(id.toString())).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            JSONObject().put("id", cursor.getLong(0)).put("planId", cursor.getLong(1)).put("name", cursor.getString(2)).put("titleNote", cursor.getString(3)).put("notes", cursor.getString(4)).put("pauseTime", cursor.getString(5)).put("chartWindow", cursor.getString(6)).put("trailingAverage", cursor.getInt(7)).put("trackingMode", cursor.getString(8)).put("currentWeight", cursor.getDouble(9)).put("sortOrder", cursor.getInt(10)).put("pinned", cursor.getInt(11)).put("orderGroup", cursor.getString(12)).put("rankingPriority", cursor.getInt(13)).put("showTrend", cursor.getInt(14)).put("hidden", cursor.getInt(15)).put("progressionSettings", cursor.getString(16)?.let(::JSONObject))
        } ?: return null
        machine.put("areas", JSONArray()).put("sets", JSONArray()).put("weights", JSONArray())
        database.rawQuery("SELECT area,strength FROM machine_areas WHERE machine_id=?", arrayOf(id.toString())).use { cursor -> while (cursor.moveToNext()) machine.getJSONArray("areas").put(JSONObject().put("area", cursor.getString(0)).put("strength", cursor.getDouble(1))) }
        database.rawQuery("SELECT id,reps,weight,performed_at FROM sets WHERE machine_id=?", arrayOf(id.toString())).use { cursor -> while (cursor.moveToNext()) machine.getJSONArray("sets").put(JSONObject().put("id", cursor.getLong(0)).put("reps", cursor.getInt(1)).put("weight", cursor.getDouble(2)).put("performedAt", cursor.getLong(3))) }
        database.rawQuery("SELECT id,weight,changed_at FROM weight_history WHERE machine_id=?", arrayOf(id.toString())).use { cursor -> while (cursor.moveToNext()) machine.getJSONArray("weights").put(JSONObject().put("id", cursor.getLong(0)).put("weight", cursor.getDouble(1)).put("changedAt", cursor.getLong(2))) }
        return machine
    }

    private fun restoreMachine(database: SQLiteDatabase, machine: JSONObject) {
        database.insertOrThrow("machines", null, ContentValues().apply {
            put("id", machine.getLong("id")); put("plan_id", machine.getLong("planId")); put("name", machine.getString("name")); put("title_note", machine.getString("titleNote")); put("notes", machine.getString("notes")); put("pause_time", machine.getString("pauseTime")); put("chart_window", machine.getString("chartWindow")); put("trailing_average", machine.getInt("trailingAverage")); put("tracking_mode", machine.getString("trackingMode")); put("current_weight", machine.getDouble("currentWeight")); put("sort_order", machine.getInt("sortOrder")); put("pinned", machine.getInt("pinned")); put("order_group", machine.getString("orderGroup")); put("ranking_priority", machine.getInt("rankingPriority")); put("show_trend", machine.getInt("showTrend")); put("hidden", machine.getInt("hidden")); put("progression_settings", machine.optJSONObject("progressionSettings")?.toString())
        })
        val areas = machine.getJSONArray("areas"); for (index in 0 until areas.length()) { val area = areas.getJSONObject(index); database.insertOrThrow("machine_areas", null, ContentValues().apply { put("machine_id", machine.getLong("id")); put("area", area.getString("area")); put("strength", area.getDouble("strength")) }) }
        val sets = machine.getJSONArray("sets"); for (index in 0 until sets.length()) { val set = sets.getJSONObject(index); database.insertOrThrow("sets", null, ContentValues().apply { put("id", set.getLong("id")); put("machine_id", machine.getLong("id")); put("reps", set.getInt("reps")); put("weight", set.getDouble("weight")); put("performed_at", set.getLong("performedAt")) }) }
        val weights = machine.getJSONArray("weights"); for (index in 0 until weights.length()) { val weight = weights.getJSONObject(index); database.insertOrThrow("weight_history", null, ContentValues().apply { put("id", weight.getLong("id")); put("machine_id", machine.getLong("id")); put("weight", weight.getDouble("weight")); put("changed_at", weight.getLong("changedAt")) }) }
    }

    fun changeRecordedSets(machineId: Long, setIds: List<Long>, reps: List<Int>) {
        require(setIds.isNotEmpty() && setIds.distinct().size == setIds.size)
        require(reps.any { it > 0 }) { "Use confirmed deletion to remove the whole entry" }
        val database = db.writableDatabase
        database.transaction {
            val placeholders = setIds.joinToString(",") { "?" }
            val records = database.rawQuery(
                "SELECT id,machine_id,reps,weight,performed_at FROM sets WHERE machine_id=? AND id IN ($placeholders)",
                arrayOf(machineId.toString(), *setIds.map { it.toString() }.toTypedArray()),
            ).use { cursor -> buildMap<Long, SetRecord> {
                while (cursor.moveToNext()) {
                    val set = SetRecord(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2), cursor.getDouble(3), cursor.getLong(4))
                    put(set.id, set)
                }
            } }
            require(records.size == setIds.size) { "The selected entry has changed; reopen it" }
            val changed = editedRecordedSets(setIds.map { records.getValue(it) }, reps)
            val retained = changed.mapTo(mutableSetOf()) { it.id }
            setIds.filter { it !in retained }.forEach { id ->
                database.delete("sets", "id=? AND machine_id=?", arrayOf(id.toString(), machineId.toString()))
            }
            changed.forEach { set ->
                if (set.id != 0L) database.update("sets", ContentValues().apply { put("reps", set.reps) },
                    "id=? AND machine_id=?", arrayOf(set.id.toString(), machineId.toString()))
                else database.insertOrThrow("sets", null, ContentValues().apply {
                    put("machine_id", machineId); put("reps", set.reps); put("weight", set.weight); put("performed_at", set.performedAt)
                })
            }
        }
    }

    fun updateSet(id: Long, reps: Int, weight: Double) {
        db.writableDatabase.update("sets", ContentValues().apply {
            put("reps", reps.coerceAtLeast(1))
            put("weight", weight.coerceAtLeast(0.0))
        }, "id=?", arrayOf(id.toString()))
    }

    fun saveAreas(machineId: Long, assignments: List<AreaAssignment>) {
        val database = db.writableDatabase
        database.transaction {
            database.delete("machine_areas", "machine_id=?", arrayOf(machineId.toString()))
            assignments.forEach { assignment ->
                database.insertOrThrow("machine_areas", null, ContentValues().apply {
                    put("machine_id", machineId)
                    put("area", assignment.area.name)
                    put("strength", assignment.intensity)
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

    fun exportTrainingPlan(id: Long = activeTrainingPlanId()): String {
        val plan = loadTrainingPlans().firstOrNull { it.id == id } ?: error("Training plan not found")
        return TrainingPlanFile.encode(plan, loadMachinesForPlan(id))
    }

    fun importTrainingPlan(text: String): Long {
        val plan = TrainingPlanFile.decode(text)
        val database = db.writableDatabase
        val id = database.transaction {
            val nextOrder = database.rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM training_plans", null)
                .use { it.moveToFirst(); it.getInt(0) }
            val planId = database.insertOrThrow("training_plans", null, ContentValues().apply {
                put("name", uniquePlanName(database, plan.getString("name")))
                put("sort_order", nextOrder); put("notes", plan.optString("notes")); put("notes_pinned", if (plan.optBoolean("notesPinned")) 1 else 0)
            })
            val exercises = plan.getJSONArray("exercises")
            for (i in 0 until exercises.length()) {
                val e = exercises.getJSONObject(i)
                val group = ExerciseOrderGroup.valueOf(e.getString("orderGroup"))
                val machineId = database.insertOrThrow("machines", null, ContentValues().apply {
                    put("plan_id", planId); put("name", e.getString("name").trim()); put("tracking_mode", e.getString("trackingMode"))
                    put("current_weight", e.getDouble("currentWeight")); put("sort_order", i)
                    put("title_note", e.optString("titleNote")); put("notes", e.optString("notes")); put("pause_time", e.optString("pauseTime"))
                    put("chart_window", ChartRange.fromStorage(e.optString("chartWindow")).storageValue)
                    put("pinned", if (group == ExerciseOrderGroup.MANUAL_TOP) 1 else 0); put("order_group", group.name)
                    put("ranking_priority", e.optInt("rankingPriority", 0)); put("show_trend", if (e.optBoolean("showTrend", true)) 1 else 0)
                    put("progression_settings", e.optJSONObject("progressionSettings")?.let(::progressionSettingsFromJson)?.toJson()?.toString())
                })
                val areas = e.getJSONArray("areas")
                for (j in 0 until areas.length()) {
                    val a = areas.getJSONObject(j)
                    database.insertOrThrow("machine_areas", null, ContentValues().apply {
                        put("machine_id", machineId); put("area", a.getString("area")); put("strength", a.getDouble("strength"))
                    })
                }
            }
            planId
        }
        setActiveTrainingPlan(id)
        return id
    }

    /** Full JSON backup; the format lives in GymBackupJson.kt. */
    fun exportJson(): String = exportBackupJson()

    /** Replaces all data with a JSON backup; see GymBackupJson.kt for validation and ordering. */
    fun importJson(json: String, preserveIds: Boolean = false) = importBackupJson(json, preserveIds)

    internal fun loadSets(database: SQLiteDatabase, id: Long, limit: Int?, since: Long? = null): List<SetRecord> {
        val result = mutableListOf<SetRecord>()
        val limitClause = limit?.let { " LIMIT $it" } ?: ""
        database.rawQuery(
            "SELECT id,machine_id,reps,weight,performed_at FROM sets WHERE machine_id=?${if (since != null) " AND performed_at>=?" else ""} ORDER BY performed_at DESC,id DESC$limitClause",
            if (since != null) arrayOf(id.toString(), since.toString()) else arrayOf(id.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) result += SetRecord(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2), cursor.getDouble(3), cursor.getLong(4))
        }
        return result
    }

    internal fun addWeightHistory(database: SQLiteDatabase, machineId: Long, weight: Double, timestamp: Long) {
        database.insertOrThrow("weight_history", null, ContentValues().apply {
            put("machine_id", machineId)
            put("weight", weight)
            put("changed_at", timestamp)
        })
    }

    internal fun areaColor(database: SQLiteDatabase, area: BodyArea): Long = database.rawQuery(
        "SELECT color FROM area_colors WHERE area=?",
        arrayOf(area.name),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else area.color }

    internal fun capitalizeName(name: String): String = name.trim().replaceFirstChar {
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
