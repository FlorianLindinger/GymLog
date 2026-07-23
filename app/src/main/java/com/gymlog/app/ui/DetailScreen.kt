package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
    onEditDetails: (ExerciseEditField) -> Unit,
    onDeleted: () -> Unit,
) {
    var showWeight by remember { mutableStateOf(false) }
    var showAreas by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deletingSets by remember { mutableStateOf<List<SetRecord>?>(null) }
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
        Card(modifier = Modifier.fillMaxWidth().clickable { onEditDetails(ExerciseEditField.OVERVIEW_NOTE) }) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (machine.notes.isBlank()) "Click to add an Overview Note" else machine.notes, color = if (machine.notes.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
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
                SessionMetricsChart(visibleSets, 0, machine.trackingMode)
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
                            Text("Delete", Modifier.clickable { deletingSets = listOf(set) }.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
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
    if (showAreas) AreaDialog(summary.areas, areaUsage, areaColors, onAreaColor, onDismiss = { showAreas = false }) {
        repository.saveAreas(machine.id, it)
        onChanged()
        showAreas = false
    }
    deletingSets?.let { sets ->
        AlertDialog(onDismissRequest = { deletingSets = null }, title = { Text("Delete ${sets.size} set${if (sets.size == 1) "" else "s"}?") }, text = { Text("This cannot be undone.") }, confirmButton = { Button(onClick = { sets.forEach { repository.deleteSet(it.id) }; deletingSets = null; onChanged() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deletingSets = null }) { Text("Cancel") } })
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
private fun SessionMetricsChart(sets: List<SetRecord>, trailingAverage: Int, trackingMode: String) {
    val allSessions = groupSetBatches(sets).asReversed()
    val sessions = allSessions.takeLast(24)
    if (sessions.isEmpty()) { Text("No training data in this window.", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val weight = movingAverage(sessions.map { it.first().weight }, trailingAverage)
    val volume = movingAverage(sessions.map { session -> session.sumOf { it.weight * it.reps } }, trailingAverage)
    val repsPerSet = movingAverage(sessions.map { session -> session.map { it.reps }.average() }, trailingAverage)
    val minutes = movingAverage(sessions.map { session -> session.sumOf { it.weight } }, trailingAverage)
    Text("Training Session Graph", style = MaterialTheme.typography.labelLarge)
    if (trackingMode == "DURATION") AxisMetricPlot("Minutes", minutes, MaterialTheme.colorScheme.primary)
    else DualAxisSessionPlot(repsPerSet, weight, volume, sessions, strength = trackingMode == "STRENGTH")
    if (allSessions.size > 24) Text("Showing the 24 most recent training sessions", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DualAxisSessionPlot(reps: List<Double>, weight: List<Double>, volume: List<Double>, sessions: List<List<SetRecord>>, strength: Boolean) {
    val repsFit = fittedSeries(reps).first
    val weightFit = fittedSeries(weight).first
    val volumeFit = fittedSeries(volume).first
    val repsMin = (reps + repsFit).minOrNull() ?: 0.0
    val repsMax = (reps + repsFit).maxOrNull() ?: 1.0
    val weightMin = (weight + weightFit).minOrNull() ?: 0.0
    val weightMax = (weight + weightFit).maxOrNull() ?: 1.0
    val volumeMin = (volume + volumeFit).minOrNull() ?: 0.0
    val volumeMax = (volume + volumeFit).maxOrNull()?.takeIf { it > volumeMin } ?: volumeMin + 1.0
    val repsColor = Color(0xFFFFA726)
    val weightColor = MaterialTheme.colorScheme.primary
    val volumeColor = Color(0xFF00BCD4)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f)
    Text(if (strength) "Level volume / training day" else "Volume / training day", color = volumeColor, style = MaterialTheme.typography.labelSmall)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AxisLabels(if (strength) "Level" else "Reps / set", repsMax, repsMin, repsColor, Modifier.width(52.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            val graphWidth = maxOf(maxWidth, (reps.size * 28).dp)
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(Modifier.width(graphWidth).height(164.dp).padding(vertical = 5.dp)) {
                    fun x(index: Int, count: Int) = if (count == 1) size.width / 2f else index * size.width / (count - 1)
                    fun y(value: Double, min: Double, max: Double): Float {
                        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
                        return size.height - 5f - ((value - min) / range).toFloat() * (size.height - 10f)
                    }
                    repeat(3) { index ->
                        val lineY = index * size.height / 2f
                        drawLine(grid, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 1.2f)
                    }
                    val volumePoints = volume.mapIndexed { index, value ->
                        Offset(x(index, volume.size), y(value, volumeMin, volumeMax))
                    }
                    volumePoints.zipWithNext().forEach { (a, b) -> drawLine(volumeColor, a, b, strokeWidth = 3.5f, cap = StrokeCap.Round) }
                    volumePoints.forEach { drawCircle(volumeColor, 3.8f, it) }
                    fun drawMetric(values: List<Double>, fit: List<Double>, min: Double, max: Double, color: Color) {
                        val points = values.mapIndexed { index, value -> Offset(x(index, values.size), y(value, min, max)) }
                        points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 4f, cap = StrokeCap.Round) }
                        points.forEach { drawCircle(color, 4.2f, it) }
                        val trend = fit.mapIndexed { index, value -> Offset(x(index, fit.size), y(value, min, max)) }
                        trend.zipWithNext().forEach { (a, b) -> drawLine(color.copy(alpha = .6f), a, b, strokeWidth = 2.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))) }
                    }
                    drawMetric(reps, repsFit, repsMin, repsMax, repsColor)
                    drawMetric(weight, weightFit, weightMin, weightMax, weightColor)
                    val volumeTrend = volumeFit.mapIndexed { index, value -> Offset(x(index, volumeFit.size), y(value, volumeMin, volumeMax)) }
                    volumeTrend.zipWithNext().forEach { (a, b) -> drawLine(Color(0xFF007C91), a, b, strokeWidth = 2.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))) }
                }
            }
        }
        AxisLabels(if (strength) "Minutes" else "Weight", weightMax, weightMin, weightColor, Modifier.width(52.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTimestamp(sessions.first().minOf { it.performedAt }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatTimestamp(sessions.last().maxOf { it.performedAt }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AxisLabels(label: String, max: Double, min: Double, color: Color, modifier: Modifier) {
    Column(modifier.height(164.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
        Text(label, modifier = Modifier.graphicsLayer { rotationZ = -90f }, style = MaterialTheme.typography.labelSmall, color = color)
        Text(formatAxisValue(max), style = MaterialTheme.typography.labelSmall)
        Text(formatAxisValue((max + min) / 2.0), style = MaterialTheme.typography.labelSmall)
        Text(formatAxisValue(min), style = MaterialTheme.typography.labelSmall)
    }
}
@Composable
private fun AxisMetricPlot(label: String, values: List<Double>, color: Color) {
    val fit = fittedSeries(values).first
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f)
    Text("$label: ${formatAxisValue(min)}–${formatAxisValue(max)}", color = color, style = MaterialTheme.typography.labelSmall)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(50.dp).height(112.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            Text(formatAxisValue(max), style = MaterialTheme.typography.labelSmall)
            Text(formatAxisValue((min + max) / 2), style = MaterialTheme.typography.labelSmall)
            Text(formatAxisValue(min), style = MaterialTheme.typography.labelSmall)
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val graphWidth = maxOf(maxWidth, (values.size * 28).dp)
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Canvas(Modifier.width(graphWidth).height(112.dp).padding(vertical = 5.dp)) {
                    val range = (max - min).takeIf { it > 0 } ?: 1.0
                    fun y(value: Double) = size.height - 4f - ((value - min) / range).toFloat() * (size.height - 8f)
                    repeat(3) { index ->
                        val lineY = index * size.height / 2f
                        drawLine(grid, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 1.2f)
                    }
                    val points = values.mapIndexed { index, value -> Offset(if (values.size == 1) size.width / 2f else index * size.width / (values.size - 1), y(value)) }
                    points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 4f, cap = StrokeCap.Round) }
                    points.forEach { drawCircle(color, 4.5f, it) }
                    val trendPoints = fit.mapIndexed { index, value -> Offset(if (fit.size == 1) size.width / 2f else index * size.width / (fit.size - 1), y(value)) }
                    trendPoints.zipWithNext().forEach { (a, b) -> drawLine(color.copy(alpha = .58f), a, b, strokeWidth = 2.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))) }
                }
            }
        }
    }
}

