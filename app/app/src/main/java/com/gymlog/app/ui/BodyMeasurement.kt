package com.gymlog.app.ui

enum class BodyMetric(val label: String, val unit: String) {
    WEIGHT("Weight", "kg"),
    FAT_MASS("Fat Mass", "kg"),
    MUSCLE_MASS("Muscle Mass", "kg"),
    FAT_PERCENTAGE("Fat", "%"),
    MUSCLE_PERCENTAGE("Muscle Mass", "%"),
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

/** Resume refreshes share a 15-minute successful-read cooldown; explicit refreshes bypass it. */
internal fun shouldAutoSyncBody(lastSuccessElapsed: Long?, nowElapsed: Long): Boolean =
    lastSuccessElapsed == null || nowElapsed < lastSuccessElapsed || nowElapsed - lastSuccessElapsed >= 15L * 60L * 1000L

/** Binary search over ascending timestamps, preferring the earlier/first record on ties. */
internal fun nearestBodyMeasurementIndex(times: List<Long>, target: Long, maximumDistance: Long): Int? {
    if (times.isEmpty()) return null
    fun lowerBound(value: Long): Int {
        var low = 0
        var high = times.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (times[middle] < value) low = middle + 1 else high = middle
        }
        return low
    }
    val after = lowerBound(target)
    val index = when {
        after == 0 -> 0
        after == times.size -> times.lastIndex
        target - times[after - 1] <= times[after] - target -> after - 1
        else -> after
    }
    if (kotlin.math.abs(times[index] - target) > maximumDistance) return null
    return lowerBound(times[index])
}
