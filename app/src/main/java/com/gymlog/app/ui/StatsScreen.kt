package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

@Composable
fun StatsScreen(
    history: List<HistorySet>,
    machines: List<MachineSummary>,
    modifier: Modifier = Modifier,
    onHistory: () -> Unit,
) {
    var window by remember { mutableStateOf("3M") }
    val cutoff = statsCutoff(window)
    val visible = history.filter { cutoff == null || it.performedAt >= cutoff }
    val zone = ZoneId.systemDefault()
    val sessions = visible.groupBy { Instant.ofEpochMilli(it.performedAt).atZone(zone).toLocalDate() }.toSortedMap()
    val durations = sessions.mapValues { (_, sets) ->
        if (sets.size < 2) 0.0 else Duration.ofMillis(sets.maxOf { it.performedAt } - sets.minOf { it.performedAt }).toMinutes().toDouble()
    }
    val last30Cutoff = Instant.now().minus(Duration.ofDays(30)).toEpochMilli()
    val allSessions = history.groupBy { Instant.ofEpochMilli(it.performedAt).atZone(zone).toLocalDate() }
    val last30Durations = (29 downTo 0).map { daysAgo ->
        val day = LocalDate.now().minusDays(daysAgo.toLong())
        val entries = allSessions[day].orEmpty()
        if (entries.size < 2) 0.0 else Duration.ofMillis(entries.maxOf { it.performedAt } - entries.minOf { it.performedAt }).toMinutes().toDouble()
    }
    val hoursLast30Days = last30Durations.sum() / 60.0
    val weeks = sessions.entries.groupBy { (date, _) -> date.minusDays((date.dayOfWeek.value - 1).toLong()) }
        .toSortedMap().mapValues { it.value.size.toDouble() }
    val periodWeeks = when {
        visible.isEmpty() -> 1.0
        cutoff != null -> ((System.currentTimeMillis() - cutoff) / 604_800_000.0).coerceAtLeast(1.0)
        else -> ((visible.maxOf { it.performedAt } - visible.minOf { it.performedAt }) / 604_800_000.0).coerceAtLeast(1.0)
    }
    val weightMachines = machines.filter { it.machine.trackingMode == "WEIGHT" }
    val increases = weightMachines.flatMap { it.weightIncreases() }
        .filter { cutoff == null || it.changedAt >= cutoff }
    val increaseWeeksByDate = increases.groupingBy {
        val date = Instant.ofEpochMilli(it.changedAt).atZone(zone).toLocalDate()
        date.minusDays((date.dayOfWeek.value - 1).toLong())
    }.eachCount()
    val thisMonday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong())
    val increasesByWeek = (19 downTo 0).map { weeksAgo ->
        (increaseWeeksByDate[thisMonday.minusWeeks(weeksAgo.toLong())] ?: 0).toDouble()
    }
    val increasesByDate = increases.groupingBy {
        Instant.ofEpochMilli(it.changedAt).atZone(zone).toLocalDate()
    }.eachCount()
    val upgradesPerGymDay = sessions.keys.map { date -> (increasesByDate[date] ?: 0).toDouble() }.takeLast(24)
    val averageUpgradesPerGymDay = if (sessions.isEmpty()) 0.0 else increases.size.toDouble() / sessions.size
    val averageUpgradesPerMonth = increases.size / (periodWeeks / 4.345).coerceAtLeast(1.0)
    val increasesByMonth = increases.groupingBy {
        YearMonth.from(Instant.ofEpochMilli(it.changedAt).atZone(zone))
    }.eachCount()
    val plottedMonths = when (window) {
        "1W", "1M" -> 2
        "3M" -> 3
        "6M" -> 6
        "1Y" -> 12
        "2Y" -> 24
        "3Y" -> 36
        else -> 36
    }
    val currentMonth = YearMonth.now()
    val upgradesByMonth = (plottedMonths - 1 downTo 0).map { monthsAgo ->
        (increasesByMonth[currentMonth.minusMonths(monthsAgo.toLong())] ?: 0).toDouble()
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            FilledTonalButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Calendar & History") }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("1W", "1M", "3M", "6M", "1Y", "2Y", "3Y", "ALL").forEach { preset ->
                    val selected = window == preset
                    Text(
                        preset,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(7.dp),
                            )
                            .clickable { window = preset }
                            .padding(vertical = 4.dp),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
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
        item { StatsChart("Gym Sessions by Week", weeks.values.toList().takeLast(20), "sessions") }
        item { StatsChart("Training Hours by Day (last 30 days)", last30Durations.map { it / 60.0 }, "hours") }
        item { StatsChart("Gym Duration by Day", durations.values.toList().takeLast(24), "minutes") }
        item { StatsChart("Weight Increases by Week", increasesByWeek, "increases") }
        item { StatsChart("Weight Upgrades per Gym Day", upgradesPerGymDay, "upgrades") }
        item { StatsChart("Weight Upgrades by Month", upgradesByMonth, "upgrades") }
        item { ProgressionByExerciseCard(weightMachines, cutoff, periodWeeks) }
        item {
            Text(
                "Duration runs from the first recorded set to the last recorded set of each day. A day with one set has a duration of 0 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun StatsChart(title: String, values: List<Double>, unit: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            val latest = values.lastOrNull() ?: 0.0
            Text(
                if (unit == "minutes") "Latest: ${formatHoursMinutes(latest.toLong())}" else "Latest: ${latest.toInt()} $unit",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val primary = MaterialTheme.colorScheme.primary
            val fitColor = MaterialTheme.colorScheme.tertiary
            val grid = MaterialTheme.colorScheme.outlineVariant
            val (fit, fittedChange) = linearFit(values)
            Text(
                "Linear fit: ${signedValue(fittedChange)} $unit over this interval",
                color = fitColor,
                style = MaterialTheme.typography.bodySmall,
            )
            val combined = values + fit
            val min = combined.minOrNull()?.coerceAtMost(0.0) ?: 0.0
            val max = combined.maxOrNull()?.takeIf { it > min } ?: (min + 1.0)
            val range = max - min
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val graphWidth = maxOf(maxWidth, (values.size * 28).dp)
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(Modifier.width(graphWidth).height(130.dp)) {
                if (values.isEmpty()) return@Canvas
                repeat(3) { index ->
                    val y = index * size.height / 2f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                repeat(5) { index ->
                    val x = index * size.width / 4f
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                }
                fun points(series: List<Double>) = series.mapIndexed { index, value ->
                    Offset(
                        x = if (series.size == 1) size.width / 2f else index * size.width / (series.size - 1),
                        y = size.height - ((value - min) / range).toFloat() * size.height,
                    )
                }
                val dataPoints = points(values)
                dataPoints.zipWithNext().forEach { (start, end) -> drawLine(primary, start, end, strokeWidth = 5f) }
                dataPoints.forEach { drawCircle(primary, 5f, it) }
                points(fit).zipWithNext().forEach { (start, end) -> drawLine(fitColor, start, end, strokeWidth = 3f) }
                }
                }
            }
        }
    }
}

private fun linearFit(values: List<Double>): Pair<List<Double>, Double> {
    if (values.size < 2) return values to 0.0
    val meanX = (values.size - 1) / 2.0
    val meanY = values.average()
    val denominator = values.indices.sumOf { index -> (index - meanX) * (index - meanX) }
    val slope = values.indices.sumOf { index -> (index - meanX) * (values[index] - meanY) } / denominator
    val intercept = meanY - slope * meanX
    val fit = values.indices.map { index -> intercept + slope * index }
    return fit to (fit.last() - fit.first())
}

private fun signedValue(value: Double): String = String.format(Locale.US, "%+.1f", value)

private fun statsCutoff(window: String): Long? {
    val days = when (window) {
        "1W" -> 7L
        "1M" -> 30L
        "3M" -> 90L
        "6M" -> 182L
        "1Y" -> 365L
        "2Y" -> 730L
        "3Y" -> 1_095L
        else -> return null
    }
    return Instant.now().minus(Duration.ofDays(days)).toEpochMilli()
}

private fun Collection<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
