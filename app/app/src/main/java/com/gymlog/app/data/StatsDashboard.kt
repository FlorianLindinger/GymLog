package com.gymlog.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

internal data class DashboardPeriod(val first: LocalDate, val last: LocalDate) {
    init { require(!last.isBefore(first)) }
    val days: Long get() = ChronoUnit.DAYS.between(first, last) + 1
    fun contains(date: LocalDate): Boolean = !date.isBefore(first) && !date.isAfter(last)
    fun previous() = DashboardPeriod(first.minusDays(days), first.minusDays(1))
}

internal data class DashboardTraining(val date: LocalDate, val entries: List<HistorySet>) {
    val weightSets get() = entries.filter { it.trackingMode == TrackingMode.WEIGHT }
    val weightVolume get() = weightSets.sumOf { it.volume }
    val cardioMinutes get() = entries.filter { it.trackingMode != TrackingMode.WEIGHT }.sumOf { it.weight }
    // Unknown elapsed time is deliberately not represented as a measured zero-minute workout.
    val recordedMinutes: Double? get() {
        val span = entries.maxOfOrNull { it.performedAt }?.minus(entries.minOf { it.performedAt }) ?: return null
        return span.takeIf { it > 0 }?.div(60_000.0)
    }
}

internal data class DashboardTotals(val trainings: List<DashboardTraining>) {
    val sessions get() = trainings.size
    val weightSets get() = trainings.sumOf { it.weightSets.size }
    val reps get() = trainings.sumOf { day -> day.weightSets.sumOf { it.reps } }
    val volume get() = trainings.sumOf { it.weightVolume }
    val cardioMinutes get() = trainings.sumOf { it.cardioMinutes }
    val exercises get() = trainings.flatMap { it.entries }.map { it.machineId }.distinct().size
    val recordedMinutes get() = trainings.sumOf { it.recordedMinutes ?: 0.0 }
    val knownDurationCount get() = trainings.count { it.recordedMinutes != null }
    val averageMinutes get() = trainings.mapNotNull { it.recordedMinutes }.takeIf { it.isNotEmpty() }?.average()
}

internal data class DashboardExerciseDay(val date: LocalDate, val sets: List<SetRecord>, val mode: TrackingMode) {
    val metric: Double get() = when (mode) {
        TrackingMode.WEIGHT -> trainingPerformance(sets, true)
        TrackingMode.STRENGTH, TrackingMode.WATTS -> sets.maxOfOrNull { it.reps.toDouble() } ?: 0.0
        TrackingMode.DURATION -> sets.sumOf { it.weight }
    }
    val volume get() = sets.sumOf { it.weight * it.reps }
}

internal data class DashboardExercise(
    val summary: MachineSummary,
    val days: List<DashboardExerciseDay>,
    val previousDays: List<DashboardExerciseDay>,
    val olderBest: Double?,
    val warning: String?,
    val today: LocalDate,
    val lastRecordedDate: LocalDate?,
) {
    val mode get() = summary.machine.trackingMode
    val label get() = when (mode) {
        TrackingMode.WEIGHT -> "1RM"
        TrackingMode.WATTS -> "Peak power"
        TrackingMode.STRENGTH -> "Peak level"
        TrackingMode.DURATION -> "Duration"
    }
    val unit get() = when (mode) {
        TrackingMode.WEIGHT -> "kg"
        TrackingMode.WATTS -> "W"
        TrackingMode.STRENGTH -> "Lv."
        TrackingMode.DURATION -> "min"
    }
    val latest get() = days.lastOrNull()?.metric
    val best get() = days.maxOfOrNull { it.metric }
    val baseline get() = previousDays.lastOrNull() ?: days.firstOrNull()?.takeIf { days.size >= 2 }
    val changePercent: Double? get() = if (mode == TrackingMode.STRENGTH) null else
        latest?.let { dashboardPercentChange(it, baseline?.metric) }
    val isRecord get() = olderBest != null && olderBest > 0 && best != null && best!! > olderBest
    val daysSinceLast: Long? get() = lastRecordedDate?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) }
    val averageGap: Double? get() = if (days.size < 2) null else
        ChronoUnit.DAYS.between(days.first().date, days.last().date).toDouble() / (days.size - 1)
}

