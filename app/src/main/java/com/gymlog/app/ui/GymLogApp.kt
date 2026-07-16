package com.gymlog.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gymlog.app.BuildConfig
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.BackupSyncStatus
import com.gymlog.app.data.GymRepository
import com.gymlog.app.data.HistorySet
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.ProgressionSettings
import com.gymlog.app.data.TrainingPlan

private enum class Screen { OVERVIEW, BODY, STATS, HISTORY, BACKUP, PLANS, DETAIL }

private data class AppUiState(
    val machines: List<MachineSummary>,
    val history: List<HistorySet>,
    val areaColors: Map<BodyArea, Long>,
    val trainingPlans: List<TrainingPlan>,
    val activeTrainingPlanId: Long,
    val progressionSettings: ProgressionSettings,
    val automaticBackupEnabled: Boolean,
    val backupSyncStatus: BackupSyncStatus,
)

private fun GymRepository.loadUiState() = AppUiState(
    machines = loadMachines(),
    history = loadHistory(),
    areaColors = loadAreaColors(),
    trainingPlans = loadTrainingPlans(),
    activeTrainingPlanId = activeTrainingPlanId(),
    progressionSettings = loadProgressionSettings(),
    automaticBackupEnabled = automaticBackupEnabled(),
    backupSyncStatus = backupSyncStatus(),
)

