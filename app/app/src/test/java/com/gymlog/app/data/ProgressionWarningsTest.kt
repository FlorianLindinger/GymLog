package com.gymlog.app.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionWarningsTest {
    @Test
    fun weightIncreaseWarningDefaultsToSixtyCompleteDaysAndResetsOnRecordedIncrease() {
        val exercise = summary(10)
        val settings = ProgressionSettings(volumeMinimumIncreasePercent = 0)
        assertEquals(60, settings.staleDays)
        fun at(offset: Long) = GymClock(Clock.fixed(start.plusMillis(offset), ZoneOffset.UTC))
        assertNull(progressionWarning(exercise, settings, at(60 * day - 1)))
        assertEquals("60 days no kg increase", progressionWarning(exercise, settings, at(60 * day)))
        assertEquals("61 days no kg increase", progressionWarning(exercise, settings, at(61 * day)))
        assertNull(progressionWarning(exercise, settings.copy(staleDays = 0), at(61 * day)))
        val increased = exercise.copy(recentSets = exercise.recentSets +
            SetRecord(99, 1, 10, 11.0, start.toEpochMilli() + 59 * day))
        assertNull(progressionWarning(increased, settings, at(60 * day)))
    }

    @Test
    fun oldWeekThresholdsConvertToDaysWithoutOverflowOrReenablingDisabledWarnings() {
        assertEquals(60, migratedStaleDays(null, null))
        assertEquals(28, migratedStaleDays(null, 4))
        assertEquals(0, migratedStaleDays(null, 0))
        assertEquals(0, migratedStaleDays(null, -1))
        assertEquals(Int.MAX_VALUE, migratedStaleDays(null, Int.MAX_VALUE))
        assertEquals(75, migratedStaleDays(75, 4))
        assertEquals(0, migratedStaleDays(0, 4))
    }

    @Test
    fun plannedWeightIncreaseHidesStaleBadgeWithoutChangingRecordedProgressOrTrend() {
        val original = summary(10, 10, 10)
        val planned = original.copy(machine = original.machine.copy(currentWeight = 12.0))
        val settings = ProgressionSettings(staleDays = 28, volumeWindowDays = 60)
        assertEquals("Strength score trend +0 %", progressionWarning(planned, settings, clock))
        assertNull(progressionWarning(planned, settings.copy(volumeMinimumIncreasePercent = 0), clock))
        assertEquals(original.weightIncreases(), planned.weightIncreases())
        assertEquals(original.recordedMetricChangeCount(), planned.recordedMetricChangeCount())
        assertEquals("28 days no kg increase", progressionWarning(original, settings.copy(volumeMinimumIncreasePercent = 0), clock))
        val lower = original.copy(machine = original.machine.copy(currentWeight = 9.0))
        assertEquals("28 days no kg increase", progressionWarning(lower, settings.copy(volumeMinimumIncreasePercent = 0), clock))
    }

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
        val settings = ProgressionSettings(useOneRepMax = false, staleDays = 28, volumeWindowDays = 60)
        assertEquals("28 days no kg increase · Strength score trend +0 %", progressionWarning(exercise, settings, clock))
        assertEquals("Strength score trend +0 %", progressionWarning(exercise, settings.copy(staleDays = 0), clock))
        assertEquals("28 days no kg increase", progressionWarning(exercise, settings.copy(volumeMinimumIncreasePercent = 0), clock))
        assertNull(progressionWarning(exercise, settings.copy(staleDays = 0, volumeMinimumIncreasePercent = 0), clock))
    }

    @Test
    fun selectedDayWindowControlsComparisonRatherThanTrainingCount() {
        val exercise = summary(1, 1, 1).let { summary ->
            summary.copy(recentSets = summary.recentSets.mapIndexed { index, set ->
                set.copy(weight = listOf(10.0, 20.0, 21.0)[index])
            })
        }
        val settings = ProgressionSettings(useOneRepMax = false, staleDays = 0, volumeWindowDays = 28, volumeMinimumIncreasePercent = 6)
        assertEquals("Strength score trend +5 %", progressionWarning(exercise, settings, clock))
        assertNull(progressionWarning(exercise, settings.copy(volumeWindowDays = 60), clock))
        assertNull(progressionWarning(exercise, settings.copy(volumeWindowDays = 27), clock))
        assertNull(progressionWarning(exercise, settings.copy(volumeMinimumIncreasePercent = 5), clock))
    }

    @Test
    fun scoreIncludesEverySetAndIgnoresOlderTrainings() {
        val exercise = summary(100, 10, 10).let { it.copy(recentSets = it.recentSets +
            SetRecord(99, 1, 1, 1.0, start.toEpochMilli() + day + 1_000)) }
        val settings = ProgressionSettings(useOneRepMax = false, staleDays = 0, volumeWindowDays = 28)
        assertEquals("Strength score trend -7.4 %", progressionWarning(exercise, settings, clock))
    }

    @Test
    fun recordedWeightIncreaseResetsDayWarningAndOtherModesHaveNoKgWarnings() {
        val exercise = summary(10, 10).let { it.copy(recentSets = it.recentSets +
            SetRecord(99, 1, 10, 11.0, clock.now() - day)) }
        val settings = ProgressionSettings(useOneRepMax = false, volumeMinimumIncreasePercent = 0)
        assertNull(progressionWarning(exercise, settings, clock))
        assertNull(progressionWarning(summary(10).let { it.copy(machine = it.machine.copy(trackingMode = TrackingMode.WATTS)) }, settings, clock))
    }

    @Test
    fun exerciseOverridesReplaceGlobalsIncludingExplicitlyDisabledWarnings() {
        val exercise = summary(10, 10, 10)
        val global = ProgressionSettings(useOneRepMax = false, staleDays = 28, volumeWindowDays = 60)
        val disabled = ProgressionSettings(useOneRepMax = false, staleDays = 0, volumeWindowDays = 0, volumeMinimumIncreasePercent = 0)
        val customized = exercise.copy(machine = exercise.machine.copy(progressionSettings = disabled))
        assertNull(progressionWarning(customized, global, clock))
        val volumeOnly = customized.copy(machine = customized.machine.copy(progressionSettings = disabled.copy(
            volumeWindowDays = 60, volumeMinimumIncreasePercent = 5,
        )))
        assertEquals("Strength score trend +0 %", progressionWarning(volumeOnly, global, clock))
        assertEquals("28 days no kg increase · Strength score trend +0 %", progressionWarning(exercise, global, clock))
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
        val settings = ProgressionSettings(useOneRepMax = false, staleDays = 0, volumeWindowDays = parseWarningSetting("")!!)
        assertNull(progressionWarning(summary(10, 10, 10), settings, clock))
        assertNull(progressionWarning(summary(10, 10, 10), settings.copy(volumeWindowDays = 60,
            volumeMinimumIncreasePercent = parseWarningSetting("")!!), clock))
    }

    @Test
    fun formulaWarningIgnoresGlobalDisplayChoiceAndLegacyExerciseMetricOverride() {
        val exercise = summary(10, 10, 10).let { it.copy(recentSets = it.recentSets +
            SetRecord(99, 1, 10, 10.0, start.toEpochMilli() + 2 * day + 1_000)) }
        val global = ProgressionSettings(useOneRepMax = true, staleDays = 0, volumeWindowDays = 60, volumeMinimumIncreasePercent = 150)
        assertEquals("Strength score trend +100 %", progressionWarning(exercise, global, clock))
        assertEquals("Strength score trend +100 %", progressionWarning(exercise, global.copy(useOneRepMax = false), clock))
        val override = exercise.copy(machine = exercise.machine.copy(progressionSettings = global.copy(useOneRepMax = false)))
        assertEquals("Strength score trend +100 %", progressionWarning(override, global, clock))
        assertNull(progressionWarning(exercise, global.copy(volumeWindowDays = 0), clock))
    }

    @Test
    fun sixtyDaysIsDefaultAndLegacyDisablementSurvivesMigration() {
        assertEquals(60, ProgressionSettings().volumeWindowDays)
        assertEquals(60, migratedTrendWindowDays(null, null))
        assertEquals(60, migratedTrendWindowDays(null, 5))
        assertEquals(0, migratedTrendWindowDays(null, 0))
        assertEquals(30, migratedTrendWindowDays(30, 5))
        assertEquals(0, migratedTrendWindowDays(0, 5))
    }

    @Test
    fun sixtyDayWindowIncludesBoundaryExcludesOldAndFutureEntriesAndSignsLosses() {
        val now = clock.now()
        val boundary = clock.trainingDayStart() - 59 * day
        val exercise = summary().copy(recentSets = listOf(
            SetRecord(1, 1, 1, 100.0, boundary - 1),
            SetRecord(2, 1, 1, 20.0, boundary),
            SetRecord(3, 1, 1, 19.0, now),
            SetRecord(4, 1, 1, 100.0, now + 1),
        ))
        assertEquals("Strength score trend -5 %", progressionWarning(exercise, ProgressionSettings(staleDays = 0), clock))
        assertNull(progressionWarning(exercise, ProgressionSettings(staleDays = 0, volumeWindowDays = 59), clock))
    }

    @Test
    fun calendarDaysFollowSixOClockAndDaylightSaving() {
        val zone = java.time.ZoneId.of("Europe/Berlin")
        val dstClock = GymClock(Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), zone))
        val boundary = Instant.parse("2026-10-24T04:00:00Z").toEpochMilli()
        val exercise = summary().copy(recentSets = listOf(
            SetRecord(1, 1, 1, 100.0, boundary - 1),
            SetRecord(2, 1, 1, 10.0, boundary),
            SetRecord(3, 1, 1, 10.0, dstClock.now()),
        ))
        assertEquals("Strength score trend +0 %", progressionWarning(exercise,
            ProgressionSettings(staleDays = 0, volumeWindowDays = 3), dstClock))
    }
}