internal data class DashboardWeek(val date: LocalDate, val sessions: Int, val coveredDays: Int, val complete: Boolean)
internal data class DashboardRegion(val area: BodyArea, val weightedSets: Double, val color: Long)

internal data class StatsDashboardSnapshot(
    val period: DashboardPeriod,
    val current: DashboardTotals,
    val previous: DashboardTotals,
    val exercises: List<DashboardExercise>,
    val weeks: List<DashboardWeek>,
    val regions: List<DashboardRegion>,
    val missingProfiles: Int,
    val higherRepSets: Int,
) {
    val medianStrengthChange: Double? get() {
        val changes = exercises.filter { it.mode == TrackingMode.WEIGHT }.mapNotNull { it.changePercent }.sorted()
        if (changes.isEmpty()) return null
        val middle = changes.size / 2
        return if (changes.size % 2 == 0) (changes[middle - 1] + changes[middle]) / 2 else changes[middle]
    }
    val records get() = exercises.filter { it.isRecord }
}

internal fun dashboardPercentChange(current: Double, previous: Double?): Double? =
    previous?.takeIf { it > 0.0 }?.let { (current - it) / it * 100.0 }

internal fun buildStatsDashboard(
    machines: List<MachineSummary>,
    period: DashboardPeriod,
    settings: ProgressionSettings,
    clock: GymClock = GymClock(),
): StatsDashboardSnapshot {
    val now = clock.now()
    fun date(time: Long): LocalDate = Instant.ofEpochMilli(clock.trainingDayStart(time)).atZone(clock.zoneId).toLocalDate()
    val today = date(now)
    val previousPeriod = period.previous()
    val allEntries = machines.flatMap { summary -> summary.recentSets.filter { it.performedAt <= now }.map {
        HistorySet(it.id, it.machineId, summary.machine.name, summary.machine.trackingMode, it.reps, it.weight, it.performedAt)
    } }
    val trainings = allEntries.groupBy { date(it.performedAt) }.toSortedMap()
        .map { (date, entries) -> DashboardTraining(date, entries) }
    val current = DashboardTotals(trainings.filter { period.contains(it.date) })
    val previous = DashboardTotals(trainings.filter { previousPeriod.contains(it.date) })
    val exercises = machines.map { summary ->
        val allDays = summary.recentSets.filter { it.performedAt <= now }.groupBy { date(it.performedAt) }.toSortedMap()
            .map { (date, sets) -> DashboardExerciseDay(date, sets, summary.machine.trackingMode) }
        DashboardExercise(
            summary, allDays.filter { period.contains(it.date) }, allDays.filter { previousPeriod.contains(it.date) },
            allDays.filter { it.date < period.first }.maxOfOrNull { it.metric },
            progressionWarning(summary, settings, clock), today, allDays.lastOrNull()?.date,
        )
    }
    val weekDays = generateSequence(period.first) { it.plusDays(1) }.take(period.days.toInt())
        .groupBy { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val weeks = weekDays.map { (week, days) ->
        DashboardWeek(week, current.trainings.count { it.date in days }, days.size, days.size == 7 && days.last() < today)
    }
    val regions = mutableMapOf<BodyArea, DashboardRegion>()
    exercises.filter { it.mode == TrackingMode.WEIGHT }.forEach { exercise ->
        val count = exercise.days.sumOf { it.sets.size }
        if (count > 0) exercise.summary.areas.forEach { assignment ->
            val previousRegion = regions[assignment.area]
            regions[assignment.area] = DashboardRegion(assignment.area,
                (previousRegion?.weightedSets ?: 0.0) + count * assignment.strength.value, assignment.displayColor)
        }
    }
    return StatsDashboardSnapshot(
        period, current, previous, exercises, weeks, regions.values.sortedByDescending { it.weightedSets },
        exercises.count { it.days.isNotEmpty() && it.summary.areas.isEmpty() },
        current.trainings.sumOf { day -> day.weightSets.count { it.reps > 10 } },
    )
}
