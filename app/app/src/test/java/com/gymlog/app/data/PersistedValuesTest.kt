package com.gymlog.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PersistedValuesTest {
    @Test fun `unknown persisted values use safe defaults`() {
        assertEquals(TrackingMode.WEIGHT, TrackingMode.fromStorage("typo"))
        assertEquals(ChartRange.THREE_MONTHS, ChartRange.fromStorage("typo"))
    }

    @Test fun `stored chart range round trips`() {
        ChartRange.entries.forEach { range ->
            assertEquals(range, ChartRange.fromStorage(range.storageValue))
        }
    }

    @Test fun `volume is reps times weight`() {
        assertEquals(180.0, HistorySet(1, 2, "Bench", "WEIGHT", 12, 15.0, 0).volume, 0.0)
    }
}
