package com.gymlog.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.GymRepository
import com.gymlog.app.data.MachineSummary

private enum class Screen { OVERVIEW, STATS, HISTORY, REORDER, BACKUP, PLANS, DETAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymLogApp(
    repository: GymRepository,
    externalDataVersion: Int,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var machines by remember { mutableStateOf(repository.loadMachines()) }
    var history by remember { mutableStateOf(repository.loadHistory()) }
    var areaColors by remember { mutableStateOf(repository.loadAreaColors()) }
    var trainingPlans by remember { mutableStateOf(repository.loadTrainingPlans()) }
    var activeTrainingPlanId by remember { mutableLongStateOf(repository.activeTrainingPlanId()) }
    var progressionSettings by remember { mutableStateOf(repository.loadProgressionSettings()) }
    var automaticBackupEnabled by remember { mutableStateOf(repository.automaticBackupEnabled()) }
    var screen by remember { mutableStateOf(Screen.OVERVIEW) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }

    fun refresh() {
        machines = repository.loadMachines()
        history = repository.loadHistory()
        areaColors = repository.loadAreaColors()
        trainingPlans = repository.loadTrainingPlans()
        activeTrainingPlanId = repository.activeTrainingPlanId()
        progressionSettings = repository.loadProgressionSettings()
        automaticBackupEnabled = repository.automaticBackupEnabled()
    }
    LaunchedEffect(externalDataVersion) { refresh() }

    val selected = machines.firstOrNull { it.machine.id == selectedId }
    if (screen == Screen.DETAIL && selected == null) screen = Screen.OVERVIEW

    BackHandler(enabled = screen != Screen.OVERVIEW) {
        screen = Screen.OVERVIEW
        selectedId = null
    }

    Scaffold(
        topBar = {
            if (screen != Screen.OVERVIEW) TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            Screen.OVERVIEW -> ""
                            Screen.STATS -> "Stats"
                            Screen.HISTORY -> "History"
                            Screen.REORDER -> "Change order"
                            Screen.BACKUP -> "Data & backup"
                            Screen.PLANS -> "Training plans"
                            Screen.DETAIL -> selected?.machine?.let { machine ->
                                machine.name + machine.titleNote.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                            } ?: "Exercise"
                        },
                    )
                },
                navigationIcon = {
                    if (screen != Screen.OVERVIEW) TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                        onClick = {
                        screen = Screen.OVERVIEW
                        selectedId = null
                    }) { Text("‹ Back") }
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
                machines = machines,
                activePlanName = trainingPlans.firstOrNull { it.id == activeTrainingPlanId }?.name ?: "Training plan",
                modifier = Modifier.padding(padding),
                onMachine = {
                    selectedId = it
                    screen = Screen.DETAIL
                },
                onAdd = { showAdd = true },
                onMenu = { showMenu = true },
                onStats = { screen = Screen.STATS },
                areaFrequency = machines.flatMap { it.areas }.groupBy { it.area }.mapValues { (_, values) -> values.sumOf { it.strength.value } },
                onQuickSet = { machine, reps ->
                    if (isDirectMetric(machine.machine.trackingMode)) {
                        reps.firstOrNull()?.let { repository.addMetricSet(machine.machine.id, it) }
                    } else {
                        repository.addSets(machine.machine.id, reps, machine.machine.currentWeight)
                    }
                    refresh()
                },
                onMetricChange = { machine, value ->
                    repository.changeWeight(machine.machine.id, value)
                    refresh()
                },
                progressionSettings = progressionSettings,
                onProgressionSettings = {
                    repository.saveProgressionSettings(it)
                    progressionSettings = it
                },
            )
            Screen.HISTORY -> HistoryScreen(
                history = history,
                modifier = Modifier.padding(padding),
                onUpdateSet = { id, reps, weight -> repository.updateSet(id, reps, weight); refresh() },
                onDeleteSet = { repository.deleteSet(it); refresh() },
                onDeleteExerciseDay = { machineId, start, end -> repository.deleteExerciseDay(machineId, start, end); refresh() },
                onDeleteTrainingDay = { start, end -> repository.deleteTrainingDay(start, end); refresh() },
            )
            Screen.STATS -> StatsScreen(
                history = history,
                machines = machines,
                modifier = Modifier.padding(padding),
            )
            Screen.REORDER -> ReorderScreen(
                machines = machines,
                modifier = Modifier.padding(padding),
                onMove = { id, direction ->
                    repository.moveMachine(id, direction)
                    refresh()
                },
            )
            Screen.PLANS -> TrainingPlansScreen(
                plans = trainingPlans,
                activePlanId = activeTrainingPlanId,
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
            )
            Screen.BACKUP -> BackupScreen(
                modifier = Modifier.padding(padding),
                automaticBackupEnabled = automaticBackupEnabled,
                onAutomaticBackupChanged = {
                    repository.setAutomaticBackupEnabled(it)
                    automaticBackupEnabled = it
                },
                onExport = onExport,
                onImport = { confirmImport = true },
            )
            Screen.DETAIL -> selected?.let { summary ->
                DetailScreen(
                    summary = summary,
                    areaUsage = machines.flatMap { it.areas }.groupingBy { it.area }.eachCount(),
                    areaColors = areaColors,
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
                TextButton(onClick = { showMenu = false; screen = Screen.HISTORY }) { Text("Calendar & history") }
                TextButton(onClick = { showMenu = false; screen = Screen.REORDER }) { Text("Change exercise order") }
                TextButton(onClick = { showMenu = false; screen = Screen.PLANS }) { Text("Training plans") }
                TextButton(onClick = { showMenu = false; screen = Screen.BACKUP }) { Text("Data, backup & sync") }
            }
        },
        confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Close") } },
    )

    if (confirmImport) AlertDialog(
        onDismissRequest = { confirmImport = false },
        title = { Text("Restore backup?") },
        text = { Text("Restoring replaces all current GymLog data. Export a backup first if you want to keep it.") },
        confirmButton = {
            Button(onClick = {
                confirmImport = false
                onImport()
            }) { Text("Choose backup") }
        },
        dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
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
    onAutomaticBackupChanged: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Backup & sync", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Android automatic backup", style = MaterialTheme.typography.titleMedium)
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
        Text("Manual file backup", style = MaterialTheme.typography.titleMedium)
        Text("Export creates a readable JSON copy containing every plan, exercise, note, body area, metric change, and set. Choose Google Drive in the file picker if you want to move it between phones yourself.")
        Button(onClick = onExport) { Text("Download backup") }
        androidx.compose.material3.OutlinedButton(onClick = onImport) { Text("Upload and restore backup") }
        Text("Restoring a manual file replaces the data currently on this phone. Automatic Android backup is unaffected by manual export and restore.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
