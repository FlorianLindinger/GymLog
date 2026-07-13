package com.gymlog.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class GymRepository(context: Context) {
    private val db = GymDatabase(context.applicationContext)

    fun loadMachines(): List<MachineSummary> {
        val database = db.readableDatabase
        val machines = mutableListOf<Machine>()
        database.rawQuery(
            "SELECT id,name,notes,current_weight,sort_order FROM machines ORDER BY sort_order,id",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                machines += Machine(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    notes = cursor.getString(2),
                    currentWeight = cursor.getDouble(3),
                    sortOrder = cursor.getInt(4),
                )
            }
        }
        return machines.map { machine ->
            MachineSummary(
                machine = machine,
                areas = loadAreas(database, machine.id),
                recentSets = loadSets(database, machine.id, 12),
                weightChanges = loadWeights(database, machine.id),
            )
        }
    }

    fun addMachine(name: String, weight: Double): Long {
        val database = db.writableDatabase
        val nextOrder = database.rawQuery("SELECT COALESCE(MAX(sort_order),-1)+1 FROM machines", null)
            .use { it.moveToFirst(); it.getInt(0) }
        val id = database.insertOrThrow("machines", null, ContentValues().apply {
            put("name", name.trim())
            put("notes", "")
            put("current_weight", weight)
            put("sort_order", nextOrder)
        })
        addWeightHistory(database, id, weight, System.currentTimeMillis())
        return id
    }

    fun updateMachine(id: Long, name: String, notes: String) {
        db.writableDatabase.update("machines", ContentValues().apply {
            put("name", name.trim())
            put("notes", notes.trim())
        }, "id=?", arrayOf(id.toString()))
    }

    fun changeWeight(id: Long, weight: Double) {
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            database.update("machines", ContentValues().apply { put("current_weight", weight) }, "id=?", arrayOf(id.toString()))
            addWeightHistory(database, id, weight, System.currentTimeMillis())
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
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

    fun deleteSet(id: Long) {
        db.writableDatabase.delete("sets", "id=?", arrayOf(id.toString()))
    }

    fun saveAreas(machineId: Long, assignments: List<AreaAssignment>) {
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            database.delete("machine_areas", "machine_id=?", arrayOf(machineId.toString()))
            assignments.forEach { assignment ->
                database.insertOrThrow("machine_areas", null, ContentValues().apply {
                    put("machine_id", machineId)
                    put("area", assignment.area.name)
                    put("strength", assignment.strength.value)
                })
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
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
        database.beginTransaction()
        try {
            ordered.forEachIndexed { index, machine ->
                database.update("machines", ContentValues().apply { put("sort_order", index) }, "id=?", arrayOf(machine.id.toString()))
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun deleteMachine(id: Long) {
        db.writableDatabase.delete("machines", "id=?", arrayOf(id.toString()))
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("format", "GymLog backup")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("settings", JSONObject().put("weightUnit", "kg"))
        root.put("machines", JSONArray().apply {
            loadMachines().forEach { summary ->
                put(JSONObject().apply {
                    put("id", summary.machine.id)
                    put("name", summary.machine.name)
                    put("notes", summary.machine.notes)
                    put("currentWeight", summary.machine.currentWeight)
                    put("sortOrder", summary.machine.sortOrder)
                    put("areas", JSONArray().apply {
                        summary.areas.forEach { put(JSONObject().put("area", it.area.name).put("strength", it.strength.value)) }
                    })
                    put("sets", JSONArray().apply {
                        loadSets(db.readableDatabase, summary.machine.id, null).forEach {
                            put(JSONObject().put("reps", it.reps).put("weight", it.weight).put("performedAt", it.performedAt))
                        }
                    })
                    put("weightChanges", JSONArray().apply {
                        summary.weightChanges.forEach {
                            put(JSONObject().put("weight", it.weight).put("changedAt", it.changedAt))
                        }
                    })
                })
            }
        })
        return root.toString(2)
    }

    fun importJson(json: String) {
        val root = JSONObject(json)
        require(root.optInt("version", -1) == 1) { "Unsupported GymLog backup version" }
        val machines = root.getJSONArray("machines")
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            database.delete("machines", null, null)
            for (index in 0 until machines.length()) {
                val item = machines.getJSONObject(index)
                val id = database.insertOrThrow("machines", null, ContentValues().apply {
                    put("name", item.getString("name"))
                    put("notes", item.optString("notes"))
                    put("current_weight", item.getDouble("currentWeight"))
                    put("sort_order", item.optInt("sortOrder", index))
                })
                val areas = item.optJSONArray("areas") ?: JSONArray()
                for (areaIndex in 0 until areas.length()) {
                    val area = areas.getJSONObject(areaIndex)
                    database.insertOrThrow("machine_areas", null, ContentValues().apply {
                        put("machine_id", id)
                        put("area", area.getString("area"))
                        put("strength", area.getDouble("strength"))
                    })
                }
                val sets = item.optJSONArray("sets") ?: JSONArray()
                for (setIndex in 0 until sets.length()) {
                    val set = sets.getJSONObject(setIndex)
                    database.insertOrThrow("sets", null, ContentValues().apply {
                        put("machine_id", id)
                        put("reps", set.getInt("reps"))
                        put("weight", set.getDouble("weight"))
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
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun loadAreas(database: SQLiteDatabase, id: Long): List<AreaAssignment> {
        val result = mutableListOf<AreaAssignment>()
        database.rawQuery("SELECT area,strength FROM machine_areas WHERE machine_id=? ORDER BY area", arrayOf(id.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val area = runCatching { BodyArea.valueOf(cursor.getString(0)) }.getOrNull() ?: continue
                result += AreaAssignment(area, if (cursor.getDouble(1) >= 0.75) Strength.FULL else Strength.HALF)
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

    private fun addWeightHistory(database: SQLiteDatabase, machineId: Long, weight: Double, timestamp: Long) {
        database.insertOrThrow("weight_history", null, ContentValues().apply {
            put("machine_id", machineId)
            put("weight", weight)
            put("changed_at", timestamp)
        })
    }
}

private class GymDatabase(context: Context) : SQLiteOpenHelper(context, "gymlog.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE machines(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,notes TEXT NOT NULL DEFAULT '',current_weight REAL NOT NULL DEFAULT 0,sort_order INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE machine_areas(machine_id INTEGER NOT NULL,area TEXT NOT NULL,strength REAL NOT NULL,PRIMARY KEY(machine_id,area),FOREIGN KEY(machine_id) REFERENCES machines(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE sets(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER NOT NULL,reps INTEGER NOT NULL,weight REAL NOT NULL,performed_at INTEGER NOT NULL,FOREIGN KEY(machine_id) REFERENCES machines(id) ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE weight_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id INTEGER NOT NULL,weight REAL NOT NULL,changed_at INTEGER NOT NULL,FOREIGN KEY(machine_id) REFERENCES machines(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX idx_sets_machine_time ON sets(machine_id,performed_at DESC)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}
