package com.gymlog.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

/**
 * "CC Stats": an insight-oriented summary (consistency, strength momentum, records, muscle balance)
 * built only from recorded sets. Everything here is pure so it can be tested without Android.
 */
internal enum class CcPeriod(val label: String, val weeks: Int) {
    FOUR_WEEKS("4 wk", 4), TWELVE_WEEKS("12 wk", 12), HALF_YEAR("26 wk", 26), YEAR("1 yr", 52),
}

internal data class CcSession(val date: LocalDate, val first: Long, val last: Long, val exercises: Int, val sets: Int) {
    /** Minutes between the first and last entry; null for a single timestamp, which has no measurable length. */
    val minutes: Double? get() = (last - first).takeIf { it > 0 }?.let { it / 60_000.0 }
}

internal data class CcWeek(val monday: LocalDate, val trainings: Int, val sets: Int)

internal enum class CcMomentum { RISING, FLAT, FALLING, NEW }

internal data class CcLift(
    val machineId: Long,
    val name: String,
    val latest: Double,
    val best: Double,
    val bestDate: LocalDate,
    /** Fitted change per 30 days within the period, in percent; null with too little history. */
    val monthlyTrendPercent: Double?,
    val momentum: CcMomentum,
    val typicalReps: Int,
    /** Smallest load on a 2.5 kg step that beats [best] at [typicalReps]. */
    val nextPrWeight: Double,
    val daysSinceBest: Long,
    val sessionsInPeriod: Int,
)

internal data class CcRecord(val name: String, val date: LocalDate, val estimate: Double, val previousBest: Double) {
    val gainPercent: Double get() = (estimate / previousBest - 1.0) * 100.0
}

internal data class CcRegionLoad(val area: BodyArea, val weeklySets: Double, val daysSinceTrained: Long?)

internal data class CcSplit(val push: Double, val pull: Double, val legs: Double, val core: Double) {
    val total: Double get() = push + pull + legs + core
}

internal data class CcRepMix(val heavy: Int, val moderate: Int, val light: Int) {
    val total: Int get() = heavy + moderate + light
}

internal data class CcStats(
    val period: CcPeriod,
    val today: LocalDate,
    val sessions: List<CcSession>,
    val previousSessions: Int,
    val weeks: List<CcWeek>,
    val currentStreakWeeks: Int,
    val longestStreakWeeks: Int,
    /** Monday first. */
    val weekdayCounts: List<Int>,
    val lifts: List<CcLift>,
    val records: List<CcRecord>,
    val regions: List<CcRegionLoad>,
    val split: CcSplit,
    val repMix: CcRepMix,
    val bodyWeight: Double?,
) {
    val trainingsPerWeek: Double get() = sessions.size.toDouble() / period.weeks
    val averageMinutes: Double? get() = sessions.mapNotNull { it.minutes }.takeIf { it.isNotEmpty() }?.average()
    val averageSets: Double? get() = sessions.takeIf { it.isNotEmpty() }?.map { it.sets }?.average()
    val averageExercises: Double? get() = sessions.takeIf { it.isNotEmpty() }?.map { it.exercises }?.average()
    val totalSets: Int get() = sessions.sumOf { it.sets }
}

// Region groups for the push/pull/legs/core split. Lower back counts as core (bracing), traps and
// forearms as pull; these are conventional groupings, not anatomical facts.
private val PUSH = setOf(BodyArea.CHEST, BodyArea.SHOULDERS, BodyArea.TRICEPS)
private val PULL = setOf(BodyArea.UPPER_BACK, BodyArea.BICEPS, BodyArea.TRAPS, BodyArea.FOREARMS)
private val LEGS = setOf(BodyArea.QUADS, BodyArea.HAMSTRINGS, BodyArea.GLUTES, BodyArea.CALVES, BodyArea.ADDUCTORS, BodyArea.ABDUCTORS)

/** Momentum threshold: below 1 % per month the fitted line is treated as flat (normal day-to-day noise). */
private const val FLAT_PERCENT_PER_MONTH = 1.0
private const val PR_STEP_KG = 2.5

