package com.gymlog.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.gymlog.app.BuildConfig
import com.gymlog.app.data.TrackingMode
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.FatigueBands
import com.gymlog.app.data.parseFatigueBands
import com.gymlog.app.data.PriorDayFatigue
import com.gymlog.app.data.parsePriorDayFatigue
import com.gymlog.app.data.BackupSyncStatus
import com.gymlog.app.data.GymRepository
import com.gymlog.app.data.HistorySet
import com.gymlog.app.data.rankOverviewMachines
import com.gymlog.app.data.recentTrainingExercises
import com.gymlog.app.data.ExerciseOrderGroup
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.ProgressionSettings
import com.gymlog.app.data.TrainingPlan
import kotlinx.coroutines.launch

private enum class Screen { OVERVIEW, BODY, STATS, WIP_STATS, CC_STATS, ORDER, HISTORY, BACKUP, PLANS, DETAIL }
enum class ExerciseEditField { NAME, TITLE_NOTE, OVERVIEW_NOTE }

internal data class AppUiState(
    val machines: List<MachineSummary>,
    val history: List<HistorySet>,
    val areaColors: Map<BodyArea, Long>,
    val trainingPlans: List<TrainingPlan>,
    val activeTrainingPlanId: Long,
    val progressionSettings: ProgressionSettings,
    val hiddenMachineIds: Set<Long>,
    val automaticBackupEnabled: Boolean,
    val backupSyncStatus: BackupSyncStatus,
    val fatigueBands: FatigueBands = FatigueBands(),
    val priorDayFatigue: PriorDayFatigue = PriorDayFatigue(),
    val fatigueExercises: List<MachineSummary> = emptyList(),
)

internal fun GymRepository.loadUiState(): AppUiState {
    val machines = loadMachines()
    return AppUiState(
        machines = machines,
        history = machines.flatMap { summary ->
            summary.recentSets.map { set ->
                HistorySet(set.id, set.machineId, summary.machine.name, summary.machine.trackingMode, set.reps, set.weight, set.performedAt)
            }
        }.sortedWith(compareByDescending<HistorySet> { it.performedAt }.thenByDescending { it.id }),
        areaColors = loadAreaColors(),
        trainingPlans = loadTrainingPlans(),
        activeTrainingPlanId = activeTrainingPlanId(),
        progressionSettings = loadProgressionSettings(),
        hiddenMachineIds = machines.mapNotNullTo(mutableSetOf()) { it.machine.id.takeIf(::isMachineHidden) },
        automaticBackupEnabled = automaticBackupEnabled(),
        backupSyncStatus = backupSyncStatus(),
        fatigueBands = loadFatigueBands(),
        priorDayFatigue = loadPriorDayFatigue(),
        fatigueExercises = loadFatigueExercises(),
    )
}

