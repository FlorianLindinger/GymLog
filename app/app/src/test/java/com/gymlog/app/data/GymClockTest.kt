package com.gymlog.app.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class GymClockTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test fun `training day before six belongs to previous local day`() {
        val clock = GymClock(Clock.fixed(Instant.parse("2026-01-15T03:30:00Z"), berlin), berlin)
        assertEquals(Instant.parse("2026-01-14T05:00:00Z").toEpochMilli(), clock.trainingDayStart())
    }

    @Test fun `training day after six begins on the same local day`() {
        val clock = GymClock(Clock.fixed(Instant.parse("2026-01-15T06:30:00Z"), berlin), berlin)
        assertEquals(Instant.parse("2026-01-15T05:00:00Z").toEpochMilli(), clock.trainingDayStart())
    }
}
