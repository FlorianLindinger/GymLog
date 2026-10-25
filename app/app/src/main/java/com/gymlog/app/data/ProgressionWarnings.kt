package com.gymlog.app.data

import java.util.Locale

internal fun progressionWarning(
    summary: MachineSummary,
    settings: ProgressionSettings,
    clock: GymClock = GymClock(),
): String? {
    val effective = summary.machine.progressionSettings ?: settings
    if (summary.machine.trackingMode != TrackingMode.WEIGHT || summary.recentSets.isEmpty()) return null
    val reference = summary.weightIncreases().maxOfOrNull { it.changedAt }
        ?: summary.recentSets.minOfOrNull { it.performedAt }
    val weeks = reference?.let { ((clock.now() - it).coerceAtLeast(0L) / 604_800_000L).toInt() } ?: 0
    val reasons = buildList {
        if (effective.staleWeeks > 0 && weeks >= effective.staleWeeks) add("$weeks weeks no kg increase")
        if (effective.volumeMinimumIncreasePercent > 0 && effective.volumeWindowTrainings >= 2) {
            val window = effective.volumeWindowTrainings
            val volumes = summary.recentSets.groupBy { clock.trainingDayStart(it.performedAt) }
                .toSortedMap().values.toList().takeLast(window).map { sets -> trainingPerformance(sets, effective.useOneRepMax) }
            if (volumes.size == window && volumes.first() > 0.0) {
                val growth = (volumes.last() - volumes.first()) / volumes.first() * 100.0
                if (growth < effective.volumeMinimumIncreasePercent) {
                    val percent = if (growth % 1.0 == 0.0) growth.toInt().toString() else String.format(Locale.US, "%.1f", growth)
                    add("${if (effective.useOneRepMax) "1RM" else "volume"} trend $percent %")
                }
            }
        }
    }
    return reasons.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Empty warning fields are explicit zero/disabled values, never inherited defaults. */
internal fun parseWarningSetting(value: String): Int? =
    if (value.isBlank()) 0 else value.toIntOrNull()?.takeIf { it >= 0 }
