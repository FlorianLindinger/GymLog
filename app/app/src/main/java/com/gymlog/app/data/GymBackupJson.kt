package com.gymlog.app.data

import android.content.ContentValues
import android.content.Context
import androidx.core.database.sqlite.transaction
import org.json.JSONArray
import org.json.JSONObject

// JSON backup format 5. Kept apart from GymRepository so the everyday read/write paths stay readable;
// these functions reach the repository's internal helpers rather than duplicating SQL.

internal fun GymRepository.exportBackupJson(): String {
    val root = JSONObject()
    root.put("format", "GymLog backup")
    root.put("version", 5)
    root.put("manualBodyEntries", JSONArray(appContext.getSharedPreferences("manual_body_entries", Context.MODE_PRIVATE).getString("entries", "[]")))
    root.put("exportedAt", gymClock.now())
    root.put("settings", JSONObject().apply {
        put("weightUnit", "kg")
        loadFatigueBands().let { bands ->
            put("fatigueBands", JSONObject().put("yellowFrom", bands.yellowFrom)
                .put("orangeFrom", bands.orangeFrom).put("redFrom", bands.redFrom))
        }
        loadPriorDayFatigue().let { prior ->
            put("priorDayFatigue", JSONObject().put("yesterday", prior.yesterday)
                .put("twoDaysAgo", prior.twoDaysAgo).put("threeDaysAgo", prior.threeDaysAgo))
        }
        loadProgressionSettings().let {
            put("progressStaleDays", it.staleDays)
            put("progressVolumeWindowDays", it.volumeWindowDays)
            put("progressVolumeMinimumPercent", it.volumeMinimumIncreasePercent)
            put("progressUseOneRepMax", it.useOneRepMax)
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
                put("showTrend", summary.machine.showTrend)
                summary.setWheelValues?.let { put("setWheelValues", JSONArray(it)) }
                put("progressionSettings", summary.machine.progressionSettings?.toJson())
                put("excludedFromAutoRanking", summary.machine.excludedFromAutoRanking)
                put("hidden", isMachineHidden(summary.machine.id))
                put("areas", JSONArray().apply {
                    summary.areas.forEach { put(JSONObject().put("area", it.area.name).put("strength", it.intensity)) }
                })
                put("sets", JSONArray().apply {
                    loadSets(db.readableDatabase, summary.machine.id, null).forEach {
                        put(JSONObject().apply {
                            put("id", it.id)
                            if (summary.machine.trackingMode.usesResistanceLevel()) {
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

internal fun GymRepository.importBackupJson(json: String, preserveIds: Boolean) {
    val root = JSONObject(json)
    val version = root.optInt("version", -1)
    require(version == 5) { "Outdated GymLog backup format $version; this WIP build accepts format 5 only" }
    val machines = root.getJSONArray("machines")
    val manualBody = root.optJSONArray("manualBodyEntries")
    if (manualBody != null) for (i in 0 until manualBody.length()) {
        val e = manualBody.getJSONObject(i)
        com.gymlog.app.ui.ManualBodyEntry(e.getDouble("weight"),
            if (e.has("fat")) e.getDouble("fat") else null,
            if (e.has("muscle")) e.getDouble("muscle") else null,
            e.getLong("time"), e.getString("id"))
    }
    // Parse settings now, but do not persist them until every workout row has validated.
    val settings = root.optJSONObject("settings")
    val importedBands = settings?.let {
        val bands = settings.optJSONObject("fatigueBands")
        fatigueBandsOrDefault(bands?.optInt("yellowFrom", 25) ?: 25,
            bands?.optInt("orangeFrom", 50) ?: 50, bands?.optInt("redFrom", 75) ?: 75)
    }
    // Backups without this block predate the setting and keep the defaults.
    val importedPriorDays = settings?.let {
        val prior = settings.optJSONObject("priorDayFatigue")
        priorDayFatigueOrDefault(prior?.optInt("yesterday", 40) ?: 40,
            prior?.optInt("twoDaysAgo", 20) ?: 20, prior?.optInt("threeDaysAgo", 10) ?: 10)
    }
    val importedProgression = settings?.let {
        ProgressionSettings(
            staleDays = migratedStaleDays(
                settings.takeIf { it.has("progressStaleDays") }?.optInt("progressStaleDays", 60),
                settings.takeIf { it.has("progressStaleWeeks") }?.optInt("progressStaleWeeks", 4)),
            volumeWindowDays = migratedTrendWindowDays(
                settings.takeIf { it.has("progressVolumeWindowDays") }?.optInt("progressVolumeWindowDays", 60),
                settings.takeIf { it.has("progressVolumeWindowTrainings") }?.optInt("progressVolumeWindowTrainings", 5)),
            volumeMinimumIncreasePercent = settings.optInt("progressVolumeMinimumPercent", 5),
            useOneRepMax = settings.optBoolean("progressUseOneRepMax", true),
        )
    }
    val database = db.writableDatabase
    val importedPlanIds = mutableMapOf<Long, Long>()
    val importedWheels = mutableMapOf<Long, String>()
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
                put("progression_settings", item.optJSONObject("progressionSettings")?.let(::progressionSettingsFromJson)?.toJson()?.toString())
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
                    put("reps", if (TrackingMode.fromStorage(trackingMode).usesResistanceLevel()) set.getInt("recordedLevel") else set.getInt("reps"))
                    put("weight", if (TrackingMode.fromStorage(trackingMode).usesResistanceLevel()) set.getDouble("trainingMinutes") else set.getDouble("weight"))
                    put("performed_at", set.getLong("performedAt"))
                })
            }
            val changes = item.optJSONArray("weightChanges") ?: JSONArray()
            for (changeIndex in 0 until changes.length()) {
                val change = changes.getJSONObject(changeIndex)
                addWeightHistory(database, id, change.getDouble("weight"), change.getLong("changedAt"))
            }
            if (changes.length() == 0) addWeightHistory(database, id, item.getDouble("currentWeight"), gymClock.now())
            if (item.has("setWheelValues") && !item.isNull("setWheelValues")) {
                val wheels = item.getJSONArray("setWheelValues")
                require(wheels.length() == 5) { "An exercise must have exactly five set-wheel values" }
                val values = (0 until wheels.length()).map { wheels.getInt(it) }
                require(values.all { it >= 0 }) { "Set-wheel values must not be negative" }
                importedWheels[id] = values.joinToString(",")
            }
        }
    }
    val activePlanId = importedPlanIds[root.getLong("activePlanId")]
        ?: importedPlanIds.values.first()
    importedBands?.let(::saveFatigueBands)
    importedPriorDays?.let(::savePriorDayFatigue)
    importedProgression?.let(::saveProgressionSettings)
    preferences.edit().apply {
        // Old IDs must not leak wheel layouts into imported exercises, even with preserveIds.
        preferences.all.keys.filter { it.startsWith("set_wheels_") }.forEach { remove(it) }
        importedWheels.forEach { (id, values) -> putString(setWheelPreference(id), values) }
        putBoolean("machine_state_in_sqlite", true)
        putLong("active_training_plan", activePlanId)
    }.apply()
    if (manualBody != null) check(appContext.getSharedPreferences("manual_body_entries", Context.MODE_PRIVATE)
        .edit().putString("entries", manualBody.toString()).commit()) { "Could not restore body entries" }
}
