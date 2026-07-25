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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlin.math.pow

private data class BodyChartSettings(
    val showFit: Boolean = true,
    val target: Double? = null,
    val acceptance: Double? = null,
)

private data class BodyFit(
    val values: List<Double>,
    val slopePerDay: Double,
    val fittedLast: Double,
)

@Composable
fun BodyOverviewScreen(
    snapshot: BodyDataSnapshot,
    syncStatus: String,
    modifier: Modifier = Modifier,
    onSync: () -> Unit,
    onGym: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("body_graph_settings", 0) }
    var window by remember { mutableStateOf(preferences.getString("window", "6M") ?: "6M") }
    var showBodyMenu by remember { mutableStateOf(false) }
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
    val newestMeasurement = snapshot.measurements.maxOfOrNull { it.measuredAt } ?: System.currentTimeMillis()
    val xEnd = newestMeasurement
    val xStart = bodyWindowStart(window, newestMeasurement, snapshot.measurements.minOfOrNull { it.measuredAt })
    val visible = snapshot.measurements.filter { it.measuredAt in xStart..xEnd }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        snapshot.syncedAt?.let { "Last synced ${formatBodySyncTime(it)}" } ?: "Not synced yet",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("1W", "2W", "3W", "1M", "3M", "6M", "1Y", "3Y", "ALL").forEach { preset ->
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
                        xStart = xStart,
                        xEnd = xEnd,
                    )
                }
            }
            item {
                Text(
                    "Muscle reference: OpenStax, redrawn by Tomáš Kebert / umimeto.org · CC BY-SA 4.0.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onGym, modifier = Modifier.weight(1f)) { Text("Gym") }
            FilledTonalButton(onClick = { showBodyMenu = true }, modifier = Modifier.weight(1f)) { Text("Menu") }
        }
    }
    if (showBodyMenu) AlertDialog(
        onDismissRequest = { showBodyMenu = false },
        title = { Text("Body") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSync(); showBodyMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("Sync Health Connect") }
                Text("Body data and graph settings are independent of training plans.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { showBodyMenu = false }) { Text("Close") } },
    )
}

