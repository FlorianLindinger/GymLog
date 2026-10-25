package com.gymlog.app.ui

import com.gymlog.app.data.ChartRange
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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

internal data class BodyChartSettings(
    val target: Double? = null,
    val acceptance: Double? = null,
    val targetEnabled: Boolean = false,
    val showFit: Boolean = true,
    val averaging: Boolean = false,
    val bins: Int = 20,
    val interval: ChartRange = ChartRange.SIX_MONTHS,
    val customStart: Long? = null,
    val customEnd: Long? = null,
    val globalInterval: Boolean = true,
    val globalFit: Boolean = true,
    val globalAveraging: Boolean = true,
    val globalTarget: Boolean = true,
    val showAllTimeHighLow: Boolean = false,
    val showHistoryHighLow: Boolean = false,
    val highLowRange: ChartRange = ChartRange.TWO_MONTHS,
    val highLowStart: Long? = null,
    val highLowEnd: Long? = null,
)

private fun readBodySettings(prefs: android.content.SharedPreferences, prefix: String): BodyChartSettings = BodyChartSettings(
    target = prefs.getString("${prefix}target", null)?.toDoubleOrNull(),
    acceptance = prefs.getString("${prefix}acceptance", null)?.toDoubleOrNull(),
    targetEnabled = prefs.getBoolean("${prefix}target_enabled", prefs.contains("${prefix}target")),
    showFit = prefs.getBoolean("${prefix}fit", true),
    averaging = prefs.getBoolean("${prefix}averaging", false),
    bins = prefs.getInt("${prefix}bins", 20).coerceIn(1, 1000),
    interval = ChartRange.fromStorage(prefs.getString("${prefix}interval", ChartRange.SIX_MONTHS.storageValue)),
    customStart = prefs.getLong("${prefix}start", 0).takeIf { it > 0 },
    customEnd = prefs.getLong("${prefix}end", 0).takeIf { it > 0 },
    showAllTimeHighLow = prefs.getBoolean("${prefix}all_time_high_low", false),
    showHistoryHighLow = prefs.getBoolean("${prefix}history_high_low", false),
    highLowRange = ChartRange.fromStorage(prefs.getString("${prefix}high_low_range", ChartRange.TWO_MONTHS.storageValue)),
    highLowStart = prefs.getLong("${prefix}high_low_start", 0).takeIf { it > 0 },
    highLowEnd = prefs.getLong("${prefix}high_low_end", 0).takeIf { it > 0 },
    globalInterval = prefs.getBoolean("${prefix}global_interval", true),
    globalFit = prefs.getBoolean("${prefix}global_fit", true),
    globalAveraging = prefs.getBoolean("${prefix}global_averaging", true),
    // Keep previously configured per-plot targets during migration.
    globalTarget = prefs.getBoolean("${prefix}global_target", !prefs.contains("${prefix}target")),
)

private fun saveBodySettings(prefs: android.content.SharedPreferences, prefix: String, value: BodyChartSettings) {
    prefs.edit().putString("${prefix}target", value.target?.toString())
        .putString("${prefix}acceptance", value.acceptance?.toString())
        .putBoolean("${prefix}target_enabled", value.targetEnabled)
        .putBoolean("${prefix}fit", value.showFit).putBoolean("${prefix}averaging", value.averaging)
        .putInt("${prefix}bins", value.bins).putString("${prefix}interval", value.interval.storageValue)
        .putLong("${prefix}start", value.customStart ?: 0).putLong("${prefix}end", value.customEnd ?: 0)
        .putBoolean("${prefix}all_time_high_low", value.showAllTimeHighLow)
        .putBoolean("${prefix}history_high_low", value.showHistoryHighLow)
        .putString("${prefix}high_low_range", value.highLowRange.storageValue)
        .putLong("${prefix}high_low_start", value.highLowStart ?: 0).putLong("${prefix}high_low_end", value.highLowEnd ?: 0)
        .putBoolean("${prefix}global_interval", value.globalInterval).putBoolean("${prefix}global_fit", value.globalFit)
        .putBoolean("${prefix}global_averaging", value.globalAveraging).putBoolean("${prefix}global_target", value.globalTarget).apply()
}

