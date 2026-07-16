package com.gymlog.app.ui

enum class BodyMetric(val label: String, val unit: String) {
    WEIGHT("Weight", "kg"),
    FAT_MASS("Fat Mass", "kg"),
    MUSCLE_MASS("Lean Mass", "kg"),
    FAT_PERCENTAGE("Fat", "%"),
    MUSCLE_PERCENTAGE("Lean Mass", "%"),
}

data class BodyMeasurement(
    val metric: BodyMetric,
    val value: Double,
    val measuredAt: Long,
    val source: String,
)

data class BodyDataSnapshot(
    val measurements: List<BodyMeasurement> = emptyList(),
    val syncedAt: Long? = null,
)