@Composable
private fun VolumePerTrainingDayPlot(values: List<Double>, sessions: List<List<SetRecord>>) {
    val fit = fittedSeries(values).first
    val max = (values + fit).maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    Text("Volume / training day", color = Color(0xFF00BCD4), style = MaterialTheme.typography.labelSmall)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val graphWidth = maxOf(maxWidth, (values.size * 28).dp)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(graphWidth).height(132.dp).padding(vertical = 5.dp)) {
                val barWidth = (size.width / values.size.coerceAtLeast(1) * .55f).coerceAtMost(22f)
                values.forEachIndexed { index, value ->
                    val x = if (values.size == 1) size.width / 2f else index * size.width / (values.size - 1)
                    val height = (value / max).toFloat() * (size.height - 8f)
                    drawRoundRect(Color(0xFF00BCD4), Offset(x - barWidth / 2f, size.height - 4f - height), Size(barWidth, height.coerceAtLeast(2f)), androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f))
                }
                val trendPoints = fit.mapIndexed { index, value ->
                    Offset(if (fit.size == 1) size.width / 2f else index * size.width / (fit.size - 1), size.height - 4f - (value / max).toFloat() * (size.height - 8f))
                }
                trendPoints.zipWithNext().forEach { (a, b) -> drawLine(Color(0xFF007C91), a, b, strokeWidth = 3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))) }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTimestamp(sessions.first().minOf { it.performedAt }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatTimestamp(sessions.last().maxOf { it.performedAt }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
                if (trackingMode == "WEIGHT") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilledTonalButton(onClick = { value = formatMetricDialogValue((parsed ?: current) + 3.0) }) { Text("+3 kg") }
                        androidx.compose.material3.FilledTonalButton(onClick = { value = formatMetricDialogValue((parsed ?: current) + 5.0) }) { Text("+5 kg") }
                    }
                }
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

private fun formatMetricDialogValue(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

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
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Training Areas", modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        onSave(choices.mapNotNull { (area, strength) ->
                            strength?.let { AreaAssignment(area, it) }
                        })
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) { Text("Save") }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BodyMapPicker(
                    choices = choices,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().aspectRatio(960f / 836f),
                )
                sortedAreas.forEach { area ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append(area.label)
                                val count = usage[area] ?: 0.0
                                if (count > 0) append(" · exists ${formatAreaUsage(count)}×")
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
private fun BodyMapPicker(choices: Map<BodyArea, Strength?>, colors: Map<BodyArea, Long>, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painter = painterResource(R.drawable.muscles_front_back), contentDescription = "Front and back muscle reference", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Text("Front (left) · Back (right)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
