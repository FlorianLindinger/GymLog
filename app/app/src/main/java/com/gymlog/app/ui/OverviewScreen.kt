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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.gymlog.app.data.usesResistanceLevel
import com.gymlog.app.data.pendingMetricDirection
import com.gymlog.app.data.recordedMetric
import com.gymlog.app.data.TrackingMode
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.AreaAssignment
import com.gymlog.app.data.ExerciseCatalog
import com.gymlog.app.data.ExerciseTemplate
import com.gymlog.app.data.exerciseOverlap
import com.gymlog.app.data.exerciseTrainingCount
import com.gymlog.app.data.rankOverviewMachines
import com.gymlog.app.data.recentTrainingExercises
import com.gymlog.app.data.ProgressionSettings
import com.gymlog.app.data.SetRecord
import com.gymlog.app.data.weightIncreases
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

@Composable
fun OverviewScreen(
    machines: List<MachineSummary>,
    hiddenMachines: List<MachineSummary>,
    activePlanName: String,
    activePlanNotes: String,
    activePlanNotesPinned: Boolean,
    modifier: Modifier = Modifier,
    onMachine: (Long) -> Unit,
    onAdd: () -> Unit,
    onMenu: () -> Unit,
    onStats: () -> Unit,
    onOrder: () -> Unit,
    onBody: () -> Unit,
    listState: LazyListState,
    showHidden: Boolean,
    onShowHiddenChanged: (Boolean) -> Unit,
    onTrainingHeader: () -> Unit,
    onEditPlanNotes: () -> Unit,
    areaFrequency: Map<BodyArea, Double>,
    onEditAreas: (MachineSummary) -> Unit,
    onQuickSet: (MachineSummary, List<Int>) -> Unit,
    onDeleteCurrentExerciseSets: (MachineSummary) -> Unit,
    onMetricChange: (MachineSummary, Double) -> Unit,
    onMetricChangeForSession: (MachineSummary, Double) -> Unit,
    progressionSettings: ProgressionSettings,
) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val latestTrainingEntry = (machines + hiddenMachines).flatMap { it.recentSets }.maxOfOrNull { it.performedAt }
    val todayTrainingDate = trainingDate(now, zone)
    val hasCurrentTraining = latestTrainingEntry?.let { trainingDate(it, zone) == todayTrainingDate } == true
    val trainingUnitCutoff = if (hasCurrentTraining) trainingDayStart(now, zone) else Long.MAX_VALUE
    val recentExercises = remember(machines, hiddenMachines, trainingUnitCutoff) {
        recentTrainingExercises(machines + hiddenMachines, trainingUnitCutoff)
    }
    val scope = rememberCoroutineScope()
    val rankedMachines = remember(machines, trainingUnitCutoff, recentExercises) {
        rankOverviewMachines(machines, trainingUnitCutoff, recentExercises)
    }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(bottom = 50.dp)) {
            TrainingDurationCard(machines, activePlanName, zone, onTrainingHeader)
            if (activePlanNotesPinned && activePlanNotes.isNotBlank()) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable(onClick = onEditPlanNotes),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Text(activePlanNotes, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            if (machines.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Build Your Routine", style = MaterialTheme.typography.headlineSmall)
                    Text("Add your first exercise, choose its training areas, then log each set in a couple of taps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onAdd) { Text("Add First Exercise") }
                }
            } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (recentExercises.isNotEmpty()) {
                    item {
                        Text("Overlap: " + recentExercises.mapIndexed { index, exercise ->
                            exercise.machine.name + if (index == 1) " (half weight)" else ""
                        }.joinToString(" + "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(rankedMachines, key = { it.machine.id }, contentType = { "machine" }) { summary ->
                    MachineCard(
                        summary, recentExercises, trainingUnitCutoff, areaFrequency, onMachine, { machine, reps ->
                            val firstEntry = !hasCurrentTraining
                            onQuickSet(machine, reps)
                            if (firstEntry) scope.launch { listState.animateScrollToItem(0) }
                        }, onDeleteCurrentExerciseSets,
                        onMetricChange, onMetricChangeForSession, progressionSettings,
                        modifier = Modifier,
                        onLongPress = {},
                        isDragging = false,
                        isReordering = false,
                        onEditAreas = onEditAreas,
                    )
                }
                if (hiddenMachines.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = {
                                val reveal = !showHidden
                                onShowHiddenChanged(reveal)
                                if (reveal) scope.launch {
                                    delay(80)
                                    listState.animateScrollToItem(rankedMachines.size + 1 + if (recentExercises.isEmpty()) 0 else 1)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (showHidden) "Hide hidden exercises" else "Show hidden exercises") }
                    }
                    if (showHidden) {
                        val rankedHidden = rankOverviewMachines(hiddenMachines, trainingUnitCutoff, recentExercises)
                        items(rankedHidden, key = { "hidden_${it.machine.id}" }) { summary ->
                            MachineCard(summary, recentExercises, trainingUnitCutoff, areaFrequency, onMachine, { machine, reps -> onQuickSet(machine, reps) }, onDeleteCurrentExerciseSets, onMetricChange, onMetricChangeForSession, progressionSettings, Modifier, {}, false, false, onEditAreas)
                        }
                    }
                }            }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
                val compactPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                FilledTonalButton(onClick = onBody, modifier = Modifier.weight(1f), contentPadding = compactPadding) { OverviewButtonLabel("Body") }
                FilledTonalButton(onClick = onMenu, modifier = Modifier.weight(1f), contentPadding = compactPadding) { OverviewButtonLabel("Menu") }
                FilledTonalButton(onClick = onStats, modifier = Modifier.weight(1f), contentPadding = compactPadding) { OverviewButtonLabel("Stats") }
                FilledTonalButton(onClick = onOrder, modifier = Modifier.weight(1f), contentPadding = compactPadding) { OverviewButtonLabel("Order") }
                FilledTonalButton(onClick = onAdd, modifier = Modifier.weight(1f), contentPadding = compactPadding) { OverviewButtonLabel("+ Add") }
        }
    }



}
@Composable
private fun OverviewButtonLabel(text: String) {
    Text(text, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun TrainingDurationCard(machines: List<MachineSummary>, activePlanName: String, zone: ZoneId, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isForeground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(isForeground) {
        while (isForeground) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val allSets = machines.flatMap { it.recentSets }
    val last = allSets.maxOfOrNull { it.performedAt }
    val sessionSets = last?.let { latest -> allSets.filter { trainingDate(it.performedAt, zone) == trainingDate(latest, zone) } }.orEmpty()
    val first = sessionSets.minOfOrNull { it.performedAt }
    val firstSet = sessionSets.minByOrNull { it.performedAt }
    val firstMode = firstSet?.let { set -> machines.firstOrNull { it.machine.id == set.machineId }?.machine?.trackingMode }
    val estimatedLeadMillis = when {
        firstSet == null -> 0L
        isDirectMetric(firstMode ?: TrackingMode.WEIGHT) -> (firstSet.weight * 60_000L).toLong()
        else -> sessionSets.size * 3L * 60_000L
    }
    val estimatedStart = first?.minus(estimatedLeadMillis)
    val isActive = last != null && now - last < 10L * 60L * 1000L
    val durationMillis = if (estimatedStart == null || last == null) 0L else ((if (isActive) now else last) - estimatedStart).coerceAtLeast(0L)
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val completedExercises = machines.count { summary -> summary.recentSets.any { first != null && it.performedAt >= first } }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(activePlanName, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (first == null) "Starts with your first entry"
                    else {
                        val startTime = Instant.ofEpochMilli(estimatedStart!!).atZone(zone)
                        val endTime = Instant.ofEpochMilli(if (isActive) now else last!!).atZone(zone)
                        "${startTime.format(clock)} – ${if (isActive) "Now" else endTime.format(clock)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "$completedExercises/${machines.size} exercises",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (completedExercises == machines.size) Color(0xFF2E7D32) else MaterialTheme.colorScheme.tertiary,
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatElapsed(durationMillis),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    when {
                        last == null -> "Not started"
                        isActive -> "● Live"
                        else -> "✓ Completed ${completedTrainingDateLabel(trainingDate(last, zone), trainingDate(now, zone))}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        last == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFF2E7D32)
                    },
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
                Text("Progress Warnings", style = MaterialTheme.typography.labelLarge)
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
fun ProgressionSettingsDialog(current: ProgressionSettings, onDismiss: () -> Unit, onSave: (ProgressionSettings) -> Unit) {
    var weeks by remember(current) { mutableStateOf(current.staleWeeks.toString()) }
    var trainings by remember(current) { mutableStateOf(current.staleTrainings.toString()) }
    var volumePercent by remember(current) { mutableStateOf(current.volumeMinimumIncreasePercent.toString()) }
    val parsedWeeks = weeks.toIntOrNull()
    val parsedTrainings = trainings.toIntOrNull()
    val parsedVolumePercent = volumePercent.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Progress Warnings") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Volume checks the latest five training sessions. Set any limit to 0 to disable it.")
            OutlinedTextField(weeks, { weeks = it.filter(Char::isDigit) }, label = { Text("Weeks Without Weight Increase") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(trainings, { trainings = it.filter(Char::isDigit) }, label = { Text("Trainings Without Weight Increase") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(volumePercent, { volumePercent = it.filter(Char::isDigit) }, label = { Text("Minimum Volume Trend Increase (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        } },
        confirmButton = { Button(onClick = { onSave(ProgressionSettings(parsedWeeks ?: 0, parsedTrainings ?: 0, parsedVolumePercent ?: 0)) }, enabled = parsedWeeks != null && parsedTrainings != null && parsedVolumePercent != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
private fun progressionWarning(summary: MachineSummary, settings: ProgressionSettings): String? {
    if (summary.machine.trackingMode != TrackingMode.WEIGHT || summary.recentSets.isEmpty()) return null
    val reference = summary.weightIncreases().maxOfOrNull { it.changedAt } ?: summary.weightChanges.minOfOrNull { it.changedAt }
    val weeksWithoutIncrease = reference?.let { ((System.currentTimeMillis() - it).coerceAtLeast(0L) / 604_800_000L).toInt() } ?: 0
    val zone = ZoneId.systemDefault()
    val trainingsWithoutIncrease = reference?.let { threshold -> summary.recentSets.filter { it.performedAt > threshold }.map { Instant.ofEpochMilli(it.performedAt).atZone(zone).toLocalDate() }.distinct().count() } ?: 0
    val reasons = buildList {
        if (reference != null && settings.staleWeeks > 0 && weeksWithoutIncrease >= settings.staleWeeks) add("$weeksWithoutIncrease weeks without weight increase")
        if (reference != null && settings.staleTrainings > 0 && trainingsWithoutIncrease >= settings.staleTrainings) add("$trainingsWithoutIncrease trainings without weight increase")
        val volumes = groupSetBatches(summary.recentSets).asReversed().takeLast(5).map { batch -> batch.sumOf { it.weight * it.reps } }
        if (settings.volumeMinimumIncreasePercent > 0 && volumes.size >= 3 && volumes.first() > 0.0) {
            val growth = (volumes.last() - volumes.first()) / volumes.first() * 100.0
            if (growth < settings.volumeMinimumIncreasePercent) add("volume trend ${formatNumber(growth)}% (needs +${settings.volumeMinimumIncreasePercent}%)")
        }
    }
    return reasons.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "Progress warning: ")
}
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MachineCard(
    summary: MachineSummary,
    recentExercises: List<MachineSummary>,
    trainingUnitCutoff: Long,
    areaFrequency: Map<BodyArea, Double>,
    onMachine: (Long) -> Unit,
    onQuickSet: (MachineSummary, List<Int>) -> Unit,
    onDeleteCurrentExerciseSets: (MachineSummary) -> Unit,
    onMetricChange: (MachineSummary, Double) -> Unit,
    onMetricChangeForSession: (MachineSummary, Double) -> Unit,
    progressionSettings: ProgressionSettings,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = {},
    isDragging: Boolean = false,
    isReordering: Boolean = false,
    onEditAreas: (MachineSummary) -> Unit,
) {
    var quickReps by remember(summary.machine.id) { mutableStateOf(false) }
    var changeMetric by remember(summary.machine.id) { mutableStateOf(false) }
    var confirmDeleteSets by remember(summary.machine.id) { mutableStateOf(false) }
    val trainedInUnit = summary.recentSets.any { it.performedAt >= trainingUnitCutoff }
    val currentUnitSets = remember(summary.recentSets) { currentTrainingUnitSets(summary) }
    val isChangingTrainingUnit = currentUnitSets.isNotEmpty()
    val isLastDone = recentExercises.firstOrNull()?.machine?.id == summary.machine.id
    val sessionPattern = remember(summary.recentSets, summary.machine.trackingMode) { sessionPatternText(summary) }
    val overlap = exerciseOverlap(summary, recentExercises)
    val clash = if (!trainedInUnit) overlap.percent else null
    val clashAreas = if (!trainedInUnit) overlap.sharedAreas else emptySet()
    val trainingCount = remember(summary.recentSets) { exerciseTrainingCount(summary) }
    val outlineColor = when {
        isLastDone -> Color(0xFF1976D2)
        !trainedInUnit && clash == null -> MaterialTheme.colorScheme.outline
        clash != null && clash >= 60 -> Color(0xFFE53935)
        clash != null && clash >= 25 -> Color(0xFFFB8C00)
        clash != null && clash > 0 -> Color(0xFFFBC02D)
        else -> Color(0xFF43A047)
    }
    val metricInHeader = summary.machine.titleNote.isBlank() ||
        summary.machine.name.length + summary.machine.titleNote.length <= 30
    Card(
        modifier = modifier.fillMaxWidth().clickable(enabled = !isReordering) { onMachine(summary.machine.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 10.dp else 1.dp),
        border = when {
            isLastDone -> BorderStroke(3.dp, outlineColor)
            trainedInUnit -> null
            else -> BorderStroke(2.5.dp, outlineColor)
        },
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (summary.areas.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    summary.areas.sortedByDescending { areaFrequency[it.area] ?: 0.0 }.forEach { assignment ->
                        Box(
                            Modifier
                                .width((38 * assignment.strength.value).dp)
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
                if (metricInHeader) {
                    Text(
                        formatMetric(summary.machine.currentWeight, summary.machine.trackingMode),
                        modifier = Modifier.clickable(enabled = !isReordering) { changeMetric = true }.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = sessionTrendColor(summary),
                    )
                }
            }
            progressionWarning(summary, progressionSettings)?.let { warning ->
                Text(warning, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (when {
                        isLastDone -> "Current exercise"
                        trainedInUnit -> "Done this training"
                        clash == null -> "Overlap unknown"
                        else -> "Est. overlap $clash%" + if (overlap.incomplete) " (partial)" else ""
                    }) + " · Trained $trainingCount times",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = outlineColor,
                )
                TextButton(onClick = { onEditAreas(summary) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text(if (summary.areas.isEmpty()) "Set regions" else "Regions", style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        isChangingTrainingUnit -> "Change"
                        isDirectMetric(summary.machine.trackingMode) -> "+ Training"
                        else -> "+ Sets"
                    },
                    modifier = Modifier.clickable(enabled = !isReordering) { quickReps = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    sessionPattern,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!metricInHeader) {
                    Text(
                        formatMetric(summary.machine.currentWeight, summary.machine.trackingMode),
                        modifier = Modifier.clickable(enabled = !isReordering) { changeMetric = true }.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = sessionTrendColor(summary),
                    )
                }
            }
        }
    }
    if (quickReps) {
        if (isDirectMetric(summary.machine.trackingMode)) {
            QuickTrainingDialog(
                initialMinutes = (currentUnitSets.firstOrNull() ?: summary.recentSets.firstOrNull())?.weight?.toInt() ?: 10,
                isChange = isChangingTrainingUnit,
                onDismiss = { quickReps = false },
                onAdd = { onQuickSet(summary, listOf(it)); quickReps = false },
                onDelete = if (isChangingTrainingUnit) { { confirmDeleteSets = true } } else null,
            )
        } else {
            QuickRepDialog(
                initialReps = initialWheelReps(summary, currentUnitSets),
                lastReps = previousTrainingUnitReps(summary, currentUnitSets),
                isChange = isChangingTrainingUnit,
                onDismiss = { quickReps = false },
                onAdd = { onQuickSet(summary, it); quickReps = false },
                onDelete = if (isChangingTrainingUnit) { { confirmDeleteSets = true } } else null,
            )
        }
    }
    if (confirmDeleteSets) AlertDialog(
        onDismissRequest = { confirmDeleteSets = false },
        title = { Text("Delete this exercise's sets?") },
        text = { Text("All sets for this exercise in the current training day will be removed.") },
        confirmButton = { Button(onClick = { onDeleteCurrentExerciseSets(summary); confirmDeleteSets = false; quickReps = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDeleteSets = false }) { Text("Cancel") } },
    )
    if (changeMetric) MetricValueDialog(
        current = summary.machine.currentWeight,
        trackingMode = summary.machine.trackingMode,
        onDismiss = { changeMetric = false },
        onSave = {
            changeMetric = false
            onMetricChange(summary, it)
        },
        onSaveCurrentAndNext = if (currentUnitSets.isNotEmpty()) { value ->
            onMetricChangeForSession(summary, value)
            changeMetric = false
        } else null,
    )
}

fun trainingDate(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
    val local = Instant.ofEpochMilli(timestamp).atZone(zone)
    return local.toLocalDate().minusDays(if (local.hour < 6) 1 else 0)
}

private fun SetRecord.performedDate(zone: ZoneId): LocalDate = trainingDate(performedAt, zone)
private fun completedTrainingDateLabel(date: LocalDate, today: LocalDate): String = when (val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)) {
    0L -> "today"
    1L -> "yesterday"
    in 2L..7L -> "last ${date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)}"
    else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
}
private fun trainingDayStart(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val local = Instant.ofEpochMilli(timestamp).atZone(zone)
    val date = local.toLocalDate().minusDays(if (local.hour < 6) 1 else 0)
    return date.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
}

private fun sessionPatternText(summary: MachineSummary): AnnotatedString {
    val batches = groupSetBatches(summary.recentSets).take(8)
    val patterns = batches.map { batch ->
        batch.sortedBy { it.performedAt }.joinToString(",") { set ->
            if (isDirectMetric(summary.machine.trackingMode)) "${formatNumber(set.weight)} min" else set.reps.toString()
        }
    }
    fun trend(index: Int): Int {
        val newer = batches.getOrNull(index)?.firstOrNull() ?: return 0
        val older = batches.getOrNull(index + 1)?.firstOrNull() ?: return 0
        val newerMetric = summary.recordedMetric(newer)
        val olderMetric = summary.recordedMetric(older)
        return newerMetric.compareTo(olderMetric)
    }
    fun AnnotatedString.Builder.appendTrendMarker(trend: Int) {
        when {
            trend > 0 -> withStyle(SpanStyle(color = Color(0xFF66BB6A), fontWeight = FontWeight.Black)) { append("|") }
            trend < 0 -> withStyle(SpanStyle(color = Color(0xFFEF5350), fontWeight = FontWeight.Black)) { append("|") }
            else -> append("|")
        }
    }
    return buildAnnotatedString {
        append("Last: ")
        if (patterns.isEmpty()) append("-")
        patterns.forEachIndexed { index, pattern ->
            append(pattern)
            if (index < patterns.lastIndex) {
                append(" ")
                appendTrendMarker(trend(index))
                append(" ")
            }
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
fun QuickTrainingDialog(initialMinutes: Int, isChange: Boolean = false, onDismiss: () -> Unit, onAdd: (Int) -> Unit, onDelete: (() -> Unit)? = null) {
    var value by remember(initialMinutes) { mutableStateOf(initialMinutes.coerceAtLeast(0).toString()) }
    val level = value.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isChange) "Change Training" else "Log Training") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value, { value = it.filter(Char::isDigit) }, label = { Text("Minutes") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                MetricStepButtons(listOf(1, 2, 3), "min") { delta -> value = ((level ?: 0) + delta).coerceAtLeast(0).toString() }
            }
        },
        confirmButton = { Button(onClick = { level?.let(onAdd) }, enabled = level != null && level > 0) { Text(if (isChange) "Change Training" else "Save Training") } },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete sets", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Cancel") } } },
    )
}

internal fun sessionTrendColor(summary: MachineSummary): Color = when (summary.pendingMetricDirection()) {
    1 -> Color(0xFF66BB6A)
    -1 -> Color(0xFFEF5350)
    else -> Color(0xFF9E9E9E)
}

private const val TRAINING_UNIT_MILLIS = 6L * 60L * 60L * 1000L

fun currentTrainingUnitSets(summary: MachineSummary, now: Long = System.currentTimeMillis()): List<SetRecord> {
    val day = trainingDate(now)
    return summary.recentSets.filter { trainingDate(it.performedAt) == day }.sortedBy { it.performedAt }
}
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
    onDelete: (() -> Unit)? = null,
) {
    val reps = remember(initialReps) { mutableStateListOf<Int>().apply { addAll((initialReps + List(5) { 0 }).take(5)) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isChange) "Change Sets" else "Log Up to 5 Sets") },
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
        confirmButton = { Button(onClick = { onAdd(reps.toList()) }, enabled = isChange || reps.any { it > 0 }) { Text(if (isChange) "Change Sets" else "Save Sets") } },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete sets", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Cancel") } } },
    )
}

@Composable
fun AddMachineDialog(onDismiss: () -> Unit, onAdd: (String, Double, TrackingMode, String, List<AreaAssignment>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var trackingMode by remember { mutableStateOf(TrackingMode.WEIGHT) }
    var note by remember { mutableStateOf("") }
    var areas by remember { mutableStateOf<List<AreaAssignment>>(emptyList()) }
    var selectedProfile by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    fun chooseTemplate(template: ExerciseTemplate) {
        if (name.isBlank()) name = template.englishName
        areas = template.areas
        selectedProfile = template.title
        trackingMode = template.trackingMode
        if (weight.isBlank()) weight = "0"
        showCatalog = false
    }
    val suggestions = remember(name) { if (name.length >= 2) ExerciseCatalog.search(name, limit = 3) else emptyList() }
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exercise") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = value.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } },
                    label = { Text("Exercise Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                )
                if (selectedProfile == null && suggestions.isNotEmpty()) {
                    Text("Suggested exercises", style = MaterialTheme.typography.labelMedium)
                    suggestions.forEach { suggestion ->
                        TextButton(onClick = { chooseTemplate(suggestion) }) { Text(suggestion.title) }
                    }
                }
                OutlinedButton(onClick = { showCatalog = true }, modifier = Modifier.fillMaxWidth()) { Text("Browse exercise suggestions") }
                selectedProfile?.let { Text("Starting profile: $it", style = MaterialTheme.typography.bodySmall) }
                Text(muscleProfileSummary(areas), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showProfile = true }) { Text("Choose / edit body regions & intensity") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = trackingMode == TrackingMode.WEIGHT, onClick = { trackingMode = TrackingMode.WEIGHT }, label = { Text("kg") })
                    FilterChip(selected = trackingMode == TrackingMode.WATTS, onClick = { trackingMode = TrackingMode.WATTS }, label = { Text("Watts") })
                    FilterChip(selected = trackingMode == TrackingMode.STRENGTH, onClick = { trackingMode = TrackingMode.STRENGTH }, label = { Text("Level") })
                }
                FilterChip(selected = trackingMode == TrackingMode.DURATION, onClick = { trackingMode = TrackingMode.DURATION }, label = { Text("Minutes") })
                if (trackingMode.usesResistanceLevel()) {
                    Text(
                        "Level and Watts exercises record training duration in minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                        label = { Text(currentMetricLabel(trackingMode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = if (trackingMode == TrackingMode.WEIGHT) KeyboardType.Decimal else KeyboardType.Number),
                        singleLine = true,
                    )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name.trim(), parsedWeight ?: 0.0, trackingMode, note.trim(), areas) },
                enabled = name.isNotBlank() && parsedWeight != null && parsedWeight >= 0,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (showCatalog) ExerciseCatalogDialog(name, onDismiss = { showCatalog = false }, onPick = ::chooseTemplate)
    if (showProfile) MuscleProfileDialog(name.ifBlank { "New exercise" }, areas, onDismiss = { showProfile = false }) {
        areas = it
        showProfile = false
    }
}

fun formatWeight(weight: Double): String = if (weight % 1.0 == 0.0) "${weight.toInt()} kg" else String.format(Locale.US, "%.1f kg", weight)

fun formatHoursMinutes(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h${minutes.toString().padStart(2, '0')}" else "${minutes}m"
}

private fun formatElapsed(totalMillis: Long): String {
    val totalSeconds = totalMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    return if (hours > 0) "${hours}h${minutes.toString().padStart(2, '0')}" else "${minutes}m"
}

fun isDirectMetric(trackingMode: TrackingMode): Boolean = trackingMode.usesResistanceLevel() || trackingMode == TrackingMode.DURATION

fun metricName(trackingMode: TrackingMode): String = when (trackingMode) {
    TrackingMode.WATTS -> "power"
    TrackingMode.WEIGHT -> "weight"
    TrackingMode.DURATION -> "duration"
    else -> "level"
}

fun metricTitle(trackingMode: TrackingMode): String = metricName(trackingMode).replaceFirstChar { it.uppercase() }

fun currentMetricLabel(trackingMode: TrackingMode): String = when (trackingMode) {
    TrackingMode.WATTS -> "Current Power (W)"
    TrackingMode.WEIGHT -> "Current Weight (kg)"
    TrackingMode.DURATION -> "Current Duration (min)"
    else -> "Current Level"
}

private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

fun formatMetric(value: Double, trackingMode: TrackingMode): String = when (trackingMode) {
    TrackingMode.WATTS -> "${formatNumber(value)} W"
    TrackingMode.WEIGHT -> formatWeight(value)
    TrackingMode.DURATION -> "${formatNumber(value)} min"
    else -> "Level ${formatNumber(value)}"
}

fun formatTrainingEntry(value: Double, trackingMode: TrackingMode, level: Int? = null): String = when (trackingMode) {
    TrackingMode.WATTS -> "${level ?: 0} W | ${formatNumber(value)} min"
    TrackingMode.STRENGTH -> "Level ${(level ?: 0).toString().padStart(2)} · ${formatNumber(value).padStart(3)} min"
    TrackingMode.DURATION -> "${formatNumber(value)} min"
    else -> formatMetric(value, trackingMode)
}
