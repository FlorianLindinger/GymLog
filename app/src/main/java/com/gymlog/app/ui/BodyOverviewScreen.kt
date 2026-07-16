package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class BodyChartSettings(
    val showFit: Boolean = true,
    val target: Double? = null,
    val acceptance: Double? = null,
)

@Composable
fun BodyOverviewScreen(
    snapshot: BodyDataSnapshot,
    syncStatus: String,
    modifier: Modifier = Modifier,
    onSync: () -> Unit,
    onMenu: () -> Unit,
    onGym: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("body_graph_settings", 0) }
    var window by remember { mutableStateOf(preferences.getString("window", "6M") ?: "6M") }
    val settings = remember {
        mutableStateMapOf<BodyMetric, BodyChartSettings>().apply {
            BodyMetric.entries.forEach { metric ->
                put(
                    metric,
                    BodyChartSettings(
                        showFit = preferences.getBoolean("${metric.name}_fit", true),
                        target = preferences.getString("${metric.name}_target", null)?.toDoubleOrNull(),
                        acceptance = preferences.getString("${metric.name}_acceptance", null)?.toDoubleOrNull(),
                    ),
                )
            }
        }
    }
    val cutoff = bodyCutoff(window)
    val visible = snapshot.measurements.filter { cutoff == null || it.measuredAt >= cutoff }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Body Measurements", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Work in progress · On-device Health Connect import", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(syncStatus, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onSync, modifier = Modifier.fillMaxWidth()) { Text("Sync Health Connect") }
                        Text(
                            "“Muscle” uses Health Connect lean body mass. It is not the same as measured skeletal-muscle mass.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("1M", "3M", "6M", "1Y", "3Y", "ALL").forEach { preset ->
                        val selected = window == preset
                        Text(
                            if (preset == "ALL") "All" else preset,
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(7.dp),
                                )
                                .clickable {
                                    window = preset
                                    preferences.edit().putString("window", preset).apply()
                                }
                                .padding(vertical = 6.dp),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            BodyMetric.entries.forEach { metric ->
                item(metric.name) {
                    BodyMetricCard(
                        metric = metric,
                        values = visible.filter { it.metric == metric },
                        settings = settings.getValue(metric),
                        onSettings = {
                            settings[metric] = it
                            preferences.edit()
                                .putBoolean("${metric.name}_fit", it.showFit)
                                .putString("${metric.name}_target", it.target?.toString())
                                .putString("${metric.name}_acceptance", it.acceptance?.toString())
                                .apply()
                        },
                    )
                }
            }
            item {
                Text(
                    "Suggested next steps: rolling averages, source filtering, measurement annotations, goal dates, forecasts, and reminders for consistent measurement times.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onMenu, modifier = Modifier.weight(1f)) { Text("Menu") }
            FilledTonalButton(onClick = onGym, modifier = Modifier.weight(1f)) { Text("Gym") }
        }
    }
}

