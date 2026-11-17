package com.gymlog.app.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
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
import com.gymlog.app.data.PriorExerciseExposure
import com.gymlog.app.data.priorExerciseExposures
import com.gymlog.app.data.exerciseOverlap
import com.gymlog.app.data.rankOverviewMachines
import com.gymlog.app.data.recentTrainingExercises
import com.gymlog.app.data.ProgressionSettings
import com.gymlog.app.data.FatigueBands
import com.gymlog.app.data.SetRecord
import com.gymlog.app.data.parseWarningSetting
import com.gymlog.app.data.progressionWarning
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
    onWipStats: () -> Unit,
    onBody: () -> Unit,
    listState: LazyListState,
    scrollToExerciseId: Long?,
    onExerciseScrollHandled: () -> Unit,
    showHidden: Boolean,
    onShowHiddenChanged: (Boolean) -> Unit,
    onTrainingHeader: () -> Unit,
    onEditPlanNotes: () -> Unit,
    areaFrequency: Map<BodyArea, Double>,
    onQuickSet: (MachineSummary, List<Int>) -> Unit,
    onDeleteCurrentExerciseSets: (MachineSummary) -> Unit,
    onMetricChange: (MachineSummary, Double) -> Unit,
    onMetricChangeForSession: (MachineSummary, Double) -> Unit,
    progressionSettings: ProgressionSettings,
    fatigueBands: FatigueBands = FatigueBands(),
) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val latestTrainingEntry = remember(machines, hiddenMachines) {
        (machines + hiddenMachines).maxOfOrNull { it.recentSets.maxOfOrNull { set -> set.performedAt } ?: Long.MIN_VALUE }
            ?.takeIf { it != Long.MIN_VALUE }
    }
    val todayTrainingDate = trainingDate(now, zone)
    val hasCurrentTraining = latestTrainingEntry?.let { trainingDate(it, zone) == todayTrainingDate } == true
    val trainingUnitCutoff = if (hasCurrentTraining) trainingDayStart(now, zone) else Long.MAX_VALUE
    val recentExercises = remember(machines, hiddenMachines, trainingUnitCutoff) {
        recentTrainingExercises(machines + hiddenMachines, trainingUnitCutoff)
    }
    val priorExposures = remember(machines, hiddenMachines, todayTrainingDate) {
        priorExerciseExposures(machines + hiddenMachines, now, zone)
    }
    val scope = rememberCoroutineScope()
    val rankedMachines = remember(machines, trainingUnitCutoff, recentExercises, priorExposures, progressionSettings, fatigueBands, todayTrainingDate) {
        rankOverviewMachines(machines, trainingUnitCutoff, recentExercises, zone, now, priorExposures, progressionSettings, fatigueBands)
    }
    val rankedHidden = remember(hiddenMachines, trainingUnitCutoff, recentExercises, showHidden, priorExposures, progressionSettings, fatigueBands, todayTrainingDate) {
        if (showHidden) rankOverviewMachines(hiddenMachines, trainingUnitCutoff, recentExercises, zone, now, priorExposures, progressionSettings, fatigueBands) else emptyList()
    }
    LaunchedEffect(scrollToExerciseId, rankedMachines, rankedHidden, recentExercises) {
        val id = scrollToExerciseId ?: return@LaunchedEffect
        // Wait for refreshed data to identify the saved exercise as current before scrolling.
        if (recentExercises.firstOrNull()?.machine?.id != id) return@LaunchedEffect
        val visibleIndex = rankedMachines.indexOfFirst { it.machine.id == id }
        val hiddenIndex = rankedHidden.indexOfFirst { it.machine.id == id }
        val index = when {
            visibleIndex >= 0 -> visibleIndex
            hiddenIndex >= 0 -> rankedMachines.size + 1 + hiddenIndex
            else -> return@LaunchedEffect
        }
        listState.animateScrollToItem(index)
        onExerciseScrollHandled()
    }
    // Keep history-derived content above LazyColumn so recycling a card does not rebuild it during a fling.
    val cardContent = remember(machines, hiddenMachines, showHidden, progressionSettings, todayTrainingDate) {
        (machines + if (showHidden) hiddenMachines else emptyList()).associate { summary ->
            summary.machine.id to MachineCardContent(sessionPatternText(summary),
                progressionWarning(summary, progressionSettings), sessionTrendColor(summary),
                currentTrainingUnitSets(summary, now, zone))
        }
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
                items(rankedMachines, key = { it.machine.id }, contentType = { "machine" }) { summary ->
                    MachineCard(
                        summary, recentExercises, trainingUnitCutoff, areaFrequency, onMachine, onQuickSet, onDeleteCurrentExerciseSets,
                        onMetricChange, onMetricChangeForSession, cardContent.getValue(summary.machine.id),
                        priorExposures = priorExposures,
                        fatigueBands = fatigueBands,
                        modifier = Modifier,
                        onLongPress = {},
                        isDragging = false,
                        isReordering = false,
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
                                    listState.animateScrollToItem(rankedMachines.size + 1)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (showHidden) "Hide hidden exercises (${hiddenMachines.size})" else "Show hidden exercises (${hiddenMachines.size})") }
                    }
                    if (showHidden) {
                        items(rankedHidden, key = { "hidden_${it.machine.id}" }) { summary ->
                            MachineCard(summary, recentExercises, trainingUnitCutoff, areaFrequency, onMachine, { machine, reps -> onQuickSet(machine, reps) }, onDeleteCurrentExerciseSets, onMetricChange, onMetricChangeForSession, cardContent.getValue(summary.machine.id), priorExposures = priorExposures, fatigueBands = fatigueBands)
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
                FilledTonalButton(onClick = onWipStats, modifier = Modifier.weight(1f), contentPadding = compactPadding) { OverviewButtonLabel("WIP stats") }
                FilledTonalButton(onClick = onAdd, modifier = Modifier.weight(1f), contentPadding = compactPadding) { OverviewButtonLabel("+ Add") }
        }
    }



}
@Composable
private fun OverviewButtonLabel(text: String) {
    Text(text, maxLines = 1, softWrap = false, style = if (text == "WIP stats") MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
}

@Composable
private fun TrainingDurationCard(machines: List<MachineSummary>, activePlanName: String, zone: ZoneId, onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val session = remember(machines, zone) { trainingTimerSnapshot(machines, zone) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isForeground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(isForeground, session) {
        now = System.currentTimeMillis()
        while (isForeground) {
            val wait = nextTrainingTimerDelay(now, session.last, session.estimatedStart) ?: break
            delay(wait)
            now = System.currentTimeMillis()
        }
    }
    val last = session.last
    val first = session.first
    val estimatedStart = session.estimatedStart
    val isActive = trainingTimerActive(now, last, estimatedStart)
    val durationMillis = if (estimatedStart == null || last == null) 0L else ((if (isActive) now else last) - estimatedStart).coerceAtLeast(0L)
    val clock = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val completedExercises = session.completedExercises

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
fun ProgressionSettingsDialog(
    current: ProgressionSettings,
    onDismiss: () -> Unit,
    title: String = "Progress Warnings",
    initiallyFollowGlobal: Boolean = false,
    onFollowGlobal: (() -> Unit)? = null,
    onSave: (ProgressionSettings) -> Unit,
) {
    var followGlobal by remember { mutableStateOf(initiallyFollowGlobal) }
    var weeks by remember(current) { mutableStateOf(current.staleWeeks.takeIf { it > 0 }?.toString().orEmpty()) }
    var volumeWindow by remember(current) { mutableStateOf(current.volumeWindowDays.takeIf { it > 0 }?.toString().orEmpty()) }
    var volumePercent by remember(current) { mutableStateOf(current.volumeMinimumIncreasePercent.takeIf { it > 0 }?.toString().orEmpty()) }
    val metricLabel = "Strength score"
    var showMetricHelp by remember { mutableStateOf(false) }
    val parsedWeeks = parseWarningSetting(weeks)
    val parsedVolumeWindow = parseWarningSetting(volumeWindow)
    val parsedVolumePercent = parseWarningSetting(volumePercent)
    val fieldsValid = parsedWeeks != null && parsedVolumeWindow != null && parsedVolumePercent != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onFollowGlobal != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Follow global warning settings", Modifier.weight(1f))
                    Switch(checked = followGlobal, onCheckedChange = { followGlobal = it })
                }
            }
            Text("Trend warnings use the all-set strength score: sum weight × reps^0.1 over every valid set in each training day. It is a comparison score, not a one-rep maximum.", style = MaterialTheme.typography.titleSmall)
            Text("Blank or 0 disables that warning. Trend warnings are disabled if either the days or minimum increase is blank or 0. Compare the first and latest training days within the last X days, including today; at least 2 training days are needed. Default: 60 days.")
            if (!followGlobal) {
                TextButton(onClick = { showMetricHelp = true }) { Text("How Strength score is calculated") }
                OutlinedTextField(weeks, { weeks = it.filter(Char::isDigit) },
                    label = { Text("Weeks Without Weight Increase") }, isError = parsedWeeks == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(volumeWindow, { volumeWindow = it.filter(Char::isDigit) },
                    label = { Text("$metricLabel Trend Window (days)") }, isError = parsedVolumeWindow == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(volumePercent, { volumePercent = it.filter(Char::isDigit) },
                    label = { Text("Minimum $metricLabel Trend Increase (%)") }, isError = parsedVolumePercent == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        } },
        confirmButton = { Button(onClick = {
            if (followGlobal) onFollowGlobal?.invoke() else onSave(ProgressionSettings(
                staleWeeks = parsedWeeks ?: 0,
                volumeWindowDays = parsedVolumeWindow ?: 0,
                volumeMinimumIncreasePercent = parsedVolumePercent ?: 0,
                useOneRepMax = current.useOneRepMax,
            ))
        }, enabled = followGlobal || fieldsValid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (showMetricHelp) TrainingMetricExplanationDialog { showMetricHelp = false }
}
internal fun overlapOutlineColor(percent: Int?, bands: FatigueBands = FatigueBands()): Color {
    val colors = listOf(
        Color(0xFF43A047), Color(0xFFFDD835), Color(0xFFFB8C00), Color(0xFFE53935),
    )
    return colors[com.gymlog.app.data.exerciseOverlapBin(percent, bands)]
}

private data class MachineCardContent(
    val sessionPattern: AnnotatedString,
    val warning: String?,
    val metricColor: Color,
    val currentSets: List<SetRecord>,
)

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
    content: MachineCardContent,
    modifier: Modifier = Modifier,
    priorExposures: List<PriorExerciseExposure> = emptyList(),
    fatigueBands: FatigueBands = FatigueBands(),
    onLongPress: () -> Unit = {},
    isDragging: Boolean = false,
    isReordering: Boolean = false,
) {
    var quickReps by remember(summary.machine.id) { mutableStateOf(false) }
    var changeMetric by remember(summary.machine.id) { mutableStateOf(false) }
    var confirmDeleteSets by remember(summary.machine.id) { mutableStateOf(false) }
    val trainedInUnit = remember(summary.recentSets, trainingUnitCutoff) {
        summary.recentSets.any { it.performedAt >= trainingUnitCutoff }
    }
    val currentUnitSets = content.currentSets
    val isChangingTrainingUnit = currentUnitSets.isNotEmpty()
    val isLastDone = recentExercises.firstOrNull()?.machine?.id == summary.machine.id
    val sessionPattern = content.sessionPattern
    val overlap = remember(summary.areas, recentExercises, priorExposures) { exerciseOverlap(summary, recentExercises, priorExposures) }
    val warning = content.warning
    val metricColor = content.metricColor
    val sortedAreas = remember(summary.areas, areaFrequency) { summary.areas.sortedByDescending { areaFrequency[it.area] ?: 0.0 } }
    val clash = if (!trainedInUnit) overlap.percent else null
    val clashAreas = if (!trainedInUnit) overlap.sharedAreas else emptySet()
    val outlineColor by animateColorAsState(
        targetValue = if (isLastDone) Color(0xFF1976D2) else overlapOutlineColor(clash, fatigueBands),
        label = "Exercise overlap outline",
    )
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
                    sortedAreas.forEach { assignment ->
                        Box(
                            Modifier
                                .width((38 * assignment.intensity).dp)
                                .height(if (assignment.area in clashAreas) 7.dp else 3.dp)
                                .background(
                                    Color(assignment.displayColor).copy(alpha = if (clashAreas.isEmpty() || assignment.area in clashAreas) 1f else .25f),
                                    RoundedCornerShape(8.dp),
                                ),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AutoFitText(
                    buildAnnotatedString {
                        append(summary.machine.name)
                        if (summary.machine.titleNote.isNotBlank()) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)) {
                                append(" · ${summary.machine.titleNote}")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    formatMetric(summary.machine.currentWeight, summary.machine.trackingMode),
                    modifier = Modifier.clickable(enabled = !isReordering) { changeMetric = true }.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = metricColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            warning?.let { warning ->
                AutoFitText(warning, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        isChangingTrainingUnit -> "Change"
                        isDirectMetric(summary.machine.trackingMode) -> "+ Time"
                        else -> "+ Sets"
                    },
                    modifier = Modifier.clickable(enabled = !isReordering) { quickReps = true }.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                OverviewHistoryText(
                    sessionPattern,
                    modifier = Modifier.weight(1f),
                )
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

internal fun sessionPatternText(summary: MachineSummary): AnnotatedString {
    val batches = groupSetBatches(summary.recentSets)
    val patterns = batches.map { batch ->
        batch.sortedWith(compareBy<SetRecord> { it.performedAt }.thenBy { it.id }).joinToString(",") { set ->
            if (isDirectMetric(summary.machine.trackingMode)) formatNumber(set.weight) else set.reps.toString()
        }
    }
    return buildAnnotatedString {
        append("Last: ")
        if (patterns.isEmpty()) append("-")
        patterns.forEachIndexed { index, pattern ->
            append(pattern)
            if (index < patterns.lastIndex) {
                append(" ")
                // IDs break timestamp ties, including after an entry has been edited.
                val entryOrder = compareBy<SetRecord> { it.performedAt }.thenBy { it.id }
                val newer = batches[index].maxWithOrNull(entryOrder)
                val older = batches[index + 1].maxWithOrNull(entryOrder)
                val change = if (newer == null || older == null) 0 else summary.recordedMetric(newer).compareTo(summary.recordedMetric(older))
                if (change == 0) append("|") else withStyle(SpanStyle(color = if (change > 0) Color(0xFF66BB6A) else Color(0xFFEF5350), fontWeight = FontWeight.Black)) { append("|") }
                append(" ")
            }
        }
    }
}

fun groupSetBatches(sets: List<SetRecord>): List<List<SetRecord>> {
    val batches = mutableListOf<MutableList<SetRecord>>()
    sets.sortedWith(compareByDescending<SetRecord> { it.performedAt }.thenByDescending { it.id }).forEach { set ->
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

fun currentTrainingUnitSets(summary: MachineSummary, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<SetRecord> {
    val day = trainingDate(now, zone)
    val start = day.atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
    val end = day.plusDays(1).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
    return summary.recentSets.filter { it.performedAt >= start && it.performedAt < end }.sortedBy { it.performedAt }
}
internal fun latestRecordedBatch(summary: MachineSummary, excludedIds: Set<Long> = emptySet()): List<SetRecord> =
    groupSetBatches(summary.recentSets.filterNot { it.id in excludedIds }).firstOrNull().orEmpty()
        .sortedWith(compareBy<SetRecord> { it.performedAt }.thenBy { it.id })

fun previousTrainingUnitReps(summary: MachineSummary, current: List<SetRecord> = currentTrainingUnitSets(summary)): List<Int> =
    latestRecordedBatch(summary, current.mapTo(mutableSetOf()) { it.id }).map { it.reps }.take(5)

fun initialWheelReps(summary: MachineSummary, current: List<SetRecord> = currentTrainingUnitSets(summary)): List<Int> {
    val recorded = if (current.isNotEmpty()) current.sortedWith(compareBy<SetRecord> { it.performedAt }.thenBy { it.id }).map { it.reps }
        else previousTrainingUnitReps(summary, current)
    val saved = summary.setWheelValues
    val base = if (recorded.isNotEmpty()) saved?.takeIf { it.filter { value -> value > 0 } == recorded } ?: recorded
        else saved ?: listOf(10, 10, 10)
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
    val slots = maxOf(5, initialReps.size)
    val reps = remember(initialReps) { mutableStateListOf<Int>().apply { addAll((initialReps + List(5) { 0 }).take(slots)) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isChange) "Change Sets" else "Log Up to 5 Sets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
                    reps.indices.forEach { index ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AndroidView(
                                modifier = Modifier.width(52.dp).height(132.dp),
                                factory = { context ->
                                    NumberPicker(context).apply {
                                        minValue = 0
                                        maxValue = maxOf(15, initialReps.getOrElse(index) { 0 })
                                        displayedValues = arrayOf("None") + (1..maxValue).map(Int::toString)
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
    var trackingModeChosen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var areas by remember { mutableStateOf<List<AreaAssignment>>(emptyList()) }
    var selectedProfile by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    fun chooseTemplate(template: ExerciseTemplate) {
        if (name.isBlank()) name = template.englishName
        areas = template.areas
        selectedProfile = template.title
        if (!trackingModeChosen) trackingMode = template.trackingMode
        if (weight.isBlank()) weight = "0"
        showCatalog = false
    }
    val suggestions = remember(name) { if (name.isNotBlank()) ExerciseCatalog.search(name, limit = 3, allowWeakMatches = true) else emptyList() }
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
                    Text("Best matching exercises (may be a weak match)", style = MaterialTheme.typography.labelMedium)
                    suggestions.forEach { suggestion ->
                        TextButton(onClick = { chooseTemplate(suggestion) }) { Text(suggestion.title) }
                    }
                }
                OutlinedButton(onClick = { showCatalog = true }, modifier = Modifier.fillMaxWidth()) { Text("Browse exercise suggestions") }
                selectedProfile?.let { Text("Starting profile: $it", style = MaterialTheme.typography.bodySmall) }
                Text(muscleProfileSummary(areas), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showProfile = true }) { Text("Choose / edit body regions & intensity") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = trackingMode == TrackingMode.WEIGHT, onClick = { trackingMode = TrackingMode.WEIGHT; trackingModeChosen = true }, label = { Text("kg") })
                    FilterChip(selected = trackingMode == TrackingMode.WATTS, onClick = { trackingMode = TrackingMode.WATTS; trackingModeChosen = true }, label = { Text("Watts") })
                    FilterChip(selected = trackingMode == TrackingMode.STRENGTH, onClick = { trackingMode = TrackingMode.STRENGTH; trackingModeChosen = true }, label = { Text("Level") })
                }
                FilterChip(selected = trackingMode == TrackingMode.DURATION, onClick = { trackingMode = TrackingMode.DURATION; trackingModeChosen = true }, label = { Text("Minutes") })
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
    else -> "Lv. ${formatNumber(value)}"
}

fun formatTrainingEntry(value: Double, trackingMode: TrackingMode, level: Int? = null): String = when (trackingMode) {
    TrackingMode.WATTS -> "${level ?: 0} W | ${formatNumber(value)} min"
    TrackingMode.STRENGTH -> "Lv. ${(level ?: 0).toString().padStart(2)} · ${formatNumber(value).padStart(3)} min"
    TrackingMode.DURATION -> "${formatNumber(value)} min"
    else -> formatMetric(value, trackingMode)
}