@Composable
private fun BodyMetricCard(
    metric: BodyMetric,
    values: List<BodyMeasurement>,
    settings: BodyChartSettings,
    onSettings: (BodyChartSettings) -> Unit,
    xStart: Long,
    xEnd: Long,
) {
    var editing by remember { mutableStateOf(false) }
    val sorted = values.sortedBy { it.measuredAt }
    val fit = bodyLinearFit(sorted)
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
                    "Linear fit: ${String.format(Locale.US, "%+.2f", fit.slopePerDay * 30.44)} ${metric.unit} / month",
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
            settings.target?.let { target ->
                val projectedAtAxisEnd = fit.fittedLast + fit.slopePerDay *
                    ((xEnd - (sorted.lastOrNull()?.measuredAt ?: xEnd)) / DAY_MILLIS.toDouble())
                val days = if (fit.slopePerDay == 0.0) null else (target - projectedAtAxisEnd) / fit.slopePerDay
                Text(
                    if (days != null && days >= 0.0) "Fit target ETA: ${formatBodyEta(days)}"
                    else "Fit target ETA: no future crossing at the current trend",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BodyGraph(sorted, if (settings.showFit) fit.values else emptyList(), settings, primary, fitColor, targetColor, grid, xStart, xEnd)
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
    xStart: Long,
    xEnd: Long,
) {
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text("No measurements in this interval.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val acceptanceValues = settings.target?.let { target ->
        listOf(target - (settings.acceptance ?: 0.0), target + (settings.acceptance ?: 0.0))
    }.orEmpty()
    val all = values.map { it.value } + fit + acceptanceValues
    val ticks = bodyNiceTicks(all.minOrNull() ?: 0.0, all.maxOrNull() ?: 1.0)
    val min = ticks.first()
    val max = ticks.last()
    val range = max - min
    val dates = bodyDateTicks(xStart, xEnd)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(
            Modifier.width(48.dp).height(150.dp).padding(end = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            ticks.asReversed().forEach { tick -> Text(formatBodyValue(tick), style = MaterialTheme.typography.labelSmall) }
        }
        Canvas(Modifier.weight(1f).height(150.dp)) {
            fun y(value: Double) = size.height - ((value - min) / range).toFloat() * size.height
            fun x(timestamp: Long) = ((timestamp - xStart).toDouble() / (xEnd - xStart).coerceAtLeast(1L))
                .coerceIn(0.0, 1.0).toFloat() * size.width
            ticks.forEach { tick ->
                drawLine(gridColor.copy(alpha = .78f), Offset(0f, y(tick)), Offset(size.width, y(tick)), strokeWidth = 1.5f)
            }
            dates.forEach { date ->
                drawLine(gridColor.copy(alpha = .78f), Offset(x(date), 0f), Offset(x(date), size.height), strokeWidth = 1.5f)
            }
            val axis = gridColor.copy(alpha = .9f)
            drawLine(axis, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2f)
            ticks.forEach { tick -> drawLine(axis, Offset(0f, y(tick)), Offset(7f, y(tick)), strokeWidth = 2f) }
            settings.target?.let { target ->
                settings.acceptance?.let { tolerance ->
                    val top = y(target + tolerance)
                    val bottom = y(target - tolerance)
                    drawRect(targetColor.copy(alpha = .14f), Offset(0f, top), Size(size.width, (bottom - top).coerceAtLeast(1f)))
                }
                drawLine(targetColor, Offset(0f, y(target)), Offset(size.width, y(target)), strokeWidth = 3f)
            }
            fun points(series: List<Double>) = series.mapIndexed { index, value -> Offset(x(values[index].measuredAt), y(value)) }
            val points = points(values.map { it.value })
            points.zipWithNext().forEach { (a, b) -> drawLine(dataColor, a, b, strokeWidth = 3.5f) }
            points.forEach { drawCircle(dataColor, 4.5f, it) }
            points(fit).zipWithNext().forEach { (a, b) -> drawLine(fitColor, a, b, strokeWidth = 3f) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 52.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        val formatter = DateTimeFormatter.ofPattern("d MMM yy")
        dates.forEach { date ->
            Text(Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).format(formatter), style = MaterialTheme.typography.labelSmall)
        }
    }
}
private fun bodyNiceTicks(rawMin: Double, rawMax: Double): List<Double> {
    val span = (rawMax - rawMin).takeIf { it > 0.0 } ?: kotlin.math.abs(rawMax).coerceAtLeast(1.0)
    val roughStep = span / 4.0
    val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(roughStep)))
    val normalized = roughStep / magnitude
    val step = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    } * magnitude
    val low = kotlin.math.floor((rawMin - step * .08) / step) * step
    val high = kotlin.math.ceil((rawMax + step * .08) / step) * step
    val count = ((high - low) / step).toInt().coerceAtLeast(1)
    return (0..count).map { low + it * step }
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
                TextButton(onClick = { fit = !fit }) { Text(if (fit) "Linear Fit: On" else "Linear Fit: Off") }
                OutlinedTextField(target, { target = it.filter { char -> char.isDigit() || char == '.' || char == ',' || char == '-' } }, label = { Text("Target (${metric.unit})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(acceptance, { acceptance = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text("Acceptance ± (${metric.unit})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text("Leave Target empty to hide the target and acceptance band.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSave(BodyChartSettings(fit, parsedTarget, parsedAcceptance?.coerceAtLeast(0.0))) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
private fun bodyLinearFit(values: List<BodyMeasurement>): BodyFit {
    if (values.size < 2) return BodyFit(values.map { it.value }, 0.0, values.lastOrNull()?.value ?: 0.0)
    val origin = values.first().measuredAt
    val x = values.map { (it.measuredAt - origin) / DAY_MILLIS.toDouble() }
    val meanX = x.average()
    val meanY = values.map { it.value }.average()
    val denominator = x.sumOf { (it - meanX) * (it - meanX) }
    if (denominator == 0.0) return BodyFit(values.map { it.value }, 0.0, values.last().value)
    val slope = values.indices.sumOf { (x[it] - meanX) * (values[it].value - meanY) } / denominator
    val intercept = meanY - slope * meanX
    val fitted = x.map { intercept + slope * it }
    return BodyFit(fitted, slope, fitted.last())
}

private fun formatBodyEta(days: Double): String {
    val totalDays = kotlin.math.ceil(days).toLong().coerceAtLeast(1)
    val years = totalDays / 365
    val months = (totalDays % 365) / 30
    val weeks = (totalDays % 30) / 7
    val remainingDays = totalDays % 7
    return buildList {
        if (years > 0) add("$years year${if (years == 1L) "" else "s"}")
        if (months > 0) add("$months month${if (months == 1L) "" else "s"}")
        if (weeks > 0) add("$weeks week${if (weeks == 1L) "" else "s"}")
        if (remainingDays > 0 && isEmpty()) add("$remainingDays day${if (remainingDays == 1L) "" else "s"}")
    }.take(2).joinToString(" ")
}
private fun bodyWindowStart(window: String, newest: Long, oldest: Long?): Long {
    val days = when (window) {
        "1W" -> 7L
        "2W" -> 14L
        "3W" -> 21L
        "1M" -> 30L
        "3M" -> 90L
        "6M" -> 182L
        "1Y" -> 365L
        "3Y" -> 1_095L
        else -> null
    }
    return days?.let { newest - it * DAY_MILLIS } ?: (oldest ?: newest - DAY_MILLIS)
}

private fun bodyDateTicks(start: Long, end: Long): List<Long> =
    listOf(start, start + (end - start) / 2, end).distinct()

private fun formatBodySyncTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
private fun formatBodyValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
