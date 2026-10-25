package com.gymlog.app.ui

import com.gymlog.app.data.ChartRange
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.widthIn
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
    val showAllTimeHigh: Boolean = false,
    val showAllTimeLow: Boolean = false,
    val showAllTimeMean: Boolean = false,
    val showHistoryHigh: Boolean = false,
    val showHistoryLow: Boolean = false,
    val showHistoryMean: Boolean = false,
    val highLowFollowPlot: Boolean = false,
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
    showAllTimeMean = prefs.getBoolean("${prefix}all_time_mean", false),
    showHistoryHigh = prefs.getBoolean("${prefix}history_high", prefs.getBoolean("${prefix}history_high_low", false)),
    showHistoryLow = prefs.getBoolean("${prefix}history_low", prefs.getBoolean("${prefix}history_high_low", false)),
    showHistoryMean = prefs.getBoolean("${prefix}history_mean", false),
    highLowFollowPlot = prefs.getBoolean("${prefix}high_low_follow_plot", false),
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
        .putBoolean("${prefix}all_time_mean", value.showAllTimeMean).putBoolean("${prefix}history_mean", value.showHistoryMean)
        .putBoolean("${prefix}high_low_follow_plot", value.highLowFollowPlot)
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
    showAllTimeMean = if (local.globalHighLow) global.showAllTimeMean else local.showAllTimeMean,
    showHistoryHigh = if (local.globalHighLow) global.showHistoryHigh else local.showHistoryHigh,
    showHistoryLow = if (local.globalHighLow) global.showHistoryLow else local.showHistoryLow,
    showHistoryMean = if (local.globalHighLow) global.showHistoryMean else local.showHistoryMean,
    highLowFollowPlot = if (local.globalHighLow) global.highLowFollowPlot else local.highLowFollowPlot,
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
    var showCustomRange by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf(preferences.getLong("custom_start", 0L).takeIf { it > 0L }) }
    var customEnd by remember { mutableStateOf(preferences.getLong("custom_end", 0L).takeIf { it > 0L }) }
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
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
                        globalRange = window,
                    )
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onGym, modifier = Modifier.weight(1f)) { Text("Gym") }
            FilledTonalButton(onClick = { showGlobalSettings = true }, modifier = Modifier.weight(1f)) { Text("Settings") }
        }
    }
    if (showGlobalSettings) BodyChartSettingsDialog(
        metric = null, current = globalSettings.copy(interval = window, customStart = customStart, customEnd = customEnd), onDismiss = { showGlobalSettings = false },
        header = {
            Button(onClick = onSync, enabled = !isSyncing, modifier = Modifier.fillMaxWidth()) {
                if (isSyncing) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                Text("Sync Health Connect")
            }
            Text(syncStatus, style = MaterialTheme.typography.bodySmall)
            Text(snapshot.syncedAt?.let { "Last sync ${formatBodySyncTime(it)}" } ?: "Not synced yet",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
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
    globalRange: ChartRange,
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
    val visiblePlotRange = remember(raw, plotWindow) {
        if (raw.isEmpty()) null else calculateVisibleTimeRange(raw.first().timestamp, raw.last().timestamp, plotWindow)
    }
    val referenceRange = if (settings.globalInterval) globalRange else settings.interval
    val referenceLabel = when (referenceRange) {
        ChartRange.ALL -> "All history"
        ChartRange.CUSTOM -> "Custom history"
        else -> "${referenceRange.storageValue} history"
    }
    val highLow = remember(raw, effective, plotWindow, visiblePlotRange, referenceLabel) {
        bodyHighLowReferences(raw, effective,
            plotWindow.endMillis ?: sorted.lastOrNull()?.measuredAt ?: System.currentTimeMillis(),
            visiblePlotRange, referenceLabel)
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
                AutoFitText("${reference.label}: " + listOfNotNull(
                    "Low ${formatBodyValue(reference.low.value)}".takeIf { reference.showLow },
                    reference.mean?.let { "Mean ${formatBodyValue(it)}" }?.takeIf { reference.showMean },
                    "High ${formatBodyValue(reference.high.value)}".takeIf { reference.showHigh }).joinToString(" / ") + " ${metric.unit}",
                    modifier = Modifier.fillMaxWidth(),
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
                highLow.flatMap { reference -> reference.visibleLines.map { line ->
                    TimeBand(line.value, 0.0, if (reference.allTime) allTimeColor else historyColor, dashed = line.dashed)
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
        mirrorLeftTicksOnRight = true,
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
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun BodySettingsSection(
    title: String,
    description: String? = null,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .18f)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                if (badge != null) Text(badge,
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            description?.let { BodySettingsHint(it) }
            content()
        }
    }
}

@Composable
private fun BodySettingsHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BodySettingsInheritance(checked: Boolean, onChange: (Boolean) -> Unit, label: String = "Use global settings") {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f), RoundedCornerShape(10.dp))
        .padding(horizontal = 10.dp, vertical = 4.dp)) {
        SettingCheck(label, checked, onChange)
        BodySettingsHint(if (checked) "Follows future global changes" else "Custom for this plot")
    }
}

@Composable
private fun BodyReferenceGroup(title: String, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(color.copy(alpha = .08f), RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun BodyReferenceToggles(
    low: Boolean, mean: Boolean, high: Boolean,
    onLow: (Boolean) -> Unit, onMean: (Boolean) -> Unit, onHigh: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(low, { onLow(!low) }, label = { Text("Low") }, modifier = Modifier.weight(1f))
        FilterChip(mean, { onMean(!mean) }, label = { Text("Mean") }, modifier = Modifier.weight(1f))
        FilterChip(high, { onHigh(!high) }, label = { Text("High") }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BodyChartSettingsDialog(
    metric: BodyMetric?,
    current: BodyChartSettings,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null,
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
    fun source(followsGlobal: Boolean) = if (global) "Default" else if (followsGlobal) "Global" else "Custom"
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 640.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(metric?.let { "${it.label} settings" } ?: "Global plot settings")
                BodySettingsHint(if (global) "Defaults for plots that follow global settings" else "Choose global defaults or customize each section")
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (header != null) BodySettingsSection("Health Connect") { header() }
                BodySettingsSection("Time range", "Which measurements appear in the plot", source(draft.globalInterval)) {
                    if (!global) BodySettingsInheritance(draft.globalInterval, { draft = draft.copy(globalInterval = it) })
                    if (global || !draft.globalInterval) {
                        ChartRangeControls(draft.interval, draft.showFit,
                            { draft = draft.copy(interval = it) }, { custom = true }, {}, showTrendControl = false)
                        if (draft.interval == ChartRange.CUSTOM && draft.customStart != null && draft.customEnd != null) {
                            BodySettingsHint("${formatChartDate(draft.customStart!!)} - ${formatChartDate(draft.customEnd!!)}")
                        }
                    }
                }
                BodySettingsSection("Averaging", "Simplify the displayed measurements", source(draft.globalAveraging)) {
                    if (!global) BodySettingsInheritance(draft.globalAveraging, { draft = draft.copy(globalAveraging = it) })
                    if (global || !draft.globalAveraging) {
                        SettingCheck("Average in time bins", draft.averaging) { draft = draft.copy(averaging = it) }
                        OutlinedTextField(bins, { bins = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Number of bins (1-1000)") },
                            enabled = draft.averaging, isError = draft.averaging && validBins == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        BodySettingsHint("Equal time bins; empty bins are skipped. Fits and reference lines use the original measurements.")
                    }
                }
                BodySettingsSection("Trend fit", "Show the direction of change", source(draft.globalFit)) {
                    if (!global) BodySettingsInheritance(draft.globalFit, { draft = draft.copy(globalFit = it) })
                    if (global || !draft.globalFit) SettingCheck("Show fit", draft.showFit) { draft = draft.copy(showFit = it) }
                }
                BodySettingsSection("Plot summary", "Values shown above the chart", source(draft.globalSummary)) {
                    if (!global) BodySettingsInheritance(draft.globalSummary, { draft = draft.copy(globalSummary = it) })
                    if (global || !draft.globalSummary) {
                        SettingCheck("Count in interval", draft.showIntervalCount) { draft = draft.copy(showIntervalCount = it) }
                        SettingCheck("Total count", draft.showTotalCount) { draft = draft.copy(showTotalCount = it) }
                        SettingCheck("Interval mean", draft.showMean) { draft = draft.copy(showMean = it) }
                        SettingCheck("Interval range", draft.showRange) { draft = draft.copy(showRange = it) }
                    }
                }
                if (!global) BodySettingsSection("Y axis (${metric!!.unit})", "Leave either limit blank for automatic scaling", "Custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(yMin, { yMin = it }, modifier = Modifier.weight(1f), label = { Text("Minimum") },
                            isError = !boundsValid && yMin.isNotBlank(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(yMax, { yMax = it }, modifier = Modifier.weight(1f), label = { Text("Maximum") },
                            isError = !boundsValid && yMax.isNotBlank(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    if (!boundsValid) Text("Use nonnegative values; maximum must exceed minimum.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                BodySettingsSection("Reference lines", "Solid low/high lines and dashed means", source(draft.globalHighLow)) {
                    if (!global) BodySettingsInheritance(draft.globalHighLow, { draft = draft.copy(globalHighLow = it) })
                    if (global || !draft.globalHighLow) {
                        BodyReferenceGroup("All-time", Color(0xFFCE93D8)) {
                            BodySettingsHint("Every recorded measurement")
                            BodyReferenceToggles(draft.showAllTimeLow, draft.showAllTimeMean, draft.showAllTimeHigh,
                                { draft = draft.copy(showAllTimeLow = it) }, { draft = draft.copy(showAllTimeMean = it) }, { draft = draft.copy(showAllTimeHigh = it) })
                        }
                        BodyReferenceGroup("History window", Color(0xFFFF8A65)) {
                            BodyReferenceToggles(draft.showHistoryLow, draft.showHistoryMean, draft.showHistoryHigh,
                                { draft = draft.copy(showHistoryLow = it) }, { draft = draft.copy(showHistoryMean = it) }, { draft = draft.copy(showHistoryHigh = it) })
                            if (draft.showHistoryHigh || draft.showHistoryLow || draft.showHistoryMean) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
                                SettingCheck("Follow plot interval", draft.highLowFollowPlot) { draft = draft.copy(highLowFollowPlot = it) }
                                if (!draft.highLowFollowPlot) {
                                    ChartRangeControls(draft.highLowRange, false,
                                        { draft = draft.copy(highLowRange = it) }, { customHighLow = true }, {}, showTrendControl = false)
                                    if (draft.highLowRange == ChartRange.CUSTOM && draft.highLowStart != null && draft.highLowEnd != null) {
                                        BodySettingsHint("${formatChartDate(draft.highLowStart!!)} - ${formatChartDate(draft.highLowEnd!!)}")
                                    }
                                }
                                BodySettingsHint(if (draft.highLowFollowPlot) "Uses the plot's exact interval, including Custom and All." else "Ends at the plot's end date. Custom uses your chosen dates.")
                            }
                        }
                    }
                }
                BodySettingsSection("Target", "Green target and acceptance band", source(draft.globalTarget)) {
                    if (!global) BodySettingsInheritance(draft.globalTarget, { draft = draft.copy(globalTarget = it) }, "Use global visibility")
                    if (global) {
                        SettingCheck("Show targets", draft.targetEnabled) { draft = draft.copy(targetEnabled = it) }
                        BodySettingsHint("Controls visibility for plots that follow this setting. Target values remain specific to each plot.")
                    } else BodyTargetFields(metric!!, draft) { draft = it }
                }
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
