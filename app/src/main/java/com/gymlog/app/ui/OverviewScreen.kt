package com.gymlog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
fun OverviewScreen(
    machines: List<MachineSummary>,
    activePlanName: String,
    activePlanNotes: String,
    activePlanNotesPinned: Boolean,
    modifier: Modifier = Modifier,
    onMachine: (Long) -> Unit,
    onAdd: () -> Unit,
    onMenu: () -> Unit,
    onStats: () -> Unit,
    areaFrequency: Map<BodyArea, Double>,
    onQuickSet: (MachineSummary, List<Int>) -> Unit,
    onMetricChange: (MachineSummary, Double) -> Unit,
    onMetricChangeForSession: (MachineSummary, Double) -> Unit,
    progressionSettings: ProgressionSettings,
    onReorder: (List<Long>) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val latestTrainingEntry = machines.flatMap { it.recentSets }.maxOfOrNull { it.performedAt }
    val trainingUnitCutoff = latestTrainingEntry
        ?.takeIf { now - it <= TRAINING_UNIT_MILLIS }
        ?.let { now - TRAINING_UNIT_MILLIS }
        ?: Long.MAX_VALUE
    val lastExerciseInUnit = mostRecentlyTrainedInUnit(machines, trainingUnitCutoff)
    val listState = rememberLazyListState()
    val orderedMachines = remember(machines) { machines.toMutableStateList() }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var footerVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var previous = 0
        snapshotFlow { listState.firstVisibleItemIndex * 100_000 + listState.firstVisibleItemScrollOffset }
            .collect { position ->
                if (position > previous + 4) footerVisible = false
                if (position < previous - 4 || position == 0) footerVisible = true
                previous = position
            }
    }
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
                state = listState,
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "training-duration") { TrainingDurationCard(machines, activePlanName, now, zone) }
                if (activePlanNotesPinned && activePlanNotes.isNotBlank()) {
                    item(key = "pinned-plan-notes") {
                        Card(Modifier.fillMaxWidth()) {
                            Text(activePlanNotes, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
                items(orderedMachines, key = { it.machine.id }) { summary ->
                    val id = summary.machine.id
                    MachineCard(
                        summary, lastExerciseInUnit, trainingUnitCutoff, areaFrequency, onMachine, onQuickSet,
                        onMetricChange, onMetricChangeForSession, progressionSettings,
                        modifier = Modifier.pointerInput(id, orderedMachines.map { it.machine.id }) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedId = id; dragOffset = 0f },
                                onDragCancel = { draggedId = null; dragOffset = 0f },
                                onDragEnd = {
                                    onReorder(orderedMachines.map { it.machine.id })
                                    draggedId = null
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val currentInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                                        ?: return@detectDragGesturesAfterLongPress
                                    val draggedCenter = currentInfo.offset + currentInfo.size / 2f + dragOffset
                                    val targetId = listState.layoutInfo.visibleItemsInfo
                                        .filter { it.key is Long }
                                        .minByOrNull { abs((it.offset + it.size / 2f) - draggedCenter) }
                                        ?.key as? Long
                                    val from = orderedMachines.indexOfFirst { it.machine.id == id }
                                    val to = orderedMachines.indexOfFirst { it.machine.id == targetId }
                                    if (from >= 0 && to >= 0 && from != to) {
                                        orderedMachines.add(to, orderedMachines.removeAt(from))
                                        dragOffset = 0f
                                    }
                                },
                            )
                        },
                        isDragging = draggedId == id,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = footerVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onMenu, modifier = Modifier.weight(1f)) { Text("Menu") }
                FilledTonalButton(onClick = onStats, modifier = Modifier.weight(1f)) { Text("Stats") }
                FilledTonalButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("+ Add") }
            }
        }
    }
}

