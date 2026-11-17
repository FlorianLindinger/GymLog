package com.gymlog.app.data

import org.json.JSONObject

internal fun ProgressionSettings.toJson(): JSONObject = JSONObject()
    .put("staleDays", staleDays.coerceAtLeast(0))
    .put("volumeWindowDays", volumeWindowDays.coerceAtLeast(0))
    .put("volumeMinimumIncreasePercent", volumeMinimumIncreasePercent.coerceAtLeast(0))
    .put("useOneRepMax", useOneRepMax)

internal fun progressionSettingsFromJson(json: JSONObject): ProgressionSettings = ProgressionSettings(
    staleDays = migratedStaleDays(
        json.takeIf { it.has("staleDays") }?.optInt("staleDays", 0),
        json.optInt("staleWeeks", 0)),
    volumeWindowDays = migratedTrendWindowDays(
        json.takeIf { it.has("volumeWindowDays") }?.optInt("volumeWindowDays", 60),
        json.takeIf { it.has("volumeWindowTrainings") }?.optInt("volumeWindowTrainings", 0)),
    volumeMinimumIncreasePercent = json.optInt("volumeMinimumIncreasePercent", 0).coerceAtLeast(0),
    useOneRepMax = json.optBoolean("useOneRepMax", true),
)

internal fun progressionSettingsFromStorage(value: String?): ProgressionSettings? =
    value?.let { progressionSettingsFromJson(JSONObject(it)) }
