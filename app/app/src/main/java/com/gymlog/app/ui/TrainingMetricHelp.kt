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
    "On: Strength score is the average estimated one-rep maximum (Epley: weight x (1 + reps / 30); a single rep is its own load) over every valid set in a training day, in kg. " +
    "Every set counts, each extra rep adds about 3.3 % of the load, and one set more or fewer at the same performance does not change the score; volume shows the workload. " +
    "For example, 3 x 10 at 100 kg scores 133.3 kg, 3 x 12 at 100 kg scores 140 kg, and 2 x 10 at 100 kg also scores 133.3 kg. " +
    "Light warm-up sets logged with the work sets pull the average down, and estimates beyond about 12 reps are less reliable. It is an estimate for comparing the same exercise, not a measured maximum. " +
    "This choice applies to weight-exercise charts and entry summaries. Trend warnings and strength statistics always use the all-set strength score, regardless of this display choice. " +
    "Weight-exercise charts put the primary training metric on the left axis and recorded weight on the right. Reps use their own unlabelled scale across the plot height, with no trend fit. " +
    "Level and watts always use a Riegel-style estimate for a 10-minute effort, independent of this switch. " +
    "Changing it recalculates the display from recorded sets. Custom warning thresholds do not change the metric."

internal const val TEN_MINUTE_EXPLANATION =
    "Riegel-style estimate = intensity x (recorded minutes / 10)^0.06, referenced to 10 minutes. " +
    "The best individual entry supplies the training value; extra entries do not add together. " +
    "At 10 minutes the estimate equals recorded intensity. Longer efforts adjust it upward; shorter efforts downward. " +
    "The fixed exponent is a heuristic, not fitted to your performance. Compare similar hard efforts near 10 minutes; extrapolation is less reliable. " +
    "The estimate uses the left axis; the legend shows its value and trend. "

internal data class TrainingMetricExplanation(val title: String, val text: String)

internal fun trainingMetricExplanation(mode: TrackingMode? = null): TrainingMetricExplanation = when (mode) {
    TrackingMode.STRENGTH -> TrainingMetricExplanation("Estimated 10-minute level",
        TEN_MINUTE_EXPLANATION +
        "For example, 20 minutes at level 6 estimates 6.25 for 10 minutes. " +
        "Use it only as a rough comparison on the same machine: it assumes a linear level scale, which GymLog cannot verify. " +
        "This is not calibrated physical work or a calorie estimate. Level is on the right axis. Minutes use an independent, unlabelled scale across the plot height, with no trend fit.")
    TrackingMode.WATTS -> TrainingMetricExplanation("Estimated 10-minute power",
        TEN_MINUTE_EXPLANATION +
        "For example, 20 minutes at 100 W estimates 104.25 W for 10 minutes. " +
        "The recorded watts are assumed to apply throughout each entry. This is an effort comparison, not measured maximum power or calories. " +
        "Watts are on the right axis. Minutes use an independent, unlabelled scale across the plot height, with no trend fit.")
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
