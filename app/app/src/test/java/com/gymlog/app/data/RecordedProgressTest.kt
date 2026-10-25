package com.gymlog.app.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordedProgressTest {
    private fun summary(mode: TrackingMode, current: Double, vararg values: Double): MachineSummary {
        val start = Instant.parse("2026-09-01T12:00:00Z").toEpochMilli()
        return MachineSummary(
            Machine(1, "Exercise", "", "", "", ChartRange.ALL, 0, mode, current, 0), emptyList(),
            values.mapIndexed { index, value ->
                SetRecord(index.toLong(), 1, if (mode.usesResistanceLevel()) value.toInt() else 10,
                    if (mode.usesResistanceLevel()) 20.0 else value, start + index * 86_400_000L)
            }.reversed(),
            // Adjustment history must never determine the recorded comparisons.
            listOf(WeightChange(100.0, start + 999), WeightChange(1.0, start)), 0,
        )
    }

    @Test
    fun setupAndRevertedAdjustmentsAreNeutralAndDoNotCount() {
        val untrained = summary(TrackingMode.STRENGTH, 10.0)
        assertEquals(0, untrained.pendingMetricDirection())
        assertEquals(0, untrained.recordedMetricChangeCount())
        assertTrue(untrained.weightIncreases().isEmpty())
        val trained = summary(TrackingMode.STRENGTH, 10.0, 10.0, 10.0)
        assertEquals(0, trained.pendingMetricDirection())
        assertEquals(0, trained.recordedMetricChangeCount())
        assertEquals(1, trained.copy(machine = trained.machine.copy(currentWeight = 11.0)).pendingMetricDirection())
        assertEquals(-1, trained.copy(machine = trained.machine.copy(currentWeight = 9.0)).pendingMetricDirection())
    }

    @Test
    fun onlyRecordedTransitionsCountAndLatestEntryResetsPendingColor() {
        val recorded = summary(TrackingMode.WEIGHT, 45.0, 40.0, 40.0, 45.0, 45.0)
        assertEquals(1, recorded.recordedMetricChangeCount())
        assertEquals(1, recorded.weightIncreases().size)
        assertEquals(0, recorded.pendingMetricDirection())
        val reversed = summary(TrackingMode.WEIGHT, 40.0, 40.0, 45.0, 40.0)
        assertEquals(2, reversed.recordedMetricChangeCount())
        assertEquals(0, reversed.pendingMetricDirection())
    }

    @Test
    fun wattsCompareRecordedPowerRatherThanMinutesAndPersistAsWatts() {
        val watts = summary(TrackingMode.WATTS, 120.0, 100.0, 120.0)
        assertEquals(TrackingMode.WATTS, TrackingMode.fromStorage("WATTS"))
        assertEquals(1, watts.recordedMetricChangeCount())
        assertEquals(0, watts.pendingMetricDirection())
        assertEquals(1, watts.copy(machine = watts.machine.copy(currentWeight = 130.0)).pendingMetricDirection())
    }

    @Test
    fun multipleSetsInOneTrainingDoNotInflateChangeCounts() {
        val base = summary(TrackingMode.WEIGHT, 40.0, 40.0)
        val first = base.recentSets.single()
        val sets = listOf(first, first.copy(id = 2, weight = 45.0, performedAt = first.performedAt + 1),
            first.copy(id = 3, performedAt = first.performedAt + 2))
        assertEquals(0, base.copy(recentSets = sets).recordedMetricChangeCount())
    }
}
