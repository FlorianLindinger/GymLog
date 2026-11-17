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
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.testTag
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class BodyVisibility { FOLLOW_GLOBAL, SHOW, HIDE }

internal data class BodyChartSettings(
    val visibility: Map<String, BodyVisibility> = emptyMap(),
    val showGoal: Boolean = true,
    val target: Double? = null,
    val acceptance: Double? = null,
    val targetEnabled: Boolean = false,
    val showTargetLower: Boolean = true,
    val showTargetUpper: Boolean = true,
    val showFit: Boolean = true,
    val averaging: Boolean = false,
    val bins: Int = 20,
    val showRawData: Boolean = true,
    val periodAveraging: Boolean = false,
    val averagePeriodDays: Double = 7.0,
    val averageFractionPercent: Double = 5.0,
    val historyPercentile: Double = 5.0,
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

internal val bodyVisibilityKeys = listOf("showFit", "showRawData", "periodAveraging", "averaging", "showIntervalCount", "showTotalCount", "showMean", "showRange", "showAllTimeHigh", "showAllTimeLow", "showAllTimeMean", "showHistoryHigh", "showHistoryLow", "showHistoryMean", "showGoal", "showTargetLower", "showTargetUpper")

internal fun bodyVisibilityChoice(settings: BodyChartSettings, key: String, follows: Boolean, localValue: Boolean): BodyVisibility =
    settings.visibility[key] ?: if (follows) BodyVisibility.FOLLOW_GLOBAL else if (localValue) BodyVisibility.SHOW else BodyVisibility.HIDE

private fun bodyVisible(local: BodyChartSettings, key: String, follows: Boolean, localValue: Boolean, globalValue: Boolean): Boolean =
    when (bodyVisibilityChoice(local, key, follows, localValue)) {
        BodyVisibility.FOLLOW_GLOBAL -> globalValue
        BodyVisibility.SHOW -> true
        BodyVisibility.HIDE -> false
    }

private fun bodyTargetVisible(local: BodyChartSettings, global: BodyChartSettings, key: String, localValue: Boolean, globalValue: Boolean): Boolean {
    // Retain legacy flags when there are no individual overrides. Once a target option
    // is overridden, Follow global also respects the global target enable switch.
    val individual = listOf("showGoal", "showTargetLower", "showTargetUpper").any { it in local.visibility }
    return bodyVisible(local, key, local.globalTarget,
        localValue && (!individual || local.targetEnabled), globalValue && (!individual || global.targetEnabled))
}

private fun readBodySettings(prefs: android.content.SharedPreferences, prefix: String): BodyChartSettings = BodyChartSettings(
    visibility = bodyVisibilityKeys.mapNotNull { key ->
        prefs.getString("${prefix}visibility_$key", null)?.let { saved ->
            runCatching { BodyVisibility.valueOf(saved) }.getOrNull()?.let { key to it }
        }
    }.toMap(),
    showGoal = prefs.getBoolean("${prefix}show_goal", true),
    target = prefs.getString("${prefix}target", null)?.toDoubleOrNull(),
    acceptance = prefs.getString("${prefix}acceptance", null)?.toDoubleOrNull(),
    targetEnabled = prefs.getBoolean("${prefix}target_enabled", prefs.contains("${prefix}target")),
    showTargetLower = prefs.getBoolean("${prefix}target_lower", true),
    showTargetUpper = prefs.getBoolean("${prefix}target_upper", true),
    showFit = prefs.getBoolean("${prefix}fit", true),
    averaging = prefs.getBoolean("${prefix}averaging", false),
    bins = prefs.getInt("${prefix}bins", 20).coerceIn(1, 1000),
    showRawData = prefs.getBoolean("${prefix}show_raw_data", !prefs.getBoolean("${prefix}averaging", false)),
    periodAveraging = prefs.getBoolean("${prefix}period_averaging", false),
    averagePeriodDays = prefs.getString("${prefix}average_period_days", null)?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0 && it <= 36500 } ?: 7.0,
    averageFractionPercent = prefs.getString("${prefix}average_fraction_percent", null)?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0 && it <= 100 } ?: (100.0 / prefs.getInt("${prefix}bins", 20).coerceIn(1, 1000)),
    historyPercentile = prefs.getString("${prefix}history_percentile", null)?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it in 0.0..50.0 } ?: 5.0,
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
    val visibilityEditor = prefs.edit()
    bodyVisibilityKeys.forEach { key -> visibilityEditor.putString("${prefix}visibility_$key", value.visibility[key]?.name) }
    visibilityEditor.putBoolean("${prefix}show_goal", value.showGoal).apply()
    prefs.edit().putString("${prefix}target", value.target?.toString())
        .putString("${prefix}acceptance", value.acceptance?.toString())
        .putBoolean("${prefix}target_enabled", value.targetEnabled)
        .putBoolean("${prefix}target_lower", value.showTargetLower)
        .putBoolean("${prefix}target_upper", value.showTargetUpper)
        .putString("${prefix}y_min", value.yMin?.toString()).putString("${prefix}y_max", value.yMax?.toString())
        .putBoolean("${prefix}fit", value.showFit).putBoolean("${prefix}averaging", value.averaging)
        .putInt("${prefix}bins", value.bins).putString("${prefix}interval", value.interval.storageValue)
        .putBoolean("${prefix}show_raw_data", value.showRawData).putBoolean("${prefix}period_averaging", value.periodAveraging)
        .putString("${prefix}average_period_days", value.averagePeriodDays.toString())
        .putString("${prefix}average_fraction_percent", value.averageFractionPercent.toString())
        .putString("${prefix}history_percentile", value.historyPercentile.toString())
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
    showFit = bodyVisible(local, "showFit", local.globalFit, local.showFit, global.showFit),
    averaging = bodyVisible(local, "averaging", local.globalAveraging, local.averaging, global.averaging),
    bins = if (local.globalAveraging) global.bins else local.bins,
    showRawData = bodyVisible(local, "showRawData", local.globalAveraging, local.showRawData, global.showRawData),
    periodAveraging = bodyVisible(local, "periodAveraging", local.globalAveraging, local.periodAveraging, global.periodAveraging),
    averagePeriodDays = if (local.globalAveraging) global.averagePeriodDays else local.averagePeriodDays,
    averageFractionPercent = if (local.globalAveraging) global.averageFractionPercent else local.averageFractionPercent,
    historyPercentile = if (local.globalHighLow) global.historyPercentile else local.historyPercentile,
    showGoal = bodyTargetVisible(local, global, "showGoal", local.showGoal, global.showGoal),
    targetEnabled = (if (local.globalTarget) global.targetEnabled else local.targetEnabled) ||
        listOf("showGoal", "showTargetLower", "showTargetUpper").any { local.visibility[it] == BodyVisibility.SHOW },
    showTargetLower = bodyTargetVisible(local, global, "showTargetLower", local.showTargetLower, global.showTargetLower),
    showTargetUpper = bodyTargetVisible(local, global, "showTargetUpper", local.showTargetUpper, global.showTargetUpper),
    showIntervalCount = bodyVisible(local, "showIntervalCount", local.globalSummary, local.showIntervalCount, global.showIntervalCount),
    showTotalCount = bodyVisible(local, "showTotalCount", local.globalSummary, local.showTotalCount, global.showTotalCount),
    showMean = bodyVisible(local, "showMean", local.globalSummary, local.showMean, global.showMean),
    showRange = bodyVisible(local, "showRange", local.globalSummary, local.showRange, global.showRange),
    showAllTimeHigh = bodyVisible(local, "showAllTimeHigh", local.globalHighLow, local.showAllTimeHigh, global.showAllTimeHigh),
    showAllTimeLow = bodyVisible(local, "showAllTimeLow", local.globalHighLow, local.showAllTimeLow, global.showAllTimeLow),
    showAllTimeMean = bodyVisible(local, "showAllTimeMean", local.globalHighLow, local.showAllTimeMean, global.showAllTimeMean),
    showHistoryHigh = bodyVisible(local, "showHistoryHigh", local.globalHighLow, local.showHistoryHigh, global.showHistoryHigh),
    showHistoryLow = bodyVisible(local, "showHistoryLow", local.globalHighLow, local.showHistoryLow, global.showHistoryLow),
    showHistoryMean = bodyVisible(local, "showHistoryMean", local.globalHighLow, local.showHistoryMean, global.showHistoryMean),
    highLowFollowPlot = if (local.globalHighLow) global.highLowFollowPlot else local.highLowFollowPlot,
    highLowRange = if (local.globalHighLow) global.highLowRange else local.highLowRange,
    highLowStart = if (local.globalHighLow) global.highLowStart else local.highLowStart,
    highLowEnd = if (local.globalHighLow) global.highLowEnd else local.highLowEnd,
)

