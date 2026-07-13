package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymlog.app.R
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
                            Box(Modifier.width(if (assignment.strength == Strength.FULL) 34.dp else 17.dp).height(8.dp).background(Color(assignment.displayColor), RoundedCornerShape(8.dp)))
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
                Text(if (machine.trackingMode == "WEIGHT") "Current weight" else "Current level", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMetric(machine.currentWeight, machine.trackingMode), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(weightChangeDescription(summary.weightChanges, machine.trackingMode))
                Button(onClick = { showWeight = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (machine.trackingMode == "WEIGHT") "Change current weight" else "Change current level")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add today's set", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pause between sets", style = MaterialTheme.typography.labelMedium)
                        Text(
                            machine.pauseTime.ifBlank { "No pause note saved" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showEdit = true }) { Text("Edit") }
                }
                Button(onClick = { showQuickSet = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Log reps at ${formatMetric(machine.currentWeight, machine.trackingMode)}")
                }
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
                Text(if (machine.trackingMode == "WEIGHT") "Weight statistics" else "Level statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Started", formatMetric(first, machine.trackingMode))
                    Stat("Current", formatMetric(machine.currentWeight, machine.trackingMode))
                    Stat("Highest", formatMetric(highest, machine.trackingMode))
                }
                Text("Chart window", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("1M", "3M", "6M", "1Y", "ALL").forEach { window ->
                        FilterChip(
                            selected = machine.chartWindow == window,
                            onClick = { repository.setChartWindow(machine.id, window); onChanged() },
                            label = { Text(window) },
                        )
                    }
                }
                Text("Trailing average", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Off", 3 to "3", 5 to "5", 10 to "10").forEach { (amount, label) ->
                        FilterChip(
                            selected = machine.trailingAverage == amount,
                            onClick = { repository.setTrailingAverage(machine.id, amount); onChanged() },
                            label = { Text(label) },
                        )
                    }
                }
                val cutoff = chartCutoff(machine.chartWindow)
                val visibleWeights = summary.weightChanges.filter { cutoff == null || it.changedAt >= cutoff }
                val visibleSets = summary.recentSets.filter { cutoff == null || it.performedAt >= cutoff }
                if (visibleWeights.size > 1) WeightChart(visibleWeights, machine.trailingAverage)
                Text(if (machine.trackingMode == "WEIGHT") "Volume (weight × reps)" else "Volume (level × reps)", style = MaterialTheme.typography.labelLarge)
                VolumeChart(visibleSets, machine.trailingAverage)
                Text("${summary.weightChanges.size} ${if (machine.trackingMode == "WEIGHT") "weight" else "level"} changes · ${summary.recentSets.size} recorded sets", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                summary.recentSets.take(12).forEachIndexed { index, set ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${set.reps} reps × ${formatMetric(set.weight, machine.trackingMode)}", fontWeight = FontWeight.Medium)
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

        OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.fillMaxWidth()) { Text("Edit exercise details") }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete exercise", color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(24.dp))
    }

    if (showQuickSet) QuickRepDialog(
        previousReps = previousReps(summary),
        onDismiss = { showQuickSet = false },
        onAdd = {
            repository.addSets(machine.id, it, machine.currentWeight)
            onChanged()
            showQuickSet = false
        },
    )
    if (showWeight) WeightDialog(machine.currentWeight, machine.trackingMode, onDismiss = { showWeight = false }) {
        repository.changeWeight(machine.id, it)
        onChanged()
        showWeight = false
    }
    if (showEdit) EditMachineDialog(machine.name, machine.notes, machine.pauseTime, onDismiss = { showEdit = false }) { name, notes, pauseTime ->
        repository.updateMachine(machine.id, name, notes, pauseTime)
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
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeightChart(changesNewestFirst: List<WeightChange>, trailingAverage: Int) {
    val values = movingAverage(changesNewestFirst.asReversed().map { it.weight }, trailingAverage)
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
    Canvas(Modifier.fillMaxWidth().height(100.dp).padding(vertical = 8.dp)) {
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
    val metric = if (trackingMode == "WEIGHT") "weight" else "level"
    val latest = changes.firstOrNull() ?: return "No $metric changes yet"
    val previous = changes.getOrNull(1) ?: return "Initial $metric set ${timeSince(latest.changedAt)} ago"
    val delta = latest.weight - previous.weight
    val amount = if (delta % 1.0 == 0.0) delta.toInt().toString() else "%.1f".format(delta)
    val signed = if (delta > 0) "+$amount" else amount
    val unit = if (trackingMode == "WEIGHT") "kg" else if (kotlin.math.abs(delta) == 1.0) "level" else "levels"
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
private fun WeightDialog(current: Double, trackingMode: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var value by remember { mutableStateOf(if (current % 1.0 == 0.0) current.toInt().toString() else current.toString()) }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (trackingMode == "WEIGHT") "Change weight" else "Change level") },
        text = { OutlinedTextField(value, { value = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text(if (trackingMode == "WEIGHT") "New weight (kg)" else "New level") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (trackingMode == "WEIGHT") KeyboardType.Decimal else KeyboardType.Number)) },
        confirmButton = { Button(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed >= 0) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditMachineDialog(currentName: String, currentNotes: String, currentPauseTime: String, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    var notes by remember { mutableStateOf(currentNotes) }
    var pauseTime by remember { mutableStateOf(currentPauseTime) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Exercise name") }, singleLine = true)
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
        confirmButton = { Button(onClick = { onSave(name.trim(), notes, pauseTime.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
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
            BodyArea.entries.filter { it.selectable }.forEach { area -> put(area, current.firstOrNull { it.area == area }?.strength) }
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
    val sortedAreas = BodyArea.entries.filter { it.selectable }.sortedWith(
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
                    onArea = ::cycle,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
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
    onArea: (BodyArea) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.openstax_muscles),
            contentDescription = "OpenStax front and back muscle diagram. Tap an upper-body region to select it.",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { position ->
                    areaFromBodyMap(position.x / size.width, position.y / size.height)?.let(onArea)
                }
            },
        )
        Text(
            "Selected: ${choices.count { it.value != null }}",
            modifier = Modifier.align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.surface.copy(alpha = .85f), RoundedCornerShape(8.dp)).padding(5.dp),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "OpenStax · CC BY 4.0 · cropped",
            modifier = Modifier.align(Alignment.TopCenter)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .88f), RoundedCornerShape(6.dp))
                .clickable { uriHandler.openUri("https://commons.wikimedia.org/wiki/File:1105_Anterior_and_Posterior_Views_of_Muscles.jpg") }
                .padding(4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun areaFromBodyMap(x: Float, y: Float): BodyArea? {
    if (y !in .16f..0.76f) return null
    val front = x < .5f
    val localX = if (front) x / .5f else (x - .5f) / .5f
    val center = localX in .28f..72f
    return if (front) when {
        y < .36f && center -> BodyArea.CHEST
        y < .39f -> BodyArea.SHOULDERS
        y < .58f && center -> BodyArea.CORE
        y < .48f -> BodyArea.BICEPS
        else -> BodyArea.FOREARMS
    } else when {
        y < .36f && !center -> BodyArea.SHOULDERS
        y < .46f && center -> BodyArea.UPPER_BACK
        y < .49f && !center -> BodyArea.TRICEPS
        y < .62f && center -> BodyArea.LOWER_BACK
        else -> BodyArea.FOREARMS
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
