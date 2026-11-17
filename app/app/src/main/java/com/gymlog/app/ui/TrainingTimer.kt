package com.gymlog.app.ui

import com.gymlog.app.data.GymClock
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.TrackingMode
import com.gymlog.app.data.usesResistanceLevel
import java.time.ZoneId

internal data class TrainingTimerSnapshot(
    val first: Long? = null,
    val last: Long? = null,
    val estimatedStart: Long? = null,
    val completedExercises: Int = 0,
)

/** History-derived values do not change when the displayed clock advances. */
internal fun trainingTimerSnapshot(machines: List<MachineSummary>, zone: ZoneId): TrainingTimerSnapshot {
    val allSets = machines.flatMap { it.recentSets }
    val last = allSets.maxOfOrNull { it.performedAt } ?: return TrainingTimerSnapshot()
    val cutoff = GymClock(zoneId = zone).trainingDayStart(last)
    val sessionSets = allSets.filter { it.performedAt >= cutoff }
    val firstSet = sessionSets.minBy { it.performedAt }
    val firstMode = machines.first { it.machine.id == firstSet.machineId }.machine.trackingMode
    val lead = if (firstMode.usesResistanceLevel() || firstMode == TrackingMode.DURATION) {
        (firstSet.weight * 60_000L).toLong()
    } else sessionSets.size * 3L * 60_000L
    return TrainingTimerSnapshot(firstSet.performedAt, last, firstSet.performedAt - lead,
        sessionSets.map { it.machineId }.distinct().size)
}

/** Short sessions allow a longer break; use the stopped duration so the deadline cannot move mid-break. */
internal fun trainingIdleDeadline(last: Long, estimatedStart: Long): Long =
    last + (if (last - estimatedStart < 60L * 60_000L) 30L else 10L) * 60_000L

internal fun trainingTimerActive(now: Long, last: Long?, estimatedStart: Long?): Boolean =
    last != null && estimatedStart != null && now < trainingIdleDeadline(last, estimatedStart)

/** Wake only for a visible minute change or the exact transition to idle. */
internal fun nextTrainingTimerDelay(now: Long, last: Long?, estimatedStart: Long?): Long? {
    if (last == null || estimatedStart == null) return null
    val untilIdle = trainingIdleDeadline(last, estimatedStart) - now
    if (untilIdle <= 0L) return null
    val untilMinute = 60_000L - (now - estimatedStart).coerceAtLeast(0L) % 60_000L
    return minOf(untilMinute, untilIdle)
}