private enum class CustomRangeMode { INTERVAL, DURATION }

private data class BodyFit(
    val slopePerDay: Double,
    val fittedLast: Double,
)

@Composable
fun BodyOverviewScreen(
    snapshot: BodyDataSnapshot,
    onAddWeight: () -> Unit,
    syncStatus: String,
    isSyncing: Boolean,
    modifier: Modifier = Modifier,
    onSync: () -> Unit,
    onAutoSync: () -> Unit,
    onGym: () -> Unit,
) {
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val autoSync by rememberUpdatedState(onAutoSync)
    androidx.compose.runtime.LaunchedEffect(lifecycleState) {
        if (lifecycleState != androidx.lifecycle.Lifecycle.State.RESUMED) return@LaunchedEffect
        while (true) {
            autoSync()
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("body_graph_settings", 0) }
    var plotLayout by remember {
        mutableStateOf(bodyPlotLayout(preferences.getString("plot_order", null),
            preferences.getStringSet("hidden_plots", emptySet()).orEmpty()))
    }
    var showPlotLayout by remember { mutableStateOf(false) }
    var window by remember { mutableStateOf(ChartRange.fromStorage(preferences.getString("window", ChartRange.SIX_MONTHS.storageValue))) }
    var showTrend by remember { mutableStateOf(preferences.getBoolean("show_trend", true)) }
    var globalSettings by remember { mutableStateOf(readBodySettings(preferences, "global_").copy(showFit = showTrend)) }
    var showGlobalSettings by remember { mutableStateOf(false) }
    var showCustomRange by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf(preferences.getLong("custom_start", 0L).takeIf { it > 0L }) }
    var customEnd by remember { mutableStateOf(preferences.getLong("custom_end", 0L).takeIf { it > 0L }) }
    val settings = remember {
        mutableStateMapOf<BodyMetric, BodyChartSettings>().apply {
            defaultBodyPlotOrder.forEach { metric ->
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
            globalSettings = globalSettings.copy(targetEnabled = globalSettings.targetEnabled || defaultBodyPlotOrder.any {
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
                    showTrendControl = false, rangeLabel = ::bodyPresetLabel, showExtendedYearRanges = false,
                    onWindowSelected = { preset ->
                        window = preset
                        preferences.edit().putString("window", preset.storageValue).apply()
                    },
                    onCustomSelected = { showCustomRange = true },
                    onTrendSelected = {},
                )
            }
            if (plotLayout.visibleOrder.isEmpty()) {
                item { Text("All body plots are hidden. Open Settings, then Order and hide plots to show them.",
                    Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            plotLayout.visibleOrder.forEach { metric ->
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
            FilledTonalButton(onClick = onGym, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Gym", maxLines = 1, softWrap = false) }
            FilledTonalButton(onClick = onAddWeight, modifier = Modifier.weight(1.4f), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Add weight", maxLines = 1, softWrap = false) }
            FilledTonalButton(onClick = { showGlobalSettings = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Settings", maxLines = 1, softWrap = false) }
        }
    }
    if (showGlobalSettings) BodyChartSettingsDialog(
        metric = null, current = globalSettings.copy(interval = window, customStart = customStart, customEnd = customEnd), onDismiss = { showGlobalSettings = false },
        plotControls = {
            FilledTonalButton(onClick = { showPlotLayout = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Order and hide plots")
            }
        },
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
    if (showPlotLayout) BodyPlotLayoutDialog(plotLayout, onDismiss = { showPlotLayout = false }) { layout ->
        plotLayout = layout
        preferences.edit().putString("plot_order", layout.order.joinToString(",") { it.name })
            .putStringSet("hidden_plots", layout.hidden.map { it.name }.toSet()).apply()
        showPlotLayout = false
    }
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
    val referenceLabel = bodyHistoryLabel(referenceRange)
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
            if (effective.targetEnabled && effective.showGoal && effective.target != null) {
                Text(
                    "Target: ${formatBodyValue(effective.target)}" +
                        (effective.acceptance?.let { "±${formatBodyValue(it)}" } ?: "") + " ${metric.unit}" +
                        (targetEta?.let { " · Est. ${formatBodyEta(it)}" } ?: ""),
                    color = targetColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            highLow.forEach { reference ->
                AutoFitText(bodyReferenceText(reference, metric.unit),
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
    if (editing) BodyChartSettingsDialog(metric, settings, { editing = false }, inherited = globalSettings,
        preview = { draft ->
            val resolved = resolveBodySettings(draft, globalSettings)
            val previewWindow = if (draft.globalInterval) window else ChartWindow(
                chartWindowDuration(draft.interval),
                if (draft.interval == ChartRange.CUSTOM) draft.customEnd else System.currentTimeMillis(),
                if (draft.interval == ChartRange.CUSTOM) draft.customStart else null,
                "preview:${draft.interval}:${draft.customStart}:${draft.customEnd}")
            val previewRange = if (raw.isEmpty()) null else calculateVisibleTimeRange(raw.first().timestamp, raw.last().timestamp, previewWindow)
            val previewValues = sorted.filter { previewRange != null && it.measuredAt in previewRange.start..previewRange.end }
            val previewFit = bodyLinearFit(previewValues)
            val previewFitPoints = if (resolved.showFit && previewValues.size >= 2 && previewRange != null)
                listOf(previewRange.start, previewRange.end).map { time -> TimeValue(time,
                    previewFit.fittedLast + previewFit.slopePerDay * (time - previewValues.last().measuredAt) / DAY_MILLIS.toDouble()) }
                else emptyList()
            val references = bodyHighLowReferences(raw, resolved, previewWindow.endMillis ?: System.currentTimeMillis(), previewRange,
                bodyHistoryLabel(if (draft.globalInterval) globalRange else draft.interval))
            BodyGraph(sorted, previewFitPoints, resolved, primary, fitColor, targetColor, previewWindow,
                references.flatMap { reference -> reference.visibleLines.map { line -> TimeBand(line.value, 0.0,
                    if (reference.allTime) allTimeColor else historyColor, dashed = line.dashed) } }, height = 220.dp)
        }) {
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
    height: Dp = 132.dp,
) {
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("No measurements yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val raw = remember(values) { values.map { TimeValue(it.measuredAt, it.value) } }
    val range = calculateVisibleTimeRange(raw.first().timestamp, raw.last().timestamp, window)
    if (pointsInWindow(raw, range).isEmpty()) {
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("No measurements in this interval.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val periodAverage = remember(raw, range, settings.periodAveraging, settings.averagePeriodDays) {
        if (settings.periodAveraging) bodyMovingAverage(raw, range, (settings.averagePeriodDays * DAY_MILLIS).toLong(), includeBoundaryNeighbors = true) else emptyList()
    }
    val fractionAverage = remember(raw, range, settings.averaging, settings.averageFractionPercent) {
        if (settings.averaging) bodyMovingAverage(raw, range, (range.duration * settings.averageFractionPercent / 100).toLong(), includeBoundaryNeighbors = true) else emptyList()
    }
    val periodColor = Color(0xFFF5F5F5)
    val fractionColor = Color(0xFFF48FB1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (settings.showRawData) Text("Raw data", Modifier.weight(1f), color = dataColor, style = MaterialTheme.typography.labelSmall)
        if (settings.periodAveraging) Text("Avg ${formatChartAxisValue(settings.averagePeriodDays)}d", Modifier.weight(1f), color = periodColor, style = MaterialTheme.typography.labelSmall)
        if (settings.averaging) Text("Avg ${formatChartAxisValue(settings.averageFractionPercent)}% of interval", Modifier.weight(1f), color = fractionColor, style = MaterialTheme.typography.labelSmall)
    }
    val series = remember(raw, settings.showRawData, periodAverage, fractionAverage, dataColor, fit, fitColor) {
        buildList {
            if (settings.showRawData) add(TimeSeries(raw, dataColor))
            if (periodAverage.isNotEmpty()) add(TimeSeries(periodAverage, periodColor, strokeWidth = 3f, showPoints = periodAverage.size == 1))
            if (fractionAverage.isNotEmpty()) add(TimeSeries(fractionAverage, fractionColor, strokeWidth = 3f, showPoints = fractionAverage.size == 1))
            if (fit.isNotEmpty()) {
                add(
                    TimeSeries(fit, fitColor, strokeWidth = 2.5f, dashed = true, showPoints = false),
                )
            }
        }
    }
    if (series.isEmpty() && highLowBands.isEmpty() && bodyTargetBand(settings, targetColor) == null) {
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Data series are hidden. Tap the plot to show raw data or a moving average.", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    TimeWindowChart(
        series = series,
        height = height,
        window = window.copy(startMillis = range.start, endMillis = range.end),
        bands = listOfNotNull(bodyTargetBand(settings, targetColor)) + highLowBands,
        compactYAxis = true,
        yMin = settings.yMin,
        yMax = settings.yMax,
        bodyCalendarLabels = true,
        mirrorLeftTicksOnRight = true,
    )
}

internal fun bodyTargetBand(settings: BodyChartSettings, color: Color): TimeBand? =
    settings.target?.takeIf { settings.targetEnabled && (settings.showGoal || settings.showTargetLower || settings.showTargetUpper) }?.let { target ->
        TimeBand(target, settings.acceptance ?: 0.0, color,
            showLower = settings.showTargetLower, showUpper = settings.showTargetUpper, showBoundaries = true, showCenter = settings.showGoal)
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
    // With onToggle the section collapses to its header plus a one-line summary of the current choice,
    // so the long settings list can be scanned without scrolling through every control.
    summary: String? = null,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .18f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                (if (onToggle != null) Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp).clickable(onClick = onToggle) else Modifier.fillMaxWidth()),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    if (!expanded && summary != null) Text(summary, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (badge != null) Text(badge,
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (onToggle != null) Text(if (expanded) "\u25B4" else "\u25BE", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                description?.let { BodySettingsHint(it) }
                content()
            }
        }
    }
}

@Composable
private fun BodyVisibilityControl(label: String, key: String, value: Boolean, follows: Boolean,
    global: Boolean, settings: BodyChartSettings, onChange: (BodyChartSettings) -> Unit, onBoolean: (Boolean) -> Unit) {
    if (global) SettingCheck(label, value, onBoolean) else {
        Row(Modifier.fillMaxWidth().testTag("visibility_$key"), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            val selected = bodyVisibilityChoice(settings, key, follows, value)
            // One compact segmented control per line instead of three wide stacked buttons.
            Row(Modifier.width(168.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(2.dp)) {
                BodyVisibility.entries.forEach { choice ->
                    val title = when (choice) { BodyVisibility.FOLLOW_GLOBAL -> "Global"; BodyVisibility.SHOW -> "Show"; BodyVisibility.HIDE -> "Hide" }
                    Text(title, Modifier.weight(1f).background(if (selected == choice) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(6.dp))
                        .selectable(selected = selected == choice, role = Role.RadioButton, onClick = { onChange(settings.copy(visibility = settings.visibility + (key to choice))) }).padding(vertical = 7.dp),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                        color = if (selected == choice) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
    plotControls: (@Composable () -> Unit)? = null,
    inherited: BodyChartSettings = BodyChartSettings(),
    preview: (@Composable (BodyChartSettings) -> Unit)? = null,
    onSave: (BodyChartSettings) -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    var periodDays by remember { mutableStateOf(formatChartAxisValue(current.averagePeriodDays)) }
    var fractionPercent by remember { mutableStateOf(formatChartAxisValue(current.averageFractionPercent)) }
    var historyPercent by remember { mutableStateOf(formatChartAxisValue(current.historyPercentile)) }
    var custom by remember { mutableStateOf(false) }
    var customHighLow by remember { mutableStateOf(false) }
    var yMin by remember { mutableStateOf(current.yMin?.let(::formatChartAxisValue).orEmpty()) }
    var yMax by remember { mutableStateOf(current.yMax?.let(::formatChartAxisValue).orEmpty()) }
    val global = metric == null
    val effectiveDraft = if (global) draft else resolveBodySettings(draft, inherited)
    fun number(text: String) = text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    val validPeriod = number(periodDays)?.takeIf { it > 0 && it <= 36500 }
    val validFraction = number(fractionPercent)?.takeIf { it > 0 && it <= 100 }
    val validHistoryPercent = number(historyPercent)?.takeIf { it in 0.0..50.0 }
    val averagesValid = (!effectiveDraft.periodAveraging || validPeriod != null) && (!effectiveDraft.averaging || validFraction != null)
    val historyPercentValid = !(effectiveDraft.showHistoryHigh || effectiveDraft.showHistoryLow) || validHistoryPercent != null
    val targetsValid = global || draft.globalTarget || !draft.targetEnabled || draft.target != null
    fun axisValue(text: String) = text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val minimum = axisValue(yMin)
    val maximum = axisValue(yMax)
    val boundsValid = (yMin.isBlank() || minimum != null) && (yMax.isBlank() || maximum != null && maximum > 0) &&
        (minimum == null || maximum == null || minimum < maximum)
    @Composable fun visibility(label: String, key: String, value: Boolean, follows: Boolean, change: (Boolean) -> Unit) {
        BodyVisibilityControl(label, key, value, follows, global, draft, { draft = it }, change)
    }
    fun source(followsGlobal: Boolean) = if (global) "Default" else if (followsGlobal) "Global" else "Custom"
    // Only the most-used section starts open; the others show a summary line until tapped.
    var expanded by remember { mutableStateOf(setOf("Time range")) }
    fun toggle(section: String): () -> Unit = { expanded = if (section in expanded) expanded - section else expanded + section }
    fun onOff(value: Boolean) = if (value) "on" else "off"
    val e = effectiveDraft
    val rangeSummary = if (e.interval == ChartRange.CUSTOM && e.customStart != null && e.customEnd != null)
        "${formatChartDate(e.customStart)} - ${formatChartDate(e.customEnd)}" else bodyPresetLabel(e.interval)
    val dataSummary = listOfNotNull("raw".takeIf { e.showRawData },
        "${formatChartAxisValue(validPeriod ?: e.averagePeriodDays)}-day avg".takeIf { e.periodAveraging },
        "${formatChartAxisValue(validFraction ?: e.averageFractionPercent)} % avg".takeIf { e.averaging }).joinToString(" · ").ifEmpty { "nothing shown" }
    val summaryCount = listOf(e.showIntervalCount, e.showTotalCount, e.showMean, e.showRange).count { it }
    val referenceCount = listOf(e.showAllTimeLow, e.showAllTimeMean, e.showAllTimeHigh, e.showHistoryLow, e.showHistoryMean, e.showHistoryHigh).count { it }
    val targetSummary = if (global) "Targets ${onOff(e.targetEnabled)}" else
        e.target?.takeIf { e.targetEnabled }?.let { "${formatBodyValue(it)}${e.acceptance?.let { a -> " ±${formatBodyValue(a)}" } ?: ""} ${metric!!.unit}" } ?: "No target"
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 640.dp).fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(metric?.let { "${it.label} plot" } ?: "Global plot settings")
                BodySettingsHint(if (global) "Defaults for every plot that follows global settings" else "Each item can follow the global setting, or be shown or hidden for this plot only")
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Collapsed sections keep the list short enough that the preview stays in view while editing.
                if (preview != null) {
                    preview(draft.copy(averagePeriodDays = validPeriod ?: current.averagePeriodDays, averageFractionPercent = validFraction ?: current.averageFractionPercent, yMin = minimum, yMax = maximum))
                    BodySettingsHint("Tap a section to change it. The preview updates live; Save applies.")
                }
                if (header != null) BodySettingsSection("Health Connect") { header() }
                if (plotControls != null) BodySettingsSection("Plots") { plotControls() }
                BodySettingsSection("Time range", "Which measurements appear in the plot", source(draft.globalInterval),
                    rangeSummary, "Time range" in expanded, toggle("Time range")) {
                    if (!global) BodySettingsInheritance(draft.globalInterval, { draft = draft.copy(globalInterval = it) })
                    if (global || !draft.globalInterval) {
                        ChartRangeControls(draft.interval, draft.showFit,
                            { draft = draft.copy(interval = it) }, { custom = true }, {}, showTrendControl = false,
                            rangeLabel = ::bodyPresetLabel, showExtendedYearRanges = false)
                        if (draft.interval == ChartRange.CUSTOM && draft.customStart != null && draft.customEnd != null) {
                            BodySettingsHint("${formatChartDate(draft.customStart!!)} - ${formatChartDate(draft.customEnd!!)}")
                        }
                    }
                }
                BodySettingsSection("Data and moving averages", "Show raw data, either average, or overlay all three", source(draft.globalAveraging),
                    dataSummary, "Data" in expanded, toggle("Data")) {
                    if (!global) BodySettingsInheritance(draft.globalAveraging, { draft = draft.copy(globalAveraging = it) })
                    run {
                        visibility("Show full raw data", "showRawData", draft.showRawData, draft.globalAveraging) { draft = draft.copy(showRawData = it) }
                        visibility("Moving average: fixed period", "periodAveraging", draft.periodAveraging, draft.globalAveraging) { draft = draft.copy(periodAveraging = it) }
                        if ((global || !draft.globalAveraging) && effectiveDraft.periodAveraging) OutlinedTextField(periodDays, { periodDays = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Period in days (e.g. 7)") }, isError = validPeriod == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        visibility("Moving average: fraction of interval", "averaging", draft.averaging, draft.globalAveraging) { draft = draft.copy(averaging = it) }
                        if ((global || !draft.globalAveraging) && effectiveDraft.averaging) OutlinedTextField(fractionPercent, { fractionPercent = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Window as % of displayed interval (0-100)") }, isError = validFraction == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        BodySettingsHint("Each point averages measurements from the preceding window, including earlier data just outside the plot. Fits and reference lines always use raw measurements.")
                    }
                }
                BodySettingsSection("Trend fit", "Show the direction of change", source(draft.globalFit),
                    "Fit line ${onOff(e.showFit)}", "Fit" in expanded, toggle("Fit")) {
                    if (!global) BodySettingsInheritance(draft.globalFit, { draft = draft.copy(globalFit = it) })
                    visibility("Show fit", "showFit", draft.showFit, draft.globalFit) { draft = draft.copy(showFit = it) }
                }
                BodySettingsSection("Plot summary", "Values shown above the chart", source(draft.globalSummary),
                    "$summaryCount of 4 values shown", "Summary" in expanded, toggle("Summary")) {
                    if (!global) BodySettingsInheritance(draft.globalSummary, { draft = draft.copy(globalSummary = it) })
                    run {
                        visibility("Count in interval", "showIntervalCount", draft.showIntervalCount, draft.globalSummary) { draft = draft.copy(showIntervalCount = it) }
                        visibility("Total count", "showTotalCount", draft.showTotalCount, draft.globalSummary) { draft = draft.copy(showTotalCount = it) }
                        visibility("Interval mean", "showMean", draft.showMean, draft.globalSummary) { draft = draft.copy(showMean = it) }
                        visibility("Interval range", "showRange", draft.showRange, draft.globalSummary) { draft = draft.copy(showRange = it) }
                    }
                }
                if (!global) BodySettingsSection("Y axis (${metric!!.unit})", "Leave either limit blank for automatic scaling",
                    if (minimum != null || maximum != null) "Custom" else "Auto",
                    "${minimum?.let(::formatChartAxisValue) ?: "auto"} – ${maximum?.let(::formatChartAxisValue) ?: "auto"}", "Axis" in expanded, toggle("Axis")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(yMin, { yMin = it }, modifier = Modifier.weight(1f), label = { Text("Minimum") },
                            isError = !boundsValid && yMin.isNotBlank(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(yMax, { yMax = it }, modifier = Modifier.weight(1f), label = { Text("Maximum") },
                            isError = !boundsValid && yMax.isNotBlank(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    if (!boundsValid) Text("Use nonnegative values; maximum must exceed minimum.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                BodySettingsSection("Reference lines", "Solid low/high lines and dashed means", source(draft.globalHighLow),
                    if (referenceCount == 0) "None shown" else "$referenceCount line${if (referenceCount == 1) "" else "s"} shown", "References" in expanded, toggle("References")) {
                    if (!global) BodySettingsInheritance(draft.globalHighLow, { draft = draft.copy(globalHighLow = it) })
                    run {
                        BodyReferenceGroup("All-time", Color(0xFFCE93D8)) {
                            BodySettingsHint("Every recorded measurement")
                            visibility("Low", "showAllTimeLow", draft.showAllTimeLow, draft.globalHighLow) { draft = draft.copy(showAllTimeLow = it) }
                            visibility("Mean", "showAllTimeMean", draft.showAllTimeMean, draft.globalHighLow) { draft = draft.copy(showAllTimeMean = it) }
                            visibility("High", "showAllTimeHigh", draft.showAllTimeHigh, draft.globalHighLow) { draft = draft.copy(showAllTimeHigh = it) }
                        }
                        BodyReferenceGroup("History window", Color(0xFFFF8A65)) {
                            visibility("Low", "showHistoryLow", draft.showHistoryLow, draft.globalHighLow) { draft = draft.copy(showHistoryLow = it) }
                            visibility("Mean", "showHistoryMean", draft.showHistoryMean, draft.globalHighLow) { draft = draft.copy(showHistoryMean = it) }
                            visibility("High", "showHistoryHigh", draft.showHistoryHigh, draft.globalHighLow) { draft = draft.copy(showHistoryHigh = it) }
                            if ((global || !draft.globalHighLow) && (effectiveDraft.showHistoryHigh || effectiveDraft.showHistoryLow)) {
                                OutlinedTextField(historyPercent, { historyPercent = it }, modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Low/high percentile cutoff % (0-50)") }, isError = validHistoryPercent == null,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                BodySettingsHint("5 uses the 5th and 95th percentiles. 0 uses exact low/high values. All-time references always use exact records.")
                            }
                            if ((global || !draft.globalHighLow) && (effectiveDraft.showHistoryHigh || effectiveDraft.showHistoryLow || effectiveDraft.showHistoryMean)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
                                SettingCheck("Follow plot interval", draft.highLowFollowPlot) { draft = draft.copy(highLowFollowPlot = it) }
                                if (!draft.highLowFollowPlot) {
                                    ChartRangeControls(draft.highLowRange, false,
                                        { draft = draft.copy(highLowRange = it) }, { customHighLow = true }, {}, showTrendControl = false,
                                        rangeLabel = ::bodyPresetLabel, showExtendedYearRanges = false)
                                    if (draft.highLowRange == ChartRange.CUSTOM && draft.highLowStart != null && draft.highLowEnd != null) {
                                        BodySettingsHint("${formatChartDate(draft.highLowStart!!)} - ${formatChartDate(draft.highLowEnd!!)}")
                                    }
                                }
                                BodySettingsHint(if (draft.highLowFollowPlot) "Uses the plot's exact interval, including Custom and All." else "Ends at the plot's end date. Custom uses your chosen dates.")
                            }
                        }
                    }
                }
                BodySettingsSection("Target", "Green target and acceptance band", source(draft.globalTarget),
                    targetSummary, "Target" in expanded, toggle("Target")) {
                    if (!global) BodySettingsInheritance(draft.globalTarget, { draft = draft.copy(globalTarget = it) }, "Use global visibility")
                    if (global) {
                        SettingCheck("Show targets", draft.targetEnabled) { draft = draft.copy(targetEnabled = it) }
                        BodySettingsHint("Controls visibility for plots that follow this setting. Target values remain specific to each plot.")
                    } else BodyTargetFields(metric!!, draft) { draft = it }
                    visibility("Show goal", "showGoal", draft.showGoal, draft.globalTarget) { draft = draft.copy(showGoal = it) }
                    run {
                        visibility("Show goal - acceptance", "showTargetLower", draft.showTargetLower, draft.globalTarget) { draft = draft.copy(showTargetLower = it) }
                        visibility("Show goal + acceptance", "showTargetUpper", draft.showTargetUpper, draft.globalTarget) { draft = draft.copy(showTargetUpper = it) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = {
            onSave(draft.copy(averagePeriodDays = validPeriod ?: current.averagePeriodDays,
                averageFractionPercent = validFraction ?: current.averageFractionPercent,
                historyPercentile = validHistoryPercent ?: current.historyPercentile, yMin = minimum, yMax = maximum))
        }, enabled = boundsValid && targetsValid && averagesValid && historyPercentValid) { Text("Save") } },
        dismissButton = { Row {
            // Per-plot only: drop every override but keep this plot's own target values.
            if (!global) TextButton(onClick = {
                draft = BodyChartSettings(target = draft.target, acceptance = draft.acceptance, targetEnabled = draft.targetEnabled)
                yMin = ""; yMax = ""
            }) { Text("Reset") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        } },
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

    OutlinedTextField(target, {
        target = it
        onChange(current.copy(targetEnabled = true, target = it.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value.isFinite() && value >= 0 }))
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
    if (totalDays in 7L..70L) return quantity(totalDays / 7, "week")
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
internal fun formatBodyValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
