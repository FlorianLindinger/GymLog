package com.gymlog.app.ui

import com.gymlog.app.data.ChartRange
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

internal data class BodyHighLowReference(val label: String, val low: TimeValue, val high: TimeValue, val allTime: Boolean,
    val showHigh: Boolean = true, val showLow: Boolean = true) {
    val visibleValues: List<Double> get() = listOfNotNull(low.value.takeIf { showLow }, high.value.takeIf { showHigh }).distinct()
}

internal fun bodyHighLowReferences(points: List<TimeValue>, settings: BodyChartSettings, end: Long): List<BodyHighLowReference> {
    val finite = points.filter { it.value.isFinite() }
    fun reference(label: String, values: List<TimeValue>, allTime: Boolean, showHigh: Boolean, showLow: Boolean): BodyHighLowReference? {
        if (values.isEmpty()) return null
        return BodyHighLowReference(label, values.minBy(TimeValue::value), values.maxBy(TimeValue::value), allTime, showHigh, showLow)
    }
    return buildList {
        if (settings.showAllTimeHigh || settings.showAllTimeLow)
            reference("All-time", finite, true, settings.showAllTimeHigh, settings.showAllTimeLow)?.let { add(it) }
        if (settings.showHistoryHigh || settings.showHistoryLow) {
            val range = settings.highLowRange
            val start = when (range) {
                ChartRange.CUSTOM -> settings.highLowStart ?: return@buildList
                ChartRange.ALL -> Long.MIN_VALUE
                else -> end - (chartWindowDuration(range) ?: 0)
            }
            val historyEnd = if (range == ChartRange.CUSTOM) settings.highLowEnd ?: return@buildList else end
            val label = if (range == ChartRange.CUSTOM) "Custom history" else "${range.storageValue} history"
            reference(label, finite.filter { it.timestamp in start..historyEnd }, false, settings.showHistoryHigh, settings.showHistoryLow)?.let { add(it) }
        }
    }
}

internal fun averageTimeBins(points: List<TimeValue>, range: VisibleTimeRange, bins: Int): List<TimeValue> =
    points.filter { it.timestamp in range.start..range.end && it.value.isFinite() }
        .groupBy {
            (((it.timestamp - range.start).toDouble() / range.duration) * bins.coerceAtLeast(1))
                .toInt().coerceIn(0, bins.coerceAtLeast(1) - 1)
        }.toSortedMap().values.map { group ->
            // Average offsets instead of epoch timestamps to preserve precision.
            TimeValue(range.start + group.map { (it.timestamp - range.start).toDouble() }.average().toLong(),
                group.map(TimeValue::value).average())
        }

internal fun formatChartDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH))

internal data class CalendarTimeTick(val timestamp: Long, val major: Boolean)

internal fun calendarTimeTicks(range: VisibleTimeRange, zone: ZoneId = ZoneId.systemDefault()): List<CalendarTimeTick> {
    val days = range.duration / 86_400_000.0
    val start = Instant.ofEpochMilli(range.start).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(range.end).atZone(zone).toLocalDate()
    // Fine ticks follow days, Mondays, month starts, quarters, and years.
    val level = when {
        days <= 21 -> 0
        days <= 100 -> 1
        days <= 400 -> 2
        days <= 1100 -> 3
        else -> 4
    }
    val desiredYears = (days / 365.25 / 10).coerceAtLeast(1.0)
    val magnitude = 10.0.pow(kotlin.math.floor(log10(desiredYears)))
    val yearStep = (listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= desiredYears } * magnitude).toLong()
    var date = when (level) {
        0 -> start
        1 -> start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        2 -> start.withDayOfMonth(1)
        3 -> start.withMonth(((start.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1)
        else -> start.withDayOfYear(1).withYear((start.year / yearStep * yearStep).toInt())
    }
    return buildList {
        while (!date.isAfter(end)) {
            val timestamp = date.atStartOfDay(zone).toInstant().toEpochMilli()
            if (timestamp in range.start..range.end) {
                val major = when (level) {
                    0 -> days <= 7 || date.dayOfWeek == DayOfWeek.MONDAY
                    1 -> date.dayOfMonth <= 7
                    2 -> (date.monthValue - 1) % 3 == 0
                    3 -> date.monthValue == 1
                    else -> date.year % (yearStep * 5) == 0L
                }
                add(CalendarTimeTick(timestamp, major))
            }
            date = when (level) {
                0 -> date.plusDays(1)
                1 -> date.plusWeeks(1)
                2 -> date.plusMonths(1)
                3 -> date.plusMonths(3)
                else -> date.plusYears(yearStep)
            }
        }
    }
}
