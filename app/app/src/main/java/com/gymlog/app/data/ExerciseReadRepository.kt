package com.gymlog.app.data

import android.database.sqlite.SQLiteDatabase

/** Read-side exercise queries. Uses batched child queries to avoid N+1 loading. */
internal class ExerciseReadRepository(
    private val database: () -> SQLiteDatabase,
    private val now: () -> Long,
    private val setWheelValues: (Long) -> List<Int>?,
) {
    fun loadForPlan(planId: Long): List<MachineSummary> {
        val db = database()
        val machines = mutableListOf<Machine>()
        db.rawQuery(
            "SELECT id,name,title_note,notes,pause_time,chart_window,trailing_average,tracking_mode,current_weight,sort_order,order_group,ranking_priority,show_trend FROM machines WHERE plan_id=? ORDER BY sort_order,id",
            arrayOf(planId.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val group = runCatching { ExerciseOrderGroup.valueOf(cursor.getString(10)) }.getOrDefault(ExerciseOrderGroup.AUTOMATIC)
                machines += Machine(
                    id = cursor.getLong(0), name = cursor.getString(1), titleNote = cursor.getString(2), notes = cursor.getString(3),
                    pauseTime = cursor.getString(4), chartWindow = ChartRange.fromStorage(cursor.getString(5)),
                    trailingAverage = cursor.getInt(6), trackingMode = TrackingMode.fromStorage(cursor.getString(7)),
                    currentWeight = cursor.getDouble(8), sortOrder = cursor.getInt(9),
                    pinned = group == ExerciseOrderGroup.MANUAL_TOP, rankingPriority = cursor.getInt(11),
                    excludedFromAutoRanking = group == ExerciseOrderGroup.MANUAL_BOTTOM, showTrend = cursor.getInt(12) != 0,
                )
            }
        }
        if (machines.isEmpty()) return emptyList()
        val placeholders = machines.joinToString(",") { "?" }
        val args = machines.map { it.id.toString() }.toTypedArray()
        val areas = mutableMapOf<Long, MutableList<AreaAssignment>>()
        db.rawQuery("SELECT ma.machine_id,ma.area,ma.strength,ac.color FROM machine_areas ma LEFT JOIN area_colors ac ON ac.area=ma.area WHERE ma.machine_id IN ($placeholders) ORDER BY ma.area", args).use { cursor ->
            while (cursor.moveToNext()) {
                val area = runCatching { BodyArea.valueOf(cursor.getString(1)) }.getOrNull() ?: continue
                areas.getOrPut(cursor.getLong(0)) { mutableListOf() } += AreaAssignment(area, Strength.fromStorage(cursor.getDouble(2)), if (cursor.isNull(3)) area.color else cursor.getLong(3))
            }
        }
        val sets = mutableMapOf<Long, MutableList<SetRecord>>()
        db.rawQuery("SELECT id,machine_id,reps,weight,performed_at FROM sets WHERE machine_id IN ($placeholders) ORDER BY performed_at DESC,id DESC", args).use { cursor ->
            while (cursor.moveToNext()) sets.getOrPut(cursor.getLong(1)) { mutableListOf() } += SetRecord(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2), cursor.getDouble(3), cursor.getLong(4))
        }
        val weights = mutableMapOf<Long, MutableList<WeightChange>>()
        db.rawQuery("SELECT machine_id,weight,changed_at FROM weight_history WHERE machine_id IN ($placeholders) ORDER BY changed_at DESC,id DESC", args).use { cursor ->
            while (cursor.moveToNext()) weights.getOrPut(cursor.getLong(0)) { mutableListOf() } += WeightChange(cursor.getDouble(1), cursor.getLong(2))
        }
        val counts = mutableMapOf<Long, Int>()
        db.rawQuery("SELECT machine_id,COUNT(*) FROM sets WHERE machine_id IN ($placeholders) AND performed_at>=? GROUP BY machine_id", args + (now() - 30L * 24 * 60 * 60 * 1000).toString()).use { cursor ->
            while (cursor.moveToNext()) counts[cursor.getLong(0)] = cursor.getInt(1)
        }
        return machines.map { machine ->
            MachineSummary(machine, areas[machine.id].orEmpty(), sets[machine.id].orEmpty(), weights[machine.id].orEmpty(), counts[machine.id] ?: 0, setWheelValues(machine.id))
        }
    }
}
