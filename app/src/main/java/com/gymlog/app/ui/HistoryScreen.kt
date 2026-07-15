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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.HistorySet
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

@Composable
fun HistoryScreen(
    history: List<HistorySet>,
    modifier: Modifier = Modifier,
    onUpdateSet: (Long, Int, Double) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onDeleteExerciseDay: (Long, Long, Long) -> Unit,
    onDeleteTrainingDay: (Long, Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val byDate = remember(history) {
        history.groupBy { Instant.ofEpochMilli(it.performedAt).atZone(zone).toLocalDate() }
    }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<HistorySet?>(null) }
    var deletingEntry by remember { mutableStateOf<HistorySet?>(null) }
    var deletingExerciseDay by remember { mutableStateOf<ExerciseDayDeletion?>(null) }
    var deletingTrainingDay by remember { mutableStateOf<LocalDate?>(null) }
    val selectedSets = selectedDate?.let { byDate[it].orEmpty() }.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Training history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Daily activity across weight and level exercises", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedDate!!.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { deletingTrainingDay = selectedDate }) { Text("Delete day") }
                }
            }
            if (selectedSets.isEmpty()) item { Text("No sets recorded on this day.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            selectedSets.groupBy { it.machineId }.forEach { (machineId, exerciseSets) ->
                item(key = "exercise-$machineId") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(exerciseSets.first().machineName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { deletingExerciseDay = ExerciseDayDeletion(machineId, exerciseSets.first().machineName, selectedDate!!) }) { Text("Delete exercise day") }
                    }
                }
                items(exerciseSets, key = { it.id }) { set -> HistorySetRow(set, { editing = set }, { deletingEntry = set }, showName = false) }
            }
        } else {
            item { Text("Recent sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            if (byDate.isEmpty()) item { Text("Your completed sets will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            byDate.entries.sortedByDescending { it.key }.take(12).forEach { (date, sets) ->
                item(key = date.toEpochDay()) {
                    SessionCard(date, sets, onEdit = { editing = it }, onDelete = { deletingEntry = it }, onManage = { selectedDate = date })
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
    editing?.let { set ->
        EditSetDialog(set.trackingMode, set.reps, set.weight, onDismiss = { editing = null }) { reps, weight ->
            onUpdateSet(set.id, reps, weight)
            editing = null
        }
    }
    deletingEntry?.let { set ->
        ConfirmDeleteDialog("Delete this entry?", "Only this recorded set will be removed.", onDismiss = { deletingEntry = null }) {
            onDeleteSet(set.id)
            deletingEntry = null
        }
    }
    deletingExerciseDay?.let { target ->
        ConfirmDeleteDialog("Delete ${target.machineName} for this day?", "Every ${target.machineName} entry on this day will be removed.", onDismiss = { deletingExerciseDay = null }) {
            val (start, end) = dayBounds(target.date, zone)
            onDeleteExerciseDay(target.machineId, start, end)
            deletingExerciseDay = null
        }
    }
    deletingTrainingDay?.let { date ->
        ConfirmDeleteDialog("Delete the full training day?", "Every exercise entry on ${date.format(DateTimeFormatter.ofPattern("d MMMM"))} will be removed.", onDismiss = { deletingTrainingDay = null }) {
            val (start, end) = dayBounds(date, zone)
            onDeleteTrainingDay(start, end)
            deletingTrainingDay = null
        }
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
        }.size.toDouble()
    }
    val max = volumes.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("8-week activity trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("This week: ${volumes.last().toInt()} logged sets", color = MaterialTheme.colorScheme.primary)
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
        date to byDate[date].orEmpty().size.toDouble()
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
            Text("Darker days had more logged sets. Tap a date for details.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionCard(
    date: LocalDate,
    sets: List<HistorySet>,
    onEdit: (HistorySet) -> Unit,
    onDelete: (HistorySet) -> Unit,
    onManage: () -> Unit,
) {
    val durationMinutes = if (sets.size < 2) 0L else ((sets.maxOf { it.performedAt } - sets.minOf { it.performedAt }) / 60_000L).coerceAtLeast(0L)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(formatSessionTotals(sets), color = MaterialTheme.colorScheme.primary)
            }
            Text("${sets.size} sets · ${sets.map { it.machineId }.distinct().size} exercises · ${formatHoursMinutes(durationMinutes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            sets.take(4).forEach { HistorySetRow(it, onEdit, onDelete) }
            if (sets.size > 4) Text("+ ${sets.size - 4} more sets", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onManage) { Text("Manage entries") }
        }
    }
}

@Composable
private fun HistorySetRow(set: HistorySet, onEdit: (HistorySet) -> Unit, onDelete: (HistorySet) -> Unit, showName: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showName) Text(set.machineName, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        Text(
            if (isDirectMetric(set.trackingMode)) formatTrainingEntry(set.weight, set.trackingMode)
            else "${set.reps} × ${formatMetric(set.weight, set.trackingMode)}",
            fontWeight = FontWeight.Medium,
        )
        Text("Edit", Modifier.clickable { onEdit(set) }.padding(horizontal = 7.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Text("Delete", Modifier.clickable { onDelete(set) }.padding(horizontal = 5.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
    }
}

private data class ExerciseDayDeletion(val machineId: Long, val machineName: String, val date: LocalDate)

private fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
    date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

@Composable
private fun ConfirmDeleteDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EditSetDialog(
    trackingMode: String,
    initialReps: Int,
    initialWeight: Double,
    onDismiss: () -> Unit,
    onSave: (Int, Double) -> Unit,
) {
    var reps by remember(initialReps) { mutableStateOf(initialReps.toString()) }
    var weight by remember(initialWeight) { mutableStateOf(formatHistoryAmount(initialWeight)) }
    val parsedReps = reps.toIntOrNull()
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!isDirectMetric(trackingMode)) OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit) },
                    label = { Text("Reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                    label = { Text(if (trackingMode == "WEIGHT") "Weight (kg)" else "Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(if (isDirectMetric(trackingMode)) initialReps else parsedReps ?: 1, parsedWeight ?: 0.0) },
                enabled = parsedWeight != null && parsedWeight >= 0 && (isDirectMetric(trackingMode) || parsedReps != null && parsedReps > 0),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatVolume(volume: Double): String = if (volume >= 1000) String.format(Locale.US, "%.1f t", volume / 1000) else "${volume.toInt()} kg"

private fun formatSessionTotals(sets: List<HistorySet>): String {
    val parts = mutableListOf<String>()
    sets.filter { it.trackingMode == "WEIGHT" }.sumOf { it.volume }.takeIf { it > 0 }?.let { parts += formatVolume(it) }
    sets.filter { isDirectMetric(it.trackingMode) }.sumOf { it.weight }.takeIf { it > 0 }?.let { parts += "${formatHistoryAmount(it)} min" }
    return parts.joinToString(" · ").ifEmpty { "0" }
}

private fun formatHistoryAmount(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
