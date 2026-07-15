package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.HistorySet
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Composable
fun HistoryScreen(history: List<HistorySet>, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val byDate = remember(history) {
        history.groupBy { Instant.ofEpochMilli(it.performedAt).atZone(zone).toLocalDate() }
    }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val selectedSets = selectedDate?.let { byDate[it].orEmpty() }.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Training history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Daily activity and total training volume (weight or level × reps)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { WeeklyVolumeCard(history) }
        item {
            CalendarCard(
                month = month,
                byDate = byDate,
                selectedDate = selectedDate,
                onPrevious = { month = month.minusMonths(1); selectedDate = null },
                onNext = { month = month.plusMonths(1); selectedDate = null },
                onSelect = { selectedDate = if (selectedDate == it) null else it },
            )
        }
        if (selectedDate != null) {
            item {
                Text(
                    selectedDate!!.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (selectedSets.isEmpty()) item { Text("No sets recorded on this day.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(selectedSets.size) { index -> HistorySetRow(selectedSets[index]) }
        } else {
            item { Text("Recent sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            if (byDate.isEmpty()) item { Text("Your completed sets will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            byDate.entries.sortedByDescending { it.key }.take(12).forEach { (date, sets) ->
                item(key = date.toEpochDay()) {
                    SessionCard(date, sets)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun WeeklyVolumeCard(history: List<HistorySet>) {
    val today = LocalDate.now()
    val thisMonday = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    val starts = (7 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
    val volumes = starts.map { start ->
        val end = start.plusDays(7)
        history.filter {
            val date = Instant.ofEpochMilli(it.performedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            !date.isBefore(start) && date.isBefore(end)
        }.sumOf { it.volume }
    }
    val max = volumes.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("8-week volume trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("This week: ${formatVolume(volumes.last())}", color = MaterialTheme.colorScheme.primary)
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
                val gap = 8f
                val barWidth = (size.width - gap * (volumes.size - 1)) / volumes.size
                volumes.forEachIndexed { index, volume ->
                    val height = (volume / max).toFloat() * (size.height - 8f)
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(index * (barWidth + gap), size.height - height),
                        size = androidx.compose.ui.geometry.Size(barWidth, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(starts.first().format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.bodySmall)
                Text("Now", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CalendarCard(
    month: YearMonth,
    byDate: Map<LocalDate, List<HistorySet>>,
    selectedDate: LocalDate?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val cells = leading + month.lengthOfMonth()
    val rows = ceil(cells / 7.0).toInt()
    val monthVolumes = (1..month.lengthOfMonth()).associate { day ->
        val date = month.atDay(day)
        date to byDate[date].orEmpty().sumOf { it.volume }
    }
    val maxVolume = monthVolumes.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrevious) { Text("‹") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onNext) { Text("›") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
            }
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val cell = row * 7 + column
                        val day = cell - leading + 1
                        if (day !in 1..month.lengthOfMonth()) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = month.atDay(day)
                            val volume = monthVolumes[date] ?: 0.0
                            val activityColor = if (volume > 0) MaterialTheme.colorScheme.primary.copy(alpha = (0.18f + 0.65f * (volume / maxVolume).toFloat())) else Color.Transparent
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                    .background(if (selectedDate == date) MaterialTheme.colorScheme.primaryContainer else activityColor, RoundedCornerShape(8.dp))
                                    .clickable { onSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(day.toString(), style = MaterialTheme.typography.bodySmall)
                                    if (volume > 0) Box(Modifier.width(5.dp).height(5.dp).background(MaterialTheme.colorScheme.onPrimaryContainer, RoundedCornerShape(5.dp)))
                                }
                            }
                        }
                    }
                }
            }
            Text("Darker days had more total volume. Tap a date for its sets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionCard(date: LocalDate, sets: List<HistorySet>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(formatVolume(sets.sumOf { it.volume }), color = MaterialTheme.colorScheme.primary)
            }
            Text("${sets.size} sets · ${sets.map { it.machineId }.distinct().size} exercises", color = MaterialTheme.colorScheme.onSurfaceVariant)
            sets.take(4).forEach { HistorySetRow(it) }
            if (sets.size > 4) Text("+ ${sets.size - 4} more sets", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistorySetRow(set: HistorySet) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(set.machineName, Modifier.weight(1f))
        Text(
            if (set.trackingMode == "STRENGTH") formatMetric(set.weight, set.trackingMode)
            else "${set.reps} × ${formatMetric(set.weight, set.trackingMode)}",
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatVolume(volume: Double): String = if (volume >= 1000) "%.1f t".format(volume / 1000) else "${volume.toInt()} kg"
