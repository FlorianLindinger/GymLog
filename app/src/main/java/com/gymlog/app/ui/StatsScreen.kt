package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.HistorySet
import com.gymlog.app.data.MachineSummary
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            FilledTonalButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Calendar & history") }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(8) { index ->
                    val preset = listOf("1W", "1M", "3M", "6M", "1Y", "2Y", "3Y", "ALL")[index]
                    FilterChip(selected = window == preset, onClick = { window = preset }, label = { Text(preset) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Gym / week", String.format(Locale.US, "%.1f", sessions.size / periodWeeks), Modifier.weight(1f))
                StatCard("Sessions", sessions.size.toString(), Modifier.weight(1f))
                StatCard("Sets", visible.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Avg upgrades / gym day", String.format(Locale.US, "%.2f", averageUpgradesPerGymDay), Modifier.weight(1f))
                StatCard("Avg upgrades / month", String.format(Locale.US, "%.2f", averageUpgradesPerMonth), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Avg duration", formatHoursMinutes(durations.values.averageOrZero().toLong()), Modifier.weight(1f))
                StatCard("Longest", formatHoursMinutes((durations.values.maxOrNull() ?: 0.0).toLong()), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Weight increases", increases.size.toString(), Modifier.weight(1f))
                StatCard("Increases / week", String.format(Locale.US, "%.2f", increases.size / periodWeeks), Modifier.weight(1f))
                StatCard("Exercises progressed", increases.map { it.machineId }.distinct().size.toString(), Modifier.weight(1f))
            }
        }
        item { StatsChart("Gym sessions by week", weeks.values.toList().takeLast(20), "sessions") }
        item { StatsChart("Gym duration by day", durations.values.toList().takeLast(24), "minutes") }
        item { StatsChart("Weight increases by week", increasesByWeek, "increases") }
        item { StatsChart("Weight upgrades per gym day", upgradesPerGymDay, "upgrades") }
        item { StatsChart("Weight upgrades by month", upgradesByMonth, "upgrades") }
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

@Composable
private fun ProgressionByExerciseCard(machines: List<MachineSummary>, cutoff: Long?, periodWeeks: Double) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Weight increase frequency by exercise", fontWeight = FontWeight.SemiBold)
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
        Column(Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
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
            val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                if (values.isEmpty()) return@Canvas
                val gap = 5f
                val width = (size.width - gap * (values.size - 1)) / values.size
                values.forEachIndexed { index, value ->
                    val h = (value / max).toFloat() * size.height
                    drawRoundRect(primary, Offset(index * (width + gap), size.height - h), Size(width, h), CornerRadius(6f, 6f))
                }
            }
        }
    }
}

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
