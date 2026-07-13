package com.gymlog.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.GymRepository
import com.gymlog.app.data.MachineSummary

private enum class Screen { OVERVIEW, REORDER, BACKUP, DETAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymLogApp(
    repository: GymRepository,
    externalDataVersion: Int,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var machines by remember { mutableStateOf(repository.loadMachines()) }
    var screen by remember { mutableStateOf(Screen.OVERVIEW) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }

    fun refresh() { machines = repository.loadMachines() }
    LaunchedEffect(externalDataVersion) { refresh() }

    val selected = machines.firstOrNull { it.machine.id == selectedId }
    if (screen == Screen.DETAIL && selected == null) screen = Screen.OVERVIEW

    BackHandler(enabled = screen != Screen.OVERVIEW) {
        screen = Screen.OVERVIEW
        selectedId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            Screen.OVERVIEW -> "GymLog"
                            Screen.REORDER -> "Change order"
                            Screen.BACKUP -> "Data & backup"
                            Screen.DETAIL -> selected?.machine?.name ?: "Machine"
                        },
                    )
                },
                navigationIcon = {
                    if (screen != Screen.OVERVIEW) TextButton(onClick = {
                        screen = Screen.OVERVIEW
                        selectedId = null
                    }) { Text("‹ Back") }
                },
                actions = {
                    if (screen == Screen.OVERVIEW) {
                        TextButton(onClick = { screen = Screen.REORDER }) { Text("Order") }
                        TextButton(onClick = { screen = Screen.BACKUP }) { Text("Backup") }
                    }
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
                modifier = Modifier.padding(padding),
                onMachine = {
                    selectedId = it
                    screen = Screen.DETAIL
                },
                onAdd = { showAdd = true },
                onQuickSet = { machine, reps ->
                    repository.addSet(machine.machine.id, reps, machine.machine.currentWeight)
                    refresh()
                },
            )
            Screen.REORDER -> ReorderScreen(
                machines = machines,
                modifier = Modifier.padding(padding),
                onMove = { id, direction ->
                    repository.moveMachine(id, direction)
                    refresh()
                },
            )
            Screen.BACKUP -> BackupScreen(
                modifier = Modifier.padding(padding),
                onExport = onExport,
                onImport = { confirmImport = true },
            )
            Screen.DETAIL -> selected?.let { summary ->
                DetailScreen(
                    summary = summary,
                    modifier = Modifier.padding(padding),
                    repository = repository,
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
        onAdd = { name, weight ->
            val id = repository.addMachine(name, weight)
            refresh()
            showAdd = false
            selectedId = id
            screen = Screen.DETAIL
        },
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
private fun BackupScreen(modifier: Modifier = Modifier, onExport: () -> Unit, onImport: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Keep your progress portable", style = MaterialTheme.typography.headlineSmall)
        Text("A backup includes every machine, its order, notes, body areas, weight history, set history, and app settings. The file is readable JSON and stays under your control.")
        Button(onClick = onExport) { Text("Download backup") }
        androidx.compose.material3.OutlinedButton(onClick = onImport) { Text("Upload and restore backup") }
        Text("Tip: save an occasional copy to cloud storage or another device. Restoring replaces the data currently on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
