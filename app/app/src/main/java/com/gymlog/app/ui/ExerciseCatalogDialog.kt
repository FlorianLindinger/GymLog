package com.gymlog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gymlog.app.data.AreaAssignment
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.ExerciseCatalog
import com.gymlog.app.data.ExerciseEquipment
import com.gymlog.app.data.ExerciseTemplate
import com.gymlog.app.data.Strength

@Composable
internal fun ExerciseCatalogDialog(initialQuery: String, onDismiss: () -> Unit, onPick: (ExerciseTemplate) -> Unit) {
    var query by remember { mutableStateOf(initialQuery) }
    var equipment by remember { mutableStateOf<ExerciseEquipment?>(null) }
    val results = remember(query, equipment) { ExerciseCatalog.search(query, equipment, ExerciseCatalog.entries.size) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Find an exercise", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("English or German name") }, singleLine = true,
                    trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") } },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = equipment == null, onClick = { equipment = null }, label = { Text("All") })
                    ExerciseEquipment.entries.forEach { choice ->
                        FilterChip(selected = equipment == choice, onClick = { equipment = choice }, label = { Text(choice.label) })
                    }
                }
                Text("${results.size} matches · ${ExerciseCatalog.entries.size} exercises available offline", style = MaterialTheme.typography.labelMedium)
                Text("Pick a starting profile, then adjust the regions and intensity before saving.", style = MaterialTheme.typography.bodySmall)
                if (results.isEmpty()) {
                    Text("No matches. Try another spelling, clear the search, or keep your own exercise name and choose regions manually.")
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(results, key = { it.englishName }) { template ->
                        Card(Modifier.fillMaxWidth().clickable { onPick(template) }) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(template.englishName, fontWeight = FontWeight.SemiBold)
                                Text(template.germanName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(template.equipment.label, style = MaterialTheme.typography.labelSmall)
                                Text(muscleProfileSummary(template.areas), style = MaterialTheme.typography.bodySmall)
                                Text("Use this profile", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun muscleProfileSummary(areas: List<AreaAssignment>): String =
    if (areas.isEmpty()) "No regions selected" else Strength.entries.asReversed().mapNotNull { intensity ->
        val names = areas.filter { it.strength == intensity }.map { it.area.label.substringBefore(" /") }
        if (names.isEmpty()) null else "${intensity.label}: ${names.joinToString(", ")}"
    }.joinToString(" · ")

@Composable
internal fun MuscleProfileFields(
    areas: List<AreaAssignment>,
    colors: Map<BodyArea, Long>,
    onColor: ((BodyArea) -> Unit)?,
    onChange: (List<AreaAssignment>) -> Unit,
) {
    BodyArea.entries.forEach { area ->
        val selected = areas.firstOrNull { it.area == area }?.strength
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(Color(colors[area] ?: area.color), CircleShape)
                    .then(if (onColor != null) Modifier.clickable { onColor(area) } else Modifier))
                Text(area.label, Modifier.weight(1f).padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (listOf<Strength?>(null) + Strength.entries).forEach { intensity ->
                    FilterChip(
                        selected = selected == intensity,
                        onClick = {
                            onChange(areas.filterNot { it.area == area } + listOfNotNull(intensity?.let {
                                AreaAssignment(area, it, colors[area] ?: area.color)
                            }))
                        },
                        label = { Text(intensity?.label ?: "Off", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
