package com.gymlog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.AreaAssignment
import com.gymlog.app.data.BodyArea
import com.gymlog.app.data.ExerciseCatalog
import com.gymlog.app.data.GYMLOG_PREFERENCES
import android.content.Context

@Composable
internal fun MuscleProfileDialog(
    exerciseName: String,
    current: List<AreaAssignment>,
    colors: Map<BodyArea, Long> = emptyMap(),
    onColorChange: ((BodyArea, Long) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (List<AreaAssignment>) -> Unit,
) {
    var draft by remember(exerciseName) { mutableStateOf(current) }
    val suggestions = remember(exerciseName) {
        if (exerciseName.isBlank()) emptyList() else ExerciseCatalog.search(exerciseName, limit = 3)
    }
    var showSuggestions by remember { mutableStateOf(false) }
    var chosenProfile by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(GYMLOG_PREFERENCES, Context.MODE_PRIVATE) }
    var showRegionGuide by remember { mutableStateOf(preferences.getBoolean("show_body_region_guide", true)) }
    var colorArea by remember { mutableStateOf<BodyArea?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Body regions & intensity") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(exerciseName, style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Body region guide", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        showRegionGuide = !showRegionGuide
                        preferences.edit().putBoolean("show_body_region_guide", showRegionGuide).apply()
                    }) { Text(if (showRegionGuide) "Hide" else "Show") }
                }
                if (showRegionGuide) BodyRegionGuide(colors)
                if (suggestions.isNotEmpty()) {
                    Text("Suggested profiles for this exercise", style = MaterialTheme.typography.labelMedium)
                    suggestions.forEach { template ->
                        TextButton(onClick = { draft = template.areas; chosenProfile = template.title }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(template.title)
                                Text(muscleProfileSummary(template.areas), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { showSuggestions = true }, modifier = Modifier.fillMaxWidth()) { Text("Browse exercise list") }
                chosenProfile?.let { Text("Starting profile: " + it, color = MaterialTheme.colorScheme.primary) }
                Text("High: primary · Medium: supporting · Low: light involvement. Off removes the region.", style = MaterialTheme.typography.bodySmall)
                Text("Suggestions are editable estimates. Adjust them for your machine and technique.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { draft = emptyList(); chosenProfile = null }, enabled = draft.isNotEmpty()) { Text("Clear all regions") }
                MuscleProfileFields(draft, colors, if (onColorChange == null) null else { area -> colorArea = area }) { draft = it }
            }
        },
        confirmButton = { Button(onClick = { onSave(draft) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (showSuggestions) ExerciseCatalogDialog(exerciseName, onDismiss = { showSuggestions = false }) { template ->
        draft = template.areas
        chosenProfile = template.title
        showSuggestions = false
    }
    colorArea?.let { area ->
        ColorPaletteDialog(area, colors[area] ?: area.color, onDismiss = { colorArea = null }) {
            onColorChange?.invoke(area, it)
            colorArea = null
        }
    }
}
