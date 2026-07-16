package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
    areaUsage: Map<BodyArea, Double>,
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
            OutlinedButton(onClick = { showAreas = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Choose Training Areas") }
        } else {
            Card(modifier = Modifier.fillMaxWidth().clickable { showAreas = true }) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Training Areas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    summary.areas.sortedByDescending { areaUsage[it.area] ?: 0.0 }.forEach { assignment ->
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
                Text("${metricTitle(machine.trackingMode)} Statistics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Started", formatMetric(first, machine.trackingMode))
                    Stat("Current", formatMetric(machine.currentWeight, machine.trackingMode))
                    Stat("Highest", formatMetric(highest, machine.trackingMode))
                }
                Text("Chart Window", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("1M", "3M", "6M", "1Y", "ALL").forEach { window ->
                        CompactChoice(
                            selected = machine.chartWindow == window,
                            onClick = { repository.setChartWindow(machine.id, window); onChanged() },
                            label = if (window == "ALL") "All" else window,
                        )
                    }
                }
                Text("Trailing Average", style = MaterialTheme.typography.labelMedium)
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
                Text("Recent Entries", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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

        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete Exercise", color = MaterialTheme.colorScheme.error) }
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
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
    Text("Training Session Graph", style = MaterialTheme.typography.labelLarge)
    series.forEach { (label, values, color) ->
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        val change = fittedSeries(values).second
        Text(
            "$label: ${formatAxisValue(min)}–${formatAxisValue(max)} · fit ${formatSignedAxisValue(change)} / selected interval",
            color = color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 6.dp)) {
        repeat(3) { index ->
            val y = index * size.height / 2f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        series.forEach { (_, values, color) ->
            val fit = fittedSeries(values).first
            val combined = values + fit
            val min = combined.minOrNull() ?: 0.0
            val max = combined.maxOrNull() ?: 0.0
            val range = (max - min).takeIf { it > 0 } ?: 1.0
            val points = values.mapIndexed { index, value ->
                Offset(
                    x = if (values.size == 1) size.width / 2f else index * size.width / (values.size - 1),
                    y = size.height - 5f - ((value - min) / range).toFloat() * (size.height - 10f),
                )
            }
            points.zipWithNext().forEach { (start, end) -> drawLine(color, start, end, strokeWidth = 5f, cap = StrokeCap.Round) }
            points.forEach { drawCircle(color, radius = 5f, center = it) }
            val fitPoints = fit.mapIndexed { index, value ->
                Offset(
                    x = if (fit.size == 1) size.width / 2f else index * size.width / (fit.size - 1),
                    y = size.height - 5f - ((value - min) / range).toFloat() * (size.height - 10f),
                )
            }
            fitPoints.zipWithNext().forEach { (start, end) ->
                drawLine(color.copy(alpha = 0.55f), start, end, strokeWidth = 3f, cap = StrokeCap.Round)
            }
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

private fun formatSignedAxisValue(value: Double): String = String.format(Locale.US, "%+.1f", value)

private fun fittedSeries(values: List<Double>): Pair<List<Double>, Double> {
    if (values.size < 2) return values to 0.0
    val meanX = (values.size - 1) / 2.0
    val meanY = values.average()
    val denominator = values.indices.sumOf { index -> (index - meanX) * (index - meanX) }
    val slope = values.indices.sumOf { index -> (index - meanX) * (values[index] - meanY) } / denominator
    val intercept = meanY - slope * meanX
    val fit = values.indices.map { index -> intercept + slope * index }
    return fit to (fit.last() - fit.first())
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
    usage: Map<BodyArea, Double>,
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
        compareByDescending<BodyArea> { usage[it] ?: 0.0 }.thenBy { it.label },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training Areas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BodyMapPicker(
                    choices = choices,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
                )
                sortedAreas.forEach { area ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(22.dp).height(22.dp)
                                .background(Color(colors[area] ?: area.color), CircleShape)
                                .clickable { colorArea = area },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            buildString {
                                append(area.label)
                                val count = usage[area] ?: 0.0
                                if (count > 0) append(" · ${formatAreaUsage(count)}×")
                            },
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AreaStrengthChoice("½", choices[area] == Strength.HALF) {
                            choices[area] = if (choices[area] == Strength.HALF) null else Strength.HALF
                        }
                        Spacer(Modifier.width(3.dp))
                        AreaStrengthChoice("Full", choices[area] == Strength.FULL) {
                            choices[area] = if (choices[area] == Strength.FULL) null else Strength.FULL
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) { Text("Cancel") }
                    Button(
                        onClick = { onSave(choices.mapNotNull { (area, strength) -> strength?.let { AreaAssignment(area, it) } }) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) { Text("Save") }
                }
            }
        },
        confirmButton = {},
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
private fun AreaStrengthChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
    )
}

private fun formatAreaUsage(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

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
                Color(colors[area] ?: area.color).copy(alpha = if (strength == Strength.FULL) .82f else .48f)
            }
            fun oval(area: BodyArea, x: Float, y: Float, width: Float, height: Float) {
                selectedColor(area)?.let { color ->
                    drawOval(color, Offset(size.width * x, size.height * y), Size(size.width * width, size.height * height))
                }
            }
            fun polygon(area: BodyArea, vararg points: Pair<Float, Float>) {
                selectedColor(area)?.let { color ->
                    val scaled = points.map { Offset(size.width * it.first, size.height * it.second) }
                    fun midpoint(first: Offset, second: Offset) =
                        Offset((first.x + second.x) / 2f, (first.y + second.y) / 2f)
                    val path = Path().apply {
                        val start = midpoint(scaled.last(), scaled.first())
                        moveTo(start.x, start.y)
                        scaled.forEachIndexed { index, point ->
                            val end = midpoint(point, scaled[(index + 1) % scaled.size])
                            quadraticTo(point.x, point.y, end.x, end.y)
                        }
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
        0xFFE6194B, 0xFFB00020, 0xFF800000, 0xFFF58231, 0xFFE64A19, 0xFFFF7F00,
        0xFFFFD700, 0xFFBFEF45, 0xFF3CB44B, 0xFF008000, 0xFF008080, 0xFF00A6D6,
        0xFF42D4F4, 0xFF0072B2, 0xFF4363D8, 0xFF000075, 0xFF3F007D, 0xFF911EB4,
        0xFFF032E6, 0xFFE7298A, 0xFF9A6324, 0xFF6B3E26, 0xFF469990, 0xFF78909C,
        0xFF000000, 0xFF4D4D4D, 0xFFFFFFFF, 0xFFBC80BD, 0xFF1B9E77, 0xFFD95F02,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${area.label} Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                palette.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { value ->
                            Box(
                                Modifier.width(36.dp).height(36.dp)
                                    .background(Color(value), CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .35f), CircleShape)
                                    .clickable { onSelect(value) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected == value) {
                                    val color = Color(value)
                                    Text("✓", color = if (color.luminance() > .55f) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
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
