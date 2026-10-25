package com.gymlog.app.data

import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

internal data class ExerciseOverlap(
    val percent: Int?,
    val sharedAreas: Set<BodyArea> = emptySet(),
    val incomplete: Boolean = false,
)

/** Latest two distinct exercises from this training; repeated sets do not fill both slots. */
internal fun recentTrainingExercises(machines: List<MachineSummary>, cutoff: Long): List<MachineSummary> =
    machines.mapNotNull { summary ->
        summary.recentSets.filter { it.performedAt >= cutoff }
            .maxWithOrNull(compareBy<SetRecord> { it.performedAt }.thenBy { it.id })
            ?.let { summary to it }
    }.sortedWith(compareByDescending<Pair<MachineSummary, SetRecord>> { it.second.performedAt }
        .thenByDescending { it.second.id }.thenBy { it.first.machine.id })
        .take(2).map { it.first }

/** Fraction of the candidate's weighted profile exposed to recent work, not a fatigue measurement. */
internal fun exerciseOverlap(candidate: MachineSummary, recent: List<MachineSummary>): ExerciseOverlap {
    val context = recent.distinctBy { it.machine.id }.take(2)
    if (candidate.areas.isEmpty()) return ExerciseOverlap(null, incomplete = true)
    val incomplete = context.any { it.areas.isEmpty() }
    if (context.isNotEmpty() && context.all { it.areas.isEmpty() }) return ExerciseOverlap(null, incomplete = true)
    val recentLoad = mutableMapOf<BodyArea, Double>()
    context.forEachIndexed { index, exercise ->
        val recencyWeight = if (index == 0) 1.0 else 0.5
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

internal fun exerciseTrainingCount(summary: MachineSummary, zone: ZoneId = ZoneId.systemDefault()): Int {
    val clock = GymClock(zoneId = zone)
    return summary.recentSets.map { clock.trainingDayStart(it.performedAt) }.distinct().size
}

internal fun rankOverviewMachines(
    machines: List<MachineSummary>,
    currentTrainingCutoff: Long,
    recent: List<MachineSummary>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<MachineSummary> {
    val currentId = recent.firstOrNull()?.machine?.id
    val counts = machines.associate { it.machine.id to exerciseTrainingCount(it, zone) }
    val overlaps = machines.associate { it.machine.id to exerciseOverlap(it, recent).percent }
    val remaining = machines.filter { it.machine.id != currentId }
    val manualOrder = compareBy<MachineSummary> { it.machine.sortOrder }.thenBy { it.machine.id }
    val automatic = remaining.filter { it.machine.orderGroup == ExerciseOrderGroup.AUTOMATIC }
        .sortedWith(compareBy<MachineSummary>(
            { it.recentSets.any { set -> set.performedAt >= currentTrainingCutoff } },
            { overlaps[it.machine.id] ?: 0 },
            { it.machine.rankingPriority },
            { counts[it.machine.id] ?: 0 },
            { it.machine.sortOrder },
            { it.machine.name.lowercase(Locale.ROOT) },
        ).thenBy { it.machine.id })
    return machines.filter { it.machine.id == currentId } +
        remaining.filter { it.machine.orderGroup == ExerciseOrderGroup.MANUAL_TOP }.sortedWith(manualOrder) +
        automatic +
        remaining.filter { it.machine.orderGroup == ExerciseOrderGroup.MANUAL_BOTTOM }.sortedWith(manualOrder)
}
