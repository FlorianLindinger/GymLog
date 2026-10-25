package com.gymlog.app.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil

internal data class TimeAxisLabel(val timestamp: Long, val text: String)
internal data class BodyTimeAxis(val ticks: List<CalendarTimeTick>, val labels: List<TimeAxisLabel>, val monthly: Boolean)

/** Calendar boundaries use local dates, including daylight-saving changes. */
internal fun bodyTimeAxis(range: VisibleTimeRange, widthDp: Float, fontScale: Float = 1f,
    zone: ZoneId = ZoneId.systemDefault()): BodyTimeAxis {
    val width = widthDp.coerceAtLeast(1f)
    val scale = fontScale.coerceAtLeast(.5f)
    val start = Instant.ofEpochMilli(range.start).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(range.end).atZone(zone).toLocalDate()
    fun time(date: java.time.LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
    if (range.duration < 60L * 86_400_000L) {
        var date = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val ticks = buildList {
            while (!date.isAfter(end)) {
                val timestamp = time(date)
                if (timestamp in range.start..range.end) add(CalendarTimeTick(timestamp, true))
                date = date.plusWeeks(1)
            }
        }
        val positions = if (ticks.size < 2) listOf(range.start, range.end) else ticks.map { it.timestamp }
        val capacity = (width / (66f * scale)).toInt().coerceAtLeast(1)
        val step = ceil(positions.size.toDouble() / capacity).toInt().coerceAtLeast(1)
        val formatter = DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH)
        val labels = positions.filterIndexed { index, _ -> index % step == 0 }.map {
            TimeAxisLabel(it, Instant.ofEpochMilli(it).atZone(zone).format(formatter))
        }.distinctBy { it.text }
        return BodyTimeAxis(ticks, labels, false)
    }
    var month = start.withDayOfMonth(1)
    val monthCount = (ChronoUnit.MONTHS.between(month, end.withDayOfMonth(1)) + 1).toInt()
    val pixelsPerMonth = width / (range.duration / (30.4375 * 86_400_000.0))
    val abbreviated = pixelsPerMonth < 38f * scale && pixelsPerMonth >= 23f * scale
    val labelWidth = (if (abbreviated) 23f else 38f) * scale
    val capacity = (width / labelWidth).toInt().coerceAtLeast(1)
    val step = if (pixelsPerMonth >= 23f * scale) 1 else ceil(monthCount.toDouble() / capacity).toInt().coerceAtLeast(1)
    val ticks = mutableListOf<CalendarTimeTick>()
    val labels = mutableListOf<TimeAxisLabel>()
    val monthName = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    val year = DateTimeFormatter.ofPattern("yy", Locale.ENGLISH)
    repeat(monthCount) { index ->
        val boundary = time(month)
        val next = time(month.plusMonths(1))
        if (boundary in range.start..range.end) ticks += CalendarTimeTick(boundary, month.monthValue == 1)
        val visibleStart = maxOf(boundary, range.start)
        val visibleEnd = minOf(next, range.end)
        val cellWidth = (visibleEnd - visibleStart).toDouble() / range.duration * width
        val middle = visibleStart + (visibleEnd - visibleStart) / 2
        val centerPosition = (middle - range.start).toDouble() / range.duration * width
        // Skip tiny partial months and space labels further apart on long histories.
        if (visibleEnd > visibleStart && index % step == 0 && (step > 1 || cellWidth >= labelWidth) &&
            centerPosition >= labelWidth / 2 && centerPosition <= width - labelWidth / 2) {
            val name = month.format(monthName).let { if (abbreviated) it.take(1) else it }
            labels += TimeAxisLabel(middle, name + month.format(year))
        }
        month = month.plusMonths(1)
    }
    if (labels.isEmpty()) {
        val middleMonth = Instant.ofEpochMilli(range.start + range.duration / 2).atZone(zone).toLocalDate().withDayOfMonth(1)
        val from = maxOf(time(middleMonth), range.start)
        val to = minOf(time(middleMonth.plusMonths(1)), range.end)
        labels += TimeAxisLabel(from + (to - from) / 2, middleMonth.format(monthName) + middleMonth.format(year))
    }
    // Keep a gap even when a clipped first/last month has an unusual width.
    val separated = labels.fold(mutableListOf<TimeAxisLabel>()) { result, label ->
        val previous = result.lastOrNull()
        if (previous == null || (label.timestamp - previous.timestamp).toDouble() / range.duration * width >= labelWidth)
            result += label
        result
    }
    return BodyTimeAxis(ticks, separated, true)
}
