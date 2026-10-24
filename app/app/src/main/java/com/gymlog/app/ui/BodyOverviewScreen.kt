package com.gymlog.app.ui

import com.gymlog.app.data.ChartRange
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class BodyChartSettings(
    val target: Double? = null,
    val acceptance: Double? = null,
)

private enum class CustomRangeMode { INTERVAL, DURATION }

private val bodyMetricDisplayOrder = listOf(
    BodyMetric.WEIGHT,
    BodyMetric.FAT_PERCENTAGE,
    BodyMetric.MUSCLE_PERCENTAGE,
    BodyMetric.FAT_MASS,
    BodyMetric.MUSCLE_MASS,
)

private data class BodyFit(
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
    var window by remember { mutableStateOf(ChartRange.fromStorage(preferences.getString("window", ChartRange.SIX_MONTHS.storageValue))) }
    var showTrend by remember { mutableStateOf(preferences.getBoolean("show_trend", true)) }
    var showBodyMenu by remember { mutableStateOf(false) }
    var showCustomRange by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf(preferences.getLong("custom_start", 0L).takeIf { it > 0L }) }
    var customEnd by remember { mutableStateOf(preferences.getLong("custom_end", 0L).takeIf { it > 0L }) }
    val settings = remember {
        mutableStateMapOf<BodyMetric, BodyChartSettings>().apply {
            bodyMetricDisplayOrder.forEach { metric ->
                put(
                    metric,
                    BodyChartSettings(
                        target = preferences.getString("${metric.name}_target", null)?.toDoubleOrNull(),
                        acceptance = preferences.getString("${metric.name}_acceptance", null)?.toDoubleOrNull(),
                    ),
                )
            }
        }
    }
    val newestMeasurement = snapshot.measurements.maxOfOrNull { it.measuredAt } ?: System.currentTimeMillis()
    val oldestMeasurement = snapshot.measurements.minOfOrNull { it.measuredAt }
    val bodyChartWindow = ChartWindow(
        durationMillis = if (window == ChartRange.CUSTOM) {
            customStart?.let { start -> customEnd?.let { end -> (end - start).coerceAtLeast(1L) } }
        } else {
            chartWindowDuration(window)
        },
        endMillis = if (window == ChartRange.CUSTOM) customEnd else System.currentTimeMillis(),
        startMillis = if (window == ChartRange.CUSTOM) customStart else null,
        key = "$window:$customStart:$customEnd",
    )

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Body", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            snapshot.syncedAt?.let { "Last sync ${formatBodySyncTime(it)}" }
                                ?: syncStatus.ifBlank { "Not synced yet" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            item {
                ChartRangeControls(
                    selectedWindow = window,
                    showTrend = showTrend,
                    onWindowSelected = { preset ->
                        window = preset
                        preferences.edit().putString("window", preset.storageValue).apply()
                    },
                    onCustomSelected = { showCustomRange = true },
                    onTrendSelected = {
                        showTrend = !showTrend
                        preferences.edit().putBoolean("show_trend", showTrend).apply()
                    },
                )
            }
            bodyMetricDisplayOrder.forEach { metric ->
                item(metric.name) {
                    BodyMetricCard(
                        metric = metric,
                        values = snapshot.measurements.filter { it.metric == metric },
                        settings = settings.getValue(metric),
                        onSettings = {
                            settings[metric] = it
                            preferences.edit()
                                .putString("${metric.name}_target", it.target?.toString())
                                .putString("${metric.name}_acceptance", it.acceptance?.toString())
                                .apply()
                        },
                        window = bodyChartWindow,
                        showTrend = showTrend,
                    )
                }
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
    if (showCustomRange) {
        val now = System.currentTimeMillis()
        CustomBodyRangeDialog(
            maxTimestamp = now,
            initialStart = customStart ?: bodyWindowStart(ChartRange.ONE_MONTH, now, oldestMeasurement),
            initialEnd = customEnd ?: now,
            onDismiss = { showCustomRange = false },
            onSave = { start, end ->
                customStart = start
                customEnd = end
                window = ChartRange.CUSTOM
                preferences.edit().putLong("custom_start", start).putLong("custom_end", end).putString("window", ChartRange.CUSTOM.storageValue).apply()
                showCustomRange = false
            },
        )
    }
}

@Composable
private fun BodyMetricCard(
    metric: BodyMetric,
    values: List<BodyMeasurement>,
    settings: BodyChartSettings,
    onSettings: (BodyChartSettings) -> Unit,
    window: ChartWindow,
    showTrend: Boolean,
) {
    var editing by remember { mutableStateOf(false) }
    val sorted = values.sortedBy { it.measuredAt }
    val intervalValues = if (sorted.isEmpty()) {
        emptyList()
    } else {
        val range = calculateVisibleTimeRange(sorted.first().measuredAt, sorted.last().measuredAt, window)
        sorted.filter { it.measuredAt in range.start..range.end }
    }
    val fit = bodyLinearFit(intervalValues)
    val latest = sorted.lastOrNull()
    val targetEta = settings.target?.let { target ->
        ((target - fit.fittedLast) / fit.slopePerDay).takeIf {
            intervalValues.size >= 2 && fit.slopePerDay != 0.0 && it >= 0.0
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val fitColor = MaterialTheme.colorScheme.tertiary
    val targetColor = Color(0xFF66BB6A)
    val fitPoints = trendTimeSeries(
        intervalValues.map { TimeValue(it.measuredAt, it.value) },
        fitColor,
        PlotAxis.LEFT,
        window,
    ).points
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        latest?.let { "${metric.label} (${formatBodyValue(it.value)} ${metric.unit})" } ?: "${metric.label} (${metric.unit})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "Settings",
                    Modifier.clickable { editing = true }.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (showTrend && intervalValues.size >= 2) {
                Text(
                    "Linear fit: ${String.format(Locale.US, "%+.2f", fit.slopePerDay * 30.44)} ${metric.unit}/month",
                    color = fitColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (settings.target != null) {
                Text(
                    "Target: ${formatBodyValue(settings.target)} ${metric.unit}" +
                        (settings.acceptance?.let { " ± ${formatBodyValue(it)}" } ?: "") +
                        (targetEta?.let { " · Est. ${formatBodyEta(it)}" } ?: ""),
                    color = targetColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            BodyGraph(
                sorted,
                if (showTrend) fitPoints else emptyList(),
                settings,
                primary,
                fitColor,
                targetColor,
                window,
            )
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
    fit: List<TimeValue>,
    settings: BodyChartSettings,
    dataColor: Color,
    fitColor: Color,
    targetColor: Color,
    window: ChartWindow,
) {
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text("No measurements yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    TimeWindowChart(
        series = buildList {
            add(TimeSeries(values.map { TimeValue(it.measuredAt, it.value) }, dataColor))
            if (fit.isNotEmpty()) {
                add(
                    TimeSeries(fit, fitColor, strokeWidth = 2.5f, dashed = true, showPoints = false),
                )
            }
        },
        window = window,
        bands = settings.target?.let { target ->
            listOf(TimeBand(target, settings.acceptance ?: 0.0, targetColor))
        }.orEmpty(),
    )
}

@Composable
internal fun CustomBodyRangeDialog(
    maxTimestamp: Long,
    initialStart: Long,
    initialEnd: Long,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd-MM-yyyy") }
    var mode by remember { mutableStateOf(CustomRangeMode.INTERVAL) }
    var dateText by remember {
        mutableStateOf(Instant.ofEpochMilli(initialStart).atZone(zone).toLocalDate().format(dateFormatter))
    }
    var endDateText by remember {
        mutableStateOf(Instant.ofEpochMilli(initialEnd).atZone(zone).toLocalDate().format(dateFormatter))
    }
    var amountText by remember { mutableStateOf("2.5") }
    var durationUnit by remember { mutableStateOf("Weeks") }
    val intervalEnd = runCatching {
        LocalDate.parse(endDateText, dateFormatter).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
    }.getOrNull()?.coerceAtMost(maxTimestamp)
    val end = if (mode == CustomRangeMode.INTERVAL) intervalEnd else maxTimestamp
    val startFromDate = runCatching {
        LocalDate.parse(dateText, dateFormatter).atStartOfDay(zone).toInstant().toEpochMilli()
    }.getOrNull()
    val unitMillis = when (durationUnit) {
        "Days" -> DAY_MILLIS
        "Months" -> (30.44 * DAY_MILLIS).toLong()
        "Years" -> 365L * DAY_MILLIS
        else -> 7L * DAY_MILLIS
    }
    val startFromDuration = end?.let { endValue ->
        amountText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let { endValue - (it * unitMillis).toLong() }
    }
    val start = if (mode == CustomRangeMode.INTERVAL) startFromDate else startFromDuration
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom graph range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(CustomRangeMode.INTERVAL to "Interval", CustomRangeMode.DURATION to "Duration").forEach { (choice, label) ->
                        Text(label, Modifier.weight(1f).background(if (mode == choice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp)).clickable { mode = choice }.padding(vertical = 7.dp), color = if (mode == choice) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (mode == CustomRangeMode.INTERVAL) {
                    OutlinedTextField(dateText, { dateText = it }, label = { Text("Start (DD-MM-YYYY)") }, singleLine = true)
                    OutlinedTextField(endDateText, { endDateText = it }, label = { Text("End (DD-MM-YYYY)") }, singleLine = true)
                } else {
                    OutlinedTextField(amountText, { amountText = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text("Duration") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Days", "Weeks", "Months", "Years").forEach { unit ->
                            Text(unit, Modifier.weight(1f).background(if (durationUnit == unit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp)).clickable { durationUnit = unit }.padding(vertical = 6.dp), color = if (durationUnit == unit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (start != null && end != null) onSave(start, end) }, enabled = start != null && end != null && start < end) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BodyChartSettingsDialog(
    metric: BodyMetric,
    current: BodyChartSettings,
    onDismiss: () -> Unit,
    onSave: (BodyChartSettings) -> Unit,
) {
    var target by remember(current) { mutableStateOf(current.target?.let(::formatBodyValue).orEmpty()) }
    var acceptance by remember(current) { mutableStateOf(current.acceptance?.let(::formatBodyValue).orEmpty()) }
    val parsedTarget = target.replace(',', '.').toDoubleOrNull()
    val parsedAcceptance = acceptance.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(target, { target = it.filter { char -> char.isDigit() || char == '.' || char == ',' || char == '-' } }, label = { Text("Target (${metric.unit})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(acceptance, { acceptance = it.filter { char -> char.isDigit() || char == '.' || char == ',' } }, label = { Text("Acceptance ± (${metric.unit})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text("Leave Target empty to hide the target and acceptance band.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onSave(BodyChartSettings(parsedTarget, parsedAcceptance?.coerceAtLeast(0.0))) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
private fun bodyLinearFit(values: List<BodyMeasurement>): BodyFit {
    if (values.size < 2) return BodyFit(0.0, values.lastOrNull()?.value ?: 0.0)
    val origin = values.first().measuredAt
    val x = values.map { (it.measuredAt - origin) / DAY_MILLIS.toDouble() }
    val meanX = x.average()
    val meanY = values.map { it.value }.average()
    val denominator = x.sumOf { (it - meanX) * (it - meanX) }
    if (denominator == 0.0) return BodyFit(0.0, values.last().value)
    val slope = values.indices.sumOf { (x[it] - meanX) * (values[it].value - meanY) } / denominator
    val intercept = meanY - slope * meanX
    val fitted = x.map { intercept + slope * it }
    return BodyFit(slope, fitted.last())
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
private fun bodyWindowStart(window: ChartRange, newest: Long, oldest: Long?): Long =
    chartWindowDuration(window)?.let { newest - it } ?: (oldest ?: newest - DAY_MILLIS)

private fun formatBodySyncTime(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val synced = Instant.ofEpochMilli(timestamp).atZone(zone)
    val today = LocalDate.now(zone)
    val dayLabel = when (synced.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (synced.toLocalDate().isAfter(today.minusDays(7))) {
            synced.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))
        } else {
            synced.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        }
    }
    return "$dayLabel, ${synced.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}
private fun formatBodyValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
