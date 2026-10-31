package com.gymlog.app.data

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
