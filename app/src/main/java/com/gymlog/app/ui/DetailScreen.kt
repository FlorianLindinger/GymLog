package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import com.gymlog.app.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    var showWeight by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showAreas by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<SetRecord?>(null) }
    var expandedBatch by remember { mutableStateOf<Long?>(null) }
    val machine = summary.machine
    val currentUnitSets = currentTrainingUnitSets(summary)
    val highest = summary.weightChanges.maxOfOrNull { it.weight } ?: machine.currentWeight
    val first = summary.weightChanges.lastOrNull()?.weight ?: machine.currentWeight

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth().clickable { showNotes = true }) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (machine.notes.isBlank()) "Click to add note" else machine.notes, color = if (machine.notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }

        if (summary.areas.isEmpty()) {
            OutlinedButton(onClick = { showAreas = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Choose training areas") }
        } else {
            Card(modifier = Modifier.fillMaxWidth().clickable { showAreas = true }) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Training areas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    summary.areas.sortedByDescending { areaUsage[it.area] ?: 0 }.forEach { assignment ->
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
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatMetric(machine.currentWeight, machine.trackingMode), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(weightChangeDescription(summary.weightChanges, machine.trackingMode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            label = if (window == "ALL") "All" else window,
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
                val visibleSets = summary.recentSets.filter { cutoff == null || it.performedAt >= cutoff }
                SessionMetricsChart(visibleSets, machine.trailingAverage, isDirectMetric(machine.trackingMode))
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
                            if (isDirectMetric(machine.trackingMode)) "${formatTrainingEntry(ordered.first().weight, machine.trackingMode)} - ${formatTimestamp(batchKey)}"
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
                                if (isDirectMetric(machine.trackingMode)) "Entry ${setIndex + 1}: ${formatTrainingEntry(set.weight, machine.trackingMode)}"
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

        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete exercise", color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
    }

    if (showWeight) MetricValueDialog(
        current = machine.currentWeight,
        trackingMode = machine.trackingMode,
        onDismiss = { showWeight = false },
        onSave = {
            repository.changeWeight(machine.id, it)
            onChanged()
            showWeight = false
        },
        onSaveCurrentAndNext = if (currentUnitSets.isNotEmpty()) { value ->
            repository.changeMetricForCurrentTrainingUnit(machine.id, value)
            onChanged()
            showWeight = false
        } else null,
    )
    if (showNotes) TextEditDialog("Notes", machine.notes, onDismiss = { showNotes = false }) {
        repository.setMachineNotes(machine.id, it)
        onChanged()
        showNotes = false
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
private fun SessionMetricsChart(sets: List<SetRecord>, trailingAverage: Int, directMetric: Boolean) {
    val allSessions = groupSetBatches(sets).asReversed()
    val sessions = allSessions.takeLast(24)
    if (sessions.isEmpty()) {
        Text("No training data in this window.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val weight = movingAverage(sessions.map { it.first().weight }, trailingAverage)
    val volume = movingAverage(sessions.map { session -> session.sumOf { it.weight * it.reps } }, trailingAverage)
    val reps = movingAverage(sessions.map { session -> session.sumOf { it.reps }.toDouble() }, trailingAverage)
    val minutes = movingAverage(sessions.map { session -> session.sumOf { it.weight } }, trailingAverage)
    val weightColor = MaterialTheme.colorScheme.primary
    val volumeColor = Color(0xFF00BCD4)
    val repsColor = Color(0xFFFFA726)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val series = if (directMetric) listOf(Triple("Minutes", minutes, weightColor)) else listOf(
        Triple("Weight (kg)", weight, weightColor),
        Triple("Volume (kg × reps)", volume, volumeColor),
        Triple("Reps", reps, repsColor),
    )
    Text("Training session chart", style = MaterialTheme.typography.labelLarge)
    series.forEach { (label, values, color) ->
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        Text("$label axis: ${formatAxisValue(min)}–${formatAxisValue(max)}", color = color, style = MaterialTheme.typography.labelSmall)
    }
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 6.dp)) {
        repeat(3) { index ->
            val y = index * size.height / 2f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        series.forEach { (_, values, color) ->
            val min = values.minOrNull() ?: 0.0
            val max = values.maxOrNull() ?: 0.0
            val range = (max - min).takeIf { it > 0 } ?: 1.0
            val points = values.mapIndexed { index, value ->
                Offset(
                    x = if (values.size == 1) size.width / 2f else index * size.width / (values.size - 1),
                    y = size.height - 5f - ((value - min) / range).toFloat() * (size.height - 10f),
                )
            }
            points.zipWithNext().forEach { (start, end) -> drawLine(color, start, end, strokeWidth = 5f, cap = StrokeCap.Round) }
            points.forEach { drawCircle(color, radius = 5f, center = it) }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTimestamp(sessions.first().minOf { it.performedAt }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatTimestamp(sessions.last().maxOf { it.performedAt }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (allSessions.size > 24) Text("Showing the 24 most recent training sessions", style = MaterialTheme.typography.bodySmall)
}

private fun formatAxisValue(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString()
else String.format(Locale.US, "%.1f", value)

private fun movingAverage(values: List<Double>, amount: Int): List<Double> {
    if (amount <= 1) return values
    return values.indices.map { index -> values.subList((index - amount + 1).coerceAtLeast(0), index + 1).average() }
}

private fun weightChangeDescription(changes: List<WeightChange>, trackingMode: String): String {
    val metric = metricName(trackingMode)
    val latest = changes.firstOrNull() ?: return "No $metric changes yet"
    val previous = changes.getOrNull(1) ?: return "Initial $metric set ${timeSince(latest.changedAt)} ago"
    val delta = latest.weight - previous.weight
    val amount = if (delta % 1.0 == 0.0) delta.toInt().toString() else String.format(Locale.US, "%.1f", delta)
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
fun MetricValueDialog(
    current: Double,
    trackingMode: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onSaveCurrentAndNext: ((Double) -> Unit)? = null,
) {
    var value by remember { mutableStateOf(if (current % 1.0 == 0.0) current.toInt().toString() else current.toString()) }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change ${metricName(trackingMode)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text(when (trackingMode) { "WEIGHT" -> "New Weight (kg)"; "DURATION" -> "New Duration (min)"; else -> "New Level" }) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (trackingMode == "WEIGHT") KeyboardType.Decimal else KeyboardType.Number))
                if (onSaveCurrentAndNext != null) {
                    Text("This exercise already has entries in the current training session.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed >= 0, modifier = Modifier.fillMaxWidth()) { Text("Change for Next Session") }
                    androidx.compose.material3.FilledTonalButton(onClick = { parsed?.let(onSaveCurrentAndNext) }, enabled = parsed != null && parsed >= 0, modifier = Modifier.fillMaxWidth()) { Text("Change This Session and Next") }
                }
            }
        },
        confirmButton = {
            if (onSaveCurrentAndNext == null) Button(onClick = { parsed?.let(onSave) }, enabled = parsed != null && parsed >= 0) { Text("Save") }
        },
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
    val sortedAreas = BodyArea.entries.sortedWith(
        compareByDescending<BodyArea> { usage[it] ?: 0 }.thenBy { it.label },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training areas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Selected areas are previewed on the anatomy image. Use the Half and Full controls below to make changes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                BodyMapPicker(
                    choices = choices,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
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
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.body_anatomy),
            contentDescription = "Front and back muscular anatomy reference",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            fun selectedColor(area: BodyArea): Color? = choices[area]?.let { strength ->
                Color(colors[area] ?: area.color).copy(alpha = if (strength == Strength.FULL) .58f else .30f)
            }
            fun oval(area: BodyArea, x: Float, y: Float, width: Float, height: Float) {
                selectedColor(area)?.let { color ->
                    drawOval(color, Offset(size.width * x, size.height * y), Size(size.width * width, size.height * height))
                }
            }
            fun polygon(area: BodyArea, vararg points: Pair<Float, Float>) {
                selectedColor(area)?.let { color ->
                    val path = Path().apply {
                        moveTo(size.width * points.first().first, size.height * points.first().second)
                        points.drop(1).forEach { (x, y) -> lineTo(size.width * x, size.height * y) }
                        close()
                    }
                    drawPath(path, color)
                }
            }

            oval(BodyArea.SHOULDERS, .218f, .205f, .053f, .075f)
            oval(BodyArea.SHOULDERS, .339f, .205f, .053f, .075f)
            polygon(BodyArea.CHEST, .263f to .238f, .305f to .220f, .347f to .238f, .343f to .315f, .305f to .333f, .267f to .315f)
            polygon(BodyArea.CORE, .277f to .322f, .333f to .322f, .329f to .493f, .281f to .493f)
            oval(BodyArea.BICEPS, .202f, .285f, .033f, .115f)
            oval(BodyArea.BICEPS, .375f, .285f, .033f, .115f)
            polygon(BodyArea.FOREARMS, .201f to .390f, .226f to .397f, .203f to .510f, .181f to .516f)
            polygon(BodyArea.FOREARMS, .384f to .397f, .409f to .390f, .429f to .516f, .407f to .510f)
            polygon(BodyArea.QUADS, .270f to .515f, .303f to .525f, .298f to .705f, .267f to .705f)
            polygon(BodyArea.QUADS, .307f to .525f, .340f to .515f, .343f to .705f, .312f to .705f)
            polygon(BodyArea.ADDUCTORS, .298f to .520f, .307f to .535f, .305f to .675f, .292f to .610f)
            polygon(BodyArea.ADDUCTORS, .303f to .535f, .312f to .520f, .318f to .610f, .305f to .675f)
            polygon(BodyArea.ABDUCTORS, .256f to .490f, .278f to .505f, .273f to .620f, .252f to .585f)
            polygon(BodyArea.ABDUCTORS, .332f to .505f, .354f to .490f, .358f to .585f, .337f to .620f)
            polygon(BodyArea.CALVES, .264f to .715f, .296f to .715f, .282f to .875f, .258f to .875f)
            polygon(BodyArea.CALVES, .314f to .715f, .346f to .715f, .352f to .875f, .328f to .875f)

            oval(BodyArea.SHOULDERS, .608f, .205f, .053f, .075f)
            oval(BodyArea.SHOULDERS, .739f, .205f, .053f, .075f)
            polygon(BodyArea.TRAPS, .685f to .180f, .715f to .180f, .742f to .270f, .700f to .315f, .658f to .270f)
            polygon(BodyArea.UPPER_BACK, .654f to .270f, .700f to .305f, .746f to .270f, .739f to .405f, .700f to .440f, .661f to .405f)
            polygon(BodyArea.LOWER_BACK, .674f to .400f, .726f to .400f, .728f to .490f, .672f to .490f)
            oval(BodyArea.TRICEPS, .592f, .285f, .033f, .115f)
            oval(BodyArea.TRICEPS, .775f, .285f, .033f, .115f)
            polygon(BodyArea.FOREARMS, .591f to .390f, .616f to .397f, .597f to .510f, .575f to .516f)
            polygon(BodyArea.FOREARMS, .784f to .397f, .809f to .390f, .825f to .516f, .803f to .510f)
            oval(BodyArea.GLUTES, .650f, .475f, .100f, .105f)
            polygon(BodyArea.HAMSTRINGS, .650f to .565f, .696f to .575f, .686f to .720f, .650f to .720f)
            polygon(BodyArea.HAMSTRINGS, .704f to .575f, .750f to .565f, .750f to .720f, .714f to .720f)
            polygon(BodyArea.CALVES, .650f to .720f, .686f to .720f, .673f to .875f, .647f to .875f)
            polygon(BodyArea.CALVES, .714f to .720f, .750f to .720f, .753f to .875f, .727f to .875f)
        }
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
        else -> "${duration.toMinutes().coerceAtLeast(0)} min"
    }
}

private fun formatTimestamp(timestamp: Long): String = DateTimeFormatter.ofPattern("dd.MM.yy")
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
