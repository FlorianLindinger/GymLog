package com.gymlog.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class GymRepository(context: Context) {
    private val appContext = context.applicationContext
    private var db = GymDatabase(appContext)
    private val preferences = appContext.getSharedPreferences(GYMLOG_PREFERENCES, Context.MODE_PRIVATE)

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

    fun accentColor(): AccentColor = runCatching {
        AccentColor.valueOf(preferences.getString("accent_color", AccentColor.BLUE.name)!!)
    }.getOrDefault(AccentColor.BLUE)

    fun setAccentColor(color: AccentColor) {
        preferences.edit().putString("accent_color", color.name).apply()
    }

    fun setAutomaticBackupEnabled(enabled: Boolean) {
        setAutomaticBackupEnabled(appContext, enabled)
    }

    fun loadProgressionSettings(): ProgressionSettings = ProgressionSettings(
        staleWeeks = preferences.getInt("progress_stale_weeks", 4),
        staleTrainings = preferences.getInt("progress_stale_trainings", 5),
    )

    fun saveProgressionSettings(settings: ProgressionSettings) {
        preferences.edit()
            .putInt("progress_stale_weeks", settings.staleWeeks.coerceAtLeast(0))
            .putInt("progress_stale_trainings", settings.staleTrainings.coerceAtLeast(0))
            .apply()
    }

    fun loadTrainingPlans(): List<TrainingPlan> {
        val database = db.writableDatabase
        ensureTrainingPlan(database)
        val result = mutableListOf<TrainingPlan>()
        database.rawQuery("SELECT id,name,sort_order FROM training_plans ORDER BY sort_order,id", null).use { cursor ->
            while (cursor.moveToNext()) result += TrainingPlan(cursor.getLong(0), cursor.getString(1), cursor.getInt(2))
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

    fun duplicateTrainingPlan(id: Long): Long {
        val database = db.writableDatabase
        val source = loadTrainingPlans().firstOrNull { it.id == id } ?: error("Training plan does not exist")
        val nextOrder = database.rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM training_plans", null)
            .use { it.moveToFirst(); it.getInt(0) }
        return database.transaction {
            val newPlanId = database.insertOrThrow("training_plans", null, ContentValues().apply {
                put("name", uniquePlanName(database, "${source.name} - Copy"))
                put("sort_order", nextOrder)
            })
            loadMachinesForPlan(id).forEach { summary ->
                val machine = summary.machine
                val newMachineId = database.insertOrThrow("machines", null, ContentValues().apply {
                    put("plan_id", newPlanId)
                    put("name", machine.name)
                    put("title_note", machine.titleNote)
                    put("notes", machine.notes)
                    put("pause_time", machine.pauseTime)
                    put("chart_window", machine.chartWindow)
                    put("trailing_average", machine.trailingAverage)
                    put("tracking_mode", machine.trackingMode)
                    put("current_weight", machine.currentWeight)
                    put("sort_order", machine.sortOrder)
                })
                summary.areas.forEach { assignment ->
                    database.insertOrThrow("machine_areas", null, ContentValues().apply {
                        put("machine_id", newMachineId)
                        put("area", assignment.area.name)
                        put("strength", assignment.strength.value)
                    })
                }
                addWeightHistory(database, newMachineId, machine.currentWeight, System.currentTimeMillis())
            }
            newPlanId
        }
    }

    fun renameTrainingPlan(id: Long, name: String) {
        val database = db.writableDatabase
        database.update("training_plans", ContentValues().apply { put("name", uniquePlanName(database, name, id)) }, "id=?", arrayOf(id.toString()))
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
        val database = db.readableDatabase
        val machines = mutableListOf<Machine>()
        database.rawQuery(
            "SELECT id,name,title_note,notes,pause_time,chart_window,trailing_average,tracking_mode,current_weight,sort_order FROM machines WHERE plan_id=? ORDER BY sort_order,id",
            arrayOf(planId.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                machines += Machine(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    titleNote = cursor.getString(2),
                    notes = cursor.getString(3),
                    pauseTime = cursor.getString(4),
                    chartWindow = cursor.getString(5),
                    trailingAverage = cursor.getInt(6),
                    trackingMode = cursor.getString(7),
                    currentWeight = cursor.getDouble(8),
                    sortOrder = cursor.getInt(9),
                )
            }
        }
        return machines.map { machine ->
            MachineSummary(
                machine = machine,
                areas = loadAreas(database, machine.id),
                recentSets = loadSets(database, machine.id, null),
                weightChanges = loadWeights(database, machine.id),
                setsLast30Days = countRecentSets(database, machine.id),
            )
        }
    }

    fun addMachine(name: String, weight: Double, trackingMode: String): Long {
        val database = db.writableDatabase
        val planId = ensureTrainingPlan(database)
        val nextOrder = database.rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM machines WHERE plan_id=?", arrayOf(planId.toString()))
            .use { it.moveToFirst(); it.getInt(0) }
        val id = database.insertOrThrow("machines", null, ContentValues().apply {
            put("name", capitalizeName(name))
            put("plan_id", planId)
            put("notes", "")
            put("tracking_mode", trackingMode)
            put("current_weight", weight)
            put("sort_order", nextOrder)
        })
        addWeightHistory(database, id, weight, System.currentTimeMillis())
        return id
    }

    fun updateMachine(id: Long, name: String, titleNote: String, notes: String, pauseTime: String) {
        db.writableDatabase.update("machines", ContentValues().apply {
            put("name", capitalizeName(name))
            put("title_note", titleNote.trim())
            put("notes", notes.trim())
            put("pause_time", pauseTime.trim())
        }, "id=?", arrayOf(id.toString()))
    }

    fun setChartWindow(id: Long, window: String) {
        db.writableDatabase.update("machines", ContentValues().apply { put("chart_window", window) }, "id=?", arrayOf(id.toString()))
    }

    fun setTrailingAverage(id: Long, amount: Int) {
        db.writableDatabase.update("machines", ContentValues().apply { put("trailing_average", amount) }, "id=?", arrayOf(id.toString()))
    }

    fun setAreaColor(area: BodyArea, color: Long) {
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
                result += HistorySet(cursor.getLong(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4), cursor.getDouble(5), cursor.getLong(6))
            }
        }
        return result
    }

    fun changeWeight(id: Long, weight: Double) {
        val database = db.writableDatabase
        database.transaction {
            database.update("machines", ContentValues().apply { put("current_weight", weight) }, "id=?", arrayOf(id.toString()))
            addWeightHistory(database, id, weight, System.currentTimeMillis())
        }
    }

    fun addSet(machineId: Long, reps: Int, weight: Double) {
        db.writableDatabase.insertOrThrow("sets", null, ContentValues().apply {
            put("machine_id", machineId)
            put("reps", reps)
            put("weight", weight)
            put("performed_at", System.currentTimeMillis())
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
            if (cursor.getString(0) == "STRENGTH") cursor.getDouble(1).roundToInt().coerceAtLeast(1) else 1
        }
        database.insertOrThrow("sets", null, ContentValues().apply {
            put("machine_id", machineId)
            put("reps", recordedLevel)
            put("weight", minutes.toDouble())
            put("performed_at", System.currentTimeMillis())
        })
    }

    fun addSets(machineId: Long, reps: List<Int>, weight: Double) {
        val database = db.writableDatabase
        val now = System.currentTimeMillis()
        database.transaction {
            reps.filter { it > 0 }.forEachIndexed { index, amount ->
                database.insertOrThrow("sets", null, ContentValues().apply {
                    put("machine_id", machineId)
                    put("reps", amount)
                    put("weight", weight)
                    put("performed_at", now + index)
                })
            }
        }
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

    fun moveMachine(machineId: Long, direction: Int) {
        val ordered = loadMachines().map { it.machine }.toMutableList()
        val from = ordered.indexOfFirst { it.id == machineId }
        val to = from + direction
        if (from < 0 || to !in ordered.indices) return
        val moved = ordered.removeAt(from)
        ordered.add(to, moved)
        val database = db.writableDatabase
        database.transaction {
            ordered.forEachIndexed { index, machine ->
                database.update("machines", ContentValues().apply { put("sort_order", index) }, "id=?", arrayOf(machine.id.toString()))
            }
        }
    }

    fun reorderMachines(machineIds: List<Long>) {
        val currentIds = loadMachines().map { it.machine.id }
        if (machineIds.size != currentIds.size || machineIds.toSet() != currentIds.toSet()) return
        val database = db.writableDatabase
        database.transaction {
            machineIds.forEachIndexed { index, id ->
                database.update("machines", ContentValues().apply { put("sort_order", index) }, "id=?", arrayOf(id.toString()))
            }
        }
    }

    fun deleteMachine(id: Long) {
        db.writableDatabase.delete("machines", "id=?", arrayOf(id.toString()))
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("format", "GymLog backup")
        root.put("version", 3)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("settings", JSONObject().apply {
            put("weightUnit", "kg")
            loadProgressionSettings().let {
                put("progressStaleWeeks", it.staleWeeks)
                put("progressStaleTrainings", it.staleTrainings)
            }
            put("areaColors", JSONObject().apply {
                BodyArea.entries.forEach { area -> put(area.name, areaColor(db.readableDatabase, area)) }
            })
        })
        val plans = loadTrainingPlans()
        root.put("activePlanId", activeTrainingPlanId())
        root.put("plans", JSONArray().apply {
            plans.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("sortOrder", it.sortOrder)) }
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
                    put("chartWindow", summary.machine.chartWindow)
                    put("trailingAverage", summary.machine.trailingAverage)
                    put("trackingMode", summary.machine.trackingMode)
                    put("currentWeight", summary.machine.currentWeight)
                    put("sortOrder", summary.machine.sortOrder)
                    put("areas", JSONArray().apply {
                        summary.areas.forEach { put(JSONObject().put("area", it.area.name).put("strength", it.strength.value)) }
                    })
                    put("sets", JSONArray().apply {
                        loadSets(db.readableDatabase, summary.machine.id, null).forEach {
                            put(JSONObject().apply {
                                if (summary.machine.trackingMode == "STRENGTH") {
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

    fun importJson(json: String) {
        val root = JSONObject(json)
        val version = root.optInt("version", -1)
        require(version == 3) { "Unsupported GymLog backup version" }
        val machines = root.getJSONArray("machines")
        root.optJSONObject("settings")?.let { settings ->
            saveProgressionSettings(ProgressionSettings(
                staleWeeks = settings.optInt("progressStaleWeeks", 4),
                staleTrainings = settings.optInt("progressStaleTrainings", 5),
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
                    put("name", plan.getString("name"))
                    put("sort_order", plan.getInt("sortOrder"))
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
                val trackingMode = item.getString("trackingMode")
                val id = database.insertOrThrow("machines", null, ContentValues().apply {
                    put("plan_id", importedPlanIds[item.getLong("planId")] ?: targetPlanId)
                    put("name", capitalizeName(item.getString("name")))
                    put("title_note", item.optString("titleNote"))
                    put("notes", item.optString("notes"))
                    put("pause_time", item.optString("pauseTime"))
                    put("chart_window", item.optString("chartWindow", "3M"))
                    put("trailing_average", item.optInt("trailingAverage", 0))
                    put("tracking_mode", trackingMode)
                    put("current_weight", item.getDouble("currentWeight"))
                    put("sort_order", item.optInt("sortOrder", index))
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
                        put("machine_id", id)
                        put("reps", if (trackingMode == "STRENGTH") set.getInt("recordedLevel") else set.getInt("reps"))
                        put("weight", if (trackingMode == "STRENGTH") set.getDouble("trainingMinutes") else set.getDouble("weight"))
                        put("performed_at", set.getLong("performedAt"))
                    })
                }
                val changes = item.optJSONArray("weightChanges") ?: JSONArray()
                for (changeIndex in 0 until changes.length()) {
                    val change = changes.getJSONObject(changeIndex)
                    addWeightHistory(database, id, change.getDouble("weight"), change.getLong("changedAt"))
                }
                if (changes.length() == 0) addWeightHistory(database, id, item.getDouble("currentWeight"), System.currentTimeMillis())
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
                    if (cursor.getDouble(1) >= 0.75) Strength.FULL else Strength.HALF,
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
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
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

private const val GYMLOG_DATABASE = "gymlog.db"

private class GymDatabase(context: Context) : SQLiteOpenHelper(context, GYMLOG_DATABASE, null, 7) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE training_plans(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,sort_order INTEGER NOT NULL)")
        db.execSQL("INSERT INTO training_plans(name,sort_order) VALUES('Default',0)")
        db.execSQL("CREATE TABLE machines(id INTEGER PRIMARY KEY AUTOINCREMENT,plan_id INTEGER NOT NULL DEFAULT 1,name TEXT NOT NULL,title_note TEXT NOT NULL DEFAULT '',notes TEXT NOT NULL DEFAULT '',pause_time TEXT NOT NULL DEFAULT '',chart_window TEXT NOT NULL DEFAULT '3M',trailing_average INTEGER NOT NULL DEFAULT 0,tracking_mode TEXT NOT NULL DEFAULT 'WEIGHT',current_weight REAL NOT NULL DEFAULT 0,sort_order INTEGER NOT NULL,FOREIGN KEY(plan_id) REFERENCES training_plans(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE machine_areas(machine_id INTEGER NOT NULL,area TEXT NOT NULL,strength REAL NOT NULL,PRIMARY KEY(machine_id,area),FOREIGN KEY(machine_id) REFERENCES machines(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE sets(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER NOT NULL,reps INTEGER NOT NULL,weight REAL NOT NULL,performed_at INTEGER NOT NULL,FOREIGN KEY(machine_id) REFERENCES machines(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE weight_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER NOT NULL,weight REAL NOT NULL,changed_at INTEGER NOT NULL,FOREIGN KEY(machine_id) REFERENCES machines(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE area_colors(area TEXT PRIMARY KEY,color INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_sets_machine_time ON sets(machine_id,performed_at DESC)")
        db.execSQL("CREATE INDEX idx_machines_plan_order ON machines(plan_id,sort_order)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("This database uses GymLog schema $oldVersion, but this app requires schema $newVersion")
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("This database uses GymLog schema $oldVersion, but this app requires schema $newVersion")
    }
}
