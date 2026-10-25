package com.gymlog.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymlog.app.R
import com.gymlog.app.data.ChartRange
import com.gymlog.app.data.usesResistanceLevel
import com.gymlog.app.data.recordedMetricHistory
import com.gymlog.app.data.recordedMetricChangeCount
import com.gymlog.app.data.recordedMetric
import com.gymlog.app.data.TrackingMode
import com.gymlog.app.data.AreaAssignment
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.GymRepository
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.SetRecord
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
    isHidden: Boolean,
    onDeleteSets: (List<SetRecord>) -> Unit,
    onDeleteExercise: () -> Unit,
    onDeleted: () -> Unit,
) {
    var showWeight by remember { mutableStateOf(false) }
    var showAreas by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deletingSets by remember { mutableStateOf<List<SetRecord>?>(null) }
    var editingSet by remember { mutableStateOf<SetRecord?>(null) }
    var expandedBatch by remember { mutableStateOf<Long?>(null) }
    var quickEntry by remember { mutableStateOf(false) }
    val machine = summary.machine
    val context = LocalContext.current
    val chartPreferences = remember { context.getSharedPreferences("gym_chart_ranges", 0) }
    var showCustomChartRange by remember { mutableStateOf(false) }
    var customChartStart by remember(machine.id) { mutableStateOf(chartPreferences.getLong("${machine.id}_start", 0L).takeIf { it > 0L }) }
    var customChartEnd by remember(machine.id) { mutableStateOf(chartPreferences.getLong("${machine.id}_end", 0L).takeIf { it > 0L }) }
    val currentUnitSets = currentTrainingUnitSets(summary)
    val entryBatches = remember(summary.recentSets) { groupSetBatches(summary.recentSets) }
    val first = summary.recordedMetricHistory().firstOrNull()?.weight ?: machine.currentWeight
    val trainedDays = summary.recentSets.map { trainingDate(it.performedAt) }.distinct().size

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (machine.notes.isNotBlank()) {
            ExerciseNoteCard(machine.notes) { onEditDetails(ExerciseEditField.OVERVIEW_NOTE) }
        }
        Button(onClick = { quickEntry = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    machine.trackingMode.usesResistanceLevel() -> if (currentUnitSets.isEmpty()) "Add training entry" else "Change training entry"
                    machine.trackingMode == TrackingMode.DURATION -> if (currentUnitSets.isEmpty()) "Add training" else "Change training"
                    else -> if (currentUnitSets.isEmpty()) "Add sets" else "Change sets"
                },
            )
        }
        Card(modifier = Modifier.fillMaxWidth().clickable { showWeight = true }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatMetric(machine.currentWeight, machine.trackingMode),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = sessionTrendColor(summary),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        weightChangeDescription(summary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                    Text("Started ${formatMetric(first, machine.trackingMode)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChartRangeControls(
                    selectedWindow = machine.chartWindow,
                    showTrend = machine.showTrend,
                    onWindowSelected = { range -> repository.setChartWindow(machine.id, range.storageValue); onChanged() },
                    onCustomSelected = { showCustomChartRange = true },
                    onTrendSelected = { repository.setShowTrend(machine.id, !machine.showTrend); onChanged() },
                )
                val chartWindow = ChartWindow(
                    durationMillis = if (machine.chartWindow == ChartRange.CUSTOM) {
                        customChartStart?.let { start -> customChartEnd?.let { end -> (end - start).coerceAtLeast(1L) } }
                    } else chartWindowDuration(machine.chartWindow),
                    endMillis = if (machine.chartWindow == ChartRange.CUSTOM) customChartEnd else System.currentTimeMillis(),
                    startMillis = if (machine.chartWindow == ChartRange.CUSTOM) customChartStart else null,
                    key = "${machine.chartWindow.storageValue}:$customChartStart:$customChartEnd",
                )
                SessionMetricsChart(summary.recentSets, machine.trackingMode, machine.showTrend, chartWindow)
            }
        }

        if (machine.notes.isBlank()) {
            ExerciseNoteCard("") { onEditDetails(ExerciseEditField.OVERVIEW_NOTE) }
        }
        if (summary.areas.isEmpty()) {
            OutlinedButton(onClick = { showAreas = true }, modifier = Modifier.fillMaxWidth()) { Text("Choose Body Regions & Intensity") }
        } else {
            Card(modifier = Modifier.fillMaxWidth().clickable { showAreas = true }) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Body Regions & Intensity · Edit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    summary.areas.sortedByDescending { areaUsage[it.area] ?: 0.0 }.forEach { assignment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width((34 * assignment.strength.value).dp).height(8.dp).background(Color(assignment.displayColor), RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(6.dp)); Text(assignment.area.label, Modifier.weight(1f)); Text(assignment.strength.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Recent Entries", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${summary.recordedMetricChangeCount()} ${metricName(machine.trackingMode)} changes · $trainedDays day${if (trainedDays == 1) "" else "s"} trained",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (summary.recentSets.isEmpty()) Text("No entries recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                entryBatches.take(30).forEachIndexed { index, batch ->
                    val ordered = batch.sortedBy { it.performedAt }
                    val batchKey = ordered.first().performedAt
                    if (index > 0) HorizontalDivider()
                    val previous = entryBatches.getOrNull(index + 1)
                    val metric = if (machine.trackingMode.usesResistanceLevel()) ordered.last().reps.toDouble() else ordered.last().weight
                    val previousMetric = previous?.maxByOrNull { it.performedAt }?.let { summary.recordedMetric(it) }
                    val volume = ordered.sumOf { it.reps * it.weight }
                    val previousVolume = previous?.sumOf { it.reps * it.weight }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTimestamp(batchKey), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (expandedBatch == batchKey) "Close" else "Edit",
                            Modifier.clickable { expandedBatch = if (expandedBatch == batchKey) null else batchKey }.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(formatRecentEntry(ordered, machine.trackingMode),
                        style = MaterialTheme.typography.bodySmall, color = historyComparisonColor(metric, previousMetric))
                    if (machine.trackingMode != TrackingMode.DURATION) Text(
                        "Volume: ${formatAxisValue(volume)} a.u.", style = MaterialTheme.typography.labelSmall,
                        color = historyComparisonColor(volume, previousVolume))
                    if (expandedBatch == batchKey) ordered.forEachIndexed { setIndex, set ->
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 1.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (isDirectMetric(machine.trackingMode)) "Entry ${setIndex + 1}: ${formatTrainingEntry(set.weight, machine.trackingMode, set.reps)}"
                                else "Set ${setIndex + 1}: ${set.reps} reps",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                            Text("Edit", Modifier.clickable { editingSet = set }.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { deletingSets = batch }, modifier = Modifier.align(Alignment.End), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                            Text("Delete entry", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { repository.setMachineHidden(machine.id, !isHidden); onChanged() }, modifier = Modifier.weight(1f)) {
                Text(if (isHidden) "Unhide exercise" else "Hide exercise")
            }
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) { Text("Delete exercise", color = MaterialTheme.colorScheme.error) }
        }
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
    if (quickEntry) {
        if (isDirectMetric(machine.trackingMode)) {
            QuickTrainingDialog(
                initialMinutes = (currentUnitSets.firstOrNull() ?: summary.recentSets.firstOrNull())?.weight?.toInt() ?: 10,
                isChange = currentUnitSets.isNotEmpty(),
                onDismiss = { quickEntry = false },
                onAdd = {
                    repository.addTrainingMinutes(machine.id, it)
                    onChanged()
                    quickEntry = false
                },
                onDelete = currentUnitSets.takeIf { it.isNotEmpty() }?.let { sets ->
                    { onDeleteSets(sets); quickEntry = false }
                },
            )
        } else {
            QuickRepDialog(
                initialReps = initialWheelReps(summary, currentUnitSets),
                lastReps = previousTrainingUnitReps(summary, currentUnitSets),
                isChange = currentUnitSets.isNotEmpty(),
                onDismiss = { quickEntry = false },
                onAdd = {
                    repository.addSets(machine.id, it, machine.currentWeight)
                    onChanged()
                    quickEntry = false
                },
                onDelete = currentUnitSets.takeIf { it.isNotEmpty() }?.let { sets ->
                    { onDeleteSets(sets); quickEntry = false }
                },
            )
        }
    }
    if (showCustomChartRange) CustomGymRangeDialog(
        maxTimestamp = System.currentTimeMillis(),
        initialStart = customChartStart ?: chartCutoff(ChartRange.ONE_MONTH) ?: System.currentTimeMillis(),
        initialEnd = customChartEnd ?: System.currentTimeMillis(),
        onDismiss = { showCustomChartRange = false },
        onSave = { start, end ->
            customChartStart = start; customChartEnd = end
            chartPreferences.edit().putLong("${machine.id}_start", start).putLong("${machine.id}_end", end).apply()
            repository.setChartWindow(machine.id, ChartRange.CUSTOM.storageValue); onChanged(); showCustomChartRange = false
        },
    )
    if (showAreas) MuscleProfileDialog(machine.name, summary.areas, areaColors, onAreaColor, onDismiss = { showAreas = false }) {
        repository.saveAreas(machine.id, it)
        onChanged()
        showAreas = false
    }
    deletingSets?.let { sets ->
        AlertDialog(onDismissRequest = { deletingSets = null }, title = { Text("Delete ${sets.size} set${if (sets.size == 1) "" else "s"}?") }, text = { Text("You can undo this from the message shown afterwards.") }, confirmButton = { Button(onClick = { onDeleteSets(sets); deletingSets = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deletingSets = null }) { Text("Cancel") } })
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${machine.name}?") },
        text = { Text("Its sets, notes, body areas, and weight history will be removed. You can undo it immediately afterwards.") },
        confirmButton = { Button(onClick = { onDeleteExercise(); onDeleted() }) { Text("Delete") } },
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
private fun ExerciseNoteCard(note: String, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("Note", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                note.ifBlank { "Click to add a note" },
                color = if (note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CompactChoice(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun SessionMetricsChart(
    sets: List<SetRecord>,
    trackingMode: TrackingMode,
    showTrend: Boolean,
    window: ChartWindow,
) {
    val orderedSets = sets.sortedBy { it.performedAt }
    if (orderedSets.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text("No training data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (trackingMode == TrackingMode.DURATION) {
        AxisMetricPlot(
            orderedSets.map { TimeValue(it.performedAt, it.weight) },
            window,
            MaterialTheme.colorScheme.primary,
            showTrend,
        )
    } else {
        DualAxisSessionPlot(orderedSets, groupSetBatches(sets).asReversed(), window, trackingMode = trackingMode, showTrend = showTrend)
    }
}

@Composable
private fun DualAxisSessionPlot(
    sets: List<SetRecord>,
    sessions: List<List<SetRecord>>,
    window: ChartWindow,
    trackingMode: TrackingMode,
    showTrend: Boolean,
) {
    val repsColor = Color(0xFFFFA726)
    val weightColor = MaterialTheme.colorScheme.primary
    val volumeColor = Color(0xFF00BCD4)
    val reps = sessions.map { session ->
        TimeValue(session.maxOf { it.performedAt }, session.map { it.reps }.average())
    }
    val weight = sets.map { TimeValue(it.performedAt, it.weight) }
    val volume = sessions.map { session ->
        TimeValue(session.maxOf { it.performedAt }, session.sumOf { it.weight * it.reps })
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ChartLegendEntry(if (trackingMode == TrackingMode.WATTS) "Watts" else if (trackingMode == TrackingMode.STRENGTH) "Level" else "Reps/set",
            reps, window, showTrend, repsColor, TextAlign.Start, Modifier.weight(1f))
        ChartLegendEntry("Volume/training [a.u.]", volume, window, showTrend, volumeColor, TextAlign.Center, Modifier.weight(1.5f))
        ChartLegendEntry(if (trackingMode.usesResistanceLevel()) "Minutes" else "Weight [kg]",
            weight, window, showTrend, weightColor, TextAlign.End, Modifier.weight(1f))
    }
    val baseSeries = listOf(
        TimeSeries(reps, repsColor, PlotAxis.LEFT),
        TimeSeries(weight, weightColor, PlotAxis.RIGHT),
        TimeSeries(volume, volumeColor, PlotAxis.INDEPENDENT, pointRadius = 3.8f),
    )
    TimeWindowChart(
        series = baseSeries + if (showTrend) {
            listOf(
                trendTimeSeries(reps, repsColor, PlotAxis.LEFT, window),
                trendTimeSeries(weight, weightColor, PlotAxis.RIGHT, window),
                trendTimeSeries(volume, Color(0xFF007C91), PlotAxis.INDEPENDENT, window),
            )
        } else emptyList(),
        window = window,
        leftColor = repsColor,
        rightColor = weightColor,
    )
}
@Composable
private fun AxisMetricPlot(
    points: List<TimeValue>,
    window: ChartWindow,
    color: Color,
    showTrend: Boolean,
) {
    ChartLegendEntry("Minutes", points, window, showTrend, color, TextAlign.Start, Modifier.fillMaxWidth())
    TimeWindowChart(
        series = listOf(TimeSeries(points, color)) +
            if (showTrend) listOf(trendTimeSeries(points, color, PlotAxis.LEFT, window)) else emptyList(),
        window = window,
        leftColor = color,
    )
}

private fun formatAxisValue(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString()
else String.format(Locale.US, "%.1f", value)

private fun weightChangeDescription(summary: MachineSummary): String {
    val latest = summary.recentSets.maxByOrNull { it.performedAt } ?: return "No recorded entry yet"
    val delta = summary.machine.currentWeight - summary.recordedMetric(latest)
    if (delta == 0.0) return "Matches last recorded entry"
    return "Next: ${if (delta > 0) "+" else ""}${formatAxisValue(delta)} ${when (summary.machine.trackingMode) {
        TrackingMode.WEIGHT -> "kg"
        TrackingMode.WATTS -> "W"
        TrackingMode.DURATION -> "min"
        else -> "levels"
    }} vs last entry"
}

@Composable
private fun ChartLegendEntry(label: String, points: List<TimeValue>, window: ChartWindow, showTrend: Boolean,
    color: Color, alignment: TextAlign, modifier: Modifier) {
    Column(modifier) {
        Text(label, Modifier.fillMaxWidth(), color = color, textAlign = alignment, style = MaterialTheme.typography.labelSmall)
        if (showTrend) Text(formatMonthlyTrend(points, window), Modifier.fillMaxWidth(), color = color,
            textAlign = alignment, style = MaterialTheme.typography.labelSmall)
    }
}

internal fun formatMonthlyTrend(points: List<TimeValue>, window: ChartWindow): String {
    val fit = trendTimeSeries(points, Color.Black, PlotAxis.LEFT, window).points
    if (fit.size < 2) return ""
    val elapsed = fit.last().timestamp - fit.first().timestamp
    if (elapsed <= 0) return ""
    val monthly = (fit.last().value - fit.first().value) / elapsed * 86_400_000.0 * 30.44
    return String.format(Locale.US, "%+.2g/mon.", if (kotlin.math.abs(monthly) < 0.0000001) 0.0 else monthly)
}

private fun historyComparisonColor(current: Double, previous: Double?): Color = when {
    previous == null || current == previous -> Color(0xFF9E9E9E)
    current > previous -> Color(0xFF66BB6A)
    else -> Color(0xFFEF5350)
}

private fun chartCutoff(window: ChartRange): Long? =
    chartWindowDuration(window)?.let { duration -> Instant.now().toEpochMilli() - duration }

private enum class GymCustomRangeMode { INTERVAL, DURATION }

@Composable
private fun CustomGymRangeDialog(
    maxTimestamp: Long,
    initialStart: Long,
    initialEnd: Long,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd-MM-yyyy") }
    var mode by remember { mutableStateOf(GymCustomRangeMode.INTERVAL) }
    var startText by remember {
        mutableStateOf(Instant.ofEpochMilli(initialStart).atZone(zone).toLocalDate().format(dateFormatter))
    }
    var endText by remember {
        mutableStateOf(Instant.ofEpochMilli(initialEnd).atZone(zone).toLocalDate().format(dateFormatter))
    }
    var amountText by remember { mutableStateOf("2.5") }
    var durationUnit by remember { mutableStateOf("Weeks") }
    val intervalEnd = runCatching {
        java.time.LocalDate.parse(endText, dateFormatter).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
    }.getOrNull()?.coerceAtMost(maxTimestamp)
    val end = if (mode == GymCustomRangeMode.INTERVAL) intervalEnd else maxTimestamp
    val startFromDate = runCatching {
        java.time.LocalDate.parse(startText, dateFormatter).atStartOfDay(zone).toInstant().toEpochMilli()
    }.getOrNull()
    val durationMillis = when (durationUnit) {
        "Days" -> 86_400_000L
        "Months" -> (30.44 * 86_400_000L).toLong()
        "Years" -> 365L * 86_400_000L
        else -> 7L * 86_400_000L
    }
    val startFromDuration = end?.let { value -> amountText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let { value - (it * durationMillis).toLong() } }
    val start = if (mode == GymCustomRangeMode.INTERVAL) startFromDate else startFromDuration
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom chart range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(GymCustomRangeMode.INTERVAL to "Interval", GymCustomRangeMode.DURATION to "Duration").forEach { (choice, label) ->
                        CompactChoice(mode == choice, { mode = choice }, label, Modifier.weight(1f))
                    }
                }
                if (mode == GymCustomRangeMode.INTERVAL) {
                    OutlinedTextField(startText, { startText = it }, label = { Text("Start (DD-MM-YYYY)") }, singleLine = true)
                    OutlinedTextField(endText, { endText = it }, label = { Text("End (DD-MM-YYYY)") }, singleLine = true)
                } else {
                    OutlinedTextField(amountText, { amountText = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text("Duration") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Days", "Weeks", "Months", "Years").forEach { unit -> CompactChoice(durationUnit == unit, { durationUnit = unit }, unit, Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (start != null && end != null) onSave(start, end) }, enabled = start != null && end != null && start < end) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
@Composable
fun MetricValueDialog(
    current: Double,
    trackingMode: TrackingMode,
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
                OutlinedTextField(value, { value = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text(when (trackingMode) { TrackingMode.WATTS -> "New Power (W)"; TrackingMode.WEIGHT -> "New Weight (kg)"; TrackingMode.DURATION -> "New Duration (min)"; else -> "New Level" }) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (trackingMode == TrackingMode.WEIGHT) KeyboardType.Decimal else KeyboardType.Number))
                val steps = when (trackingMode) {
                    TrackingMode.WEIGHT -> listOf(3, 5)
                    TrackingMode.WATTS -> listOf(10)
                    TrackingMode.DURATION -> listOf(1, 2, 3)
                    TrackingMode.STRENGTH -> listOf(1)
                }
                MetricStepButtons(steps, when (trackingMode) {
                    TrackingMode.WEIGHT -> "kg"; TrackingMode.WATTS -> "W"; TrackingMode.DURATION -> "min"; else -> ""
                }) { delta -> value = formatMetricDialogValue(((parsed ?: current) + delta).coerceAtLeast(0.0)) }
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
internal fun BodyMapPicker(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painter = painterResource(R.drawable.muscles_front_back), contentDescription = "Front and back muscle reference", modifier = Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
        Text("Front (left) · Back (right)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
internal fun ColorPaletteDialog(area: BodyArea, selected: Long, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
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
    val days = kotlin.math.ceil(duration.toMillis() / 86_400_000.0).toLong()
    return when {
        days >= 365 -> "${days / 365}y ${days % 365 / 30}mo"
        days >= 30 -> "${days / 30}mo ${days % 30}d"
        days > 0 -> "${days}d"
        duration.toHours() > 0 -> "${duration.toHours()}h"
        else -> "${duration.toMinutes().coerceAtLeast(0)} min"
    }
}

private fun formatRecentEntry(sets: List<SetRecord>, trackingMode: TrackingMode): String {
    val first = sets.first()
    return if (isDirectMetric(trackingMode)) formatTrainingEntry(first.weight, trackingMode, first.reps)
        else "${formatMetric(first.weight, trackingMode)} | ${sets.joinToString(", ") { it.reps.toString() }} reps"
}

@Composable
internal fun MetricStepButtons(steps: List<Int>, unit: String, onStep: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(-1, 1).forEach { sign ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                steps.forEach { step ->
                    androidx.compose.material3.FilledTonalButton(onClick = { onStep(sign * step) }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                        Text("${if (sign > 0) "+" else "-"}$step $unit")
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String = DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH)
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
