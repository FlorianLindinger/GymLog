package com.gymlog.app.data

import java.time.ZoneId
import java.time.Instant
import java.time.Clock
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

/**
 * Conservative planning heuristics, not measured recovery rates. Larger factors retain overlap longer.
 * Eccentric limb comparisons suggest slower arm recovery, but do not establish universal ratios:
 * https://pubmed.ncbi.nlm.nih.gov/20852880/ and https://pubmed.ncbi.nlm.nih.gov/16007451/.
 * Keep unstudied regions neutral; see docs/recovery-weighting.md for limitations.
 */
internal val BodyArea.recoveryFactor: Double get() = when (this) {
    BodyArea.BICEPS, BodyArea.TRICEPS -> 1.1
    BodyArea.QUADS, BodyArea.HAMSTRINGS -> 0.9
    BodyArea.CORE -> 1.25 // 80% of the previous decay rate: 1 / 0.8.
    BodyArea.CHEST, BodyArea.UPPER_BACK, BodyArea.LOWER_BACK, BodyArea.TRAPS,
    BodyArea.SHOULDERS, BodyArea.FOREARMS, BodyArea.GLUTES,
    BodyArea.ADDUCTORS, BodyArea.ABDUCTORS, BodyArea.CALVES -> 1.0
}

/** One exercise's contribution from a previous training day. */
internal data class PriorExerciseExposure(val summary: MachineSummary, val weight: Double)

/** Each exercise contributes once per previous training day, independent of logging order (default 40%, 20%, 10%). */
internal fun priorExerciseExposures(
    machines: List<MachineSummary>,
    now: Long,
    zone: ZoneId,
    priorDays: PriorDayFatigue = PriorDayFatigue(),
): List<PriorExerciseExposure> {
    val clock = GymClock(zoneId = zone)
    val today = Instant.ofEpochMilli(clock.trainingDayStart(now)).atZone(zone)
    return (1L..3L).flatMap { days ->
        val weight = priorDays.weights[days.toInt() - 1]
        // A disabled day must not add context either; it would otherwise still mark regions as shared.
        if (weight <= 0.0) return@flatMap emptyList()
        val start = today.minusDays(days).toInstant().toEpochMilli()
        val end = today.minusDays(days - 1).toInstant().toEpochMilli()
        machines.filter { summary -> summary.recentSets.any { it.performedAt >= start && it.performedAt < end } }
            .map { PriorExerciseExposure(it, weight) }
    }
}

/** Strongest intensity-weighted regional clash, using cumulative recent work rather than a profile average. */
internal fun exerciseOverlap(candidate: MachineSummary, recent: List<MachineSummary>, prior: List<PriorExerciseExposure> = emptyList()): ExerciseOverlap {
    val context = recent.distinctBy { it.machine.id }
    if (candidate.areas.isEmpty()) return ExerciseOverlap(null, incomplete = true)
    val allContext = context + prior.map { it.summary }
    val incomplete = allContext.any { it.areas.isEmpty() }
    if (allContext.isNotEmpty() && allContext.all { it.areas.isEmpty() }) return ExerciseOverlap(null, incomplete = true)
    val recentLoad = mutableMapOf<BodyArea, Double>()
    context.forEachIndexed { index, exercise ->
        exercise.areas.forEach { assignment ->
            // Preserve the 10% floor while applying per-region decay to this training.
            val weight = 0.5.pow(index / assignment.area.recoveryFactor).coerceAtLeast(0.1)
            recentLoad[assignment.area] = ((recentLoad[assignment.area] ?: 0.0) + assignment.intensity * weight).coerceAtMost(1.0)
        }
    }
    prior.forEach { exposure ->
        exposure.summary.areas.forEach { assignment ->
            recentLoad[assignment.area] = ((recentLoad[assignment.area] ?: 0.0) + assignment.intensity * exposure.weight).coerceAtMost(1.0)
        }
    }
    if (candidate.areas.none { it.intensity > 0.0 }) return ExerciseOverlap(null, incomplete = true)
    // Adding unrelated regions must never dilute an already fatigued region.
    val overlap = candidate.areas.maxOf { it.intensity * (recentLoad[it.area] ?: 0.0) }
    return ExerciseOverlap(
        // Summed decimal weights can land just below a half-percent (for example 52.5%).
        percent = (100.0 * overlap + 1e-9).roundToInt().coerceIn(0, 100),
        sharedAreas = candidate.areas.mapNotNullTo(mutableSetOf()) { it.area.takeIf(recentLoad::containsKey) },
        incomplete = incomplete,
    )
}

/** Four configurable bins; lower boundaries are inclusive and unknown profiles use green. */
internal fun exerciseOverlapBin(percent: Int?, bands: FatigueBands = FatigueBands()): Int {
    val score = (percent ?: 0).coerceIn(0, 100)
    return when {
        score < bands.yellowFrom -> 0
        score < bands.orangeFrom -> 1
        score < bands.redFrom -> 2
        else -> 3
    }
}

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

/** Small boosts within one overlap/priority group; three recent trainings exhaust the catch-up advantage. */
internal fun overviewOrderBias(trainingCount: Int, peerCount: Double, warningCount: Int): Double =
    (peerCount.coerceIn(0.0, 3.0) - trainingCount.coerceIn(0, 3)) * 0.25 +
        warningCount.coerceIn(0, 2) * 0.25

internal fun rankOverviewMachines(
    machines: List<MachineSummary>,
    currentTrainingCutoff: Long,
    recent: List<MachineSummary>,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Long = System.currentTimeMillis(),
    prior: List<PriorExerciseExposure> = priorExerciseExposures(machines, now, zone),
    progressionSettings: ProgressionSettings? = null,
    fatigueBands: FatigueBands = FatigueBands(),
): List<MachineSummary> {
    val currentId = recent.firstOrNull()?.machine?.id
    val counts = machines.associate { it.machine.id to exerciseTrainingCount(it, zone, now) }
    val overlaps = machines.associate { it.machine.id to exerciseOverlap(it, recent, prior).percent }
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
    val automaticCandidates = unfinished.filter { it.machine.orderGroup == ExerciseOrderGroup.AUTOMATIC }
    val peerCount = automaticCandidates.map { counts.getValue(it.machine.id).coerceAtMost(3) }.average()
        .takeIf { it.isFinite() } ?: 0.0
    val clock = GymClock(Clock.fixed(Instant.ofEpochMilli(now), zone), zone)
    val biases = automaticCandidates.associate { summary ->
        val warnings = progressionSettings?.let { progressionWarningReasons(summary, it, clock).size } ?: 0
        summary.machine.id to overviewOrderBias(counts.getValue(summary.machine.id), peerCount, warnings)
    }
    val automatic = automaticCandidates
        .sortedWith(compareBy<MachineSummary>(
            { exerciseOverlapBin(overlaps[it.machine.id], fatigueBands) },
            { it.machine.rankingPriority },
            { -biases.getValue(it.machine.id) },
            { it.machine.sortOrder },
            { it.machine.name.lowercase(Locale.ROOT) },
        ).thenBy { it.machine.id })
    return machines.filter { it.machine.id == currentId } +
        unfinished.filter { it.machine.orderGroup == ExerciseOrderGroup.MANUAL_TOP }.sortedWith(manualOrder) +
        automatic +
        unfinished.filter { it.machine.orderGroup == ExerciseOrderGroup.MANUAL_BOTTOM }.sortedWith(manualOrder) +
        completed
}
