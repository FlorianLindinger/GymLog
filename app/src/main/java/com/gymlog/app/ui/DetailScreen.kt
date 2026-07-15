package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.AreaAssignment
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.GymRepository
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.SetRecord
import com.gymlog.app.data.Strength
import com.gymlog.app.data.WeightChange
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DetailScreen(
    summary: MachineSummary,
    areaUsage: Map<BodyArea, Int>,
    areaColors: Map<BodyArea, Long>,
    modifier: Modifier = Modifier,
    repository: GymRepository,
    onAreaColor: (BodyArea, Long) -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit,
) {
    var showQuickSet by remember { mutableStateOf(false) }
    var showWeight by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showAreas by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<SetRecord?>(null) }
    var expandedBatch by remember { mutableStateOf<Long?>(null) }
    val machine = summary.machine
    val highest = summary.weightChanges.maxOfOrNull { it.weight } ?: machine.currentWeight
    val first = summary.weightChanges.lastOrNull()?.weight ?: machine.currentWeight
    val lastChange = summary.weightChanges.firstOrNull()?.changedAt

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notes", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Edit", Modifier.clickable { showEdit = true }.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                Text(if (machine.notes.isBlank()) "No notes yet. Add seat positions, form cues, or other reminders." else machine.notes, color = if (machine.notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }

        if (summary.areas.isEmpty()) {
            OutlinedButton(onClick = { showAreas = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Choose training areas") }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Training areas", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Edit", Modifier.clickable { showAreas = true }.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    summary.areas.forEach { assignment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(if (assignment.strength == Strength.FULL) 34.dp else 17.dp).height(8.dp).background(Color(assignment.displayColor), RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(6.dp))
                            Text(assignment.area.label, Modifier.weight(1f))
                            Text(assignment.strength.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().clickable { showWeight = true }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(formatMetric(machine.currentWeight, machine.trackingMode), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(weightChangeDescription(summary.weightChanges, machine.trackingMode), style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("Add today's set", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Pause: ${machine.pauseTime.ifBlank { "not set" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Edit", Modifier.clickable { showEdit = true }.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                Button(onClick = { showQuickSet = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(when (machine.trackingMode) {
                        "STRENGTH" -> "+ Log level"
                        "DURATION" -> "+ Log minutes"
                        else -> "+ Log reps at ${formatMetric(machine.currentWeight, machine.trackingMode)}"
                    })
                }
                val recentAmounts = summary.recentSets.map { it.reps }.distinct().take(3)
                if (machine.trackingMode == "WEIGHT" && recentAmounts.isNotEmpty()) {
                    Text("Quick repeat", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentAmounts.forEach { reps ->
                            Text("$reps reps", Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                .clickable {
                                repository.addSet(machine.id, reps, machine.currentWeight)
                                onChanged()
                            }.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${metricTitle(machine.trackingMode)} statistics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Started", formatMetric(first, machine.trackingMode))
                    Stat("Current", formatMetric(machine.currentWeight, machine.trackingMode))
                    Stat("Highest", formatMetric(highest, machine.trackingMode))
                }
                Text("Chart window", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("1M", "3M", "6M", "1Y", "ALL").forEach { window ->
                        CompactChoice(
                            selected = machine.chartWindow == window,
                            onClick = { repository.setChartWindow(machine.id, window); onChanged() },
                            label = window,
                        )
                    }
                }
                Text("Trailing average", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 3 to "3", 5 to "5", 10 to "10").forEach { (amount, label) ->
                        CompactChoice(
                            selected = machine.trailingAverage == amount,
                            onClick = { repository.setTrailingAverage(machine.id, amount); onChanged() },
                            label = label,
                        )
                    }
                }
                val cutoff = chartCutoff(machine.chartWindow)
                val visibleWeights = summary.weightChanges.filter { cutoff == null || it.changedAt >= cutoff }
                val visibleSets = summary.recentSets.filter { cutoff == null || it.performedAt >= cutoff }
                if (visibleWeights.size > 1) WeightChart(visibleWeights, machine.trailingAverage)
                Text(when (machine.trackingMode) {
                    "WEIGHT" -> "Volume (weight × reps)"
                    "DURATION" -> "Minutes logged"
                    else -> "Levels logged"
                }, style = MaterialTheme.typography.labelLarge)
                VolumeChart(visibleSets, machine.trailingAverage)
                Text("${summary.weightChanges.size} ${metricName(machine.trackingMode)} changes · ${summary.recentSets.size} recorded sets", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Recent entries", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (summary.recentSets.isEmpty()) Text("No entries recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                groupSetBatches(summary.recentSets).take(12).forEachIndexed { index, batch ->
                    val ordered = batch.sortedBy { it.performedAt }
                    val batchKey = ordered.first().performedAt
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isDirectMetric(machine.trackingMode)) "${formatMetric(ordered.first().weight, machine.trackingMode)} - ${formatTimestamp(batchKey)}"
                            else "${formatMetric(ordered.first().weight, machine.trackingMode)} - ${ordered.joinToString(",") { it.reps.toString() }} - ${formatTimestamp(batchKey)}",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                        )
                        Text(if (expandedBatch == batchKey) "Close" else "Edit", Modifier.clickable { expandedBatch = if (expandedBatch == batchKey) null else batchKey }.padding(horizontal = 6.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Text("Delete", Modifier.clickable {
                            batch.forEach { repository.deleteSet(it.id) }
                            onChanged()
                        }.padding(horizontal = 6.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                    if (expandedBatch == batchKey) ordered.forEachIndexed { setIndex, set ->
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 1.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isDirectMetric(machine.trackingMode)) "Entry ${setIndex + 1}: ${formatMetric(set.weight, machine.trackingMode)}"
                                else "Set ${setIndex + 1}: ${set.reps} reps",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text("Edit", Modifier.clickable { editingSet = set }.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            Text("Delete", Modifier.clickable { repository.deleteSet(set.id); onChanged() }.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit exercise details") }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete exercise", color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
    }

    if (showQuickSet) {
        if (isDirectMetric(machine.trackingMode)) {
            QuickMetricDialog(
                initialValue = (summary.recentSets.firstOrNull()?.weight ?: machine.currentWeight).toInt(),
                trackingMode = machine.trackingMode,
                onDismiss = { showQuickSet = false },
                onAdd = {
                    repository.addMetricSet(machine.id, it)
                    onChanged()
                    showQuickSet = false
                },
            )
        } else {
            QuickRepDialog(
                previousReps = previousReps(summary),
                onDismiss = { showQuickSet = false },
                onAdd = {
                    repository.addSets(machine.id, it, machine.currentWeight)
                    onChanged()
                    showQuickSet = false
                },
            )
        }
    }
    if (showWeight) MetricValueDialog(machine.currentWeight, machine.trackingMode, onDismiss = { showWeight = false }) {
        repository.changeWeight(machine.id, it)
        onChanged()
        showWeight = false
    }
    if (showEdit) EditMachineDialog(machine.name, machine.titleNote, machine.notes, machine.pauseTime, onDismiss = { showEdit = false }) { name, titleNote, notes, pauseTime ->
        repository.updateMachine(machine.id, name, titleNote, notes, pauseTime)
        onChanged()
        showEdit = false
    }
    if (showAreas) AreaDialog(summary.areas, areaUsage, areaColors, onAreaColor, onDismiss = { showAreas = false }) {
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
    editingSet?.let { set ->
        EditSetDialog(machine.trackingMode, set.reps, set.weight, onDismiss = { editingSet = null }) { reps, weight ->
            repository.updateSet(set.id, reps, weight)
            onChanged()
            editingSet = null
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactChoice(selected: Boolean, onClick: () -> Unit, label: String) {
    Text(
        label,
        modifier = Modifier
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun WeightChart(changesNewestFirst: List<WeightChange>, trailingAverage: Int) {
    val values = movingAverage(changesNewestFirst.asReversed().map { it.weight }, trailingAverage)
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(80.dp).padding(vertical = 4.dp)) {
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
private fun VolumeChart(sets: List<SetRecord>, trailingAverage: Int) {
    val daily = sets.groupBy {
        Instant.ofEpochMilli(it.performedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }.toSortedMap().map { (_, daySets) -> daySets.sumOf { it.weight * it.reps } }
    if (daily.isEmpty()) {
        Text("No volume in this window.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val values = movingAverage(daily, trailingAverage).takeLast(24)
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val primary = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxWidth().height(64.dp).padding(vertical = 4.dp)) {
        val gap = 4f
        val width = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { index, value ->
            val barHeight = (value / max).toFloat() * size.height
            drawRoundRect(
                color = primary,
                topLeft = Offset(index * (width + gap), size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(width, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f),
            )
        }
    }
    if (daily.size > 24) Text("Showing the most recent 24 active days", style = MaterialTheme.typography.bodySmall)
}

private fun movingAverage(values: List<Double>, amount: Int): List<Double> {
    if (amount <= 1) return values
    return values.indices.map { index -> values.subList((index - amount + 1).coerceAtLeast(0), index + 1).average() }
}

private fun weightChangeDescription(changes: List<WeightChange>, trackingMode: String): String {
    val metric = metricName(trackingMode)
    val latest = changes.firstOrNull() ?: return "No $metric changes yet"
    val previous = changes.getOrNull(1) ?: return "Initial $metric set ${timeSince(latest.changedAt)} ago"
    val delta = latest.weight - previous.weight
    val amount = if (delta % 1.0 == 0.0) delta.toInt().toString() else "%.1f".format(delta)
    val signed = if (delta > 0) "+$amount" else amount
    val unit = when (trackingMode) {
        "WEIGHT" -> "kg"
        "DURATION" -> "min"
        else -> if (kotlin.math.abs(delta) == 1.0) "level" else "levels"
    }
    return "Changed by $signed $unit ${timeSince(latest.changedAt)} ago"
}

private fun chartCutoff(window: String): Long? {
    val now = Instant.now()
    return when (window) {
        "1M" -> now.minus(Duration.ofDays(30)).toEpochMilli()
        "3M" -> now.minus(Duration.ofDays(90)).toEpochMilli()
        "6M" -> now.minus(Duration.ofDays(182)).toEpochMilli()
        "1Y" -> now.minus(Duration.ofDays(365)).toEpochMilli()
        else -> null
    }
}

@Composable
fun MetricValueDialog(current: Double, trackingMode: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember { mutableStateOf(if (current % 1.0 == 0.0) current.toInt().toString() else current.toString()) }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change ${metricName(trackingMode)}") },
        text = { OutlinedTextField(value, { value = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text(when (trackingMode) { "WEIGHT" -> "New weight (kg)"; "DURATION" -> "New duration (min)"; else -> "New level" }) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (trackingMode == "WEIGHT") KeyboardType.Decimal else KeyboardType.Number)) },
        confirmButton = { Button(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed >= 0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditMachineDialog(currentName: String, currentTitleNote: String, currentNotes: String, currentPauseTime: String, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    var titleNote by remember { mutableStateOf(currentTitleNote) }
    var notes by remember { mutableStateOf(currentNotes) }
    var pauseTime by remember { mutableStateOf(currentPauseTime) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Exercise name") }, singleLine = true)
                OutlinedTextField(
                    titleNote,
                    { titleNote = it },
                    label = { Text("Title note") },
                    placeholder = { Text("e.g. Seat 4 or Heavy") },
                    supportingText = { Text("Shown beside the exercise name.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    pauseTime,
                    { pauseTime = it },
                    label = { Text("Pause note") },
                    placeholder = { Text("e.g. 90 sec or 2 min") },
                    supportingText = { Text("Saved as a note; no timer will start.") },
                    singleLine = true,
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 4)
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), titleNote.trim(), notes, pauseTime.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AreaDialog(
    current: List<AreaAssignment>,
    usage: Map<BodyArea, Int>,
    colors: Map<BodyArea, Long>,
    onColorChange: (BodyArea, Long) -> Unit,
    onDismiss: () -> Unit,
    onSave: (List<AreaAssignment>) -> Unit,
) {
    val choices = remember(current) {
        mutableStateMapOf<BodyArea, Strength?>().apply {
            BodyArea.entries.forEach { area -> put(area, current.firstOrNull { it.area == area }?.strength) }
        }
    }
    var colorArea by remember { mutableStateOf<BodyArea?>(null) }
    fun cycle(area: BodyArea) {
        choices[area] = when (choices[area]) {
            null -> Strength.HALF
            Strength.HALF -> Strength.FULL
            Strength.FULL -> null
        }
    }
    val sortedAreas = BodyArea.entries.sortedWith(
        compareByDescending<BodyArea> { usage[it] ?: 0 }.thenBy { it.label },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training areas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tap a colored body region to cycle: none → half → full.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                BodyMapPicker(
                    choices = choices,
                    colors = colors,
                    onArea = ::cycle,
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                )
                Text("Most-used areas appear first. Tap a color dot to change it.", style = MaterialTheme.typography.bodySmall)
                sortedAreas.forEach { area ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(22.dp).height(36.dp)
                                .background(Color(colors[area] ?: area.color), CircleShape)
                                .clickable { colorArea = area },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(area.label)
                            if ((usage[area] ?: 0) > 0) Text("Used ${usage[area]}×", style = MaterialTheme.typography.labelSmall)
                        }
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
    colorArea?.let { area ->
        ColorPaletteDialog(
            area = area,
            selected = colors[area] ?: area.color,
            onDismiss = { colorArea = null },
            onSelect = { onColorChange(area, it); colorArea = null },
        )
    }
}

@Composable
private fun BodyMapPicker(
    choices: Map<BodyArea, Strength?>,
    colors: Map<BodyArea, Long>,
    onArea: (BodyArea) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val baseColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .32f)
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { position ->
                    areaFromBodyMap(position.x / size.width, position.y / size.height)?.let(onArea)
                }
            },
        ) {
            fun drawFigure(cx: Float) {
                drawCircle(baseColor, radius = size.width * .035f, center = Offset(size.width * cx, size.height * .12f))
                drawRoundRect(baseColor, Offset(size.width * (cx - .065f), size.height * .19f), androidx.compose.ui.geometry.Size(size.width * .13f, size.height * .34f), androidx.compose.ui.geometry.CornerRadius(18f, 18f))
                drawLine(baseColor, Offset(size.width * (cx - .06f), size.height * .24f), Offset(size.width * (cx - .15f), size.height * .48f), strokeWidth = size.width * .035f, cap = StrokeCap.Round)
                drawLine(baseColor, Offset(size.width * (cx + .06f), size.height * .24f), Offset(size.width * (cx + .15f), size.height * .48f), strokeWidth = size.width * .035f, cap = StrokeCap.Round)
                drawLine(baseColor, Offset(size.width * (cx - .035f), size.height * .50f), Offset(size.width * (cx - .07f), size.height * .92f), strokeWidth = size.width * .05f, cap = StrokeCap.Round)
                drawLine(baseColor, Offset(size.width * (cx + .035f), size.height * .50f), Offset(size.width * (cx + .07f), size.height * .92f), strokeWidth = size.width * .05f, cap = StrokeCap.Round)
            }
            fun selectedColor(area: BodyArea): Color? = choices[area]?.let { strength ->
                Color(colors[area] ?: area.color).copy(alpha = if (strength == Strength.FULL) .95f else .55f)
            }
            fun region(area: BodyArea, x: Float, y: Float, width: Float, height: Float) {
                selectedColor(area)?.let { color ->
                    drawRoundRect(color, Offset(size.width * x, size.height * y), androidx.compose.ui.geometry.Size(size.width * width, size.height * height), androidx.compose.ui.geometry.CornerRadius(12f, 12f))
                }
            }
            fun limb(area: BodyArea, startX: Float, startY: Float, endX: Float, endY: Float, width: Float) {
                selectedColor(area)?.let { color -> drawLine(color, Offset(size.width * startX, size.height * startY), Offset(size.width * endX, size.height * endY), strokeWidth = size.width * width, cap = StrokeCap.Round) }
            }

            drawFigure(.25f)
            drawFigure(.75f)
            region(BodyArea.CHEST, .19f, .25f, .12f, .11f)
            region(BodyArea.CORE, .20f, .37f, .10f, .14f)
            limb(BodyArea.SHOULDERS, .16f, .25f, .34f, .25f, .055f)
            limb(BodyArea.BICEPS, .15f, .29f, .11f, .42f, .035f)
            limb(BodyArea.BICEPS, .35f, .29f, .39f, .42f, .035f)
            limb(BodyArea.QUADS, .215f, .57f, .19f, .73f, .045f)
            limb(BodyArea.QUADS, .285f, .57f, .31f, .73f, .045f)
            limb(BodyArea.CALVES, .19f, .76f, .18f, .90f, .035f)
            limb(BodyArea.CALVES, .31f, .76f, .32f, .90f, .035f)

            limb(BodyArea.SHOULDERS, .66f, .25f, .84f, .25f, .055f)
            region(BodyArea.UPPER_BACK, .69f, .25f, .12f, .14f)
            region(BodyArea.LOWER_BACK, .70f, .39f, .10f, .12f)
            limb(BodyArea.TRICEPS, .65f, .29f, .61f, .42f, .035f)
            limb(BodyArea.TRICEPS, .85f, .29f, .89f, .42f, .035f)
            region(BodyArea.GLUTES, .68f, .50f, .14f, .10f)
            limb(BodyArea.HAMSTRINGS, .715f, .61f, .69f, .75f, .045f)
            limb(BodyArea.HAMSTRINGS, .785f, .61f, .81f, .75f, .045f)
            limb(BodyArea.CALVES, .69f, .77f, .68f, .90f, .035f)
            limb(BodyArea.CALVES, .81f, .77f, .82f, .90f, .035f)
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Text("Front", style = MaterialTheme.typography.labelMedium)
            Text("Back", style = MaterialTheme.typography.labelMedium)
        }
        Text(
            "Selected: ${choices.count { it.value != null }}",
            modifier = Modifier.align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.surface.copy(alpha = .85f), RoundedCornerShape(8.dp)).padding(5.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun areaFromBodyMap(x: Float, y: Float): BodyArea? {
    val front = x < .5f
    val centerX = if (front) .25f else .75f
    val distance = kotlin.math.abs(x - centerX)
    return if (front) when {
        y in 0.20f..0.29f && distance in 0.07f..0.18f -> BodyArea.SHOULDERS
        y in 0.25f..0.37f && distance < .075f -> BodyArea.CHEST
        y in 0.29f..0.48f && distance in 0.09f..0.20f -> BodyArea.BICEPS
        y in 0.37f..0.53f && distance < .075f -> BodyArea.CORE
        y in 0.54f..0.75f && distance < .10f -> BodyArea.QUADS
        y in 0.75f..0.95f && distance < .11f -> BodyArea.CALVES
        else -> null
    } else when {
        y in 0.20f..0.29f && distance in 0.07f..0.18f -> BodyArea.SHOULDERS
        y in 0.25f..0.40f && distance < .075f -> BodyArea.UPPER_BACK
        y in 0.29f..0.48f && distance in 0.09f..0.20f -> BodyArea.TRICEPS
        y in 0.40f..0.52f && distance < .07f -> BodyArea.LOWER_BACK
        y in 0.50f..0.62f && distance < .10f -> BodyArea.GLUTES
        y in 0.62f..0.78f && distance < .10f -> BodyArea.HAMSTRINGS
        y in 0.78f..0.95f && distance < .11f -> BodyArea.CALVES
        else -> null
    }
}

@Composable
private fun ColorPaletteDialog(area: BodyArea, selected: Long, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    val palette = listOf(
        0xFFE63946, 0xFFFF7F11, 0xFFF2C14E, 0xFF70C1B3,
        0xFF2A9D8F, 0xFF3A86FF, 0xFF4361EE, 0xFF8338EC,
        0xFFB565D9, 0xFFFF5D8F, 0xFF8D6E63, 0xFF577590,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${area.label} color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { value ->
                            Box(
                                Modifier.width(48.dp).height(48.dp)
                                    .background(Color(value), CircleShape)
                                    .clickable { onSelect(value) },
                                contentAlignment = Alignment.Center,
                            ) { if (selected == value) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
