package com.gymlog.app.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gymlog.app.data.TrackingMode

internal const val TRAINING_METRIC_EXPLANATION =
    "Off: Volume is total weight x reps across all sets in a training. " +
    "On: Strength score sums weight x reps^0.1 for every valid set in a training day. Every set contributes fully, with no best-set bonus cap. " +
    "The Lombardi-style exponent makes reps less influential than load. For example, 3 x 10 at 100 kg scores 377.7; 3 x 8 at 105 kg scores 387.8 even though volume falls. " +
    "A large reduction in reps or sets can still lower the score. It is an app-specific comparison score in points, not a measured one-rep maximum, physical work or a validated sum of strength. Compare the same exercise with similar technique and effort. " +
    "This choice applies to weight-exercise charts and entry summaries. Trend warnings and strength statistics always use the all-set strength score, regardless of this display choice. " +
    "Weight-exercise charts put the primary training metric on the left axis and recorded weight on the right. Reps use their own unlabelled scale across the plot height, with no trend fit. " +
    "Level and watts always use a Riegel-style estimate for a 10-minute effort, independent of this switch. " +
    "Changing it recalculates the display from recorded sets. Custom warning thresholds do not change the metric."

internal const val TEN_MINUTE_EXPLANATION =
    "Riegel-style estimate = intensity x (recorded minutes / 10)^0.06, referenced to 10 minutes. " +
    "The best individual entry supplies the training value; extra entries do not add together. " +
    "At 10 minutes the estimate equals recorded intensity. Longer efforts adjust it upward; shorter efforts downward. " +
    "The fixed exponent is a heuristic, not fitted to your performance. Compare similar hard efforts near 10 minutes; extrapolation is less reliable. " +
    "The estimate line uses its own scale; the legend shows its value and trend. "

internal data class TrainingMetricExplanation(val title: String, val text: String)

internal fun trainingMetricExplanation(mode: TrackingMode? = null): TrainingMetricExplanation = when (mode) {
    TrackingMode.STRENGTH -> TrainingMetricExplanation("Estimated 10-minute level",
        TEN_MINUTE_EXPLANATION +
        "For example, 20 minutes at level 6 estimates 6.25 for 10 minutes. " +
        "Use it only as a rough comparison on the same machine: it assumes a linear level scale, which GymLog cannot verify. " +
        "This is not calibrated physical work or a calorie estimate. Level is on the right axis and minutes on the left.")
    TrackingMode.WATTS -> TrainingMetricExplanation("Estimated 10-minute power",
        TEN_MINUTE_EXPLANATION +
        "For example, 20 minutes at 100 W estimates 104.25 W for 10 minutes. " +
        "The recorded watts are assumed to apply throughout each entry. This is an effort comparison, not measured maximum power or calories. " +
        "Watts are on the right axis and minutes on the left.")
    TrackingMode.DURATION -> TrainingMetricExplanation("Recorded minutes",
        "This chart shows recorded training minutes. No intensity or load is estimated for duration-only exercises.")
    else -> TrainingMetricExplanation("Volume or strength score", TRAINING_METRIC_EXPLANATION)
}

@Composable
internal fun TrainingMetricExplanationDialog(trackingMode: TrackingMode? = null, onDismiss: () -> Unit) {
    val explanation = trainingMetricExplanation(trackingMode)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(explanation.title) },
        text = { Text(explanation.text, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
