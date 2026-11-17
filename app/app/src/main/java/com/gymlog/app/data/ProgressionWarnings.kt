package com.gymlog.app.data

import java.util.Locale
import java.time.Instant

internal fun progressionWarning(
    summary: MachineSummary,
    settings: ProgressionSettings,
    clock: GymClock = GymClock(),
): String? = progressionWarningReasons(summary, settings, clock).takeIf { it.isNotEmpty() }?.joinToString(" · ")

internal fun progressionWarningReasons(
    summary: MachineSummary,
    settings: ProgressionSettings,
    clock: GymClock = GymClock(),
): List<String> {
    val effective = summary.machine.progressionSettings ?: settings
    if (summary.machine.trackingMode != TrackingMode.WEIGHT || summary.recentSets.isEmpty()) return emptyList()
    val reference = summary.weightIncreases().maxOfOrNull { it.changedAt }
        ?: summary.recentSets.minOfOrNull { it.performedAt }
    val weeks = reference?.let { ((clock.now() - it).coerceAtLeast(0L) / 604_800_000L).toInt() } ?: 0
    return buildList {
        if (effective.staleWeeks > 0 && weeks >= effective.staleWeeks && summary.pendingMetricDirection() <= 0) add("$weeks weeks no kg increase")
        if (effective.volumeMinimumIncreasePercent > 0 && effective.volumeWindowDays > 0) {
            // Calendar training days preserve the 06:00 boundary across daylight-saving changes.
            val start = Instant.ofEpochMilli(clock.trainingDayStart()).atZone(clock.zoneId)
                .minusDays(effective.volumeWindowDays.toLong() - 1).toInstant().toEpochMilli()
            val volumes = summary.recentSets.filter { it.performedAt in start..clock.now() }
                .groupBy { clock.trainingDayStart(it.performedAt) }
                .toSortedMap().values.map { sets -> trainingPerformance(sets, useOneRepMax = true) }
            if (volumes.size >= 2 && volumes.first() > 0.0) {
                val growth = (volumes.last() - volumes.first()) / volumes.first() * 100.0
                if (growth < effective.volumeMinimumIncreasePercent) {
                    val percent = if (growth % 1.0 == 0.0) growth.toInt().toString() else String.format(Locale.US, "%.1f", growth)
                    add("Strength score trend ${if (growth >= 0.0) "+" else ""}$percent %")
                }
            }
        }
    }
}

/** Empty warning fields are explicit zero/disabled values, never inherited defaults. */
internal fun parseWarningSetting(value: String): Int? =
    if (value.isBlank()) 0 else value.toIntOrNull()?.takeIf { it >= 0 }

/** Training counts have no exact day equivalent; adopt 60 days but preserve explicit disablement. */
internal fun migratedTrendWindowDays(days: Int?, legacyTrainings: Int?): Int =
    days?.coerceAtLeast(0) ?: if (legacyTrainings != null && legacyTrainings <= 0) 0 else 60
