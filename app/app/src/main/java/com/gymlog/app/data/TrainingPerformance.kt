package com.gymlog.app.data

import kotlin.math.pow

/** Riegel-style intensity-duration heuristic, referenced to a 10-minute effort.
 * The 0.06 fatigue exponent is an app assumption, not a fitted power/level model.
 * Levels are only comparable on the same machine and are treated as a linear scale.
 */
internal fun estimatedTenMinuteIntensity(intensity: Double, minutes: Double): Double =
    if (!intensity.isFinite() || intensity <= 0.0 || !minutes.isFinite() || minutes <= 0.0) 0.0
    else intensity * (minutes / 10.0).pow(0.06)

/** Lombardi-style set contribution: load dominates and reps have a diminishing influence. */
internal fun setStrengthScore(weight: Double, reps: Int): Double = when {
    !weight.isFinite() || weight <= 0.0 || reps <= 0 -> 0.0
    else -> weight * reps.toDouble().pow(0.1)
}

/** Sum every valid set contribution; extra sets are fully counted, without a best-set bonus cap.
 * This is an app-specific training score, not a one-rep maximum or measured physical work.
 * Large losses of reps/sets can still lower the score despite an increase in load.
 */
internal fun sessionStrengthScore(sets: List<SetRecord>): Double = sets.sumOf { setStrengthScore(it.weight, it.reps) }

/** The legacy `useOneRepMax` selector now chooses strength score; timed modes keep their own metric. */
internal fun trainingPerformance(
    sets: List<SetRecord>,
    useOneRepMax: Boolean,
    trackingMode: TrackingMode = TrackingMode.WEIGHT,
): Double = if (trackingMode.usesResistanceLevel()) {
    sets.maxOfOrNull { estimatedTenMinuteIntensity(it.reps.toDouble(), it.weight) } ?: 0.0
} else if (trackingMode == TrackingMode.DURATION) {
    sets.sumOf { it.weight }
} else if (useOneRepMax) {
    sessionStrengthScore(sets)
} else {
    sets.sumOf { it.weight * it.reps }
}