private fun GymRepository.loadUiStateResult(): Result<AppUiState> = try {
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
    onImport: () -> Unit,
    onAccentColorChanged: (Long) -> Unit,
    bodyData: BodyDataSnapshot,
    healthSyncStatus: String,
    onHealthSync: () -> Unit,
) {
    var uiStateResult by remember(repository) { mutableStateOf(repository.loadUiStateResult()) }
    var screen by remember { mutableStateOf(Screen.OVERVIEW) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    var showProgressionSettings by remember { mutableStateOf(false) }
    var showAccentColor by remember { mutableStateOf(false) }
    var showPlanNotes by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var confirmDeleteCurrentTraining by remember { mutableStateOf(false) }
    var showExerciseName by remember { mutableStateOf(false) }
    var showExerciseTitleNote by remember { mutableStateOf(false) }

    fun refresh() {
        uiStateResult = repository.loadUiStateResult()
    }
    LaunchedEffect(externalDataVersion) { refresh() }

    val loadError = uiStateResult.exceptionOrNull()
    if (loadError != null) {
        DataRecoveryDialog(
            error = loadError,
            onRetry = ::refresh,
            onCreateFresh = {
                uiStateResult = try {
                    repository.createFreshDatabase()
                    Result.success(repository.loadUiState())
                } catch (exception: Exception) {
                    Result.failure(exception)
                }
            },
        )
        return
    }
    val uiState = uiStateResult.getOrThrow()
    val activePlan = uiState.trainingPlans.firstOrNull { it.id == uiState.activeTrainingPlanId }

    val selected = uiState.machines.firstOrNull { it.machine.id == selectedId }
    val areaFrequency = remember(uiState.machines) {
        uiState.machines.flatMap { it.areas }.groupBy { it.area }
            .mapValues { (_, values) -> values.sumOf { it.strength.value } }
    }
    val areaUsage = remember(uiState.machines) {
        uiState.machines.flatMap { it.areas }.groupBy { it.area }
            .mapValues { (_, assignments) -> assignments.sumOf { it.strength.value } }
    }
    LaunchedEffect(screen, selected) {
        if (screen == Screen.DETAIL && selected == null) screen = Screen.OVERVIEW
    }

    fun navigateBack() {
        when (screen) {
            Screen.HISTORY -> screen = Screen.STATS
            Screen.PLANS, Screen.BACKUP -> {
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
        topBar = {
            if (screen != Screen.OVERVIEW && screen != Screen.BODY) TopAppBar(
                title = {
                    if (screen == Screen.DETAIL && selected != null) {
                        Column {
                            Text(
                                selected.machine.name,
                                modifier = Modifier.clickable { showExerciseName = true },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                selected.machine.titleNote.ifBlank { "Add a title note" },
                                modifier = Modifier.clickable { showExerciseTitleNote = true },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (selected.machine.titleNote.isBlank()) .7f else 1f),
                            )
                        }
                    } else Text(
                        when (screen) {
                            Screen.OVERVIEW -> ""
                            Screen.BODY -> ""
                            Screen.STATS -> "Stats"
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
                    ) { Text("‹ Back") }
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
                machines = uiState.machines,
                activePlanName = activePlan?.name ?: "Training plan",
                activePlanNotes = activePlan?.notes.orEmpty(),
                activePlanNotesPinned = activePlan?.notesPinned == true,
                modifier = Modifier.padding(padding),
                onMachine = {
                    selectedId = it
                    screen = Screen.DETAIL
                },
                onAdd = { showAdd = true },
                onMenu = { showMenu = true },
                onStats = { screen = Screen.STATS },
                onBody = { screen = Screen.BODY },
                onEditPlanNotes = { showPlanNotes = true },
                areaFrequency = areaFrequency,
                onQuickSet = { machine, reps ->
                    if (isDirectMetric(machine.machine.trackingMode)) {
                        reps.firstOrNull()?.let { repository.addTrainingMinutes(machine.machine.id, it) }
                    } else {
                        repository.addSets(machine.machine.id, reps, machine.machine.currentWeight)
                    }
                    refresh()
                },
                onMetricChange = { machine, value ->
                    repository.changeWeight(machine.machine.id, value)
                    refresh()
                },
                onMetricChangeForSession = { machine, value ->
                    repository.changeMetricForCurrentTrainingUnit(machine.machine.id, value)
                    refresh()
                },
                progressionSettings = uiState.progressionSettings,
                onReorder = { orderedIds ->
                    repository.reorderMachines(orderedIds)
                    refresh()
                },
                onPinnedChange = { machine, pinned ->
                    repository.setMachinePinned(machine.machine.id, pinned)
                    refresh()
                },
            )
            Screen.BODY -> BodyOverviewScreen(
                snapshot = bodyData,
                syncStatus = healthSyncStatus,
                modifier = Modifier.padding(padding),
                onSync = onHealthSync,
                onMenu = { showMenu = true },
                onGym = { screen = Screen.OVERVIEW },
            )
            Screen.HISTORY -> HistoryScreen(
                history = uiState.history,
                modifier = Modifier.padding(padding),
                onUpdateSet = { id, reps, weight -> repository.updateSet(id, reps, weight); refresh() },
                onAddSet = { machineId, reps, weight, performedAt ->
                    repository.addHistoricalSet(machineId, reps, weight, performedAt)
                    refresh()
                },
                onDeleteSet = { repository.deleteSet(it); refresh() },
                onDeleteExerciseDay = { machineId, start, end -> repository.deleteExerciseDay(machineId, start, end); refresh() },
                onDeleteTrainingDay = { start, end -> repository.deleteTrainingDay(start, end); refresh() },
            )
            Screen.STATS -> StatsScreen(
                history = uiState.history,
                machines = uiState.machines,
                modifier = Modifier.padding(padding),
                onHistory = { screen = Screen.HISTORY },
            )
            Screen.PLANS -> TrainingPlansScreen(
                plans = uiState.trainingPlans,
                activePlanId = uiState.activeTrainingPlanId,
                modifier = Modifier.padding(padding),
                onOpen = {
                    repository.setActiveTrainingPlan(it)
                    refresh()
                    screen = Screen.OVERVIEW
                },
                onCreate = {
                    repository.createTrainingPlan(it)
                    refresh()
                    screen = Screen.OVERVIEW
                },
                onRename = { id, name -> repository.renameTrainingPlan(id, name); refresh() },
                onDelete = { repository.deleteTrainingPlan(it); refresh() },
                onDuplicate = { repository.duplicateTrainingPlan(it); refresh() },
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
                    onAreaColor = { area, color -> repository.setAreaColor(area, color); refresh() },
                    onChanged = ::refresh,
                    onDeleted = {
                        refresh()
                        screen = Screen.OVERVIEW
                        selectedId = null
                    },
                )
            }
        }
    }

    if (showAdd) AddMachineDialog(
        onDismiss = { showAdd = false },
        onAdd = { name, weight, trackingMode ->
            val id = repository.addMachine(name, weight, trackingMode)
            refresh()
            showAdd = false
            selectedId = id
            screen = Screen.DETAIL
        },
    )

    if (showMenu) AlertDialog(
        onDismissRequest = { showMenu = false },
        title = { Text("Menu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        Text("Training Plan Note", style = MaterialTheme.typography.labelLarge)
                        Text(
                            activePlan?.notes?.ifBlank { "Tap to add a note." } ?: "No active training plan.",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                TextButton(onClick = { showMenu = false; screen = Screen.PLANS }) { Text("Training Plans") }
                TextButton(onClick = { showMenu = false; showProgressionSettings = true }) { Text("Progress Warnings") }
                TextButton(onClick = { showMenu = false; screen = Screen.BACKUP }) { Text("Data, Backup & Sync") }
                TextButton(onClick = { showMenu = false; showAccentColor = true }) { Text("UI Color") }
                TextButton(onClick = { showMenu = false; showHelp = true }) { Text("Help & Explanations") }
                TextButton(onClick = { showMenu = false; confirmDeleteCurrentTraining = true }) {
                    Text("Delete Current Training", color = MaterialTheme.colorScheme.error)
                }
                androidx.compose.material3.HorizontalDivider()
                Text(
                    "Version ${BuildConfig.VERSION_NAME} · Built ${BuildConfig.BUILD_TIME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Close") } },
    )

    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false; showMenu = true },
        title = { Text("Help & Explanations") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Outlines", style = MaterialTheme.typography.titleSmall)
                Text("A colored outline marks interactive or emphasized cards. Tap cards with notes, metrics, or training areas to edit them.")
                Text("Body-Area Colors", style = MaterialTheme.typography.titleSmall)
                Text("Each color identifies a body area throughout the overview, exercise details, and statistics. A full-width marker means primary involvement; a half-width marker means secondary involvement.")
                Text("Training Time", style = MaterialTheme.typography.titleSmall)
                Text("Live means the timer is still counting from the first entry. It becomes completed after ten minutes without a new entry.")
                Text("Progress Warnings", style = MaterialTheme.typography.titleSmall)
                Text("Warnings appear when an exercise has not increased after your configured number of weeks or training sessions.")
                Text("Pinned Exercises", style = MaterialTheme.typography.titleSmall)
                Text("Tap ☆ to pin an exercise at the top. Pinned exercises can be manually reordered; unpinned exercises are automatically sorted by name.")
            }
        },
        confirmButton = { TextButton(onClick = { showHelp = false; showMenu = true }) { Text("Close") } },
    )

    if (confirmDeleteCurrentTraining) AlertDialog(
        onDismissRequest = { confirmDeleteCurrentTraining = false; showMenu = true },
        title = { Text("Delete Current Training?") },
        text = { Text("This removes all entries in the current six-hour training unit from the active plan.") },
        confirmButton = {
            Button(onClick = {
                repository.deleteCurrentTraining()
                refresh()
                confirmDeleteCurrentTraining = false
            }) { Text("Delete Training") }
        },
        dismissButton = { TextButton(onClick = { confirmDeleteCurrentTraining = false; showMenu = true }) { Text("Cancel") } },
    )

    if (showPlanNotes && activePlan != null) TrainingPlanNotesDialog(
        notes = activePlan.notes,
        pinned = activePlan.notesPinned,
        onDismiss = { showPlanNotes = false; showMenu = true },
        onSave = { notes, pinned ->
            repository.saveTrainingPlanNotes(activePlan.id, notes, pinned)
            refresh()
            showPlanNotes = false
            showMenu = true
        },
    )

    if (showProgressionSettings) ProgressionSettingsDialog(
        current = uiState.progressionSettings,
        onDismiss = { showProgressionSettings = false; showMenu = true },
        onSave = {
            repository.saveProgressionSettings(it)
            uiStateResult = Result.success(uiState.copy(progressionSettings = it))
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

    if (showExerciseName) selected?.machine?.let { machine ->
        TextEditDialog(
            title = "Exercise Name",
            initial = machine.name,
            singleLine = true,
            required = true,
            onDismiss = { showExerciseName = false },
            onSave = {
                repository.renameMachine(machine.id, it)
                refresh()
                showExerciseName = false
            },
        )
    }

    if (showExerciseTitleNote) selected?.machine?.let { machine ->
        TextEditDialog(
            title = "Title Note",
            initial = machine.titleNote,
            singleLine = true,
            onDismiss = { showExerciseTitleNote = false },
            onSave = {
                repository.setMachineTitleNote(machine.id, it)
                refresh()
                showExerciseTitleNote = false
            },
        )
    }

    if (confirmImport) AlertDialog(
        onDismissRequest = { confirmImport = false },
        title = { Text("Restore Backup?") },
        text = { Text("Restoring replaces all current GymLog data. Export a backup first if you want to keep it.") },
        confirmButton = {
            Button(onClick = {
                confirmImport = false
                onImport()
            }) { Text("Choose Backup") }
        },
        dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
    )
}

@Composable
private fun DataRecoveryDialog(
    error: Throwable,
    onRetry: () -> Unit,
    onCreateFresh: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("GymLog data could not be opened") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The local database may be from an incompatible app version or may be damaged.")
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
        confirmButton = { Button(onClick = onCreateFresh) { Text("Create New Database") } },
        dismissButton = { TextButton(onClick = onRetry) { Text("Try Again") } },
    )
}

@Composable
private fun ReorderScreen(
    machines: List<MachineSummary>,
    modifier: Modifier = Modifier,
    onMove: (Long, Int) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Use the arrows to arrange your routine. The overview follows this order.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        itemsIndexed(machines, key = { _, item -> item.machine.id }) { index, item ->
            androidx.compose.material3.Card {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${index + 1}. ${item.machine.name}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onMove(item.machine.id, -1) }, enabled = index > 0) { Text("▲") }
                    Spacer(Modifier.width(2.dp))
                    TextButton(onClick = { onMove(item.machine.id, 1) }, enabled = index < machines.lastIndex) { Text("▼") }
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
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
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
                    if (automaticBackupEnabled) "On · included in future Android backups" else "Off · future Android backups skip GymLog data",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = automaticBackupEnabled, onCheckedChange = onAutomaticBackupChanged)
        }
        Text(
            "This is not live sync. Android decides when an automatic backup runs and may restore it after reinstalling the app or while setting up another phone. Changes do not immediately appear on a second phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Manual File Backup", style = MaterialTheme.typography.titleMedium)
        Text("Export creates a readable JSON copy containing every plan, exercise, note, body area, metric change, and set. Choose Google Drive in the file picker if you want to move it between phones yourself.")
        Button(onClick = onExport) { Text("Download Backup") }
        androidx.compose.material3.OutlinedButton(onClick = onImport) { Text("Upload and Restore Backup") }
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
        title = { Text("Training Plan Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    label = { Text("Training Plan Note") },
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
