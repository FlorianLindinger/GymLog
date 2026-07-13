package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.AreaAssignment
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.GymRepository
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.Strength
import com.gymlog.app.data.WeightChange
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DetailScreen(
    summary: MachineSummary,
    modifier: Modifier = Modifier,
    repository: GymRepository,
    onChanged: () -> Unit,
    onDeleted: () -> Unit,
) {
    var showQuickSet by remember { mutableStateOf(false) }
    var showWeight by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showAreas by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val machine = summary.machine
    val highest = summary.weightChanges.maxOfOrNull { it.weight } ?: machine.currentWeight
    val first = summary.weightChanges.lastOrNull()?.weight ?: machine.currentWeight
    val lastChange = summary.weightChanges.firstOrNull()?.changedAt

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (summary.areas.isEmpty()) {
            OutlinedButton(onClick = { showAreas = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Choose training areas") }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Training areas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    summary.areas.forEach { assignment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(if (assignment.strength == Strength.FULL) 34.dp else 17.dp).height(8.dp).background(Color(assignment.area.color), RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text(assignment.area.label, Modifier.weight(1f))
                            Text(assignment.strength.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { showAreas = true }) { Text("Edit areas") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current weight", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatWeight(machine.currentWeight), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(lastChange?.let(::timeSince).let { if (it == null) "No weight changes yet" else "Changed $it ago" })
                Button(onClick = { showWeight = true }, modifier = Modifier.fillMaxWidth()) { Text("Change current weight") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add today's set", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = { showQuickSet = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Log reps at ${formatWeight(machine.currentWeight)}") }
                val recentAmounts = summary.recentSets.map { it.reps }.distinct().take(3)
                if (recentAmounts.isNotEmpty()) {
                    Text("Quick repeat", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentAmounts.forEach { reps ->
                            OutlinedButton(onClick = {
                                repository.addSet(machine.id, reps, machine.currentWeight)
                                onChanged()
                            }) { Text("$reps reps") }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Weight statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Started", formatWeight(first))
                    Stat("Current", formatWeight(machine.currentWeight))
                    Stat("Highest", formatWeight(highest))
                }
                if (summary.weightChanges.size > 1) WeightChart(summary.weightChanges)
                Text("${summary.weightChanges.size} weight changes · ${summary.recentSets.size}${if (summary.recentSets.size == 12) "+" else ""} recorded sets", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notes", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { showEdit = true }) { Text("Edit") }
                }
                Text(if (machine.notes.isBlank()) "No notes yet. Add seat positions, form cues, or other reminders." else machine.notes, color = if (machine.notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recent sets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (summary.recentSets.isEmpty()) Text("No sets recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                summary.recentSets.forEachIndexed { index, set ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${set.reps} reps × ${formatWeight(set.weight)}", fontWeight = FontWeight.Medium)
                            Text(formatTimestamp(set.performedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            repository.deleteSet(set.id)
                            onChanged()
                        }) { Text("Delete") }
                    }
                }
            }
        }

        OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit name and notes") }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete machine", color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(24.dp))
    }

    if (showQuickSet) QuickRepDialog(
        previousReps = summary.recentSets.map { it.reps }.distinct().take(3),
        onDismiss = { showQuickSet = false },
        onAdd = {
            repository.addSet(machine.id, it, machine.currentWeight)
            onChanged()
            showQuickSet = false
        },
    )
    if (showWeight) WeightDialog(machine.currentWeight, onDismiss = { showWeight = false }) {
        repository.changeWeight(machine.id, it)
        onChanged()
        showWeight = false
    }
    if (showEdit) EditMachineDialog(machine.name, machine.notes, onDismiss = { showEdit = false }) { name, notes ->
        repository.updateMachine(machine.id, name, notes)
        onChanged()
        showEdit = false
    }
    if (showAreas) AreaDialog(summary.areas, onDismiss = { showAreas = false }) {
        repository.saveAreas(machine.id, it)
        onChanged()
        showAreas = false
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${machine.name}?") },
        text = { Text("Its sets, notes, body areas, and weight history will be permanently removed.") },
        confirmButton = { Button(onClick = { repository.deleteMachine(machine.id); onDeleted() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeightChart(changesNewestFirst: List<WeightChange>) {
    val values = changesNewestFirst.asReversed().map { it.weight }
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp)) {
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val points = values.mapIndexed { index, value ->
            Offset(
                x = if (values.size == 1) size.width / 2 else index * size.width / (values.size - 1),
                y = size.height - ((value - min) / range).toFloat() * (size.height - 8f) - 4f,
            )
        }
        points.zipWithNext().forEach { (a, b) -> drawLine(primary, a, b, strokeWidth = 6f, cap = StrokeCap.Round) }
        points.forEach { drawCircle(primary, radius = 7f, center = it) }
    }
}

@Composable
private fun WeightDialog(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember { mutableStateOf(if (current % 1.0 == 0.0) current.toInt().toString() else current.toString()) }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change weight") },
        text = { OutlinedTextField(value, { value = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text("New weight (kg)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) },
        confirmButton = { Button(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed >= 0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditMachineDialog(currentName: String, currentNotes: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    var notes by remember { mutableStateOf(currentNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit machine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Machine name") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 4)
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), notes) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AreaDialog(current: List<AreaAssignment>, onDismiss: () -> Unit, onSave: (List<AreaAssignment>) -> Unit) {
    val choices = remember(current) {
        mutableStateMapOf<BodyArea, Strength?>().apply {
            BodyArea.entries.forEach { area -> put(area, current.firstOrNull { it.area == area }?.strength) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training areas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Full uses a full-width color marker; half uses half-width.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                BodyArea.entries.forEach { area ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(12.dp).height(36.dp).background(Color(area.color), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(area.label, Modifier.weight(1f))
                        FilterChip(selected = choices[area] == Strength.HALF, onClick = { choices[area] = if (choices[area] == Strength.HALF) null else Strength.HALF }, label = { Text("½") })
                        Spacer(Modifier.width(5.dp))
                        FilterChip(selected = choices[area] == Strength.FULL, onClick = { choices[area] = if (choices[area] == Strength.FULL) null else Strength.FULL }, label = { Text("Full") })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(choices.mapNotNull { (area, strength) -> strength?.let { AreaAssignment(area, it) } }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun timeSince(timestamp: Long): String {
    val duration = Duration.between(Instant.ofEpochMilli(timestamp), Instant.now()).coerceAtLeast(Duration.ZERO)
    val days = duration.toDays()
    return when {
        days >= 365 -> "${days / 365}y ${days % 365 / 30}mo"
        days >= 30 -> "${days / 30}mo ${days % 30}d"
        days > 0 -> "${days}d"
        duration.toHours() > 0 -> "${duration.toHours()}h"
        else -> "${duration.toMinutes().coerceAtLeast(0)}m"
    }
}

private fun formatTimestamp(timestamp: Long): String = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm")
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
