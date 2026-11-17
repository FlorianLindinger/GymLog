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
    val days = reference?.let { ((clock.now() - it).coerceAtLeast(0L) / 86_400_000L).toInt() } ?: 0
    return buildList {
        if (effective.staleDays > 0 && days >= effective.staleDays && summary.pendingMetricDirection() <= 0) add("$days days no kg increase")
        if (effective.volumeMinimumIncreasePercent > 0 && effective.volumeWindowDays > 0) {
            // Calendar training days preserve the 06:00 boundary across daylight-saving changes.
            val start = Instant.ofEpochMilli(clock.trainingDayStart()).atZone(clock.zoneId)
                .minusDays(effective.volumeWindowDays.toLong() - 1).toInstant().toEpochMilli()
            val scores = summary.recentSets.filter { it.performedAt in start..clock.now() }
                .groupBy { clock.trainingDayStart(it.performedAt) }
                .toSortedMap().map { (dayStart, sets) -> dayStart to trainingPerformance(sets, useOneRepMax = true) }
            val growth = trendGrowthPercent(scores)
            if (growth != null) {
                if (growth < effective.volumeMinimumIncreasePercent) {
                    // Round first: a fitted line can land a hair off a whole number (119.99999…).
                    val shown = kotlin.math.round(growth * 10.0) / 10.0
                    val percent = if (shown % 1.0 == 0.0) shown.toInt().toString() else String.format(Locale.US, "%.1f", shown)
                    add("Strength score trend ${if (shown >= 0.0) "+" else ""}$percent %")
                }
            }
        }
    }
}

/**
 * Percentage change of a least-squares line through every training day, from its value on the first day
 * to its value on the last day. One unusually good or bad day therefore cannot decide the warning alone,
 * while exactly two days still give the plain first-to-last change. Null when fewer than two days exist
 * or the fitted start value is not positive (a percentage of it would be meaningless).
 */
internal fun trendGrowthPercent(points: List<Pair<Long, Double>>): Double? {
    if (points.size < 2) return null
    val origin = points.first().first
    // Real elapsed days, so irregular gaps between trainings weigh correctly.
    val xs = points.map { (it.first - origin) / 86_400_000.0 }
    val ys = points.map { it.second }
    val meanX = xs.average()
    val meanY = ys.average()
    val sxx = xs.sumOf { (it - meanX) * (it - meanX) }
    if (sxx == 0.0) return null
    val slope = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) } / sxx
    val fittedStart = meanY + slope * (xs.first() - meanX)
    if (fittedStart <= 0.0) return null
    return slope * (xs.last() - xs.first()) / fittedStart * 100.0
}

/** Empty warning fields are explicit zero/disabled values, never inherited defaults. */
internal fun parseWarningSetting(value: String): Int? =
    if (value.isBlank()) 0 else value.toIntOrNull()?.takeIf { it >= 0 }

/** Training counts have no exact day equivalent; adopt 60 days but preserve explicit disablement. */
internal fun migratedTrendWindowDays(days: Int?, legacyTrainings: Int?): Int =
    days?.coerceAtLeast(0) ?: if (legacyTrainings != null && legacyTrainings <= 0) 0 else 60

/** Keep explicit old durations and disablement; absent settings use the new 60-day default. */
internal fun migratedStaleDays(days: Int?, legacyWeeks: Int?): Int =
    days?.coerceAtLeast(0) ?: legacyWeeks?.let {
        (it.toLong() * 7L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    } ?: 60
