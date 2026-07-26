package com.gymlog.app.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Time is a dependency so database behaviour can be tested without the device clock. */
data class GymClock(
    val clock: Clock = Clock.systemDefaultZone(),
    val zoneId: ZoneId = clock.zone,
) {
    fun now(): Long = clock.millis()

    /** A training day starts at 06:00 local time. */
    fun trainingDayStart(timestamp: Long = now()): Long {
        val local = Instant.ofEpochMilli(timestamp).atZone(zoneId)
        val date = local.toLocalDate().minusDays(if (local.hour < 6) 1 else 0)
        return date.atTime(6, 0).atZone(zoneId).toInstant().toEpochMilli()
    }
}