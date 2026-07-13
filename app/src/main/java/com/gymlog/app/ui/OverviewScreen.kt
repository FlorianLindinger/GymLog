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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.MachineSummary
import com.gymlog.app.data.Strength
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun OverviewScreen(
    machines: List<MachineSummary>,
    modifier: Modifier = Modifier,
    onMachine: (Long) -> Unit,
    onAdd: () -> Unit,
    onQuickSet: (MachineSummary, Int) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        if (machines.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Build your routine", style = MaterialTheme.typography.headlineSmall)
                Text("Add your first machine, choose its training areas, then log each set in a couple of taps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onAdd) { Text("Add first machine") }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Your routine", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Tap a machine for notes, history and controls", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(machines, key = { it.machine.id }) { summary ->
                    MachineCard(summary, onMachine, onQuickSet)
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Text("+ Add") }
    }
}

@Composable
private fun MachineCard(
    summary: MachineSummary,
    onMachine: (Long) -> Unit,
    onQuickSet: (MachineSummary, Int) -> Unit,
) {
    var quickReps by remember(summary.machine.id) { mutableStateOf(false) }
    val today = LocalDate.now()
    val todaySets = summary.recentSets.filter {
        Instant.ofEpochMilli(it.performedAt).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onMachine(summary.machine.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (summary.areas.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    summary.areas.forEach { assignment ->
                        Box(
                            Modifier
                                .width(if (assignment.strength == Strength.FULL) 38.dp else 19.dp)
                                .height(7.dp)
                                .background(Color(assignment.area.color), RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(summary.machine.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (summary.areas.isEmpty()) "No body areas selected" else summary.areas.joinToString(" · ") { it.area.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatWeight(summary.machine.currentWeight), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (todaySets.isEmpty()) "No sets today" else "Today: ${todaySets.joinToString(" · ") { "${it.reps} reps" }}",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { quickReps = true }) { Text("+ SET") }
            }
        }
    }
    if (quickReps) QuickRepDialog(
        previousReps = summary.recentSets.map { it.reps }.distinct().take(3),
        onDismiss = { quickReps = false },
        onAdd = {
            onQuickSet(summary, it)
            quickReps = false
        },
    )
}

@Composable
fun QuickRepDialog(previousReps: List<Int>, onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var reps by remember { mutableStateOf(previousReps.firstOrNull()?.toString() ?: "10") }
    val parsed = reps.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log reps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit).take(3) },
                    label = { Text("Reps in this set") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (previousReps.isNotEmpty()) {
                    Text("Recent amounts", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        previousReps.forEach { amount ->
                            TextButton(onClick = { reps = amount.toString() }) { Text(amount.toString()) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { parsed?.let(onAdd) }, enabled = parsed != null && parsed > 0) { Text("Add set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddMachineDialog(onDismiss: () -> Unit, onAdd: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    val parsedWeight = weight.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add machine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Your machine name") }, singleLine = true)
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                    label = { Text("Current weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name.trim(), parsedWeight ?: 0.0) }, enabled = name.isNotBlank() && parsedWeight != null && parsedWeight >= 0) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun formatWeight(weight: Double): String = if (weight % 1.0 == 0.0) "${weight.toInt()} kg" else "%.1f kg".format(weight)
