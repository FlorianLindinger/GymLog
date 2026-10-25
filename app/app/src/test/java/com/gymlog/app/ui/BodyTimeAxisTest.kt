package com.gymlog.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyTimeAxisTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun time(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun range(start: String, end: String) = VisibleTimeRange(time(start), time(end))

    @Test
    fun monthsHaveBoundaryTicksAndLabelsInsideEachMonth() {
        val axis = bodyTimeAxis(range("2026-06-01", "2026-08-01"), 320f, zone = zone)
        assertTrue(axis.monthly)
        assertEquals(listOf("Jun26", "Jul26"), axis.labels.map { it.text })
        assertEquals(listOf(time("2026-06-01"), time("2026-07-01"), time("2026-08-01")), axis.ticks.map { it.timestamp })
        assertTrue(axis.labels.none { label -> axis.ticks.any { it.timestamp == label.timestamp } })
        axis.labels.forEach { label ->
            val day = Instant.ofEpochMilli(label.timestamp).atZone(zone).dayOfMonth
            assertTrue(day in 15..17)
        }
    }

    @Test
    fun compactMonthNamesAndLabelCountAdaptToWidthAndFontSize() {
        val year = range("2026-01-01", "2027-01-01")
        val compact = bodyTimeAxis(year, 320f, zone = zone)
        assertEquals(listOf("J26", "F26"), compact.labels.take(2).map { it.text })
        val wide = bodyTimeAxis(year, 700f, zone = zone)
        assertEquals("Jan26", wide.labels.first().text)
        val narrow = bodyTimeAxis(year, 160f, zone = zone)
        assertTrue(narrow.labels.size < compact.labels.size)
        val enlarged = bodyTimeAxis(year, 320f, fontScale = 2f, zone = zone)
        assertTrue(enlarged.labels.size < compact.labels.size)
        assertTrue(narrow.labels.all { it.timestamp in year.start..year.end })
        val years = range("2026-01-01", "2029-01-01")
        bodyTimeAxis(years, 300f, zone = zone).labels.forEach { label ->
            val position = (label.timestamp - years.start).toDouble() / years.duration * 300
            assertTrue(position in 19.0..281.0)
        }
    }

    @Test
    fun zoomedIntervalsUseMondayTicksAndSpecificDatesAcrossDst() {
        val axis = bodyTimeAxis(range("2026-10-19", "2026-11-09"), 400f, zone = zone)
        assertEquals(false, axis.monthly)
        assertEquals(listOf("19 Oct 26", "26 Oct 26", "2 Nov 26", "9 Nov 26"), axis.labels.map { it.text })
        assertEquals(169L * 60L * 60L * 1000L, axis.ticks[1].timestamp - axis.ticks[0].timestamp)
        val tiny = bodyTimeAxis(range("2026-10-22", "2026-10-23"), 280f, zone = zone)
        assertEquals(listOf("22 Oct 26", "23 Oct 26"), tiny.labels.map { it.text })
        assertEquals(1, bodyTimeAxis(range("2026-10-22", "2026-10-23"), 90f, fontScale = 2f, zone = zone).labels.size)
    }
}
