package com.gymlog.app.data

import kotlin.math.exp

/** Wathan's nonlinear estimate; a recorded single is already a measured single-rep load. */
internal fun estimatedOneRepMax(weight: Double, reps: Int): Double = when {
    !weight.isFinite() || weight <= 0.0 || reps <= 0 -> 0.0
    reps == 1 -> weight
    else -> 100.0 * weight / (48.8 + 53.8 * exp(-0.075 * reps))
}

internal fun trainingPerformance(
    sets: List<SetRecord>,
    useOneRepMax: Boolean,
): Double = if (useOneRepMax) {
    sets.maxOfOrNull { estimatedOneRepMax(it.weight, it.reps) } ?: 0.0
} else {
    sets.sumOf { it.weight * it.reps }
}
