package com.gymlog.app.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal const val TRAINING_METRIC_EXPLANATION =
    "Off: Volume is total weight x reps across all sets in a training. " +
    "On: 1RM is the estimated maximum weight for one repetition, in kg, using the Wathan formula. " +
    "It uses the best set of each training, so extra sets do not add to 1RM. " +
    "The estimate is most useful with hard sets of 2-10 reps. " +
    "This choice applies to weight-exercise charts, entry summaries and trend warnings. " +
    "Level and watts record intensity and minutes, not repetitions: their load/work is intensity x minutes, not a 1RM estimate. " +
    "Changing it recalculates the display from recorded sets. Exercises with custom warning settings keep their own choice."

@Composable
internal fun TrainingMetricExplanationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Volume or 1RM") },
        text = { Text(TRAINING_METRIC_EXPLANATION, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