@Composable
private fun TrainingDurationCard(machines: List<MachineSummary>, activePlanName: String, now: Long, zone: ZoneId) {
    val allSets = machines.flatMap { it.recentSets }
    val last = allSets.maxOfOrNull { it.performedAt }
    val sessionSets = last?.let { latest -> allSets.filter { it.performedAt >= latest - TRAINING_UNIT_MILLIS } }.orEmpty()
    val first = sessionSets.minOfOrNull { it.performedAt }
    val isActive = last != null && now - last < 10L * 60L * 1000L
    val durationMillis = if (first == null || last == null) 0L else ((if (isActive) now else last) - first).coerceAtLeast(0L)
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val dateClock = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    val completedExercises = machines.count { summary -> summary.recentSets.any { first != null && it.performedAt >= first } }

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
                Text(activePlanName, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (first == null) "Starts with your first entry"
                    else {
                        val startTime = Instant.ofEpochMilli(first).atZone(zone)
                        val endTime = Instant.ofEpochMilli(if (isActive) now else last!!).atZone(zone)
                        val formatter = if (startTime.toLocalDate() == endTime.toLocalDate()) clock else dateClock
                        "${startTime.format(formatter)} - ${if (isActive) "" else endTime.format(formatter)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "$completedExercises/${machines.size} exercises",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (completedExercises == machines.size) Color(0xFF2E7D32) else MaterialTheme.colorScheme.tertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatElapsed(durationMillis),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
fun ProgressionSettingsDialog(
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
    lastExerciseInUnit: MachineSummary?,
    trainingUnitCutoff: Long,
    areaFrequency: Map<BodyArea, Double>,
    onMachine: (Long) -> Unit,
    onQuickSet: (MachineSummary, List<Int>) -> Unit,
    onMetricChange: (MachineSummary, Double) -> Unit,
    onMetricChangeForSession: (MachineSummary, Double) -> Unit,
    progressionSettings: ProgressionSettings,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
) {
    var quickReps by remember(summary.machine.id) { mutableStateOf(false) }
    var changeMetric by remember(summary.machine.id) { mutableStateOf(false) }
    var pendingMetric by remember(summary.machine.id) { mutableStateOf<Double?>(null) }
    val trainedInUnit = summary.recentSets.any { it.performedAt >= trainingUnitCutoff }
    val currentUnitSets = currentTrainingUnitSets(summary)
    val isChangingTrainingUnit = currentUnitSets.isNotEmpty()
    val isLastDone = lastExerciseInUnit?.machine?.id == summary.machine.id
    val clash = if (!trainedInUnit && lastExerciseInUnit != null) strongestClash(summary, lastExerciseInUnit) else null
    val clashAreas = if (!trainedInUnit && lastExerciseInUnit != null) clashingAreas(summary, lastExerciseInUnit) else emptySet()
    val outlineColor = when {
        isLastDone -> Color(0xFF1976D2)
        trainedInUnit -> sessionTrendColor(summary)
        clash != null && clash >= 1.0 -> Color(0xFFE53935)
        clash != null -> Color(0xFFFBC02D)
        else -> Color(0xFF43A047)
    }
    Card(
        modifier = modifier.fillMaxWidth().clickable { onMachine(summary.machine.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 10.dp else 1.dp),
        border = BorderStroke(if (isLastDone) 3.dp else if (trainedInUnit) 1.5.dp else 2.5.dp, outlineColor),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (summary.areas.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    summary.areas.sortedByDescending { areaFrequency[it.area] ?: 0.0 }.forEach { assignment ->
                        Box(
                            Modifier
                                .width(if (assignment.strength == Strength.FULL) 38.dp else 19.dp)
                                .height(if (assignment.area in clashAreas) 7.dp else 3.dp)
                                .background(
                                    Color(assignment.displayColor).copy(alpha = if (clashAreas.isEmpty() || assignment.area in clashAreas) 1f else .25f),
                                    RoundedCornerShape(8.dp),
                                ),
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
            }
            progressionWarning(summary, progressionSettings)?.let { warning ->
                Text(warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        isChangingTrainingUnit -> "Change"
                        isDirectMetric(summary.machine.trackingMode) -> "+ Training"
                        else -> "+ Sets"
                    },
                    modifier = Modifier.clickable { quickReps = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    sessionPatternText(summary),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatMetric(summary.machine.currentWeight, summary.machine.trackingMode),
                    modifier = Modifier.clickable { changeMetric = true }.padding(horizontal = 6.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (quickReps) {
        if (isDirectMetric(summary.machine.trackingMode)) {
            QuickTrainingDialog(
                initialMinutes = (currentUnitSets.firstOrNull() ?: summary.recentSets.firstOrNull())?.weight?.toInt() ?: 0,
                isChange = isChangingTrainingUnit,
                onDismiss = { quickReps = false },
                onAdd = { onQuickSet(summary, listOf(it)); quickReps = false },
            )
        } else {
            QuickRepDialog(
                initialReps = initialWheelReps(summary, currentUnitSets),
                lastReps = previousTrainingUnitReps(summary, currentUnitSets),
                isChange = isChangingTrainingUnit,
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
            changeMetric = false
            if (currentUnitSets.isEmpty()) onMetricChange(summary, it) else pendingMetric = it
        },
    )
    pendingMetric?.let { value ->
        MetricChangeScopeDialog(
            onDismiss = { pendingMetric = null },
            onNextSession = { onMetricChange(summary, value); pendingMetric = null },
            onCurrentAndNext = { onMetricChangeForSession(summary, value); pendingMetric = null },
        )
    }
}

@Composable
fun MetricChangeScopeDialog(
    onDismiss: () -> Unit,
    onNextSession: () -> Unit,
    onCurrentAndNext: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply metric change") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This exercise already has entries in the current training session.")
                Button(onClick = onNextSession, modifier = Modifier.fillMaxWidth()) { Text("Change for next session") }
                FilledTonalButton(onClick = onCurrentAndNext, modifier = Modifier.fillMaxWidth()) { Text("Change this session and next") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun mostRecentlyTrainedInUnit(
    machines: List<MachineSummary>,
    cutoff: Long,
): MachineSummary? = machines
    .mapNotNull { summary ->
        summary.recentSets
            .filter { it.performedAt >= cutoff }
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

private fun clashingAreas(current: MachineSummary, previous: MachineSummary): Set<BodyArea> {
    val previousAreas = previous.areas.mapTo(mutableSetOf()) { it.area }
    return current.areas.mapNotNullTo(mutableSetOf()) { it.area.takeIf(previousAreas::contains) }
}

private fun SetRecord.performedDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(performedAt).atZone(zone).toLocalDate()

private fun sessionPatternText(summary: MachineSummary): AnnotatedString {
    val batches = groupSetBatches(summary.recentSets)
    val patterns = batches.map { batch ->
        batch.sortedBy { it.performedAt }.joinToString(",") {
            if (isDirectMetric(summary.machine.trackingMode)) "${formatNumber(it.weight)} min" else it.reps.toString()
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
fun QuickTrainingDialog(initialMinutes: Int, isChange: Boolean = false, onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var value by remember(initialMinutes) { mutableStateOf(initialMinutes.coerceAtLeast(0).toString()) }
    val level = value.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isChange) "Change training" else "Log training") },
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

private const val TRAINING_UNIT_MILLIS = 6L * 60L * 60L * 1000L

fun currentTrainingUnitSets(summary: MachineSummary, now: Long = System.currentTimeMillis()): List<SetRecord> =
    summary.recentSets.filter { it.performedAt >= now - TRAINING_UNIT_MILLIS }.sortedBy { it.performedAt }

fun previousTrainingUnitReps(summary: MachineSummary, current: List<SetRecord> = currentTrainingUnitSets(summary)): List<Int> {
    val currentIds = current.mapTo(mutableSetOf()) { it.id }
    val previousNewest = summary.recentSets.firstOrNull { it.id !in currentIds } ?: return emptyList()
    return summary.recentSets
        .filter { it.id !in currentIds && it.performedAt >= previousNewest.performedAt - TRAINING_UNIT_MILLIS }
        .sortedBy { it.performedAt }
        .map { it.reps }
        .takeLast(5)
}

fun initialWheelReps(summary: MachineSummary, current: List<SetRecord> = currentTrainingUnitSets(summary)): List<Int> {
    val base = when {
        current.isNotEmpty() -> summary.setWheelValues ?: current.map { it.reps }
        summary.setWheelValues != null -> summary.setWheelValues
        else -> previousTrainingUnitReps(summary, current).ifEmpty { listOf(10, 10, 10) }
    }
    return (base.take(5) + List(5) { 0 }).take(5)
}

@Composable
fun QuickRepDialog(
    initialReps: List<Int>,
    lastReps: List<Int>,
    isChange: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: (List<Int>) -> Unit,
) {
    val reps = remember(initialReps) { mutableStateListOf<Int>().apply { addAll((initialReps + List(5) { 0 }).take(5)) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isChange) "Change sets" else "Log up to 5 sets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    reps.indices.forEach { index ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AndroidView(
                                modifier = Modifier.width(52.dp).height(132.dp),
                                factory = { context ->
                                    NumberPicker(context).apply {
                                        minValue = 0
                                        maxValue = 15
                                        displayedValues = arrayOf("None") + (1..15).map(Int::toString)
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
                Text("Last: ${lastReps.joinToString(",").ifEmpty { "None" }}", style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = { Button(onClick = { onAdd(reps.toList()) }, enabled = isChange || reps.any { it > 0 }) { Text("Save sets") } },
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

fun formatWeight(weight: Double): String = if (weight % 1.0 == 0.0) "${weight.toInt()} kg" else String.format(Locale.US, "%.1f kg", weight)

fun formatHoursMinutes(totalMinutes: Long): String = "${totalMinutes / 60}:${(totalMinutes % 60).toString().padStart(2, '0')}"

private fun formatElapsed(totalMillis: Long): String {
    val totalSeconds = totalMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    return "$hours:${minutes.toString().padStart(2, '0')}"
}

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

private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

fun formatMetric(value: Double, trackingMode: String): String = when (trackingMode) {
    "WEIGHT" -> formatWeight(value)
    "DURATION" -> "${formatNumber(value)} min"
    else -> "Level ${formatNumber(value)}"
}

fun formatTrainingEntry(value: Double, trackingMode: String): String =
    if (isDirectMetric(trackingMode)) "${formatNumber(value)} min" else formatMetric(value, trackingMode)
