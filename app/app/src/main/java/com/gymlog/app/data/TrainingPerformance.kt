package com.gymlog.app.data

import kotlin.math.pow

/** Riegel-style intensity-duration heuristic, referenced to a 10-minute effort.
 * The 0.06 fatigue exponent is an app assumption, not a fitted power/level model.
 * Levels are only comparable on the same machine and are treated as a linear scale.
 */
internal fun estimatedTenMinuteIntensity(intensity: Double, minutes: Double): Double =
    if (!intensity.isFinite() || intensity <= 0.0 || !minutes.isFinite() || minutes <= 0.0) 0.0
    else intensity * (minutes / 10.0).pow(0.06)

/** Epley one-rep-max estimate, weight x (1 + reps / 30); a single rep is its own load.
 * Each extra rep adds about 3.3 % of the load. Estimates get less reliable beyond roughly 12 reps.
 */
internal fun setStrengthScore(weight: Double, reps: Int): Double = when {
    !weight.isFinite() || weight <= 0.0 || reps <= 0 -> 0.0
    reps == 1 -> weight
    else -> weight * (1.0 + reps / 30.0)
}

/** Mean estimated 1RM over every valid set in a training day.
 * Every set counts, but doing one set more or fewer at the same performance leaves the score unchanged;
 * workload lives in volume instead. Light warm-up sets logged with the work sets pull the mean down.
 */
internal fun sessionStrengthScore(sets: List<SetRecord>): Double {
    val estimates = sets.map { setStrengthScore(it.weight, it.reps) }.filter { it > 0.0 }
    return if (estimates.isEmpty()) 0.0 else estimates.average()
}

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
