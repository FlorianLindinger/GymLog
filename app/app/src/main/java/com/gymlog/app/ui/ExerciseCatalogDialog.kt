package com.gymlog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    val results = remember(query, equipment) { ExerciseCatalog.search(query, equipment, ExerciseCatalog.entries.size, allowWeakMatches = true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Find an exercise", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Exercise, machine, or manufacturer") }, singleLine = true,
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
                                Text(if (template.areas.isEmpty()) "Choose body regions manually for this machine." else muscleProfileSummary(template.areas), style = MaterialTheme.typography.bodySmall)
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
    if (areas.isEmpty()) "No regions selected" else areas.joinToString(" ? ") {
        "${it.area.label.substringBefore(" /")} ${formatRegionPercent(it.intensity * 100)}%"
    }

internal fun parseRegionPercent(text: String): Double? =
    if (text.isBlank()) 0.0 else text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1000.0 }

internal fun formatRegionPercent(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

@Composable
internal fun MuscleProfileFields(
    areas: List<AreaAssignment>,
    colors: Map<BodyArea, Long>,
    onColor: ((BodyArea) -> Unit)?,
    onValidityChange: (Boolean) -> Unit = {},
    onChange: (List<AreaAssignment>) -> Unit,
) {
    var invalid by remember { mutableStateOf(emptySet<BodyArea>()) }
    BodyArea.entries.forEach { area ->
        val assignment = areas.firstOrNull { it.area == area }
        var percent by remember(area, assignment?.intensity) { mutableStateOf(assignment?.let { formatRegionPercent(it.intensity * 100) } ?: "") }
        androidx.compose.runtime.LaunchedEffect(area, assignment?.intensity) {
            invalid = invalid - area
            onValidityChange(invalid.isEmpty())
        }
        fun update(value: Double) {
            onChange(areas.filterNot { it.area == area } + if (value <= 0.0) emptyList() else listOf(
                AreaAssignment(area, Strength.fromStorage(value / 100), colors[area] ?: area.color, value / 100)))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(10.dp).background(Color(colors[area] ?: area.color), CircleShape)
                .then(if (onColor != null) Modifier.clickable { onColor(area) } else Modifier))
            Text(area.label.substringBefore(" /"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            listOf("L" to 25.0, "M" to 50.0, "H" to 100.0).forEach { (label, value) ->
                Text(label, Modifier.width(24.dp).background(
                    if (assignment?.intensity == value / 100) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.small).clickable {
                        invalid = invalid - area; onValidityChange(invalid.isEmpty()); update(value)
                    }.padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium)
            }
            androidx.compose.foundation.text.BasicTextField(percent, { next ->
                percent = next
                val value = parseRegionPercent(next)
                invalid = if (value == null) invalid + area else invalid - area
                onValidityChange(invalid.isEmpty())
                if (value != null) update(value)
            }, Modifier.width(52.dp).border(1.dp, if (area in invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.small).padding(horizontal = 4.dp, vertical = 8.dp)
                .semantics { contentDescription = "${area.label.substringBefore(" /")} percentage" },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            Text("%", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (invalid.isNotEmpty()) Text("Use a percentage from 0 to 1000.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
