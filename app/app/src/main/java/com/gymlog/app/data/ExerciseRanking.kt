package com.gymlog.app.data

import java.time.ZoneId
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.pow

internal data class ExerciseOverlap(
    val percent: Int?,
    val sharedAreas: Set<BodyArea> = emptySet(),
    val incomplete: Boolean = false,
)

/** All distinct exercises from this training, newest first. */
internal fun recentTrainingExercises(machines: List<MachineSummary>, cutoff: Long): List<MachineSummary> =
    machines.mapNotNull { summary ->
        summary.recentSets.filter { it.performedAt >= cutoff }
            .maxWithOrNull(compareBy<SetRecord> { it.performedAt }.thenBy { it.id })
            ?.let { summary to it }
    }.sortedWith(compareByDescending<Pair<MachineSummary, SetRecord>> { it.second.performedAt }
        .thenByDescending { it.second.id }.thenBy { it.first.machine.id })
        .map { it.first }

/** Fraction of the candidate's weighted profile exposed to recent work, not a fatigue measurement. */
internal fun exerciseOverlap(candidate: MachineSummary, recent: List<MachineSummary>): ExerciseOverlap {
    val context = recent.distinctBy { it.machine.id }
    if (candidate.areas.isEmpty()) return ExerciseOverlap(null, incomplete = true)
    val incomplete = context.any { it.areas.isEmpty() }
    if (context.isNotEmpty() && context.all { it.areas.isEmpty() }) return ExerciseOverlap(null, incomplete = true)
    val recentLoad = mutableMapOf<BodyArea, Double>()
    context.forEachIndexed { index, exercise ->
        val recencyWeight = 0.5.pow(index)
        exercise.areas.forEach { assignment ->
            recentLoad[assignment.area] = ((recentLoad[assignment.area] ?: 0.0) + assignment.strength.value * recencyWeight)
                .coerceAtMost(1.0)
        }
    }
    val total = candidate.areas.sumOf { it.strength.value }
    val overlap = candidate.areas.sumOf { it.strength.value * (recentLoad[it.area] ?: 0.0) }
    return ExerciseOverlap(
        percent = (100.0 * overlap / total).roundToInt().coerceIn(0, 100),
        sharedAreas = candidate.areas.mapNotNullTo(mutableSetOf()) { it.area.takeIf(recentLoad::containsKey) },
        incomplete = incomplete,
    )
}

/** Seven equal-width bins shared by ranking and outline colors; unknown profiles use the lowest bin. */
internal fun exerciseOverlapBin(percent: Int?): Int = ((percent ?: 0).coerceIn(0, 100) * 7 / 100).coerceAtMost(6)

internal fun exerciseTrainingCount(
    summary: MachineSummary,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Long = System.currentTimeMillis(),
): Int {
    val clock = GymClock(zoneId = zone)
    val start = clock.trainingDayStart(Instant.ofEpochMilli(now).atZone(zone).minusMonths(2).toInstant().toEpochMilli())
    return summary.recentSets.filter { it.performedAt in start..now }
        .map { clock.trainingDayStart(it.performedAt) }.distinct().size
}

internal fun rankOverviewMachines(
    machines: List<MachineSummary>,
    currentTrainingCutoff: Long,
    recent: List<MachineSummary>,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Long = System.currentTimeMillis(),
): List<MachineSummary> {
    val currentId = recent.firstOrNull()?.machine?.id
    val counts = machines.associate { it.machine.id to exerciseTrainingCount(it, zone, now) }
    val overlaps = machines.associate { it.machine.id to exerciseOverlap(it, recent).percent }
    val remaining = machines.filter { it.machine.id != currentId }
    val completionSets = remaining.mapNotNull { summary ->
        summary.recentSets.filter { it.performedAt >= currentTrainingCutoff }
            .maxWithOrNull(compareBy<SetRecord> { it.performedAt }.thenBy { it.id })
            ?.let { summary.machine.id to it }
    }.toMap()
    val unfinished = remaining.filter { it.machine.id !in completionSets }
    val completed = remaining.filter { it.machine.id in completionSets }
        .sortedWith(compareBy<MachineSummary> { completionSets.getValue(it.machine.id).performedAt }
            .thenBy { completionSets.getValue(it.machine.id).id }.thenBy { it.machine.id })
    val manualOrder = compareBy<MachineSummary> { it.machine.sortOrder }.thenBy { it.machine.id }
    val automatic = unfinished.filter { it.machine.orderGroup == ExerciseOrderGroup.AUTOMATIC }
        .sortedWith(compareBy<MachineSummary>(
            { exerciseOverlapBin(overlaps[it.machine.id]) },
            { counts[it.machine.id] ?: 0 },
            { it.machine.rankingPriority },
            { it.machine.sortOrder },
            { it.machine.name.lowercase(Locale.ROOT) },
        ).thenBy { it.machine.id })
    return machines.filter { it.machine.id == currentId } +
        unfinished.filter { it.machine.orderGroup == ExerciseOrderGroup.MANUAL_TOP }.sortedWith(manualOrder) +
        automatic +
        unfinished.filter { it.machine.orderGroup == ExerciseOrderGroup.MANUAL_BOTTOM }.sortedWith(manualOrder) +
        completed
}