@Composable
private fun BodyMetricCard(
    metric: BodyMetric,
    values: List<BodyMeasurement>,
    settings: BodyChartSettings,
    onSettings: (BodyChartSettings) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val sorted = values.sortedBy { it.measuredAt }
    val fit = bodyLinearFit(sorted.map { it.value })
    val primary = MaterialTheme.colorScheme.primary
    val fitColor = MaterialTheme.colorScheme.tertiary
    val targetColor = Color(0xFF66BB6A)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${metric.label} (${metric.unit})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        sorted.lastOrNull()?.let { "Latest: ${formatBodyValue(it.value)} ${metric.unit}" } ?: "No measurements in this interval",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { editing = true }) { Text("Graph Settings") }
            }
            if (settings.showFit && sorted.size >= 2) {
                Text(
                    "Linear fit: ${String.format(Locale.US, "%+.1f", fit.second)} ${metric.unit} / selected interval",
                    color = fitColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (settings.target != null) {
                Text(
                    "Target: ${formatBodyValue(settings.target)} ${metric.unit}" +
                        (settings.acceptance?.let { " ± ${formatBodyValue(it)}" } ?: ""),
                    color = targetColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            BodyGraph(sorted, if (settings.showFit) fit.first else emptyList(), settings, primary, fitColor, targetColor, grid)
        }
    }
    if (editing) BodyChartSettingsDialog(metric, settings, { editing = false }) {
        onSettings(it)
        editing = false
    }
}

@Composable
private fun BodyGraph(
    values: List<BodyMeasurement>,
    fit: List<Double>,
    settings: BodyChartSettings,
    dataColor: Color,
    fitColor: Color,
    targetColor: Color,
    gridColor: Color,
) {
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text("Sync Health Connect to populate this graph.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val acceptanceValues = settings.target?.let { target ->
        listOf(target - (settings.acceptance ?: 0.0), target + (settings.acceptance ?: 0.0))
    }.orEmpty()
    val all = values.map { it.value } + fit + acceptanceValues
    val rawMin = all.minOrNull() ?: 0.0
    val rawMax = all.maxOrNull() ?: 1.0
    val padding = ((rawMax - rawMin) * .12).takeIf { it > 0 } ?: 1.0
    val min = rawMin - padding
    val max = rawMax + padding
    val range = max - min
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        fun y(value: Double) = size.height - ((value - min) / range).toFloat() * size.height
        repeat(3) { index ->
            val lineY = index * size.height / 2f
            drawLine(gridColor, Offset(0f, lineY), Offset(size.width, lineY), strokeWidth = 1f)
        }
        settings.target?.let { target ->
            settings.acceptance?.let { tolerance ->
                val top = y(target + tolerance)
                val bottom = y(target - tolerance)
                drawRect(targetColor.copy(alpha = .14f), Offset(0f, top), Size(size.width, (bottom - top).coerceAtLeast(1f)))
            }
            drawLine(targetColor, Offset(0f, y(target)), Offset(size.width, y(target)), strokeWidth = 3f)
        }
        fun points(series: List<Double>) = series.mapIndexed { index, value ->
            Offset(
                if (series.size == 1) size.width / 2f else index * size.width / (series.size - 1),
                y(value),
            )
        }
        val data = points(values.map { it.value })
        data.zipWithNext().forEach { (a, b) -> drawLine(dataColor, a, b, strokeWidth = 5f) }
        data.forEach { drawCircle(dataColor, 5f, it) }
        points(fit).zipWithNext().forEach { (a, b) -> drawLine(fitColor, a, b, strokeWidth = 3f) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val formatter = DateTimeFormatter.ofPattern("d MMM yy")
        Text(Instant.ofEpochMilli(values.first().measuredAt).atZone(ZoneId.systemDefault()).format(formatter), style = MaterialTheme.typography.labelSmall)
        Text(Instant.ofEpochMilli(values.last().measuredAt).atZone(ZoneId.systemDefault()).format(formatter), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BodyChartSettingsDialog(
    metric: BodyMetric,
    current: BodyChartSettings,
    onDismiss: () -> Unit,
    onSave: (BodyChartSettings) -> Unit,
) {
    var fit by remember(current) { mutableStateOf(current.showFit) }
    var target by remember(current) { mutableStateOf(current.target?.let(::formatBodyValue).orEmpty()) }
    var acceptance by remember(current) { mutableStateOf(current.acceptance?.let(::formatBodyValue).orEmpty()) }
    val parsedTarget = target.replace(',', '.').toDoubleOrNull()
    val parsedAcceptance = acceptance.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${metric.label} Graph Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show Linear Fit", Modifier.weight(1f))
                    Switch(fit, { fit = it })
                }
                OutlinedTextField(
                    target,
                    { target = it.filter { char -> char.isDigit() || char == '.' || char == ',' || char == '-' } },
                    label = { Text("Target (${metric.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    acceptance,
                    { acceptance = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                    label = { Text("Acceptance ± (${metric.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text("Leave Target empty to hide the target and acceptance band.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(BodyChartSettings(fit, parsedTarget, parsedAcceptance?.coerceAtLeast(0.0)))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun bodyLinearFit(values: List<Double>): Pair<List<Double>, Double> {
    if (values.size < 2) return values to 0.0
    val meanX = (values.size - 1) / 2.0
    val meanY = values.average()
    val denominator = values.indices.sumOf { (it - meanX) * (it - meanX) }
    val slope = values.indices.sumOf { (it - meanX) * (values[it] - meanY) } / denominator
    val intercept = meanY - slope * meanX
    val fit = values.indices.map { intercept + slope * it }
    return fit to (fit.last() - fit.first())
}

private fun bodyCutoff(window: String): Long? {
    val days = when (window) {
        "1M" -> 30L
        "3M" -> 90L
        "6M" -> 182L
        "1Y" -> 365L
        "3Y" -> 1_095L
        else -> return null
    }
    return Instant.now().minus(Duration.ofDays(days)).toEpochMilli()
}

private fun formatBodyValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
