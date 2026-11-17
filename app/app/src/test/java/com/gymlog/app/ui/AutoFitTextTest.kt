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
    fun longHistoriesOnlyMeasurePrefixesNearTheVisibleWidth() {
        val text = "Last: " + List(10_000) { "12,10,8" }.joinToString(" | ")
        var measuredCharacters = 0
        val end = overviewHistoryPrefixLength(text) {
            measuredCharacters += it.length
            assertTrue("Must not lay out thousands of offscreen entries", it.length <= 190)
            it.length <= 80
        }
        assertEquals("Last: 12,10,8 | 12,10,8 | 12,10,8 | 12,10,8 | 12,10,8 | 12,10,8 | 12,10,8".length, end)
        assertTrue(measuredCharacters < 1000)
    }

    @Test
    fun historyFittingKeepsTheLongestWholePrefixOrAtLeastTheNewestEntry() {
        for (count in listOf(1, 2, 19, 1000)) {
            val entries = List(count) { if (it % 2 == 0) "12,10,8" else "5" }
            val text = "Last: " + entries.joinToString(" | ")
            val prefixes = entries.indices.map { "Last: " + entries.take(it + 1).joinToString(" | ") }
            for (width in listOf(0, 1, 12, 30, 100, 100_000)) {
                val expected = prefixes.lastOrNull { it.length <= width } ?: prefixes.first()
                assertEquals(expected.length, overviewHistoryPrefixLength(text) { it.length <= width })
            }
        }
    }

    @Test
    fun timedHistoryCanUseMoreThanEightEntriesAndFitsWholeEntries() {
        val summary = MachineSummary(
            Machine(1, "Time", "", "", "", ChartRange.ALL, 0, TrackingMode.DURATION, 10.0, 0), emptyList(),
            (1..20).map { SetRecord(it.toLong(), 1, 0, 5.0, it * 60_000L) }, emptyList(), 0,
        )
        val text = sessionPatternText(summary).text
        assertEquals(20, Regex("5").findAll(text).count())
        val end = overviewHistoryPrefixLength(text) { it.length <= 90 }
        val shown = text.substring(0, end)
        assertTrue(Regex("5").findAll(shown).count() > 8)
        assertTrue(shown.endsWith("5"))
        assertEquals(text.length, overviewHistoryPrefixLength(text) { true })
        assertEquals("Last: 5".length, overviewHistoryPrefixLength(text) { false })
    }

    @Test
    fun readableMinimumStopsHistoryShrinkingOnNarrowCards() {
        assertEquals(12f, fittedSingleLineFontSize(14f, 100, minimumSize = 12f) { ceil(it * 20).toInt() }, 0f)
        assertEquals(14f, fittedSingleLineFontSize(14f, 500, minimumSize = 12f) { ceil(it * 20).toInt() }, 0f)
    }

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