internal fun GymRepository.loadUiStateResult(): Result<AppUiState> = try {
    Result.success(loadUiState())
} catch (exception: Exception) {
    Result.failure(exception)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymLogApp(
    repository: GymRepository,
    externalDataVersion: Int,
    onExport: () -> Unit,
    onImport: ((BackupRestoreResult?) -> Unit) -> Unit,
    onSaveBodyEntry: (ManualBodyEntry, (String?) -> Unit) -> Unit,
    onExportPlan: () -> Unit,
    onImportPlan: () -> Unit,
    onAccentColorChanged: (Long) -> Unit,
    bodyData: BodyDataSnapshot,
    healthSyncStatus: String,
    healthSyncing: Boolean,
    onHealthSync: () -> Unit,
    onAutoHealthSync: () -> Unit,
    restoredBackup: BackupRestoreResult? = null,
    onRestoredBackupShown: () -> Unit = {},
) {
    val viewModel: GymLogViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = GymLogViewModel.factory(repository))
    val loadState by viewModel.uiState.collectAsState()
    val uiStateResult = loadState.result ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading GymLog\u2026") }
        return
    }
    var screen by remember { mutableStateOf(Screen.OVERVIEW) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var detailOrigin by remember { mutableStateOf(Screen.OVERVIEW) }
    var historyOrigin by remember { mutableStateOf(Screen.STATS) }
    var showAdd by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddBody by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    var showProgressionSettings by remember { mutableStateOf(false) }
    var showAccentColor by remember { mutableStateOf(false) }
    var showPlanNotes by remember { mutableStateOf(false) }
    var showTrainingHeader by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showTrainingMetricHelp by remember { mutableStateOf(false) }
    var confirmDeleteLastTraining by remember { mutableStateOf(false) }
    var editingExerciseField by remember { mutableStateOf<ExerciseEditField?>(null) }
    var initialExternalVersionHandled by remember { mutableStateOf(false) }
    var showHiddenExercises by rememberSaveable { mutableStateOf(repository.showHiddenExercises()) }
    val overviewListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun refresh() {
        viewModel.refresh()
    }
    fun mutate(action: () -> Unit) = viewModel.mutate(action)
    fun deleteWithUndo(message: String, action: () -> com.gymlog.app.data.DeletionJournal?) {
        scope.launch {
            val journal = viewModel.write(action) ?: return@launch
            refresh()
            snackbarHostState.currentSnackbarData?.dismiss()
            if (snackbarHostState.showSnackbar(message, "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                viewModel.write { repository.restoreDeletion(journal) }
                refresh()
            }
        }
    }
    fun offerRestoreUndo(restored: BackupRestoreResult) {
        scope.launch {
            refresh()
            snackbarHostState.currentSnackbarData?.dismiss()
            if (snackbarHostState.showSnackbar("Backup restored", "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                try {
                    restored.undo()
                    refresh()
                } catch (exception: kotlinx.coroutines.CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    snackbarHostState.showSnackbar("Could not undo restore: ${exception.message}")
                }
            }
        }
    }
    // A restore that finished after the activity was recreated has no callback; offer its Undo here.
    LaunchedEffect(restoredBackup) {
        restoredBackup?.let {
            onRestoredBackupShown()
            offerRestoreUndo(it)
        }
    }
    LaunchedEffect(externalDataVersion) {
        if (!initialExternalVersionHandled) {
            initialExternalVersionHandled = true
            return@LaunchedEffect
        }
        refresh()
    }

    val loadError = uiStateResult.exceptionOrNull()
    if (loadError != null) {
        DataRecoveryDialog(
            error = loadError,
            onRetry = ::refresh,
            onCreateFresh = viewModel::createFreshDatabase,
        )
        return
    }
    val uiState = uiStateResult.getOrThrow()
    var scrollToExerciseId by remember(uiState.activeTrainingPlanId) { mutableStateOf<Long?>(null) }
    val lastTraining = remember(uiState.history, uiState.activeTrainingPlanId) { repository.lastTrainingWindow() }
    val activePlan = uiState.trainingPlans.firstOrNull { it.id == uiState.activeTrainingPlanId }

    val selected = uiState.machines.firstOrNull { it.machine.id == selectedId }
    val areaFrequency = remember(uiState.machines) {
        uiState.machines.flatMap { it.areas }.groupBy { it.area }
            .mapValues { (_, values) -> values.sumOf { it.intensity } }
    }
    val areaUsage = remember(uiState.machines) {
        uiState.machines.flatMap { it.areas }.groupBy { it.area }
            .mapValues { (_, assignments) -> assignments.sumOf { it.intensity } }
    }
    LaunchedEffect(screen, selected) {
        if (screen == Screen.DETAIL && selected == null) screen = Screen.OVERVIEW
    }

    fun navigateBack() {
        when (screen) {
            Screen.HISTORY -> screen = historyOrigin
            Screen.DETAIL -> screen = detailOrigin
            Screen.PLANS, Screen.BACKUP, Screen.ORDER -> {
                screen = Screen.OVERVIEW
                showMenu = true
            }
            else -> screen = Screen.OVERVIEW
        }
        if (screen != Screen.DETAIL) selectedId = null
    }

    BackHandler(enabled = screen != Screen.OVERVIEW) {
        navigateBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (screen != Screen.OVERVIEW && screen != Screen.BODY) TopAppBar(
                title = {
                    if (screen == Screen.DETAIL && selected != null) {
                        Column {
                            Text(
                                selected.machine.name,
                                modifier = Modifier.clickable { editingExerciseField = ExerciseEditField.NAME },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                selected.machine.titleNote.ifBlank { "Add a title note" },
                                modifier = Modifier.clickable { editingExerciseField = ExerciseEditField.TITLE_NOTE },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (selected.machine.titleNote.isBlank()) .7f else 1f),
                            )
                        }
                    } else Text(
                        when (screen) {
                            Screen.OVERVIEW -> ""
                            Screen.BODY -> ""
                            Screen.STATS -> "Stats"
                            Screen.WIP_STATS -> "WIP stats"
                            Screen.CC_STATS -> "CC Stats"
                            Screen.ORDER -> "Exercise Order"
                            Screen.HISTORY -> "History"
                            Screen.BACKUP -> "Data & Backup"
                            Screen.PLANS -> "Training Plans"
                            Screen.DETAIL -> "Exercise"
                        },
                    )
                },
                navigationIcon = {
                    if (screen != Screen.OVERVIEW) TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                        onClick = ::navigateBack,
                    ) { Text("\u2039 Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        when (screen) {
            Screen.OVERVIEW -> OverviewScreen(
                machines = uiState.machines.filterNot { it.machine.id in uiState.hiddenMachineIds },
                hiddenMachines = uiState.machines.filter { it.machine.id in uiState.hiddenMachineIds },
                fatigueExercises = uiState.fatigueExercises,
                activePlanName = activePlan?.name ?: "Training plan",
                activePlanNotes = activePlan?.notes.orEmpty(),
                activePlanNotesPinned = activePlan?.notesPinned == true,
                modifier = Modifier.padding(padding),
                onMachine = {
                    detailOrigin = Screen.OVERVIEW
                    selectedId = it
                    screen = Screen.DETAIL
                },
                onAdd = { showAdd = true },
                onMenu = { showMenu = true },
                onStats = { screen = Screen.STATS },
                onWipStats = { screen = Screen.WIP_STATS },
                onCcStats = { screen = Screen.CC_STATS },
                onBody = { screen = Screen.BODY },
                listState = overviewListState,
                scrollToExerciseId = scrollToExerciseId,
                onExerciseScrollHandled = { scrollToExerciseId = null },
                showHidden = showHiddenExercises,
                onShowHiddenChanged = {
                    showHiddenExercises = it
                    repository.setShowHiddenExercises(it)
                },
                onTrainingHeader = { showTrainingHeader = true },
                onEditPlanNotes = { showPlanNotes = true },
                areaFrequency = areaFrequency,
                onQuickSet = { machine, reps ->
                    scrollToExerciseId = machine.machine.id.takeIf { reps.any { it > 0 } }
                    mutate {
                        if (isDirectMetric(machine.machine.trackingMode)) {
                            reps.firstOrNull()?.let { repository.addTrainingMinutes(machine.machine.id, it) }
                        } else {
                            repository.addSets(machine.machine.id, reps, machine.machine.currentWeight)
                        }
                    }
                },
                onDeleteCurrentExerciseSets = { machine -> deleteWithUndo("Current ${machine.machine.name} entries deleted") { repository.deleteCurrentExerciseSetsWithJournal(machine.machine.id) } },
                onMetricChange = { machine, value -> mutate { repository.changeWeight(machine.machine.id, value) } },
                onMetricChangeForSession = { machine, value ->
                    mutate { repository.changeMetricForCurrentTrainingUnit(machine.machine.id, value) }
                },
                progressionSettings = uiState.progressionSettings,
                fatigueBands = uiState.fatigueBands,
                priorDayFatigue = uiState.priorDayFatigue,
            )
            Screen.BODY -> BodyOverviewScreen(
                snapshot = bodyData,
                onAddWeight = { showAddBody = true },
                syncStatus = healthSyncStatus,
                isSyncing = healthSyncing,
                modifier = Modifier.padding(padding),
                onSync = onHealthSync,
                onAutoSync = onAutoHealthSync,
                onGym = { screen = Screen.OVERVIEW },
            )
            Screen.HISTORY -> HistoryScreen(
                history = uiState.history,
                modifier = Modifier.padding(padding),
                onUpdateSet = { id, reps, weight -> mutate { repository.updateSet(id, reps, weight) } },
                onAddSet = { machineId, reps, weight, performedAt ->
                    mutate { repository.addHistoricalSet(machineId, reps, weight, performedAt) }
                },
                onDeleteSet = { id -> deleteWithUndo("Set deleted") { repository.deleteSetWithJournal(id) } },
                onDeleteExerciseDay = { machineId, start, end -> deleteWithUndo("Exercise entries deleted") { repository.deleteExerciseDayWithJournal(machineId, start, end) } },
                onDeleteTrainingDay = { start, end -> deleteWithUndo("Training day deleted") { repository.deleteTrainingDayWithJournal(start, end) } },
            )
            Screen.STATS -> StatsScreen(
                history = uiState.history,
                machines = uiState.machines,
                modifier = Modifier.padding(padding),
                onHistory = { historyOrigin = Screen.STATS; screen = Screen.HISTORY },
            )
            Screen.CC_STATS -> CcStatsScreen(
                machines = uiState.machines,
                bodyData = bodyData,
                modifier = Modifier.padding(padding),
                onMachine = { id ->
                    detailOrigin = Screen.CC_STATS
                    selectedId = id
                    screen = Screen.DETAIL
                },
            )
            Screen.WIP_STATS -> WipStatsScreen(
                machines = uiState.machines,
                hiddenIds = uiState.hiddenMachineIds,
                planName = activePlan?.name ?: "Training plan",
                progressionSettings = uiState.progressionSettings,
                modifier = Modifier.padding(padding),
                onHistory = { historyOrigin = Screen.WIP_STATS; screen = Screen.HISTORY },
                onMachine = { id ->
                    detailOrigin = Screen.WIP_STATS
                    selectedId = id
                    screen = Screen.DETAIL
                },
            )
            Screen.ORDER -> OrderScreen(
                machines = uiState.machines,
                fatigueExercises = uiState.fatigueExercises,
                fatigueBands = uiState.fatigueBands,
                progressionSettings = uiState.progressionSettings,
                onFatigueBands = { bands ->
                    repository.saveFatigueBands(bands)
                    viewModel.replace(Result.success(uiState.copy(fatigueBands = bands)))
                },
                priorDayFatigue = uiState.priorDayFatigue,
                onPriorDayFatigue = { prior ->
                    repository.savePriorDayFatigue(prior)
                    viewModel.replace(Result.success(uiState.copy(priorDayFatigue = prior)))
                },
                modifier = Modifier.padding(padding),
                onMove = { id, direction -> mutate { repository.moveMachineWithinOrderGroup(id, direction) } },
                onGroup = { id, group -> mutate { repository.setExerciseOrderGroup(id, group) } },
                onPriority = { id, delta -> mutate { repository.changeRankingPriority(id, delta) } },
            )
            Screen.PLANS -> TrainingPlansScreen(
                plans = uiState.trainingPlans,
                activePlanId = uiState.activeTrainingPlanId,
                modifier = Modifier.padding(padding),
                onOpen = {
                    mutate { repository.setActiveTrainingPlan(it) }
                    screen = Screen.OVERVIEW
                },
                onCreate = {
                    mutate { repository.createTrainingPlan(it) }
                    screen = Screen.OVERVIEW
                },
                onRename = { id, name -> mutate { repository.renameTrainingPlan(id, name) } },
                onDelete = { id -> deleteWithUndo("Training plan deleted") { repository.deleteTrainingPlanWithJournal(id) } },
                onDuplicate = { mutate { repository.duplicateTrainingPlan(it) } },
                onCreateDemo = {
                    mutate { repository.createDemoTrainingPlan() }
                    screen = Screen.OVERVIEW
                },
            )
            Screen.BACKUP -> BackupScreen(
                modifier = Modifier.padding(padding),
                automaticBackupEnabled = uiState.automaticBackupEnabled,
                syncStatus = uiState.backupSyncStatus,
                onAutomaticBackupChanged = {
                    repository.setAutomaticBackupEnabled(it)
                    refresh()
                },
                onExport = onExport,
                onImport = { confirmImport = true },
            )
            Screen.DETAIL -> selected?.let { summary ->
                DetailScreen(
                    summary = summary,
                    areaUsage = areaUsage,
                    areaColors = uiState.areaColors,
                    modifier = Modifier.padding(padding),
                    repository = repository,
                    onAreaColor = { area, color -> mutate { repository.setAreaColor(area, color) } },
                    onChanged = ::refresh,
                    write = ::mutate,
                    onTrainingLogged = { scrollToExerciseId = summary.machine.id },
                    onEditDetails = { editingExerciseField = it },
                    isHidden = summary.machine.id in uiState.hiddenMachineIds,
                    onDeleteSets = { sets -> deleteWithUndo("${sets.size} set${if (sets.size == 1) "" else "s"} deleted") { repository.deleteSetsWithJournal(sets.map { it.id }) } },
                    onDeleteExercise = { deleteWithUndo("${summary.machine.name} deleted") { repository.deleteMachineWithJournal(summary.machine.id) } },
                    onDeleted = {
                        refresh()
                        screen = detailOrigin
                        selectedId = null
                    },
                )
            }
        }
    }

    if (showAdd) AddMachineDialog(
        onDismiss = { showAdd = false },
        onAdd = { name, weight, trackingMode, note, areas ->
            showAdd = false
            viewModel.mutate({ repository.addMachine(name, weight, trackingMode, note, areas) }) { id ->
                selectedId = id
                screen = Screen.DETAIL
            }
        },
    )

    if (showMenu) AlertDialog(
        onDismissRequest = { showMenu = false },
        title = { Text("Menu") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Card(
                    Modifier.fillMaxWidth().clickable {
                        showMenu = false
                        showPlanNotes = true
                    },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Training Plan Notes", style = MaterialTheme.typography.labelLarge)
                        Text(
                            activePlan?.notes?.ifBlank { "Tap to add a note." } ?: "No active training plan.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                TextButton(onClick = { showMenu = false; screen = Screen.PLANS }) { Text("Training Plans") }
                TextButton(onClick = { showMenu = false; screen = Screen.ORDER }) { Text("Exercise Order") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showMenu = false; showTrainingMetricHelp = true },
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Explain volume and strength score" },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("Use strength score instead of volume", style = MaterialTheme.typography.bodyMedium) }
                    Switch(checked = uiState.progressionSettings.useOneRepMax, onCheckedChange = { enabled ->
                        val updated = uiState.progressionSettings.copy(useOneRepMax = enabled)
                        repository.saveProgressionSettings(updated)
                        viewModel.replace(Result.success(uiState.copy(progressionSettings = updated)))
                    })
                }
                TextButton(onClick = { showMenu = false; showProgressionSettings = true }) { Text("Progress Warnings") }
                TextButton(onClick = { showMenu = false; screen = Screen.BACKUP }) { Text("Data, Backup & Sync") }
                TextButton(onClick = { showMenu = false; showAccentColor = true }) { Text("UI Color") }
                TextButton(onClick = { showMenu = false; showHelp = true }) { Text("Help & Explanations") }
                TextButton(onClick = { showMenu = false; confirmDeleteLastTraining = true }, enabled = lastTraining != null) {
                    Text("Delete Last Training", color = MaterialTheme.colorScheme.error)
                }
                androidx.compose.material3.HorizontalDivider()
                Text(
                    "Version ${BuildConfig.VERSION_NAME} \u00B7 Built ${BuildConfig.BUILD_TIME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Close") } },
    )

    if (showTrainingMetricHelp) TrainingMetricExplanationDialog {
        showTrainingMetricHelp = false
        showMenu = true
    }

    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false; showMenu = true },
        title = { Text("Help & Explanations") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Topics start collapsed so the whole list fits on one screen; tap one to read it.
                var openTopics by remember { mutableStateOf(emptySet<String>()) }
                BodyHint("Tap a topic to expand it.")
                HelpTopic("Volume or strength score", "Volume or strength score" in openTopics, { openTopics = if ("Volume or strength score" in openTopics) openTopics - "Volume or strength score" else openTopics + "Volume or strength score" }) {
                    Text("Choose Use strength score instead of volume with the switch in Menu. Tap its outlined label for details. " + TRAINING_METRIC_EXPLANATION)
                }
                HelpTopic("Progress Warnings & Overrides", "Progress Warnings & Overrides" in openTopics, { openTopics = if ("Progress Warnings & Overrides" in openTopics) openTopics - "Progress Warnings & Overrides" else openTopics + "Progress Warnings & Overrides" }) {
                    Text("Warnings show days without a kg increase (default 60 days) and growth in all-set strength score. The trend fits a straight line through every training day within the last X days, including today (default 60 days), and reports its change from the first to the latest day, so a single unusual day does not decide the warning. The Menu metric switch only changes charts and entry summaries. Blank or 0 disables the corresponding warning; either a blank/0 window or minimum percentage disables the trend warning. At least 2 training days in that period are needed.")
                    Text("Every exercise has Progress Warnings in its details. Follow global warning settings uses future global changes. Turn it off for custom thresholds. Trend warnings always use the all-set strength score formula; there is no per-exercise metric setting. Blank custom fields disable warnings rather than inheriting global values. Warnings apply to weight exercises.")
                }
                HelpTopic("Exercise Ordering & Outlines", "Exercise Ordering & Outlines" in openTopics, { openTopics = if ("Exercise Ordering & Outlines" in openTopics) openTopics - "Exercise Ordering & Outlines" else openTopics + "Exercise Ordering & Outlines" }) {
                    Text("Open Exercise Order from Menu. The current exercise stays first with a blue outline. Auto groups unfinished exercises into four configurable color bins. All distinct exercises in this training contribute with decreasing influence (neutral regions: 100%, 50%, 25%, 12.5%, then a 10% floor). Planning factors keep biceps/triceps overlap slightly longer (1.1), quads/hamstrings slightly shorter (0.9), core/abs 20% slower to recover (factor 1.25), and other regions neutral (1.0). These are heuristics, not measured recovery times. Preset Core/abs percentages are 50% higher; saved profiles keep their editable percentages, without dividing by exercise count. By default yesterday adds 40% per exercise, two days ago 20%, and three days ago 10%; change these in Exercise Order (0 turns a day off). Contributions from exercises across all plans, including hidden exercises, accumulate once per training day without order weighting. The score uses the strongest intensity-weighted region, so adding regions cannot dilute an existing clash. Choose the green/yellow/orange/red boundaries in Exercise Order. Defaults switch at 25%, 50% and 75%; Auto priority and warning/frequency bias stay within those color groups. Completed exercises have no overlap outline. Missing profiles count as zero overlap, so green can also mean an unconfigured profile.")
                    Text("Within each overlap bin, higher Auto priority comes first (+ moves earlier, - moves later), then a small combined boost for one or two active warnings and fewer recent training days. The frequency boost is capped and disappears after three training days in the last two calendar months. Unfinished manual top and bottom use your arrow order; completed exercises follow from first to last done. Exercises across all plans, including hidden exercises, contribute recent training context.")
                }
                HelpTopic("Body Regions & Colors", "Body Regions & Colors" in openTopics, { openTopics = if ("Body Regions & Colors" in openTopics) openTopics - "Body Regions & Colors" else openTopics + "Body Regions & Colors" }) {
                    Text("Choose L (25%), M (50%), H (100%), or type a custom region percentage in exercise details. Zero removes the region. Marker widths follow the percentage. Preset Core/abs values are 50% higher; existing saved profiles are unchanged. English/German exercise suggestions provide editable starting profiles. These are planning estimates, not recovery measurements. The body-region guide's Show/Hide choice is remembered for every exercise, even when profile edits are cancelled.")
                }
                HelpTopic("Training Days & Time", "Training Days & Time" in openTopics, { openTopics = if ("Training Days & Time" in openTopics) openTopics - "Training Days & Time" else openTopics + "Training Days & Time" }) {
                    Text("A training day runs from 06:00 to 05:59 the next day. The overview timer estimates your training duration and becomes completed after 30 minutes without an entry when the estimated duration at the last entry is under one hour; otherwise after 10 minutes. Stats duration uses the first and last recorded entry; a single entry has 0 minutes between them. Overview history shows minutes without a suffix; Lv. means level, and W means watts.")
                }
                HelpTopic("Stats", "Stats" in openTopics, { openTopics = if ("Stats" in openTopics) openTopics - "Stats" else openTopics + "Stats" }) {
                    Text("Weight-increase counters compare recorded training loads; editing the next weight alone does not count. A higher next-session weight hides the days-without-increase warning until you log it or revert it; the recorded-history counter and Strength score trend stay unchanged. Body Area Training counts each exercise once per training day, weighted High = 1, Medium = 0.5 and Low = 0.25. Cards marked last 30 days keep that fixed window.")
                }
                HelpTopic("WIP stats", "WIP stats" in openTopics, { openTopics = if ("WIP stats" in openTopics) openTopics - "WIP stats" else openTopics + "WIP stats" }) {
                    Text("WIP stats is a separate dashboard beside Stats. It compares equal calendar periods, tracks weekly goals, and offers a tappable training calendar, exercise charts and records. Its totals follow the selected interval and hidden-exercise switch. Tap How measured for the calculation rules.")
                    Text("The strength explorer uses all-set strength score, while level and watts use estimated intensity for a 10-minute effort. The Riegel-style estimate is intensity x (minutes / 10)^0.06, taking the best entry. This is a fixed heuristic, not a fitted model; level comparisons assume a linear scale on the same machine. Level changes are estimated level steps, not percentages. Tap the exercise chart's metric legend for its mode-specific calculation and limitations. Level and watt charts show the estimated 10-minute measure on the left and recorded intensity on the right; minutes use an independent, unlabelled scale across the plot height with no trend fit. New settings default to Strength score; saved choices are preserved.")
                }
                HelpTopic("Body Plot Settings", "Body Plot Settings" in openTopics, { openTopics = if ("Body Plot Settings" in openTopics) openTopics - "Body Plot Settings" else openTopics + "Body Plot Settings" }) {
                    Text("Body Settings controls global plot defaults. Order and hide plots saves your plot order and visibility; reset restores Weight, Muscle Mass, Fat Mass, Fat %, then Muscle Mass %. Tap an individual plot for overrides; Follow global switches choose which settings inherit future global changes. Raw data and two trailing moving averages can be toggled independently: one uses a period in days, the other a percentage of the displayed interval. Both averages can be overlaid. Fits use original measurements. Blank Y-axis limits use automatic scaling. Targets and acceptance ranges belong to each plot; the global target switches control visibility. Show goal - acceptance and Show goal + acceptance independently control the lower and upper dashed boundary lines and their shaded halves of the acceptance band.")
                }
                HelpTopic("Body Reference Lines", "Body Reference Lines" in openTopics, { openTopics = if ("Body Reference Lines" in openTopics) openTopics - "Body Reference Lines" else openTopics + "Body Reference Lines" }) {
                    Text("Blue marks raw data, white the fixed-period moving average, pink the interval-fraction moving average, yellow the fit, green the target, purple all-time low/mean/high and orange history references. Reference text names All-time or the history interval, such as 8wk history. Each reference has its own visibility switch; means are dashed. History low/high default to the 5th/95th percentiles; choose a cutoff from 0 to 50%. A nonzero cutoff appears in the labels; 0 uses exact low/high. All-time records remain exact. History can follow the plot interval or use a separate window. Means and fits always use raw measurements.")
                }
                HelpTopic("Health Connect", "Health Connect" in openTopics, { openTopics = if ("Health Connect" in openTopics) openTopics - "Health Connect" else openTopics + "Health Connect" }) {
                    Text("Sync Health Connect is at the top of Body Settings, with status and last sync time. Body syncs on entry, on returning to the foreground, and every minute while visible, using existing permissions. Its refresh timer stops when you leave Body or background the app. Outside Body, automatic refresh on resume reuses a successful sync for 15 minutes. The Settings button requests a fresh sync and can request permissions. Body data and plot settings are independent of training plans.")
                }
                HelpTopic("History, Hidden Exercises & Undo", "History, Hidden Exercises & Undo" in openTopics, { openTopics = if ("History, Hidden Exercises & Undo" in openTopics) openTopics - "History, Hidden Exercises & Undo" else openTopics + "History, Hidden Exercises & Undo" }) {
                    Text("Calendar & History lets you edit or delete individual entries, an exercise's day or a whole training day. Hiding an exercise retains its records; use Show hidden exercises to find it again. When an Undo message appears after a deletion, tap Undo while it is visible to restore the data.")
                }
                HelpTopic("Backups", "Backups" in openTopics, { openTopics = if ("Backups" in openTopics) openTopics - "Backups" else openTopics + "Backups" }) {
                    Text("Data, Backup & Sync provides JSON export and restore. Restoring replaces the current workout data. Backups preserve exercise warning overrides, trend visibility, rep-wheel layouts and the Volume/Strength score choice. Undo is offered only after a successful backup restore; cancelling the file picker leaves data unchanged. Export a backup before moving devices or replacing data.")
                }
                HelpTopic("Charts", "Charts" in openTopics, { openTopics = if ("Charts" in openTopics) openTopics - "Charts" else openTopics + "Charts" }) {
                    Text("Tap a point in any chart to see its date and exact value; when several series share that moment, each value is shown in its series color. Tap the point again to hide it. Taps away from points still open the plot settings or enlarge the chart as before.")
                }
                HelpTopic("CC Stats", "CC Stats" in openTopics, { openTopics = if ("CC Stats" in openTopics) openTopics - "CC Stats" else openTopics + "CC Stats" }) {
                    Text("CC Stats summarizes consistency (trainings, week streak, weekly and weekday rhythm), strength momentum per weight exercise with the next PR load, recent records, four-week muscle balance, the push/pull/legs/core split, session length, rep ranges and strength relative to body weight. Each card has a ? with its calculation.")
                }
            }
        },
        confirmButton = { TextButton(onClick = { showHelp = false; showMenu = true }) { Text("Close") } },
    )

    if (confirmDeleteLastTraining) AlertDialog(
        onDismissRequest = { confirmDeleteLastTraining = false; showMenu = true },
        title = { Text("Delete Last Training?") },
        text = { Text("Delete all entries from the latest recorded training day in this plan (${lastTraining?.let { formatTimestamp(it.startInclusive) } ?: "none"})? You can undo this afterwards.") },
        confirmButton = {
            Button(onClick = {
                deleteWithUndo("Last training deleted") { repository.deleteLastTrainingWithJournal(lastTraining) }
                confirmDeleteLastTraining = false
            }) { Text("Delete Training") }
        },
        dismissButton = { TextButton(onClick = { confirmDeleteLastTraining = false; showMenu = true }) { Text("Cancel") } },
    )

    if (showAddBody) AddBodyMeasurementDialog(onDismiss = { showAddBody = false }, onSave = onSaveBodyEntry)

    if (showPlanNotes && activePlan != null) TrainingPlanNotesDialog(
        notes = activePlan.notes,
        pinned = activePlan.notesPinned,
        onDismiss = { showPlanNotes = false; showMenu = true },
        onSave = { notes, pinned ->
            mutate { repository.saveTrainingPlanNotes(activePlan.id, notes, pinned) }
            showPlanNotes = false
            showMenu = true
        },
    )

    if (showTrainingHeader && activePlan != null) TrainingHeaderDialog(
        activePlan = activePlan,
        plans = uiState.trainingPlans,
        onCreate = { name ->
            mutate { repository.createTrainingPlan(name) }
            showTrainingHeader = false
        },
        onExport = onExportPlan, onImport = onImportPlan,
        onDismiss = { showTrainingHeader = false },
        onSave = { name, notes, pinned ->
            mutate {
                repository.renameTrainingPlan(activePlan.id, name)
                repository.saveTrainingPlanNotes(activePlan.id, notes, pinned)
            }
            showTrainingHeader = false
        },
        onSwitch = { planId ->
            mutate { repository.setActiveTrainingPlan(planId) }
            showTrainingHeader = false
        },
    )

    if (showProgressionSettings) ProgressionSettingsDialog(
        current = uiState.progressionSettings,
        onDismiss = { showProgressionSettings = false; showMenu = true },
        onSave = {
            repository.saveProgressionSettings(it)
            viewModel.replace(Result.success(uiState.copy(progressionSettings = it)))
            showProgressionSettings = false
            showMenu = true
        },
    )

    if (showAccentColor) AccentColorDialog(
        current = repository.accentColor(),
        onDismiss = { showAccentColor = false; showMenu = true },
        onSave = {
            onAccentColorChanged(it)
            showAccentColor = false
            showMenu = true
        },
    )

    editingExerciseField?.let { focus ->
        selected?.machine?.let { machine ->
            ExerciseDetailsDialog(
                machineName = machine.name,
                titleNote = machine.titleNote,
                overviewNote = machine.notes,
                initialFocus = focus,
                onDismiss = { editingExerciseField = null },
                onSave = { name, titleNote, overviewNote ->
                    mutate { repository.updateMachine(machine.id, name, titleNote, overviewNote, machine.pauseTime) }
                    editingExerciseField = null
                },
            )
        }
    }

    if (confirmImport) AlertDialog(
        onDismissRequest = { confirmImport = false },
        title = { Text("Restore Backup?") },
        text = { Text("Restoring replaces all current GymLog data. Export a backup first if you want to keep it.") },
        confirmButton = {
            Button(onClick = {
                confirmImport = false
                onImport { restored -> if (restored != null) offerRestoreUndo(restored) }
            }) { Text("Choose Backup") }
        },
        dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ExerciseDetailsDialog(
    machineName: String,
    titleNote: String,
    overviewNote: String,
    initialFocus: ExerciseEditField,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by remember(machineName) { mutableStateOf(machineName) }
    var title by remember(titleNote) { mutableStateOf(titleNote) }
    var note by remember(overviewNote) { mutableStateOf(overviewNote) }
    val nameFocus = remember { FocusRequester() }
    val titleFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    LaunchedEffect(initialFocus) {
        when (initialFocus) {
            ExerciseEditField.NAME -> nameFocus
            ExerciseEditField.TITLE_NOTE -> titleFocus
            ExerciseEditField.OVERVIEW_NOTE -> noteFocus
        }.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    Modifier.fillMaxWidth().focusRequester(nameFocus),
                    label = { Text("Exercise Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    title,
                    { title = it },
                    Modifier.fillMaxWidth().focusRequester(titleFocus),
                    label = { Text("Title Note") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    note,
                    { note = it },
                    Modifier.fillMaxWidth().focusRequester(noteFocus),
                    label = { Text("Note") },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), title.trim(), note.trim()) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DataRecoveryDialog(
    error: Throwable,
    onRetry: () -> Unit,
    onCreateFresh: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Delete existing GymLog data?") },
            text = { Text("This permanently deletes the preserved database. Keep a copy or a JSON backup before continuing.") },
            confirmButton = { Button(onClick = { confirmReset = false; onCreateFresh() }) { Text("Delete and Create New") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("GymLog data could not be opened") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The local database may be from an incompatible app version or may be damaged. Its existing data has been kept.")
                Text("Creating a new database permanently removes the GymLog data stored in this app. Manual JSON backup files are not deleted.")
                error.message?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "Technical details: ${it.take(240)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onRetry) { Text("Try Again") } },
        dismissButton = { TextButton(onClick = { confirmReset = true }) { Text("Create New Database") } },
    )
}

@Composable
internal fun FatigueBandSettings(current: FatigueBands, onSave: (FatigueBands) -> Unit) {
    var yellow by remember(current) { mutableStateOf(current.yellowFrom.toString()) }
    var orange by remember(current) { mutableStateOf(current.orangeFrom.toString()) }
    var red by remember(current) { mutableStateOf(current.redFrom.toString()) }
    val draft = parseFatigueBands(yellow, orange, red)
    val preview = draft ?: current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Fatigue color boundaries", style = MaterialTheme.typography.titleSmall)
            Text("Each color starts at its percentage. Auto bias stays within a color group.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple("Yellow (%)", yellow, { value: String -> yellow = value }),
                    Triple("Orange (%)", orange, { value: String -> orange = value }),
                    Triple("Red (%)", red, { value: String -> red = value })).forEach { (label, value, update) ->
                    OutlinedTextField(value, { update(it.filter(Char::isDigit)) }, Modifier.weight(1f),
                        label = { Text(label) }, singleLine = true, isError = draft == null,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                }
            }
            if (draft == null) Text("Use increasing values from 1 to 100.", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth()) {
                listOf("Green\n0-${preview.yellowFrom - 1}%" to 0,
                    "Yellow\n${preview.yellowFrom}-${preview.orangeFrom - 1}%" to preview.yellowFrom,
                    "Orange\n${preview.orangeFrom}-${preview.redFrom - 1}%" to preview.orangeFrom,
                    "Red\n${preview.redFrom}-100%" to preview.redFrom).forEach { (label, score) ->
                    Text(label, Modifier.weight(1f), color = overlapOutlineColor(score, preview),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Row {
                TextButton(onClick = {
                    val defaults = FatigueBands()
                    yellow = defaults.yellowFrom.toString(); orange = defaults.orangeFrom.toString(); red = defaults.redFrom.toString()
                }) { Text("Default colors") }
                Button(onClick = { draft?.let(onSave) }, enabled = draft != null && draft != current) { Text("Save colors") }
            }
        }
    }
}

@Composable
private fun BodyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One collapsible help entry: a tappable header with a chevron, and its explanation when open. */
@Composable
private fun HelpTopic(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(if (expanded) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                content()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
internal fun PriorDayFatigueSettings(current: PriorDayFatigue, onSave: (PriorDayFatigue) -> Unit) {
    var yesterday by remember(current) { mutableStateOf(current.yesterday.toString()) }
    var twoDays by remember(current) { mutableStateOf(current.twoDaysAgo.toString()) }
    var threeDays by remember(current) { mutableStateOf(current.threeDaysAgo.toString()) }
    val draft = parsePriorDayFatigue(yesterday, twoDays, threeDays)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Fatigue from previous days", style = MaterialTheme.typography.titleSmall)
            Text("Each exercise from an earlier training day adds this share of a full exercise to its regions. 0 ignores that day.",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple("Yesterday (%)", yesterday, { value: String -> yesterday = value }),
                    Triple("2 days (%)", twoDays, { value: String -> twoDays = value }),
                    Triple("3 days (%)", threeDays, { value: String -> threeDays = value })).forEach { (label, value, update) ->
                    OutlinedTextField(value, { update(it.filter(Char::isDigit)) }, Modifier.weight(1f),
                        label = { Text(label) }, singleLine = true, isError = draft == null,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                }
            }
            if (draft == null) Text("Use values from 0 to 100.", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = {
                    val defaults = PriorDayFatigue()
                    yesterday = defaults.yesterday.toString(); twoDays = defaults.twoDaysAgo.toString(); threeDays = defaults.threeDaysAgo.toString()
                }) { Text("Default days") }
                Button(onClick = { draft?.let(onSave) }, enabled = draft != null && draft != current) { Text("Save days") }
            }
        }
    }
}

@Composable
private fun OrderScreen(
    machines: List<MachineSummary>,
    fatigueExercises: List<MachineSummary>,
    fatigueBands: FatigueBands,
    progressionSettings: ProgressionSettings,
    onFatigueBands: (FatigueBands) -> Unit,
    priorDayFatigue: PriorDayFatigue,
    onPriorDayFatigue: (PriorDayFatigue) -> Unit,
    modifier: Modifier = Modifier,
    onMove: (Long, Int) -> Unit,
    onGroup: (Long, ExerciseOrderGroup) -> Unit,
    onPriority: (Long, Int) -> Unit,
) {
    val now = System.currentTimeMillis()
    val clock = com.gymlog.app.data.GymClock()
    val cutoff = clock.trainingDayStart(now)
    // Ranking walks every exercise's history; recompute it only when its inputs change, not on every redraw.
    val ranked = remember(machines, fatigueExercises, fatigueBands, priorDayFatigue, progressionSettings, cutoff) {
        rankOverviewMachines(machines, cutoff, recentTrainingExercises(fatigueExercises, cutoff),
            zone = clock.zoneId, now = now,
            prior = com.gymlog.app.data.priorExerciseExposures(fatigueExercises, now, clock.zoneId, priorDayFatigue),
            progressionSettings = progressionSettings, fatigueBands = fatigueBands)
    }
    var showSortingHelp by rememberSaveable { mutableStateOf(false) }
    val groups = listOf(
        ExerciseOrderGroup.MANUAL_TOP to "Manual top",
        ExerciseOrderGroup.AUTOMATIC to "Auto sorted",
        ExerciseOrderGroup.MANUAL_BOTTOM to "Manual bottom",
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { FatigueBandSettings(fatigueBands, onFatigueBands) }
        item { PriorDayFatigueSettings(priorDayFatigue, onPriorDayFatigue) }
        item {
            // The full rules are long; keep them one tap away so the exercise list starts near the top.
            TextButton(onClick = { showSortingHelp = !showSortingHelp }) {
                Text(if (showSortingHelp) "Hide how sorting works ▴" else "How sorting works ▾")
            }
        }
        if (showSortingHelp) item {
            Text(
                "The current exercise stays first, then unfinished Manual top in your arrow order. Auto uses four configurable color bins, green, yellow, orange and red. All distinct exercises in this training contribute with decreasing influence (neutral regions: 100%, 50%, 25%, 12.5%, then a 10% floor). Planning factors keep biceps/triceps overlap slightly longer (1.1), quads/hamstrings slightly shorter (0.9), core/abs 20% slower to recover (factor 1.25), and other regions neutral (1.0). These are heuristics, not measured recovery times. Preset Core/abs percentages are 50% higher; saved profiles keep their editable percentages, without dividing by exercise count. By default yesterday adds 40% per exercise, two days ago 20%, and three days ago 10%; change these in Exercise Order (0 turns a day off). Contributions from exercises across all plans, including hidden exercises, accumulate once per training day without order weighting. The score uses the strongest intensity-weighted region, so adding regions cannot dilute an existing clash. Within each bin, higher Auto priority comes first (+ moves earlier, - moves later), then a small combined boost for one or two active warnings and fewer recent training days. The frequency boost is capped and disappears after three training days in the last two calendar months. Unknown overlap counts as zero. Unfinished Manual bottom keeps your arrow order, followed by completed exercises from first to last done.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        groups.forEach { (group, title) ->
            val entries = if (group == ExerciseOrderGroup.AUTOMATIC) {
                ranked.filter { it.machine.orderGroup == group }
            } else {
                machines.filter { it.machine.orderGroup == group }
                    .sortedWith(compareBy<MachineSummary> { it.machine.sortOrder }.thenBy { it.machine.id })
            }
            item(key = "header-$group") {
                Text("$title (${entries.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
            }
            itemsIndexed(entries, key = { _, item -> item.machine.id }) { index, item ->
                val machine = item.machine
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(machine.name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            if (group != ExerciseOrderGroup.AUTOMATIC) {
                                TextButton(onClick = { onMove(machine.id, -1) }, enabled = index > 0, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) { Text("\u25B2") }
                                TextButton(onClick = { onMove(machine.id, 1) }, enabled = index < entries.lastIndex, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) { Text("\u25BC") }
                            } else {
                                Text("Auto priority ${if (machine.rankingPriority < 0) "+" else ""}${-machine.rankingPriority}", style = MaterialTheme.typography.labelSmall)
                                TextButton(onClick = { onPriority(machine.id, 1) }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) { Text("\u2212") }
                                TextButton(onClick = { onPriority(machine.id, -1) }, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)) { Text("+") }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            groups.forEach { (choice, choiceTitle) ->
                                Row(Modifier.weight(1f).clickable { onGroup(machine.id, choice) }, verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = group == choice, onClick = { onGroup(machine.id, choice) }, modifier = Modifier.size(30.dp))
                                    Text(choiceTitle.removePrefix("Manual ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupScreen(
    modifier: Modifier = Modifier,
    automaticBackupEnabled: Boolean,
    syncStatus: BackupSyncStatus,
    onAutomaticBackupChanged: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Backup & Sync", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    when {
                        !syncStatus.automaticBackupEnabled -> "Not synced"
                        syncStatus.lastBackupAt == null -> "Waiting for first Android backup"
                        syncStatus.hasChangesSinceBackup -> "Changes waiting for backup"
                        else -> "Backed up"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        !syncStatus.automaticBackupEnabled -> MaterialTheme.colorScheme.error
                        syncStatus.hasChangesSinceBackup -> MaterialTheme.colorScheme.tertiary
                        else -> Color(0xFF66BB6A)
                    },
                )
                Text(
                    syncStatus.lastBackupAt?.let { "Last Android backup: ${formatTimestamp(it)}" }
                        ?: "No completed Android backup has been recorded yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Android Automatic Backup", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (automaticBackupEnabled) "On \u00B7 included in future Android backups" else "Off \u00B7 future Android backups skip GymLog data",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = automaticBackupEnabled, onCheckedChange = onAutomaticBackupChanged)
        }
        Text(
            "This is not live sync. Android does not let apps force a full cloud backup; it decides when one runs. You can open the system backup page to check that device backup is enabled.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.OutlinedButton(
            onClick = {
                // Android Settings registers this action with the com.android.settings prefix.
                runCatching { context.startActivity(Intent("com.android.settings.BACKUP_SETTINGS")) }
                    .recoverCatching {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        android.widget.Toast.makeText(context, "Search for Backup in Android Settings.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    .onFailure {
                        android.widget.Toast.makeText(context, "Could not open Android Settings. Open Settings and search for Backup.", android.widget.Toast.LENGTH_LONG).show()
                    }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open Android Backup Settings") }
        Text("Manual File Backup", style = MaterialTheme.typography.titleMedium)
        Text("Export creates a readable JSON copy containing every plan, exercise, note, body area, metric change, and set. Choose Google Drive in the file picker if you want to move it between phones yourself.")
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Download Backup") }
        androidx.compose.material3.OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Restore from Backup") }
        Text("Restoring a manual file replaces the data currently on this phone. Automatic Android backup is unaffected by manual export and restore.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccentColorDialog(
    current: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var red by remember(current) { mutableStateOf(android.graphics.Color.red(current.toInt()).toFloat()) }
    var green by remember(current) { mutableStateOf(android.graphics.Color.green(current.toInt()).toFloat()) }
    var blue by remember(current) { mutableStateOf(android.graphics.Color.blue(current.toInt()).toFloat()) }
    val selected = 0xFF000000L or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("UI Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).background(Color(selected), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)))
                Text("Red ${red.toInt()}", style = MaterialTheme.typography.labelMedium)
                androidx.compose.material3.Slider(
                    value = red,
                    onValueChange = { red = it },
                    valueRange = 0f..255f,
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red, inactiveTrackColor = Color.Red.copy(alpha = .22f)),
                )
                Text("Green ${green.toInt()}", style = MaterialTheme.typography.labelMedium)
                androidx.compose.material3.Slider(
                    value = green,
                    onValueChange = { green = it },
                    valueRange = 0f..255f,
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color(0xFF00A040), activeTrackColor = Color(0xFF00A040), inactiveTrackColor = Color(0xFF00A040).copy(alpha = .22f)),
                )
                Text("Blue ${blue.toInt()}", style = MaterialTheme.typography.labelMedium)
                androidx.compose.material3.Slider(
                    value = blue,
                    onValueChange = { blue = it },
                    valueRange = 0f..255f,
                    colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Color.Blue, activeTrackColor = Color.Blue, inactiveTrackColor = Color.Blue.copy(alpha = .22f)),
                )
                Text("#${selected.toString(16).takeLast(6).uppercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onSave(selected) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun TrainingHeaderDialog(
    activePlan: TrainingPlan,
    plans: List<TrainingPlan>,
    onExport: () -> Unit, onImport: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
    onSwitch: (Long) -> Unit,
    onCreate: (String) -> Unit = {},
) {
    var name by remember(activePlan.id) { mutableStateOf(activePlan.name) }
    var notes by remember(activePlan.id) { mutableStateOf(activePlan.notes) }
    var pinned by remember(activePlan.id) { mutableStateOf(activePlan.notesPinned) }
    var creating by remember { mutableStateOf(false) }
    if (!creating) AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training plan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Training Plan Notes") }, minLines = 2, maxLines = 4)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Pin note on overview", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
                val otherPlans = plans.filter { it.id != activePlan.id }
                if (otherPlans.isNotEmpty()) Text("Switch training plan", style = MaterialTheme.typography.titleSmall)
                otherPlans.forEach { plan ->
                    OutlinedButton(onClick = { onSwitch(plan.id) }, modifier = Modifier.fillMaxWidth()) { Text(plan.name) }
                }
                OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add another training plan")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Download plan") }
                    OutlinedButton(onClick = { onDismiss(); onImport() }, modifier = Modifier.weight(1f)) { Text("Load file") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim(), notes.trim(), pinned) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (creating) PlanNameDialog("Add another training plan", "", { creating = false }) { value ->
        onCreate(value)
        creating = false
    }
}

@Composable
private fun TrainingPlanNotesDialog(
    notes: String,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit,
) {
    var value by remember(notes) { mutableStateOf(notes) }
    var isPinned by remember(pinned) { mutableStateOf(pinned) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training Plan Notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    label = { Text("Training Plan Notes") },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Pin Below Training Time")
                        Text("Show this note on the overview.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = isPinned, onCheckedChange = { isPinned = it })
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(value.trim(), isPinned) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextEditDialog(
    title: String,
    initial: String,
    singleLine: Boolean = false,
    required: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 5,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onSave(value.trim()) }, enabled = !required || value.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatTimestamp(timestamp: Long): String = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
    .format(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()))
