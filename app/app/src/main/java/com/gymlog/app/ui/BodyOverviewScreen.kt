package com.gymlog.app.ui

import com.gymlog.app.data.ChartRange
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

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
    val showAllTimeHigh: Boolean = false,
    val showAllTimeLow: Boolean = false,
    val showHistoryHigh: Boolean = false,
    val showHistoryLow: Boolean = false,
    val highLowRange: ChartRange = ChartRange.TWO_MONTHS,
    val highLowStart: Long? = null,
    val highLowEnd: Long? = null,
    val yMin: Double? = null,
    val yMax: Double? = null,
    val showIntervalCount: Boolean = true,
    val showTotalCount: Boolean = true,
    val showMean: Boolean = true,
    val showRange: Boolean = true,
    val globalSummary: Boolean = true,
    val globalHighLow: Boolean = true,
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
    showAllTimeHigh = prefs.getBoolean("${prefix}all_time_high", prefs.getBoolean("${prefix}all_time_high_low", false)),
    showAllTimeLow = prefs.getBoolean("${prefix}all_time_low", prefs.getBoolean("${prefix}all_time_high_low", false)),
    showHistoryHigh = prefs.getBoolean("${prefix}history_high", prefs.getBoolean("${prefix}history_high_low", false)),
    showHistoryLow = prefs.getBoolean("${prefix}history_low", prefs.getBoolean("${prefix}history_high_low", false)),
    highLowRange = ChartRange.fromStorage(prefs.getString("${prefix}high_low_range", ChartRange.TWO_MONTHS.storageValue)),
    highLowStart = prefs.getLong("${prefix}high_low_start", 0).takeIf { it > 0 },
    highLowEnd = prefs.getLong("${prefix}high_low_end", 0).takeIf { it > 0 },
    globalInterval = prefs.getBoolean("${prefix}global_interval", true),
    globalFit = prefs.getBoolean("${prefix}global_fit", true),
    globalAveraging = prefs.getBoolean("${prefix}global_averaging", true),
    yMin = prefs.getString("${prefix}y_min", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 },
    yMax = prefs.getString("${prefix}y_max", null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 },
    // Keep previously configured per-plot targets during migration.
    globalTarget = prefs.getBoolean("${prefix}global_target", !prefs.contains("${prefix}target")),
    showIntervalCount = prefs.getBoolean("${prefix}show_interval_count", true),
    showTotalCount = prefs.getBoolean("${prefix}show_total_count", true),
    showMean = prefs.getBoolean("${prefix}show_mean", true),
    showRange = prefs.getBoolean("${prefix}show_range", true),
    globalSummary = prefs.getBoolean("${prefix}global_summary", true),
    globalHighLow = prefs.getBoolean("${prefix}global_high_low", !prefs.contains("${prefix}all_time_high_low") && !prefs.contains("${prefix}history_high_low")),
)

private fun saveBodySettings(prefs: android.content.SharedPreferences, prefix: String, value: BodyChartSettings) {
    prefs.edit().putString("${prefix}target", value.target?.toString())
        .putString("${prefix}acceptance", value.acceptance?.toString())
        .putBoolean("${prefix}target_enabled", value.targetEnabled)
        .putString("${prefix}y_min", value.yMin?.toString()).putString("${prefix}y_max", value.yMax?.toString())
        .putBoolean("${prefix}fit", value.showFit).putBoolean("${prefix}averaging", value.averaging)
        .putInt("${prefix}bins", value.bins).putString("${prefix}interval", value.interval.storageValue)
        .putLong("${prefix}start", value.customStart ?: 0).putLong("${prefix}end", value.customEnd ?: 0)
        .putBoolean("${prefix}all_time_high", value.showAllTimeHigh).putBoolean("${prefix}all_time_low", value.showAllTimeLow)
        .putBoolean("${prefix}history_high", value.showHistoryHigh).putBoolean("${prefix}history_low", value.showHistoryLow)
        .putString("${prefix}high_low_range", value.highLowRange.storageValue)
        .putLong("${prefix}high_low_start", value.highLowStart ?: 0).putLong("${prefix}high_low_end", value.highLowEnd ?: 0)
        .putBoolean("${prefix}global_interval", value.globalInterval).putBoolean("${prefix}global_fit", value.globalFit)
        .putBoolean("${prefix}global_averaging", value.globalAveraging).putBoolean("${prefix}global_target", value.globalTarget)
        .putBoolean("${prefix}show_interval_count", value.showIntervalCount)
        .putBoolean("${prefix}show_total_count", value.showTotalCount)
        .putBoolean("${prefix}show_mean", value.showMean).putBoolean("${prefix}show_range", value.showRange)
        .putBoolean("${prefix}global_summary", value.globalSummary).putBoolean("${prefix}global_high_low", value.globalHighLow).apply()
}

