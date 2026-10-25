package com.gymlog.app.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionWarningsTest {
    private val start = Instant.parse("2026-09-01T12:00:00Z")
    private val day = 86_400_000L
    private val clock = GymClock(Clock.fixed(start.plusSeconds(28 * 86_400L), ZoneOffset.UTC))

    private fun summary(vararg reps: Int) = MachineSummary(
        Machine(1, "Press", "", "", "", ChartRange.ALL, 0, TrackingMode.WEIGHT, 10.0, 0),
        emptyList(),
        reps.mapIndexed { index, count -> SetRecord(index.toLong(), 1, count, 10.0, start.toEpochMilli() + index * day) },
        emptyList(), 0,
    )

    @Test
    fun warningsUseCompactTextAndOnlySeparateTwoWarnings() {
        val exercise = summary(10, 10, 10)
        val settings = ProgressionSettings(useOneRepMax = false, staleWeeks = 4, volumeWindowTrainings = 3)
        assertEquals("4 weeks no kg increase · volume trend 0 %", progressionWarning(exercise, settings, clock))
        assertEquals("volume trend 0 %", progressionWarning(exercise, settings.copy(staleWeeks = 0), clock))
        assertEquals("4 weeks no kg increase", progressionWarning(exercise, settings.copy(volumeMinimumIncreasePercent = 0), clock))
        assertNull(progressionWarning(exercise, settings.copy(staleWeeks = 0, volumeMinimumIncreasePercent = 0), clock))
    }

    @Test
    fun selectedWindowControlsVolumeComparisonAndRequiresEnoughTrainings() {
        val exercise = summary(10, 20, 21)
        val settings = ProgressionSettings(useOneRepMax = false, staleWeeks = 0, volumeWindowTrainings = 2, volumeMinimumIncreasePercent = 6)
        assertEquals("volume trend 5 %", progressionWarning(exercise, settings, clock))
        assertNull(progressionWarning(exercise, settings.copy(volumeWindowTrainings = 3), clock))
        assertNull(progressionWarning(exercise, settings.copy(volumeWindowTrainings = 4), clock))
        assertNull(progressionWarning(exercise, settings.copy(volumeMinimumIncreasePercent = 5), clock))
    }

    @Test
    fun volumeUsesAllSetsInEachTrainingDayAndIgnoresOlderTrainings() {
        val exercise = summary(100, 10, 20).let { it.copy(recentSets = it.recentSets +
            SetRecord(99, 1, 10, 10.0, start.toEpochMilli() + day + 1_000)) }
        val settings = ProgressionSettings(useOneRepMax = false, staleWeeks = 0, volumeWindowTrainings = 2)
        assertEquals("volume trend 0 %", progressionWarning(exercise, settings, clock))
    }

    @Test
    fun recordedWeightIncreaseResetsWeekWarningAndOtherModesHaveNoKgWarnings() {
        val exercise = summary(10, 10).let { it.copy(recentSets = it.recentSets +
            SetRecord(99, 1, 10, 11.0, clock.now() - day)) }
        val settings = ProgressionSettings(useOneRepMax = false, volumeMinimumIncreasePercent = 0)
        assertNull(progressionWarning(exercise, settings, clock))
        assertNull(progressionWarning(summary(10).let { it.copy(machine = it.machine.copy(trackingMode = TrackingMode.WATTS)) }, settings, clock))
    }

    @Test
    fun exerciseOverridesReplaceGlobalsIncludingExplicitlyDisabledWarnings() {
        val exercise = summary(10, 10, 10)
        val global = ProgressionSettings(useOneRepMax = false, staleWeeks = 4, volumeWindowTrainings = 3)
        val disabled = ProgressionSettings(useOneRepMax = false, staleWeeks = 0, volumeWindowTrainings = 0, volumeMinimumIncreasePercent = 0)
        val customized = exercise.copy(machine = exercise.machine.copy(progressionSettings = disabled))
        assertNull(progressionWarning(customized, global, clock))
        val volumeOnly = customized.copy(machine = customized.machine.copy(progressionSettings = disabled.copy(
            volumeWindowTrainings = 3, volumeMinimumIncreasePercent = 5,
        )))
        assertEquals("volume trend 0 %", progressionWarning(volumeOnly, global, clock))
        assertEquals("4 weeks no kg increase · volume trend 0 %", progressionWarning(exercise, global, clock))
        assertNull(progressionWarning(exercise, disabled, clock))
    }

    @Test
    fun blankAndZeroFieldsDisableWarningsAndNeverUseDefaults() {
        assertEquals(0, parseWarningSetting(""))
        assertEquals(0, parseWarningSetting("   "))
        assertEquals(0, parseWarningSetting("0"))
        assertEquals(5, parseWarningSetting("5"))
        assertNull(parseWarningSetting("-1"))
        assertNull(parseWarningSetting("99999999999999999999"))
        val settings = ProgressionSettings(useOneRepMax = false, staleWeeks = 0, volumeWindowTrainings = parseWarningSetting("")!!)
        assertNull(progressionWarning(summary(10, 10, 10), settings, clock))
        assertNull(progressionWarning(summary(10, 10, 10), settings.copy(volumeWindowTrainings = 3,
            volumeMinimumIncreasePercent = parseWarningSetting("")!!), clock))
    }

    @Test
    fun oneRepMaxWarningUsesBestSetRatherThanTotalVolumeAndCanBeOverridden() {
        val exercise = summary(10, 10, 10).let { it.copy(recentSets = it.recentSets +
            SetRecord(99, 1, 10, 10.0, start.toEpochMilli() + 2 * day + 1_000)) }
        val global = ProgressionSettings(useOneRepMax = true, staleWeeks = 0, volumeWindowTrainings = 3)
        assertEquals("1RM trend 0 %", progressionWarning(exercise, global, clock))
        assertNull(progressionWarning(exercise, global.copy(useOneRepMax = false), clock))
        val override = exercise.copy(machine = exercise.machine.copy(progressionSettings = global.copy(useOneRepMax = false)))
        assertNull(progressionWarning(override, global, clock))
        assertNull(progressionWarning(exercise, global.copy(volumeWindowTrainings = 0), clock))
    }
}