internal fun buildCcStats(
    machines: List<MachineSummary>,
    period: CcPeriod,
    clock: GymClock = GymClock(),
    bodyWeight: Double? = null,
): CcStats {
    val zone = clock.zoneId
    fun dateOf(timestamp: Long): LocalDate = Instant.ofEpochMilli(clock.trainingDayStart(timestamp)).atZone(zone).toLocalDate()
    val now = clock.now()
    val today = dateOf(now)
    val firstDay = today.minusWeeks(period.weeks.toLong()).plusDays(1)
    val previousFirst = firstDay.minusWeeks(period.weeks.toLong())

    // Future-dated entries (clock changes, manual typos) are ignored everywhere.
    val allSets = machines.flatMap { summary -> summary.recentSets.filter { it.performedAt <= now }.map { summary to it } }
    val byDay = allSets.groupBy { dateOf(it.second.performedAt) }
    val sessions = byDay.filterKeys { it in firstDay..today }.toSortedMap().map { (date, entries) ->
        CcSession(date, entries.minOf { it.second.performedAt }, entries.maxOf { it.second.performedAt },
            entries.map { it.first.machine.id }.distinct().size, entries.size)
    }
    val previousSessions = byDay.keys.count { it >= previousFirst && it < firstDay }

    // Weekly bars cover the period (capped so 1 yr stays readable) and always end with the current week.
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val trainingWeeks = byDay.keys.groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val weeks = (minOf(period.weeks, 26) - 1 downTo 0).map { back ->
        val monday = currentMonday.minusWeeks(back.toLong())
        val days = trainingWeeks[monday].orEmpty()
        CcWeek(monday, days.size, days.sumOf { byDay.getValue(it).size })
    }

    // A streak counts consecutive Monday-Sunday weeks with a training. The current week only extends
    // it, so an untrained Monday does not reset a streak that is still alive.
    var streak = 0
    var cursor = if (currentMonday in trainingWeeks) currentMonday else currentMonday.minusWeeks(1)
    while (cursor in trainingWeeks) { streak++; cursor = cursor.minusWeeks(1) }
    var longest = 0
    var run = 0
    var previousWeek: LocalDate? = null
    trainingWeeks.keys.sorted().forEach { monday ->
        run = if (previousWeek != null && ChronoUnit.WEEKS.between(previousWeek, monday) == 1L) run + 1 else 1
        longest = maxOf(longest, run)
        previousWeek = monday
    }

    val weekdayCounts = List(7) { index -> sessions.count { it.date.dayOfWeek.value - 1 == index } }

    val lifts = mutableListOf<CcLift>()
    val records = mutableListOf<CcRecord>()
    machines.filter { it.machine.trackingMode == TrackingMode.WEIGHT }.forEach { summary ->
        val days = summary.recentSets.filter { it.performedAt <= now }.groupBy { dateOf(it.performedAt) }.toSortedMap()
        if (days.isEmpty()) return@forEach
        val estimates = days.mapValues { (_, sets) -> sessionStrengthScore(sets) }.filterValues { it > 0.0 }
        if (estimates.isEmpty()) return@forEach
        var runningBest: Double? = null
        estimates.forEach { (date, estimate) ->
            val previous = runningBest
            if (previous != null && estimate > previous && date >= firstDay) {
                records += CcRecord(summary.machine.name, date, estimate, previous)
            }
            if (previous == null || estimate > previous) runningBest = estimate
        }
        val inPeriod = estimates.filterKeys { it >= firstDay }
        if (inPeriod.isEmpty()) return@forEach
        val (bestDate, best) = estimates.entries.maxWith(compareBy<Map.Entry<LocalDate, Double>> { it.value }.thenBy { it.key })
            .let { it.key to it.value }
        val points = inPeriod.map { (date, value) -> date.atStartOfDay(zone).toInstant().toEpochMilli() to value }
        val spanDays = ChronoUnit.DAYS.between(inPeriod.keys.first(), inPeriod.keys.last())
        // Under two weeks of history a monthly rate would mostly extrapolate noise.
        val monthly = if (spanDays < 14) null else trendGrowthPercent(points)?.let { it / spanDays * 30.0 }
        val momentum = when {
            monthly == null -> CcMomentum.NEW
            monthly > FLAT_PERCENT_PER_MONTH -> CcMomentum.RISING
            monthly < -FLAT_PERCENT_PER_MONTH -> CcMomentum.FALLING
            else -> CcMomentum.FLAT
        }
        val lastSets = days.getValue(days.lastKey()).filter { it.reps > 0 && it.weight > 0.0 }
        val typicalReps = lastSets.map { it.reps }.sorted().let { if (it.isEmpty()) 1 else it[it.size / 2] }
        lifts += CcLift(
            machineId = summary.machine.id,
            name = summary.machine.name,
            latest = inPeriod.values.last(),
            best = best,
            bestDate = bestDate,
            monthlyTrendPercent = monthly,
            momentum = momentum,
            typicalReps = typicalReps,
            nextPrWeight = nextPrWeight(best, typicalReps),
            daysSinceBest = ChronoUnit.DAYS.between(bestDate, today),
            sessionsInPeriod = inPeriod.size,
        )
    }

    // Balance looks at the last four weeks regardless of the period: it is about what to train next.
    val balanceStart = today.minusDays(27)
    val profiledAreas = machines.flatMap { it.areas }.filter { it.intensity > 0.0 }.map { it.area }.toSet()
    val regions = profiledAreas.map { area ->
        val hits = allSets.filter { (summary, _) -> summary.areas.any { it.area == area && it.intensity > 0.0 } }
        val recent = hits.filter { dateOf(it.second.performedAt) >= balanceStart }
        val weighted = recent.sumOf { (summary, _) -> summary.areas.first { it.area == area }.intensity }
        val lastDate = hits.maxOfOrNull { dateOf(it.second.performedAt) }
        CcRegionLoad(area, weighted / 4.0, lastDate?.let { ChronoUnit.DAYS.between(it, today) })
    }.sortedByDescending { it.weeklySets }

    val periodSets = allSets.filter { dateOf(it.second.performedAt) >= firstDay }
    fun groupLoad(group: Set<BodyArea>) = periodSets.sumOf { (summary, _) ->
        summary.areas.filter { it.area in group }.sumOf { it.intensity }
    }
    val split = CcSplit(groupLoad(PUSH), groupLoad(PULL), groupLoad(LEGS),
        groupLoad(BodyArea.entries.toSet() - PUSH - PULL - LEGS))

    val weightSets = periodSets.filter { it.first.machine.trackingMode == TrackingMode.WEIGHT && it.second.reps > 0 }.map { it.second.reps }
    val repMix = CcRepMix(weightSets.count { it <= 5 }, weightSets.count { it in 6..12 }, weightSets.count { it >= 13 })

    return CcStats(
        period = period,
        today = today,
        sessions = sessions,
        previousSessions = previousSessions,
        weeks = weeks,
        currentStreakWeeks = streak,
        longestStreakWeeks = longest,
        weekdayCounts = weekdayCounts,
        lifts = lifts.sortedWith(compareByDescending<CcLift> { it.monthlyTrendPercent ?: Double.NEGATIVE_INFINITY }.thenBy { it.name }),
        records = records.sortedWith(compareByDescending<CcRecord> { it.date }.thenBy { it.name }),
        regions = regions,
        split = split,
        repMix = repMix,
        bodyWeight = bodyWeight?.takeIf { it.isFinite() && it > 0.0 },
    )
}

/** Inverse Epley, rounded up to the next plate step strictly above the current best. */
internal fun nextPrWeight(best: Double, reps: Int): Double {
    val needed = if (reps <= 1) best else best / (1.0 + reps / 30.0)
    var weight = ceil(needed / PR_STEP_KG - 1e-9) * PR_STEP_KG
    if (setStrengthScore(weight, reps.coerceAtLeast(1)) <= best + 1e-9) weight += PR_STEP_KG
    return weight
}
