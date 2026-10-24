package com.gymlog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.ChartRange
import com.gymlog.app.data.TrackingMode
import com.gymlog.app.data.HistorySet
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.Strength
import com.gymlog.app.data.weightIncreases
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun StatsScreen(
    history: List<HistorySet>,
    machines: List<MachineSummary>,
    modifier: Modifier = Modifier,
    onHistory: () -> Unit,
) {
    var window by remember { mutableStateOf(ChartRange.THREE_MONTHS) }
    var showTrend by remember { mutableStateOf(true) }
    var showCustomRange by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    val cutoff = if (window == ChartRange.CUSTOM) customStart else statsCutoff(window)
    val visible = history.filter {
        (cutoff == null || it.performedAt >= cutoff) &&
            (window != ChartRange.CUSTOM || customEnd == null || it.performedAt <= customEnd!!)
    }
    val zone = ZoneId.systemDefault()
    val sessions = visible.groupBy { trainingDate(it.performedAt, zone) }.toSortedMap()
    val durations = sessions.mapValues { (_, sets) ->
        if (sets.size < 2) 0.0 else Duration.ofMillis(sets.maxOf { it.performedAt } - sets.minOf { it.performedAt }).toMinutes().toDouble()
    }
    val allSessions = history.groupBy { trainingDate(it.performedAt, zone) }
    val allDurations = allSessions.toSortedMap().mapValues { (_, sets) ->
        if (sets.size < 2) 0.0 else Duration.ofMillis(sets.maxOf { it.performedAt } - sets.minOf { it.performedAt }).toMinutes().toDouble()
    }
    val todayTrainingDate = trainingDate(System.currentTimeMillis(), zone)
    val last30TrainingHours = trainingHoursPerTraining(history, todayTrainingDate.minusDays(29), todayTrainingDate, zone)
    val hoursLast30Days = last30TrainingHours.sumOf(TimeValue::value)
    val thisMonday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
    val sessionsByWeek = allSessions.keys.groupingBy { date ->
        date.minusDays((date.dayOfWeek.value - 1).toLong())
    }.eachCount()
    val firstSessionWeek = sessionsByWeek.keys.minOrNull() ?: thisMonday
    val sessionWeekDates = generateSequence(firstSessionWeek) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(thisMonday) }
        .toList()
    val weeks = sessionWeekDates.associateWith { week -> (sessionsByWeek[week] ?: 0).toDouble() }
    val periodWeeks = when {
        visible.isEmpty() -> 1.0
        cutoff != null -> ((System.currentTimeMillis() - cutoff) / 604_800_000.0).coerceAtLeast(1.0)
        else -> ((visible.maxOf { it.performedAt } - visible.minOf { it.performedAt }) / 604_800_000.0).coerceAtLeast(1.0)
    }
    val weightMachines = machines.filter { it.machine.trackingMode == TrackingMode.WEIGHT }
    val allIncreases = weightMachines.flatMap { it.weightIncreases() }
    val increases = allIncreases.filter { cutoff == null || it.changedAt >= cutoff }
    val increaseWeeksByDate = allIncreases.groupingBy {
        val date = trainingDate(it.changedAt, zone)
        date.minusDays((date.dayOfWeek.value - 1).toLong())
    }.eachCount()
    val increaseWeekDates = sessionWeekDates
    val increasesByWeek = increaseWeekDates.map { week ->
        (increaseWeeksByDate[week] ?: 0).toDouble()
    }
    val increasesByDate = allIncreases.groupingBy {
        trainingDate(it.changedAt, zone)
    }.eachCount()
    val upgradesPerGymDay = allSessions.keys.sorted().map { date -> (increasesByDate[date] ?: 0).toDouble() }
    val averageUpgradesPerGymDay = if (sessions.isEmpty()) 0.0 else increases.size.toDouble() / sessions.size
    val averageUpgradesPerMonth = increases.size / (periodWeeks / 4.345).coerceAtLeast(1.0)
    val increasesByMonth = allIncreases.groupingBy {
        YearMonth.from(Instant.ofEpochMilli(it.changedAt).atZone(zone))
    }.eachCount()
    val currentMonth = YearMonth.now()
    val plottedMonths = increasesByMonth.keys.minOrNull()?.let { oldest ->
        (java.time.temporal.ChronoUnit.MONTHS.between(oldest, currentMonth).toInt() + 1).coerceAtLeast(1)
    } ?: 1
    val plottedMonthDates = (plottedMonths - 1 downTo 0).map { monthsAgo ->
        currentMonth.minusMonths(monthsAgo.toLong())
    }
    val upgradesByMonth = plottedMonthDates.map { month ->
        (increasesByMonth[month] ?: 0).toDouble()
    }
    val machinesById = machines.associateBy { it.machine.id }
    val bodyCounts = mutableMapOf<BodyArea, Pair<Int, Int>>()
    sessions.values.forEach { dayEntries ->
        dayEntries.map { it.machineId }.distinct().forEach { machineId ->
            machinesById[machineId]?.areas?.forEach { assignment ->
                val current = bodyCounts[assignment.area] ?: (0 to 0)
                bodyCounts[assignment.area] = if (assignment.strength == Strength.FULL) {
                    current.copy(first = current.first + 1)
                } else {
                    current.copy(second = current.second + 1)
                }
            }
        }
    }
    val bodyAreaStats = bodyCounts.map { (area, counts) ->
        BodyAreaTrainingStat(
            area = area,
            full = counts.first,
            half = counts.second,
            color = machines.asSequence().flatMap { it.areas.asSequence() }.firstOrNull { it.area == area }?.displayColor ?: area.color,
        )
    }.sortedByDescending { it.weighted }
    val newestEntry = history.maxOfOrNull { it.performedAt } ?: System.currentTimeMillis()
    val oldestEntry = history.minOfOrNull { it.performedAt } ?: newestEntry - 30L * 86_400_000L
    val statsChartWindow = ChartWindow(
        durationMillis = if (window == ChartRange.CUSTOM) {
            customStart?.let { start -> customEnd?.let { end -> (end - start).coerceAtLeast(1L) } }
        } else chartWindowDuration(window),
        endMillis = if (window == ChartRange.CUSTOM) customEnd else System.currentTimeMillis(),
        startMillis = if (window == ChartRange.CUSTOM) customStart else null,
        key = window.storageValue,
    )
    fun datedPoints(dates: List<LocalDate>, values: List<Double>): List<TimeValue> =
        dates.zip(values).map { (date, value) -> TimeValue(date.atStartOfDay(zone).toInstant().toEpochMilli(), value) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            FilledTonalButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Calendar & History") }
        }
        item {
            ChartRangeControls(
                selectedWindow = window,
                showTrend = showTrend,
                onWindowSelected = { window = it },
                onCustomSelected = { showCustomRange = true },
                onTrendSelected = { showTrend = !showTrend },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatCard("Gym / Week", String.format(Locale.US, "%.1f", sessions.size / periodWeeks), Modifier.weight(1f))
                StatCard("Sessions", sessions.size.toString(), Modifier.weight(1f))
                StatCard("Sets", visible.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatCard("Avg. Upgrades / Gym Day", String.format(Locale.US, "%.2f", averageUpgradesPerGymDay), Modifier.weight(1f))
                StatCard("Avg. Upgrades / Month", String.format(Locale.US, "%.2f", averageUpgradesPerMonth), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatCard("Hours, last 30 days", String.format(Locale.US, "%.1f h", hoursLast30Days), Modifier.weight(1f))
                StatCard("Avg. Duration", formatHoursMinutes(durations.values.averageOrZero().toLong()), Modifier.weight(1f))
                StatCard("Longest", formatHoursMinutes((durations.values.maxOrNull() ?: 0.0).toLong()), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatCard("Weight Increases", increases.size.toString(), Modifier.weight(1f))
                StatCard("Increases / Week", String.format(Locale.US, "%.2f", increases.size / periodWeeks), Modifier.weight(1f))
                StatCard("Exercises Progressed", increases.map { it.machineId }.distinct().size.toString(), Modifier.weight(1f))
            }
        }
        item { BodyAreaStatsCard(bodyAreaStats) }
        item { StatsChart("Gym Sessions by Week", datedPoints(weeks.keys.toList(), weeks.values.toList()), "sessions", statsChartWindow, showTrend) }
        item {
            StatsChart(
                "Hours per Training (last 30 days)",
                last30TrainingHours,
                "hours",
                ChartWindow(
                    durationMillis = 30L * 86_400_000L,
                    endMillis = System.currentTimeMillis(),
                    key = "LAST_30_DAYS",
                ),
                showTrend,
            )
        }
        item { StatsChart("Gym Duration per Training", datedPoints(allDurations.keys.toList(), allDurations.values.toList()), "minutes", statsChartWindow, showTrend) }
        item { StatsChart("Weight Increases by Week", datedPoints(increaseWeekDates, increasesByWeek), "increases", statsChartWindow, showTrend) }
        item { StatsChart("Weight Upgrades per Gym Day", datedPoints(allSessions.keys.sorted(), upgradesPerGymDay), "upgrades", statsChartWindow, showTrend) }
        item {
            StatsChart(
                "Weight Upgrades by Month",
                datedPoints(
                    plottedMonthDates.map { month -> if (month == currentMonth) LocalDate.now() else month.atEndOfMonth() },
                    upgradesByMonth,
                ),
                "upgrades",
                statsChartWindow,
                showTrend,
            )
        }
        item { ProgressionByExerciseCard(weightMachines, cutoff, periodWeeks) }
        item {
            Text(
                "A training groups sets from 06:00 to 05:59 the next day. Duration runs from its first recorded set to its last. A training with one set has a duration of 0 minutes. Days without training are omitted from Hours per Training.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showCustomRange) {
        CustomBodyRangeDialog(
            maxTimestamp = System.currentTimeMillis(),
            initialStart = customStart ?: oldestEntry,
            initialEnd = customEnd ?: System.currentTimeMillis(),
            onDismiss = { showCustomRange = false },
            onSave = { start, end ->
                customStart = start
                customEnd = end
                window = ChartRange.CUSTOM
                showCustomRange = false
            },
        )
    }
}

private data class BodyAreaTrainingStat(
    val area: BodyArea,
    val full: Int,
    val half: Int,
    val color: Long,
) {
    val weighted: Double get() = full + half * 0.5
}

@Composable
private fun BodyAreaStatsCard(stats: List<BodyAreaTrainingStat>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Body Area Training", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth()) {
                Text("Area", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Full", Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text("Half", Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text("Total", Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
            if (stats.isEmpty()) Text("No body-area training in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            stats.forEach { stat ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.width(12.dp).height(12.dp).background(Color(stat.color), CircleShape))
                    Text(stat.area.label, Modifier.weight(1f).padding(start = 5.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text(stat.full.toString(), Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text(stat.half.toString(), Modifier.width(42.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text(formatWeighted(stat.weighted), Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                }
            }
        }
    }
}

private fun formatWeighted(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

@Composable
private fun ProgressionByExerciseCard(machines: List<MachineSummary>, cutoff: Long?, periodWeeks: Double) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Weight Increase Frequency by Exercise", fontWeight = FontWeight.SemiBold)
            if (machines.isEmpty()) {
                Text("No weight-tracked exercises yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            machines.forEach { summary ->
                val count = summary.weightIncreases().count { cutoff == null || it.changedAt >= cutoff }
                Row(Modifier.fillMaxWidth()) {
                    Text(summary.machine.name, Modifier.weight(1f))
                    Text("$count · ${String.format(Locale.US, "%.2f", count / periodWeeks)}/week", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StatsChart(
    title: String,
    points: List<TimeValue>,
    unit: String,
    window: ChartWindow,
    showTrend: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (points.isEmpty()) {
                Text("No data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val latest = points.last().value
            Text(
                when (unit) {
                    "minutes" -> "Latest: ${formatHoursMinutes(latest.toLong())}"
                    "hours" -> "Latest: ${formatHoursMinutes((latest * 60.0).roundToLong())}"
                    else -> "Latest: ${latest.toInt()} $unit"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val primary = MaterialTheme.colorScheme.primary
            TimeWindowChart(
                series = listOf(TimeSeries(points, primary, strokeWidth = 4f, pointRadius = 5f)) +
                    if (showTrend) listOf(trendTimeSeries(points, primary, PlotAxis.LEFT, window)) else emptyList(),
                window = window,
                leftColor = primary,
                height = 110.dp,
            )
        }
    }
}

internal fun trainingHoursPerTraining(
    history: List<HistorySet>,
    firstDate: LocalDate,
    lastDate: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TimeValue> = history.groupBy { trainingDate(it.performedAt, zone) }
    .toSortedMap()
    .filterKeys { !it.isBefore(firstDate) && !it.isAfter(lastDate) }
    .map { (_, sets) ->
        val start = sets.minOf { it.performedAt }
        val end = sets.maxOf { it.performedAt }
        TimeValue(end, Duration.ofMillis(end - start).toMinutes() / 60.0)
    }

private fun statsCutoff(window: ChartRange): Long? =
    chartWindowDuration(window)?.let { duration -> System.currentTimeMillis() - duration }

private fun Collection<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
