package com.gymlog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.TrainingPlan

@Composable
fun TrainingPlansScreen(
    plans: List<TrainingPlan>,
    activePlanId: Long,
    modifier: Modifier = Modifier,
    onOpen: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<TrainingPlan?>(null) }
    var deleting by remember { mutableStateOf<TrainingPlan?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Keep separate routines, exercise histories, statistics, and ordering in each plan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(plans, key = { it.id }) { plan ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(plan.name, style = MaterialTheme.typography.titleMedium)
                            if (plan.id == activePlanId) Text("Active plan", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { onOpen(plan.id) }, enabled = plan.id != activePlanId) { Text("Open") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onDuplicate(plan.id) }) { Text("Duplicate") }
                        TextButton(onClick = { renaming = plan }) { Text("Rename") }
                        TextButton(onClick = { deleting = plan }, enabled = plans.size > 1) { Text("Delete") }
                    }
                }
            }
        }
        item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("+ New training plan") } }
    }

    if (creating) PlanNameDialog("New training plan", "", onDismiss = { creating = false }) {
        onCreate(it)
        creating = false
    }
    renaming?.let { plan ->
        PlanNameDialog("Rename training plan", plan.name, onDismiss = { renaming = null }) {
            onRename(plan.id, it)
            renaming = null
        }
    }
    deleting?.let { plan ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${plan.name}?") },
            text = { Text("This permanently deletes every exercise and recorded set in this plan.") },
            confirmButton = { Button(onClick = { onDelete(plan.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PlanNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Plan name") }, singleLine = true) },
        confirmButton = { Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
