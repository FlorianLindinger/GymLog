package com.gymlog.app.ui

import com.gymlog.app.data.ChartRange
import com.gymlog.app.data.Machine
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.SetRecord
import com.gymlog.app.data.TrackingMode
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class AutoFitTextTest {
    @Test
    fun fittingTextKeepsPreferredSizeWithOneMeasurement() {
        var calls = 0
        assertEquals(16f, fittedSingleLineFontSize(16f, 200) { calls++; 100 }, 0f)
        assertEquals(1, calls)
    }

    @Test
    fun longTextFitsWithoutTwelveSearchMeasurements() {
        var calls = 0
        val size = fittedSingleLineFontSize(16f, 100) { calls++; ceil(it * 20).toInt() }
        assertTrue(ceil(size * 20) <= 100)
        assertTrue(size in 4.75f..5f)
        assertTrue(calls <= 3)
    }

    @Test
    fun widthCorrectionHandlesFixedLetterSpacingAndMinimumSize() {
        var calls = 0
        val size = fittedSingleLineFontSize(16f, 100) { calls++; ceil(it * 10 + 30).toInt() }
        assertTrue(ceil(size * 10 + 30) <= 100)
        assertTrue(size in 6.5f..7f)
        assertTrue(calls <= 6)
        assertEquals(1f, fittedSingleLineFontSize(16f, 0) { 1000 }, 0f)
    }

    @Test
    fun currentSetsUseSixOClockBoundariesIncludingDaylightSavingAndExcludeFutureDays() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = Instant.parse("2026-10-25T04:30:00Z").toEpochMilli()
        val times = listOf("2026-10-24T03:59:59Z", "2026-10-24T04:00:00Z", "2026-10-25T04:30:00Z", "2026-10-25T05:00:00Z")
            .map { Instant.parse(it).toEpochMilli() }
        val summary = MachineSummary(
            Machine(1, "Press", "", "", "", ChartRange.ALL, 0, TrackingMode.WEIGHT, 10.0, 0), emptyList(),
            times.reversed().mapIndexed { index, time -> SetRecord(index.toLong(), 1, 10, 10.0, time) }, emptyList(), 0,
        )
        assertEquals(times.subList(1, 3), currentTrainingUnitSets(summary, now, zone).map { it.performedAt })
    }
}
