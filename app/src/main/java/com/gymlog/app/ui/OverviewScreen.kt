package com.gymlog.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.NumberPicker
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.ProgressionSettings
import com.gymlog.app.data.SetRecord
import com.gymlog.app.data.Strength
import com.gymlog.app.data.weightIncreases
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun OverviewScreen(
    machines: List<MachineSummary>,
    activePlanName: String,
    modifier: Modifier = Modifier,
    onMachine: (Long) -> Unit,
    onAdd: () -> Unit,
    onMenu: () -> Unit,
    onStats: () -> Unit,
    areaFrequency: Map<BodyArea, Double>,
    onQuickSet: (MachineSummary, List<Int>) -> Unit,
    onMetricChange: (MachineSummary, Double) -> Unit,
    progressionSettings: ProgressionSettings,
    onProgressionSettings: (ProgressionSettings) -> Unit,
) {
    var showProgressionSettings by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val lastExerciseToday = mostRecentlyTrainedToday(machines, today, zone)
    Box(modifier.fillMaxSize()) {
        if (machines.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(activePlanName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Build your routine", style = MaterialTheme.typography.headlineSmall)
                Text("Add your first exercise, choose its training areas, then log each set in a couple of taps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onAdd) { Text("Add first exercise") }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "training-duration") { TrainingDurationCard(machines, activePlanName, today, zone) }
                item(key = "progression-settings") {
                    ProgressionSettingsCard(progressionSettings, onEdit = { showProgressionSettings = true })
                }
                items(machines, key = { it.machine.id }) { summary ->
                    MachineCard(summary, lastExerciseToday, today, zone, areaFrequency, onMachine, onQuickSet, onMetricChange, progressionSettings)
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onMenu, modifier = Modifier.weight(1f)) { Text("Menu") }
            FilledTonalButton(onClick = onStats, modifier = Modifier.weight(1f)) { Text("Stats") }
            Button(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("+ Add") }
        }
    }
    if (showProgressionSettings) ProgressionSettingsDialog(
        current = progressionSettings,
        onDismiss = { showProgressionSettings = false },
        onSave = {
            onProgressionSettings(it)
            showProgressionSettings = false
        },
    )
}

@Composable
private fun TrainingDurationCard(machines: List<MachineSummary>, activePlanName: String, today: LocalDate, zone: ZoneId) {
    val todaySets = machines.flatMap { it.recentSets }.filter { it.performedDate(zone) == today }
    val first = todaySets.minOfOrNull { it.performedAt }
    val last = todaySets.maxOfOrNull { it.performedAt }
    val durationMinutes = if (first == null || last == null) 0L else ((last - first) / 60_000L).coerceAtLeast(0L)
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val completedExercises = machines.count { wasTrainedToday(it, today, zone) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("$activePlanName · Today's training duration", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (first == null) "Starts with your first entry"
                    else "${Instant.ofEpochMilli(first).atZone(zone).format(clock)} – ${Instant.ofEpochMilli(last!!).atZone(zone).format(clock)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "$completedExercises of ${machines.size} exercises done today",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (completedExercises == machines.size) Color(0xFF2E7D32) else MaterialTheme.colorScheme.tertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatHoursMinutes(durationMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "TRAINING TIME",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun ProgressionSettingsCard(settings: ProgressionSettings, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Progress warnings", style = MaterialTheme.typography.labelLarge)
                Text(
                    "${settingSummary(settings.staleWeeks, "weeks")} · ${settingSummary(settings.staleTrainings, "trainings")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEdit) { Text("Settings") }
        }
    }
}

private fun settingSummary(value: Int, unit: String): String = if (value <= 0) "$unit off" else "$value $unit"

@Composable
private fun ProgressionSettingsDialog(
    current: ProgressionSettings,
    onDismiss: () -> Unit,
    onSave: (ProgressionSettings) -> Unit,
) {
    var weeks by remember(current) { mutableStateOf(current.staleWeeks.toString()) }
    var trainings by remember(current) { mutableStateOf(current.staleTrainings.toString()) }
    val parsedWeeks = weeks.toIntOrNull()
    val parsedTrainings = trainings.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Progress warnings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Warn on weight exercises when either limit is reached without an increase. Set a limit to 0 to disable it.")
                OutlinedTextField(
                    value = weeks,
                    onValueChange = { weeks = it.filter(Char::isDigit) },
                    label = { Text("Weeks without increase") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = trainings,
                    onValueChange = { trainings = it.filter(Char::isDigit) },
                    label = { Text("Trainings without increase") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(ProgressionSettings(parsedWeeks ?: 0, parsedTrainings ?: 0)) },
                enabled = parsedWeeks != null && parsedTrainings != null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun progressionWarning(summary: MachineSummary, settings: ProgressionSettings): String? {
    if (summary.machine.trackingMode != "WEIGHT" || summary.recentSets.isEmpty()) return null
    val reference = summary.weightIncreases().maxOfOrNull { it.changedAt }
        ?: summary.weightChanges.minOfOrNull { it.changedAt }
        ?: return null
    val weeksWithoutIncrease = ((System.currentTimeMillis() - reference).coerceAtLeast(0L) / 604_800_000L).toInt()
    val zone = ZoneId.systemDefault()
    val trainingsWithoutIncrease = summary.recentSets
        .asSequence()
        .filter { it.performedAt > reference }
        .map { Instant.ofEpochMilli(it.performedAt).atZone(zone).toLocalDate() }
        .distinct()
        .count()
    val reasons = buildList {
        if (settings.staleWeeks > 0 && weeksWithoutIncrease >= settings.staleWeeks) add("$weeksWithoutIncrease weeks")
        if (settings.staleTrainings > 0 && trainingsWithoutIncrease >= settings.staleTrainings) add("$trainingsWithoutIncrease trainings")
    }
    return reasons.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "No weight increase for ")
}

@Composable
private fun MachineCard(
    summary: MachineSummary,
    lastExerciseToday: MachineSummary?,
    today: LocalDate,
    zone: ZoneId,
    areaFrequency: Map<BodyArea, Double>,
    onMachine: (Long) -> Unit,
    onQuickSet: (MachineSummary, List<Int>) -> Unit,
    onMetricChange: (MachineSummary, Double) -> Unit,
    progressionSettings: ProgressionSettings,
) {
    var quickReps by remember(summary.machine.id) { mutableStateOf(false) }
    var changeMetric by remember(summary.machine.id) { mutableStateOf(false) }
    val trainedToday = wasTrainedToday(summary, today, zone)
    val clash = if (!trainedToday && lastExerciseToday != null) strongestClash(summary, lastExerciseToday) else null
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onMachine(summary.machine.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.5.dp, sessionTrendColor(summary)),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (summary.areas.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    summary.areas.sortedByDescending { areaFrequency[it.area] ?: 0.0 }.forEach { assignment ->
                        Box(
                            Modifier
                                .width(if (assignment.strength == Strength.FULL) 38.dp else 19.dp)
                                .height(3.dp)
                                .background(Color(assignment.displayColor), RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(summary.machine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (summary.machine.titleNote.isNotBlank()) {
                        Text(" · ${summary.machine.titleNote}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!trainedToday) {
                    TodoBadge(clash)
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    formatMetric(summary.machine.currentWeight, summary.machine.trackingMode),
                    modifier = Modifier.clickable { changeMetric = true }.padding(horizontal = 6.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            progressionWarning(summary, progressionSettings)?.let { warning ->
                Text(warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sessionPatternText(summary),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (isDirectMetric(summary.machine.trackingMode)) "+ TRAINING" else "+ SET",
                    modifier = Modifier.clickable { quickReps = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (quickReps) {
        if (isDirectMetric(summary.machine.trackingMode)) {
            QuickTrainingDialog(
                initialMinutes = (summary.recentSets.firstOrNull()?.weight ?: 0.0).toInt(),
                onDismiss = { quickReps = false },
                onAdd = { onQuickSet(summary, listOf(it)); quickReps = false },
            )
        } else {
            QuickRepDialog(
                previousReps = previousReps(summary),
                onDismiss = { quickReps = false },
                onAdd = { onQuickSet(summary, it); quickReps = false },
            )
        }
    }
    if (changeMetric) MetricValueDialog(
        current = summary.machine.currentWeight,
        trackingMode = summary.machine.trackingMode,
        onDismiss = { changeMetric = false },
        onSave = {
            onMetricChange(summary, it)
            changeMetric = false
        },
    )
}

@Composable
private fun TodoBadge(clash: Double?) {
    val label = when (clash) {
        1.0 -> "TO DO · FULL CLASH"
        0.5 -> "TO DO · ½ CLASH"
        0.25 -> "TO DO · ¼ CLASH"
        else -> "TO DO"
    }
    val containerColor = when (clash) {
        1.0 -> MaterialTheme.colorScheme.errorContainer
        0.5 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (clash) {
        1.0 -> MaterialTheme.colorScheme.onErrorContainer
        0.5 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Text(
        label,
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        maxLines = 1,
    )
}

private fun mostRecentlyTrainedToday(
    machines: List<MachineSummary>,
    today: LocalDate,
    zone: ZoneId,
): MachineSummary? = machines
    .mapNotNull { summary ->
        summary.recentSets
            .filter { it.performedDate(zone) == today }
            .maxOfOrNull { it.performedAt }
            ?.let { latest -> summary to latest }
    }
    .maxByOrNull { (_, latest) -> latest }
    ?.first

private fun strongestClash(current: MachineSummary, previous: MachineSummary): Double? {
    val previousStrengths = previous.areas.associate { it.area to it.strength.value }
    return current.areas.mapNotNull { area ->
        previousStrengths[area.area]?.let { area.strength.value * it }
    }.maxOrNull()
}

private fun wasTrainedToday(summary: MachineSummary, today: LocalDate, zone: ZoneId): Boolean =
    summary.recentSets.any { it.performedDate(zone) == today }

private fun SetRecord.performedDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(performedAt).atZone(zone).toLocalDate()

private fun sessionPatternText(summary: MachineSummary): AnnotatedString {
    val batches = groupSetBatches(summary.recentSets)
    val patterns = batches.map { batch ->
        batch.sortedBy { it.performedAt }.joinToString(",") {
            if (isDirectMetric(summary.machine.trackingMode)) "${formatNumber(it.weight)}m" else it.reps.toString()
        }
    }
    fun trend(index: Int): Int {
        val newer = batches.getOrNull(index)?.firstOrNull() ?: return 0
        val older = batches.getOrNull(index + 1)?.firstOrNull() ?: return 0
        val newerMetric = if (summary.machine.trackingMode == "STRENGTH") newer.reps.toDouble() else newer.weight
        val olderMetric = if (summary.machine.trackingMode == "STRENGTH") older.reps.toDouble() else older.weight
        return newerMetric.compareTo(olderMetric)
    }
    fun AnnotatedString.Builder.appendTrendMarker(marker: String, trend: Int) {
        when {
            trend > 0 -> withStyle(SpanStyle(color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold)) { append(marker) }
            trend < 0 -> withStyle(SpanStyle(color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)) { append(marker) }
            else -> append(marker)
        }
    }
    return buildAnnotatedString {
        appendTrendMarker("Last", trend(0))
        append(": ")
        if (patterns.isEmpty()) append("-")
        patterns.forEachIndexed { index, pattern ->
            if (index > 0) {
                append(" ")
                appendTrendMarker("|", trend(index))
                append(" ")
            }
            append(pattern)
        }
    }
}

fun groupSetBatches(sets: List<SetRecord>): List<List<SetRecord>> {
    val batches = mutableListOf<MutableList<SetRecord>>()
    sets.sortedByDescending { it.performedAt }.forEach { set ->
        val currentBatch = batches.lastOrNull()
        if (currentBatch == null || currentBatch.last().performedAt - set.performedAt > 100L) {
            batches += mutableListOf(set)
        } else {
            currentBatch += set
        }
    }
    return batches
}

@Composable
fun QuickTrainingDialog(initialMinutes: Int, onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var value by remember(initialMinutes) { mutableStateOf(initialMinutes.coerceAtLeast(0).toString()) }
    val level = value.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log training") },
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { value = ((level ?: 0) - 1).coerceAtLeast(0).toString() }) { Text("−1") }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedButton(onClick = { value = ((level ?: 0) + 1).toString() }) { Text("+1") }
            }
        },
        confirmButton = { Button(onClick = { level?.let(onAdd) }, enabled = level != null && level > 0) { Text("Save training") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun sessionTrendColor(summary: MachineSummary): Color {
    val zone = ZoneId.systemDefault()
    val sessions = summary.recentSets.groupBy { it.performedDate(zone) }.values.toList()
    fun List<SetRecord>.trackedMetric(): Double =
        if (summary.machine.trackingMode == "STRENGTH") map { it.reps }.average() else map { it.weight }.average()
    val latest = sessions.getOrNull(0)?.trackedMetric() ?: return Color(0xFF444444)
    val before = sessions.getOrNull(1)?.trackedMetric() ?: return Color(0xFF444444)
    return when {
        latest > before -> Color(0xFF2E7D32)
        latest < before -> Color(0xFFC62828)
        else -> Color(0xFF555555)
    }
}

fun previousReps(summary: MachineSummary): List<Int> {
    val newest = summary.recentSets.firstOrNull() ?: return emptyList()
    val zone = ZoneId.systemDefault()
    val date = newest.performedDate(zone)
    return summary.recentSets
        .filter { it.performedDate(zone) == date }
        .sortedBy { it.performedAt }
        .map { it.reps }
        .takeLast(5)
}

@Composable
fun QuickRepDialog(previousReps: List<Int>, onDismiss: () -> Unit, onAdd: (List<Int>) -> Unit) {
    val initial = if (previousReps.isEmpty()) listOf(10, 10, 10, 0, 0) else previousReps.take(5) + List((5 - previousReps.size).coerceAtLeast(0)) { 0 }
    val reps = remember(previousReps) { mutableStateListOf<Int>().apply { addAll(initial.take(5)) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log up to 5 sets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    reps.indices.forEach { index ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
                            AndroidView(
                                modifier = Modifier.width(52.dp).height(132.dp),
                                factory = { context ->
                                    NumberPicker(context).apply {
                                        minValue = 0
                                        maxValue = 15
                                        wrapSelectorWheel = true
                                        value = reps[index]
                                        setOnValueChangedListener { _, _, newValue -> reps[index] = newValue }
                                    }
                                },
                                update = { if (it.value != reps[index]) it.value = reps[index] },
                            )
                        }
                    }
                }
                Text("Sets: ${reps.filter { it > 0 }.joinToString(",").ifEmpty { "none" }}", style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = { Button(onClick = { onAdd(reps.toList()) }, enabled = reps.any { it > 0 }) { Text("Save sets") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddMachineDialog(onDismiss: () -> Unit, onAdd: (String, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var trackingMode by remember { mutableStateOf("WEIGHT") }
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = value.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } },
                    label = { Text("Exercise name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = trackingMode == "WEIGHT", onClick = { trackingMode = "WEIGHT" }, label = { Text("kg") })
                    FilterChip(selected = trackingMode == "STRENGTH", onClick = { trackingMode = "STRENGTH" }, label = { Text("Level") })
                }
                if (trackingMode == "STRENGTH") {
                    Text(
                        "Trainings for Level exercises are recorded in minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                        label = { Text(currentMetricLabel(trackingMode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = if (trackingMode == "WEIGHT") KeyboardType.Decimal else KeyboardType.Number),
                        singleLine = true,
                    )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name.trim(), parsedWeight ?: 0.0, trackingMode) },
                enabled = name.isNotBlank() && parsedWeight != null && parsedWeight >= 0,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun formatWeight(weight: Double): String = if (weight % 1.0 == 0.0) "${weight.toInt()} kg" else "%.1f kg".format(weight)

fun formatHoursMinutes(totalMinutes: Long): String = "${totalMinutes / 60}:${(totalMinutes % 60).toString().padStart(2, '0')}"

fun isDirectMetric(trackingMode: String): Boolean = trackingMode == "STRENGTH" || trackingMode == "DURATION"

fun metricName(trackingMode: String): String = when (trackingMode) {
    "WEIGHT" -> "weight"
    "DURATION" -> "duration"
    else -> "level"
}

fun metricTitle(trackingMode: String): String = metricName(trackingMode).replaceFirstChar { it.uppercase() }

fun currentMetricLabel(trackingMode: String): String = when (trackingMode) {
    "WEIGHT" -> "Current weight (kg)"
    "DURATION" -> "Current duration (min)"
    else -> "Current level"
}

private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

fun formatMetric(value: Double, trackingMode: String): String = when (trackingMode) {
    "WEIGHT" -> formatWeight(value)
    "DURATION" -> "${formatNumber(value)} min"
    else -> "Level ${formatNumber(value)}"
}

fun formatTrainingEntry(value: Double, trackingMode: String): String =
    if (isDirectMetric(trackingMode)) "${formatNumber(value)} min" else formatMetric(value, trackingMode)
