package com.gymlog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.unit.dp
import com.gymlog.app.data.AreaAssignment
import com.gymlog.app.data.BodyArea

@Composable
internal fun MuscleProfileDialog(
    exerciseName: String,
    current: List<AreaAssignment>,
    colors: Map<BodyArea, Long> = emptyMap(),
    onColorChange: ((BodyArea, Long) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (List<AreaAssignment>) -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    var showSuggestions by remember { mutableStateOf(false) }
    var chosenProfile by remember { mutableStateOf<String?>(null) }
    var showReference by remember { mutableStateOf(false) }
    var colorArea by remember { mutableStateOf<BodyArea?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Body regions & intensity") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(exerciseName, style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { showSuggestions = true }, modifier = Modifier.fillMaxWidth()) { Text("Suggest from exercise list") }
                chosenProfile?.let { Text("Starting profile: " + it, color = MaterialTheme.colorScheme.primary) }
                Text("High: primary · Medium: supporting · Low: light involvement. Off removes the region.", style = MaterialTheme.typography.bodySmall)
                Text("Suggestions are editable estimates. Adjust them for your machine and technique.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { draft = emptyList(); chosenProfile = null }, enabled = draft.isNotEmpty()) { Text("Clear all regions") }
                MuscleProfileFields(draft, colors, if (onColorChange == null) null else { area -> colorArea = area }) { draft = it }
                TextButton(onClick = { showReference = !showReference }) { Text(if (showReference) "Hide muscle reference" else "Show muscle reference") }
                if (showReference) {
                    BodyMapPicker(Modifier.fillMaxWidth().aspectRatio(960f / 836f))
                    Text("Diagram: OpenStax; adaptation by Tomáš Kebert / umimeto.org. See third-party notices.", style = MaterialTheme.typography.labelSmall)
                }
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
