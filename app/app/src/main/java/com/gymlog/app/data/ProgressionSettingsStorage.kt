package com.gymlog.app.data

import org.json.JSONObject

internal fun ProgressionSettings.toJson(): JSONObject = JSONObject()
    .put("staleWeeks", staleWeeks.coerceAtLeast(0))
    .put("volumeWindowTrainings", volumeWindowTrainings.coerceAtLeast(0))
    .put("volumeMinimumIncreasePercent", volumeMinimumIncreasePercent.coerceAtLeast(0))
    .put("useOneRepMax", useOneRepMax)

internal fun progressionSettingsFromJson(json: JSONObject): ProgressionSettings = ProgressionSettings(
    staleWeeks = json.optInt("staleWeeks", 0).coerceAtLeast(0),
    volumeWindowTrainings = json.optInt("volumeWindowTrainings", 0).coerceAtLeast(0),
    volumeMinimumIncreasePercent = json.optInt("volumeMinimumIncreasePercent", 0).coerceAtLeast(0),
    useOneRepMax = json.optBoolean("useOneRepMax", true),
)

internal fun progressionSettingsFromStorage(value: String?): ProgressionSettings? =
    value?.let { progressionSettingsFromJson(JSONObject(it)) }