internal fun resolveBodySettings(local: BodyChartSettings, global: BodyChartSettings): BodyChartSettings = local.copy(
    showFit = if (local.globalFit) global.showFit else local.showFit,
    averaging = if (local.globalAveraging) global.averaging else local.averaging,
    bins = if (local.globalAveraging) global.bins else local.bins,
    targetEnabled = if (local.globalTarget) global.targetEnabled else local.targetEnabled,
    showIntervalCount = if (local.globalSummary) global.showIntervalCount else local.showIntervalCount,
    showTotalCount = if (local.globalSummary) global.showTotalCount else local.showTotalCount,
    showMean = if (local.globalSummary) global.showMean else local.showMean,
    showRange = if (local.globalSummary) global.showRange else local.showRange,
    showAllTimeHigh = if (local.globalHighLow) global.showAllTimeHigh else local.showAllTimeHigh,
    showAllTimeLow = if (local.globalHighLow) global.showAllTimeLow else local.showAllTimeLow,
    showHistoryHigh = if (local.globalHighLow) global.showHistoryHigh else local.showHistoryHigh,
    showHistoryLow = if (local.globalHighLow) global.showHistoryLow else local.showHistoryLow,
    highLowRange = if (local.globalHighLow) global.highLowRange else local.highLowRange,
    highLowStart = if (local.globalHighLow) global.highLowStart else local.highLowStart,
    highLowEnd = if (local.globalHighLow) global.highLowEnd else local.highLowEnd,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyOverviewScreen(
    snapshot: BodyDataSnapshot,
    syncStatus: String,
    isSyncing: Boolean,
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
    var showBodyMenu by remember { mutableStateOf(false) }
    var showCustomRange by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf(preferences.getLong("custom_start", 0L).takeIf { it > 0L }) }
    var customEnd by remember { mutableStateOf(preferences.getLong("custom_end", 0L).takeIf { it > 0L }) }
    val pullState = rememberPullToRefreshState()
    var pulledRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(pulledRefresh, isSyncing, syncStatus) {
        if (pulledRefresh && !isSyncing) {
            delay(1500)
            pulledRefresh = false
        }
    }
    val settings = remember {
        mutableStateMapOf<BodyMetric, BodyChartSettings>().apply {
            bodyMetricDisplayOrder.forEach { metric ->
                put(
                    metric,
                    readBodySettings(preferences, "${metric.name}_").let { local ->
                        // Keep local values; recover old per-metric global targets only when no local value exists.
                        if (!preferences.getBoolean("target_visibility_v2", false) && local.globalTarget) {
                            val oldTarget = readBodySettings(preferences, "global_${metric.name}_")
                            local.copy(target = local.target ?: oldTarget.target,
                                acceptance = if (local.target != null) local.acceptance else oldTarget.acceptance)
                        } else local
                    },
                )
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!preferences.getBoolean("target_visibility_v2", false)) {
            globalSettings = globalSettings.copy(targetEnabled = globalSettings.targetEnabled || bodyMetricDisplayOrder.any {
                readBodySettings(preferences, "global_${it.name}_").targetEnabled
            })
            saveBodySettings(preferences, "global_", globalSettings)
            settings.forEach { (metric, value) -> saveBodySettings(preferences, "${metric.name}_", value) }
            preferences.edit().putBoolean("target_visibility_v2", true).apply()
        }
    }
    val oldestMeasurement = remember(snapshot.measurements) { snapshot.measurements.minOfOrNull { it.measuredAt } }
    val measurementsByMetric = remember(snapshot.measurements) { snapshot.measurements.groupBy { it.metric } }
    val windowNow = remember(window, customStart, customEnd, snapshot.syncedAt) { System.currentTimeMillis() }
    val bodyChartWindow = ChartWindow(
        durationMillis = if (window == ChartRange.CUSTOM) {
            customStart?.let { start -> customEnd?.let { end -> (end - start).coerceAtLeast(1L) } }
        } else {
            chartWindowDuration(window)
        },
        endMillis = if (window == ChartRange.CUSTOM) customEnd else windowNow,
        startMillis = if (window == ChartRange.CUSTOM) customStart else null,
        key = "$window:$customStart:$customEnd",
    )

    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = pulledRefresh && isSyncing,
            onRefresh = { pulledRefresh = true; if (!isSyncing) onSync() },
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
            indicator = {
                if (pulledRefresh || pullState.distanceFraction > 0f) {
                    Card(Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isSyncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Column {
                                Text("Body", style = MaterialTheme.typography.titleSmall)
                                Text(if (pulledRefresh) syncStatus else if (pullState.distanceFraction >= 1f) "Release to sync" else "Pull to sync",
                                    style = MaterialTheme.typography.labelSmall)
                                Text(snapshot.syncedAt?.let { "Last sync ${formatBodySyncTime(it)}" } ?: "Not synced yet",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ChartRangeControls(
                        selectedWindow = window,
                        showTrend = showTrend,
                        showTrendControl = false,
                        onWindowSelected = { preset ->
                            window = preset
                            preferences.edit().putString("window", preset.storageValue).apply()
                        },
                        onCustomSelected = { showCustomRange = true },
                        onTrendSelected = {},
                    )
                }
                bodyMetricDisplayOrder.forEach { metric ->
                    item(metric.name) {
                        BodyMetricCard(
                            metric = metric,
                            values = measurementsByMetric[metric].orEmpty(),
                            settings = settings.getValue(metric),
                            globalSettings = globalSettings,
                            onSettings = {
                                settings[metric] = it
                                saveBodySettings(preferences, "${metric.name}_", it)
                            },
                            window = bodyChartWindow,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onGym, modifier = Modifier.weight(1f)) { Text("Gym") }
            FilledTonalButton(onClick = { showGlobalSettings = true }, modifier = Modifier.weight(1f)) { Text("Settings") }
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
) {
    var editing by remember { mutableStateOf(false) }
    val effective = resolveBodySettings(settings, globalSettings)
    val plotWindow = remember(settings.globalInterval, settings.interval, settings.customStart, settings.customEnd, window) {
        if (settings.globalInterval) window else ChartWindow(
            durationMillis = chartWindowDuration(settings.interval),
            startMillis = if (settings.interval == ChartRange.CUSTOM) settings.customStart else null,
            endMillis = if (settings.interval == ChartRange.CUSTOM) settings.customEnd else System.currentTimeMillis(),
            key = "${settings.interval}:${settings.customStart}:${settings.customEnd}",
        )
    }
    val sorted = remember(values) { values.sortedBy { it.measuredAt } }
    val intervalValues = remember(sorted, plotWindow) {
        if (sorted.isEmpty()) emptyList() else {
            val range = calculateVisibleTimeRange(sorted.first().measuredAt, sorted.last().measuredAt, plotWindow)
            sorted.filter { it.measuredAt in range.start..range.end }
        }
    }
    val fit = remember(intervalValues, effective.showFit, effective.targetEnabled, effective.target) {
        if (effective.showFit || effective.targetEnabled && effective.target != null) bodyLinearFit(intervalValues)
        else BodyFit(0.0, intervalValues.lastOrNull()?.value ?: 0.0)
    }
    val latest = sorted.lastOrNull()
    val targetEta = effective.target?.takeIf { effective.targetEnabled }?.let { target ->
        ((target - fit.fittedLast) / fit.slopePerDay).takeIf {
            intervalValues.size >= 2 && fit.slopePerDay != 0.0 && it >= 0.0
        }
    }
    // Fixed chart roles stay distinct even when the app accent color changes.
    val primary = Color(0xFF64B5F6)
    val fitColor = Color(0xFFFFD54F)
    val targetColor = Color(0xFF66BB6A)
    val raw = remember(sorted) { sorted.map { TimeValue(it.measuredAt, it.value) } }
    val highLow = remember(raw, effective, plotWindow.endMillis) {
        if (effective.showAllTimeHigh || effective.showAllTimeLow || effective.showHistoryHigh || effective.showHistoryLow)
            bodyHighLowReferences(raw, effective, plotWindow.endMillis ?: sorted.lastOrNull()?.measuredAt ?: System.currentTimeMillis())
        else emptyList()
    }
    val allTimeColor = Color(0xFFCE93D8)
    val historyColor = Color(0xFFFF8A65)
    val fitPoints = remember(intervalValues, fit, plotWindow, effective.showFit) {
        if (!effective.showFit || intervalValues.size < 2) emptyList() else {
            val range = calculateVisibleTimeRange(sorted.first().measuredAt, sorted.last().measuredAt, plotWindow)
            val lastTime = intervalValues.last().measuredAt
            listOf(range.start, range.end).map { time -> TimeValue(time, fit.fittedLast + fit.slopePerDay * (time - lastTime) / DAY_MILLIS.toDouble()) }
        }
    }
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
            val summary = remember(intervalValues, sorted.size, effective, metric.unit) {
                bodyPlotSummary(intervalValues, sorted.size, effective, metric.unit)
            }
            if (summary.isNotEmpty()) Text(summary,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("${reference.label}: " + listOfNotNull(
                    "Low ${formatBodyValue(reference.low.value)}".takeIf { reference.showLow },
                    "High ${formatBodyValue(reference.high.value)}".takeIf { reference.showHigh }).joinToString(" / ") + " ${metric.unit}",
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
                highLow.flatMap { reference -> reference.visibleValues.map { value ->
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
    val raw = remember(values) { values.map { TimeValue(it.measuredAt, it.value) } }
    val range = calculateVisibleTimeRange(raw.first().timestamp, raw.last().timestamp, window)
    if (raw.none { it.timestamp in range.start..range.end }) {
        Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
            Text("No measurements in this interval.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val data = remember(raw, range, settings.averaging, settings.bins) {
        if (settings.averaging) averageTimeBins(raw, range, settings.bins) else raw
    }
    val series = remember(data, dataColor, fit, fitColor) {
        buildList {
            add(TimeSeries(data, dataColor))
            if (fit.isNotEmpty()) {
                add(
                    TimeSeries(fit, fitColor, strokeWidth = 2.5f, dashed = true, showPoints = false),
                )
            }
        }
    }
    TimeWindowChart(
        series = series,
        window = window.copy(startMillis = range.start, endMillis = range.end),
        bands = settings.target?.takeIf { settings.targetEnabled }?.let { target ->
            listOf(TimeBand(target, settings.acceptance ?: 0.0, targetColor))
        }.orEmpty() + highLowBands,
        compactYAxis = true,
        yMin = settings.yMin,
        yMax = settings.yMax,
        bodyCalendarLabels = true,
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
    onSave: (BodyChartSettings) -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    var bins by remember { mutableStateOf(current.bins.toString()) }
    var custom by remember { mutableStateOf(false) }
    var customHighLow by remember { mutableStateOf(false) }
    var yMin by remember { mutableStateOf(current.yMin?.let(::formatChartAxisValue).orEmpty()) }
    var yMax by remember { mutableStateOf(current.yMax?.let(::formatChartAxisValue).orEmpty()) }
    val global = metric == null
    val validBins = bins.toIntOrNull()?.takeIf { it in 1..1000 }
    val targetsValid = global || draft.globalTarget || !draft.targetEnabled || draft.target != null
    fun axisValue(text: String) = text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val minimum = axisValue(yMin)
    val maximum = axisValue(yMax)
    val boundsValid = (yMin.isBlank() || minimum != null) && (yMax.isBlank() || maximum != null && maximum > 0) &&
        (minimum == null || maximum == null || minimum < maximum)
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
                        {}, showTrendControl = false)
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
                Text("Plot summary", fontWeight = FontWeight.SemiBold)
                if (!global) SettingCheck("Follow global summary", draft.globalSummary) { draft = draft.copy(globalSummary = it) }
                if (global || !draft.globalSummary) {
                    SettingCheck("Show count in interval", draft.showIntervalCount) { draft = draft.copy(showIntervalCount = it) }
                    SettingCheck("Show total count", draft.showTotalCount) { draft = draft.copy(showTotalCount = it) }
                    SettingCheck("Show interval mean", draft.showMean) { draft = draft.copy(showMean = it) }
                    SettingCheck("Show interval range", draft.showRange) { draft = draft.copy(showRange = it) }
                }
                if (!global) {
                    Text("Y axis (${metric!!.unit})", fontWeight = FontWeight.SemiBold)
                    Text("Leave either limit blank for automatic scaling.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(yMin, { yMin = it }, label = { Text("Y minimum (Auto if blank)") },
                        isError = !boundsValid && yMin.isNotBlank(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(yMax, { yMax = it }, label = { Text("Y maximum (Auto if blank)") },
                        isError = !boundsValid && yMax.isNotBlank(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    if (!boundsValid) Text("Use nonnegative numbers, with maximum greater than minimum.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text("High / low", fontWeight = FontWeight.SemiBold)
                if (!global) SettingCheck("Follow global high / low", draft.globalHighLow) { draft = draft.copy(globalHighLow = it) }
                if (global || !draft.globalHighLow) {
                    SettingCheck("Show all-time high", draft.showAllTimeHigh) { draft = draft.copy(showAllTimeHigh = it) }
                    SettingCheck("Show all-time low", draft.showAllTimeLow) { draft = draft.copy(showAllTimeLow = it) }
                    SettingCheck("Show history-window high", draft.showHistoryHigh) { draft = draft.copy(showHistoryHigh = it) }
                    SettingCheck("Show history-window low", draft.showHistoryLow) { draft = draft.copy(showHistoryLow = it) }
                    if (draft.showHistoryHigh || draft.showHistoryLow) {
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
                if (!global) SettingCheck("Follow global target visibility", draft.globalTarget) { draft = draft.copy(globalTarget = it) }
                if (global) {
                    SettingCheck("Show targets", draft.targetEnabled) { draft = draft.copy(targetEnabled = it) }
                    Text("Plots that follow this setting show or hide their own target values.", style = MaterialTheme.typography.bodySmall)
                } else BodyTargetFields(metric!!, draft) { draft = it }
            }
        },
        confirmButton = { Button(onClick = {
            onSave(draft.copy(bins = validBins ?: current.bins, yMin = minimum, yMax = maximum))
        }, enabled = boundsValid && targetsValid && !((global || !draft.globalAveraging) && draft.averaging && validBins == null)) { Text("Save") } },
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
    if (!current.globalTarget) SettingCheck("Show target", current.targetEnabled) { onChange(current.copy(targetEnabled = it)) }
    OutlinedTextField(target, {
        target = it
        onChange(current.copy(target = it.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value.isFinite() && value >= 0 }))
    }, label = { Text("Target (${metric.unit})") },
        isError = !current.globalTarget && current.targetEnabled && current.target == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
    OutlinedTextField(acceptance, {
        acceptance = it
        onChange(current.copy(acceptance = it.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value.isFinite() && value >= 0 }))
    }, label = { Text("Acceptance \u00b1 (${metric.unit})") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
}

internal fun bodyPlotSummary(values: List<BodyMeasurement>, total: Int, settings: BodyChartSettings, unit: String): String =
    buildList {
        if (settings.showIntervalCount) add("${values.size} in interval")
        if (settings.showTotalCount) add("$total total")
        if (values.isNotEmpty()) {
            if (settings.showMean) add("Mean ${formatBodyValue(values.map { it.value }.average())} $unit")
            if (settings.showRange) add("${formatBodyValue(values.minOf { it.value })}\u2013${formatBodyValue(values.maxOf { it.value })} $unit")
        }
    }.joinToString(" \u00b7 ")

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
    if (totalDays > 365 && months >= 12) return listOf(quantity(months / 12, "year"),
        (months % 12).takeIf { it > 0 }?.let { quantity(it, "month") }).filterNotNull().joinToString(" ")
    if (months >= 1) {
        val weeks = ((totalDays - months * (365.25 / 12)) / 7).toLong().coerceAtLeast(0)
        return listOf("$months mon.", weeks.takeIf { it > 0 }?.let { quantity(it, "week") }).filterNotNull().joinToString(" ")
    }
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