internal fun resolveBodySettings(local: BodyChartSettings, global: BodyChartSettings, target: BodyChartSettings): BodyChartSettings = local.copy(
    showFit = if (local.globalFit) global.showFit else local.showFit,
    averaging = if (local.globalAveraging) global.averaging else local.averaging,
    bins = if (local.globalAveraging) global.bins else local.bins,
    target = if (local.globalTarget) target.target else local.target,
    acceptance = if (local.globalTarget) target.acceptance else local.acceptance,
    targetEnabled = if (local.globalTarget) target.targetEnabled else local.targetEnabled,
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
    var globalSettings by remember { mutableStateOf(readBodySettings(preferences, "global_").copy(showFit = showTrend)) }
    var showGlobalSettings by remember { mutableStateOf(false) }
    val globalTargets = remember {
        mutableStateMapOf<BodyMetric, BodyChartSettings>().apply {
            bodyMetricDisplayOrder.forEach { put(it, readBodySettings(preferences, "global_${it.name}_")) }
        }
    }
    var showBodyMenu by remember { mutableStateOf(false) }
    var showCustomRange by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf(preferences.getLong("custom_start", 0L).takeIf { it > 0L }) }
    var customEnd by remember { mutableStateOf(preferences.getLong("custom_end", 0L).takeIf { it > 0L }) }
    val settings = remember {
        mutableStateMapOf<BodyMetric, BodyChartSettings>().apply {
            bodyMetricDisplayOrder.forEach { metric ->
                put(
                    metric,
                    readBodySettings(preferences, "${metric.name}_"),
                )
            }
        }
    }
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
                    trendLabel = "Fit",
                    onWindowSelected = { preset ->
                        window = preset
                        preferences.edit().putString("window", preset.storageValue).apply()
                    },
                    onCustomSelected = { showCustomRange = true },
                    onTrendSelected = {
                        showTrend = !showTrend
                        globalSettings = globalSettings.copy(showFit = showTrend)
                        saveBodySettings(preferences, "global_", globalSettings)
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
                        globalSettings = globalSettings,
                        globalTarget = globalTargets.getValue(metric),
                        onSettings = {
                            settings[metric] = it
                            saveBodySettings(preferences, "${metric.name}_", it)
                        },
                        window = bodyChartWindow,
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
                Button(onClick = { showGlobalSettings = true; showBodyMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("Global plot settings") }
                Text("Body data and graph settings are independent of training plans.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { showBodyMenu = false }) { Text("Close") } },
    )
    if (showGlobalSettings) BodyChartSettingsDialog(
        metric = null, current = globalSettings.copy(interval = window, customStart = customStart, customEnd = customEnd), onDismiss = { showGlobalSettings = false },
        targets = globalTargets,
        onTargetsSave = { targets -> targets.forEach { (metric, value) ->
            globalTargets[metric] = value
            saveBodySettings(preferences, "global_${metric.name}_", value)
        } },
        onSave = {
            globalSettings = it
            window = it.interval
            customStart = it.customStart
            customEnd = it.customEnd
            preferences.edit().putString("window", window.storageValue)
                .putLong("custom_start", customStart ?: 0).putLong("custom_end", customEnd ?: 0).apply()
            showTrend = it.showFit
            saveBodySettings(preferences, "global_", it)
            preferences.edit().putBoolean("show_trend", showTrend).apply()
            showGlobalSettings = false
        },
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
    globalSettings: BodyChartSettings,
    globalTarget: BodyChartSettings,
) {
    var editing by remember { mutableStateOf(false) }
    val effective = resolveBodySettings(settings, globalSettings, globalTarget)
    val plotWindow = if (settings.globalInterval) window else ChartWindow(
        durationMillis = chartWindowDuration(settings.interval),
        startMillis = if (settings.interval == ChartRange.CUSTOM) settings.customStart else null,
        endMillis = if (settings.interval == ChartRange.CUSTOM) settings.customEnd else System.currentTimeMillis(),
        key = "${settings.interval}:${settings.customStart}:${settings.customEnd}",
    )
    val sorted = values.sortedBy { it.measuredAt }
    val intervalValues = if (sorted.isEmpty()) {
        emptyList()
    } else {
        val range = calculateVisibleTimeRange(sorted.first().measuredAt, sorted.last().measuredAt, plotWindow)
        sorted.filter { it.measuredAt in range.start..range.end }
    }
    val fit = bodyLinearFit(intervalValues)
    val latest = sorted.lastOrNull()
    val targetEta = effective.target?.takeIf { effective.targetEnabled }?.let { target ->
        ((target - fit.fittedLast) / fit.slopePerDay).takeIf {
            intervalValues.size >= 2 && fit.slopePerDay != 0.0 && it >= 0.0
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val fitColor = MaterialTheme.colorScheme.tertiary
    val targetColor = Color(0xFF66BB6A)
    val highLow = bodyHighLowReferences(sorted.map { TimeValue(it.measuredAt, it.value) }, effective,
        plotWindow.endMillis ?: sorted.lastOrNull()?.measuredAt ?: System.currentTimeMillis())
    val allTimeColor = MaterialTheme.colorScheme.secondary
    val historyColor = MaterialTheme.colorScheme.tertiary
    val fitPoints = trendTimeSeries(
        intervalValues.map { TimeValue(it.measuredAt, it.value) },
        fitColor,
        PlotAxis.LEFT,
        plotWindow,
    ).points
    Card(Modifier.fillMaxWidth().clickable { editing = true }) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        latest?.let { "${metric.label} (${formatBodyValue(it.value)} ${metric.unit})" } ?: "${metric.label} (${metric.unit})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (effective.showFit && intervalValues.size >= 2) {
                    Text(
                        "Fit: ${String.format(Locale.US, "%+.2f", fit.slopePerDay * 30.44)} ${metric.unit}/mon.",
                        color = fitColor, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End,
                        modifier = Modifier.width(132.dp),
                    )
                }
            }
            Text(
                "${intervalValues.size} in interval \u00b7 ${sorted.size} total" +
                    if (intervalValues.isEmpty()) "" else " \u00b7 Mean ${formatBodyValue(intervalValues.map { it.value }.average())}" +
                        " \u00b7 ${formatBodyValue(intervalValues.minOf { it.value })}\u2013${formatBodyValue(intervalValues.maxOf { it.value })} ${metric.unit}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (effective.targetEnabled && effective.target != null) {
                Text(
                    "Target: ${formatBodyValue(effective.target)} ${metric.unit}" +
                        (effective.acceptance?.let { " ± ${formatBodyValue(it)}" } ?: "") +
                        (targetEta?.let { " · Est. ${formatBodyEta(it)}" } ?: ""),
                    color = targetColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            highLow.forEach { reference ->
                Text("${reference.label}: Low ${formatBodyValue(reference.low.value)} / High ${formatBodyValue(reference.high.value)} ${metric.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reference.allTime) allTimeColor else historyColor)
            }
            BodyGraph(
                sorted,
                if (effective.showFit) fitPoints else emptyList(),
                effective,
                primary,
                fitColor,
                targetColor,
                plotWindow,
                highLow.flatMap { reference -> listOf(reference.low.value, reference.high.value).distinct().map { value ->
                    TimeBand(value, 0.0, if (reference.allTime) allTimeColor else historyColor)
                } },
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
    highLowBands: List<TimeBand>,
) {
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            Text("No measurements yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val raw = values.map { TimeValue(it.measuredAt, it.value) }
    val range = calculateVisibleTimeRange(raw.first().timestamp, raw.last().timestamp, window)
    if (raw.none { it.timestamp in range.start..range.end }) {
        Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
            Text("No measurements in this interval.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    TimeWindowChart(
        series = buildList {
            add(TimeSeries(if (settings.averaging) averageTimeBins(raw, range, settings.bins) else raw, dataColor))
            if (fit.isNotEmpty()) {
                add(
                    TimeSeries(fit, fitColor, strokeWidth = 2.5f, dashed = true, showPoints = false),
                )
            }
        },
        window = window.copy(startMillis = range.start, endMillis = range.end),
        bands = settings.target?.takeIf { settings.targetEnabled }?.let { target ->
            listOf(TimeBand(target, settings.acceptance ?: 0.0, targetColor))
        }.orEmpty() + highLowBands,
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
private fun SettingCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BodyChartSettingsDialog(
    metric: BodyMetric?,
    current: BodyChartSettings,
    onDismiss: () -> Unit,
    targets: Map<BodyMetric, BodyChartSettings> = emptyMap(),
    onTargetsSave: (Map<BodyMetric, BodyChartSettings>) -> Unit = {},
    onSave: (BodyChartSettings) -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    var bins by remember { mutableStateOf(current.bins.toString()) }
    var custom by remember { mutableStateOf(false) }
    var customHighLow by remember { mutableStateOf(false) }
    val targetDrafts = remember { mutableStateMapOf<BodyMetric, BodyChartSettings>().apply { putAll(targets) } }
    val global = metric == null
    val validBins = bins.toIntOrNull()?.takeIf { it in 1..1000 }
    val targetsValid = if (global) targetDrafts.values.all { !it.targetEnabled || it.target != null }
        else draft.globalTarget || !draft.targetEnabled || draft.target != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(metric?.let { "${it.label} settings" } ?: "Global plot settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Interval", fontWeight = FontWeight.SemiBold)
                if (!global) SettingCheck("Follow global interval", draft.globalInterval) { draft = draft.copy(globalInterval = it) }
                if (global || !draft.globalInterval) {
                    ChartRangeControls(draft.interval, draft.showFit,
                        { draft = draft.copy(interval = it) }, { custom = true },
                        { draft = draft.copy(showFit = !draft.showFit, globalFit = false) }, trendLabel = "Fit")
                    if (draft.interval == ChartRange.CUSTOM && draft.customStart != null && draft.customEnd != null) {
                        Text("${formatChartDate(draft.customStart!!)} \u2013 ${formatChartDate(draft.customEnd!!)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text("Averaging", fontWeight = FontWeight.SemiBold)
                if (!global) SettingCheck("Follow global averaging", draft.globalAveraging) { draft = draft.copy(globalAveraging = it) }
                if (global || !draft.globalAveraging) {
                    SettingCheck("Average in time bins", draft.averaging) { draft = draft.copy(averaging = it) }
                    OutlinedTextField(bins, { bins = it.filter(Char::isDigit) }, label = { Text("Number of bins (1-1000)") },
                        enabled = draft.averaging, isError = draft.averaging && validBins == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    Text("Equal intervals across the selected range; empty bins are skipped. Fit uses the original measurements.", style = MaterialTheme.typography.bodySmall)
                }
                Text("Fit", fontWeight = FontWeight.SemiBold)
                if (!global) SettingCheck("Follow global fit", draft.globalFit) { draft = draft.copy(globalFit = it) }
                if (global || !draft.globalFit) SettingCheck("Show fit", draft.showFit) { draft = draft.copy(showFit = it) }
                if (!global) {
                    Text("High / low", fontWeight = FontWeight.SemiBold)
                    SettingCheck("Show all-time high / low", draft.showAllTimeHighLow) { draft = draft.copy(showAllTimeHighLow = it) }
                    SettingCheck("Show history-window high / low", draft.showHistoryHighLow) { draft = draft.copy(showHistoryHighLow = it) }
                    if (draft.showHistoryHighLow) {
                        Text("High / low history window", style = MaterialTheme.typography.labelMedium)
                        ChartRangeControls(draft.highLowRange, false,
                            { draft = draft.copy(highLowRange = it) }, { customHighLow = true }, {}, showTrendControl = false)
                        if (draft.highLowRange == ChartRange.CUSTOM && draft.highLowStart != null && draft.highLowEnd != null) {
                            Text("${formatChartDate(draft.highLowStart!!)} - ${formatChartDate(draft.highLowEnd!!)}", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("History ends at the plot's end date. Custom uses the dates you choose. Highs and lows use original measurements.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Target", fontWeight = FontWeight.SemiBold)
                if (!global) SettingCheck("Follow global target", draft.globalTarget) { draft = draft.copy(globalTarget = it) }
                if (global) {
                    bodyMetricDisplayOrder.forEach { item ->
                        Text(item.label, fontWeight = FontWeight.Medium)
                        BodyTargetFields(item, targetDrafts.getValue(item)) { targetDrafts[item] = it }
                    }
                } else if (!draft.globalTarget) BodyTargetFields(metric!!, draft) { draft = it }
            }
        },
        confirmButton = { Button(onClick = {
            onTargetsSave(targetDrafts.toMap())
            onSave(draft.copy(bins = validBins ?: current.bins))
        }, enabled = targetsValid && !((global || !draft.globalAveraging) && draft.averaging && validBins == null)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (customHighLow) CustomBodyRangeDialog(
        maxTimestamp = System.currentTimeMillis(),
        initialStart = draft.highLowStart ?: System.currentTimeMillis() - 61L * DAY_MILLIS,
        initialEnd = draft.highLowEnd ?: System.currentTimeMillis(),
        onDismiss = { customHighLow = false },
        onSave = { start, end ->
            draft = draft.copy(highLowRange = ChartRange.CUSTOM, highLowStart = start, highLowEnd = end)
            customHighLow = false
        },
    )
    if (custom) CustomBodyRangeDialog(
        maxTimestamp = System.currentTimeMillis(),
        initialStart = draft.customStart ?: System.currentTimeMillis() - 30L * DAY_MILLIS,
        initialEnd = draft.customEnd ?: System.currentTimeMillis(),
        onDismiss = { custom = false },
        onSave = { start, end -> draft = draft.copy(interval = ChartRange.CUSTOM, customStart = start, customEnd = end); custom = false },
    )
}

@Composable
private fun BodyTargetFields(metric: BodyMetric, current: BodyChartSettings, onChange: (BodyChartSettings) -> Unit) {
    var target by remember { mutableStateOf(current.target?.toString().orEmpty()) }
    var acceptance by remember { mutableStateOf(current.acceptance?.toString().orEmpty()) }
    SettingCheck("Enable target", current.targetEnabled) { onChange(current.copy(targetEnabled = it)) }
    OutlinedTextField(target, {
        target = it
        onChange(current.copy(target = it.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value.isFinite() && value >= 0 }))
    }, label = { Text("Target (${metric.unit})") }, enabled = current.targetEnabled,
        isError = current.targetEnabled && current.target == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
    OutlinedTextField(acceptance, {
        acceptance = it
        onChange(current.copy(acceptance = it.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value.isFinite() && value >= 0 }))
    }, label = { Text("Acceptance \u00b1 (${metric.unit})") }, enabled = current.targetEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
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

internal fun formatBodyEta(days: Double): String {
    if (!days.isFinite()) return "\u2014"
    val totalDays = kotlin.math.ceil(days).toLong().coerceAtLeast(1)
    val months = ((totalDays + 0.5) / (365.25 / 12)).toLong()
    fun quantity(value: Long, unit: String) = "$value $unit${if (value == 1L) "" else "s"}"
    if (months >= 120) return quantity(months / 12, "year")
    if (months >= 13) return listOf(quantity(months / 12, "year"),
        (months % 12).takeIf { it > 0 }?.let { quantity(it, "month") }).filterNotNull().joinToString(" ")
    if (months >= 1) return quantity(months, "month")
    if (totalDays >= 7) return quantity(totalDays / 7, "week")
    return quantity(totalDays, "day")
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
