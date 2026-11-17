package com.gymlog.app.data

/** Keep each wheel slot tied to its recorded set, even when an earlier slot is removed. */
internal fun editedRecordedSets(original: List<SetRecord>, reps: List<Int>): List<SetRecord> {
    require(original.isNotEmpty() && original.map { it.machineId }.distinct().size == 1)
    require(reps.all { it >= 0 })
    return reps.mapIndexedNotNull { index, amount ->
        if (amount == 0) null else {
            val existing = original.getOrNull(index)
            (existing ?: original.last()).copy(id = existing?.id ?: 0L, reps = amount)
        }
    }
}

/** Rep/minute edits keep recorded intensity; configured intensity applies to a new training day. */
internal fun trainingEntryMetric(
    configuredMetric: Double,
    mode: TrackingMode,
    currentSets: List<SetRecord>,
    setIndex: Int = 0,
): Double {
    val recorded = currentSets.getOrNull(setIndex) ?: currentSets.lastOrNull() ?: return configuredMetric
    return if (mode.usesResistanceLevel()) recorded.reps.toDouble() else recorded.weight
}
