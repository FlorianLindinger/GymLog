package com.gymlog.app.data

import org.json.JSONArray
import org.json.JSONObject

internal object TrainingPlanFile {
    fun encode(plan: TrainingPlan, exercises: List<MachineSummary>): String = JSONObject()
        .put("format", "GymLog training plan").put("version", 1).put("name", plan.name)
        .put("notes", plan.notes).put("notesPinned", plan.notesPinned)
        .put("exercises", JSONArray().apply { exercises.forEach { summary ->
            val m = summary.machine
            put(JSONObject().put("name", m.name).put("trackingMode", m.trackingMode.name)
                .put("currentWeight", m.currentWeight).put("titleNote", m.titleNote).put("notes", m.notes)
                .put("pauseTime", m.pauseTime).put("chartWindow", m.chartWindow.storageValue)
                .put("orderGroup", m.orderGroup.name).put("rankingPriority", m.rankingPriority)
                .put("showTrend", m.showTrend).put("progressionSettings", m.progressionSettings?.toJson())
                .put("areas", JSONArray().apply { summary.areas.forEach { a ->
                    put(JSONObject().put("area", a.area.name).put("strength", a.intensity))
                } }))
        } }).toString(2)

    fun decode(text: String): JSONObject {
        require(text.length <= 2_000_000) { "Plan file is too large" }
        val root = JSONObject(text)
        require(root.getString("format") == "GymLog training plan" && root.getInt("version") == 1) { "Not a supported GymLog training plan" }
        require(root.getString("name").isNotBlank()) { "Plan name is required" }
        val exercises = root.getJSONArray("exercises")
        require(exercises.length() <= 1000) { "Too many exercises" }
        for (i in 0 until exercises.length()) {
            val e = exercises.getJSONObject(i)
            require(e.getString("name").isNotBlank()) { "Exercise name is required" }
            TrackingMode.valueOf(e.getString("trackingMode"))
            ExerciseOrderGroup.valueOf(e.getString("orderGroup"))
            require(e.getDouble("currentWeight").let { it.isFinite() && it >= 0 }) { "Invalid exercise value" }
            require(e.optInt("rankingPriority", 0) in -20..20) { "Invalid priority" }
            val areas = e.getJSONArray("areas")
            val seen = mutableSetOf<BodyArea>()
            for (j in 0 until areas.length()) {
                val a = areas.getJSONObject(j)
                require(seen.add(BodyArea.valueOf(a.getString("area")))) { "Duplicate body region" }
                require(a.getDouble("strength").let { it.isFinite() && it > 0 && it <= 10 }) { "Invalid region intensity" }
            }
        }
        return root
    }
}
