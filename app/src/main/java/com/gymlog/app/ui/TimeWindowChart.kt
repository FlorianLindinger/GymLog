package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

internal data class TimeValue(val timestamp: Long, val value: Double)

internal enum class PlotAxis { LEFT, RIGHT, INDEPENDENT }

internal data class TimeSeries(
    val points: List<TimeValue>,
    val color: Color,
    val axis: PlotAxis = PlotAxis.LEFT,
    val strokeWidth: Float = 3.5f,
    val pointRadius: Float = 4.5f,
    val dashed: Boolean = false,
    val showPoints: Boolean = true,
)

internal data class TimeBand(val center: Double, val tolerance: Double, val color: Color)

internal data class ChartWindow(
    val durationMillis: Long?,
    val endMillis: Long? = null,
    val startMillis: Long? = null,
    val key: Any,
)

internal data class VisibleTimeRange(val start: Long, val end: Long) {
    val duration: Long get() = (end - start).coerceAtLeast(1L)
}

internal fun calculateVisibleTimeRange(dataStart: Long, dataEnd: Long, window: ChartWindow): VisibleTimeRange {
    if (window.startMillis != null && window.endMillis != null) {
        return VisibleTimeRange(
            minOf(window.startMillis, window.endMillis - 1L),
            maxOf(window.startMillis + 1L, window.endMillis),
        )
    }
    if (window.durationMillis == null) return VisibleTimeRange(dataStart, maxOf(dataStart + 1L, dataEnd))
    val end = window.endMillis ?: dataEnd
    return VisibleTimeRange(end - window.durationMillis.coerceAtLeast(1L), end)
}

private data class AxisRange(val min: Double, val max: Double, val ticks: List<Double>) {
    val span: Double get() = (max - min).coerceAtLeast(1e-9)
}

private fun niceCeiling(value: Double): Double {
    if (!value.isFinite() || value <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val normalized = value / magnitude
    val nice = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 2.5 -> 2.5
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

private fun chartAxisRange(values: List<Double>): AxisRange {
    val finite = values.filter(Double::isFinite)
    if (finite.isEmpty()) return AxisRange(0.0, 1.0, listOf(0.0, 0.25, 0.5, 0.75, 1.0))
    var rawMin = finite.min()
    var rawMax = finite.max()
    val rawSpan = rawMax - rawMin
    val padding = if (rawSpan == 0.0) maxOf(abs(rawMax) * 0.02, 0.5) else rawSpan * 0.02
    rawMin -= padding
    rawMax += padding
    val step = niceCeiling((rawMax - rawMin) / (Y_TICK_COUNT - 1))
    var minimum = floor(rawMin / step) * step
    var maximum = minimum + step * (Y_TICK_COUNT - 1)
    if (maximum < rawMax) {
        maximum = ceil(rawMax / step) * step
        minimum = maximum - step * (Y_TICK_COUNT - 1)
    }
    return AxisRange(minimum, maximum, List(Y_TICK_COUNT) { minimum + step * it })
}

internal fun trendTimeSeries(points: List<TimeValue>, color: Color, axis: PlotAxis): TimeSeries {
    if (points.size < 2) return TimeSeries(points, color, axis, showPoints = false)
    val origin = points.first().timestamp
    val x = points.map { (it.timestamp - origin) / DAY_MILLIS_SHARED.toDouble() }
    val meanX = x.average()
    val meanY = points.map(TimeValue::value).average()
    val denominator = x.sumOf { value -> (value - meanX) * (value - meanX) }
    if (denominator == 0.0) return TimeSeries(points, color, axis, showPoints = false)
    val slope = points.indices.sumOf { index ->
        (x[index] - meanX) * (points[index].value - meanY)
    } / denominator
    val intercept = meanY - slope * meanX
    return TimeSeries(
        points = points.indices.map { index -> TimeValue(points[index].timestamp, intercept + slope * x[index]) },
        color = color.copy(alpha = 0.62f),
        axis = axis,
        strokeWidth = 2.5f,
        dashed = true,
        showPoints = false,
    )
}

internal fun chartWindowDuration(window: String): Long? {
    val days = when (window) {
        "1W" -> 7L
        "2W" -> 14L
        "3W" -> 21L
        "1M" -> 30L
        "2M" -> 61L
        "3M" -> 91L
        "4M" -> 122L
        "6M" -> 183L
        "1Y" -> 365L
        "2Y" -> 730L
        "3Y" -> 1_095L
        "ALL" -> return null
        else -> return null
    }
    return days * DAY_MILLIS_SHARED
}

@Composable
internal fun ChartRangeControls(
    selectedWindow: String,
    showTrend: Boolean,
    onWindowSelected: (String) -> Unit,
    onCustomSelected: () -> Unit,
    onTrendSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf("1W", "2W", "3W", "1M", "2M", "3M", "4M"),
        listOf("6M", "1Y", "2Y", "3Y", "ALL", "CUSTOM", "TREND"),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { value ->
                    val selected = if (value == "TREND") showTrend else selectedWindow == value
                    val label = when (value) {
                        "ALL" -> "All"
                        "CUSTOM" -> "Custom"
                        "TREND" -> "Trend"
                        else -> value
                    }
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(7.dp),
                            )
                            .clickable {
                                when (value) {
                                    "CUSTOM" -> onCustomSelected()
                                    "TREND" -> onTrendSelected()
                                    else -> onWindowSelected(value)
                                }
                            }
                            .padding(vertical = 6.dp),
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartAxisLabels(
    range: AxisRange,
    color: Color,
    formatter: (Double) -> String,
    width: Dp,
    height: Dp,
    endAligned: Boolean,
) {
    Column(
        Modifier.width(width).height(height),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = if (endAligned) Alignment.End else Alignment.Start,
    ) {
        range.ticks.asReversed().forEach { tick ->
            Text(formatter(tick), color = color, maxLines = 1, style = MaterialTheme.typography.labelSmall,
                textAlign = if (endAligned) TextAlign.End else TextAlign.Start)
        }
    }
}

@Composable
private fun ChartDateLabels(start: Long, end: Long, width: Dp) {
    val formatter = DateTimeFormatter.ofPattern("d MMM yy")
    fun label(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(formatter)
    Box(Modifier.width(width).height(20.dp)) {
        Text(label(start), Modifier.align(Alignment.TopStart), maxLines = 1, style = MaterialTheme.typography.labelSmall)
        Text(label(start + (end - start) / 2L), Modifier.align(Alignment.TopCenter), maxLines = 1, style = MaterialTheme.typography.labelSmall)
        Text(label(end), Modifier.align(Alignment.TopEnd), maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
}

private const val Y_TICK_COUNT = 5
private const val X_GRID_COUNT = 7
private const val CHART_INSET = 6f
private const val DAY_MILLIS_SHARED = 24L * 60L * 60L * 1000L

@Composable
internal fun TimeWindowChart(
    series: List<TimeSeries>,
    window: ChartWindow,
    modifier: Modifier = Modifier,
    bands: List<TimeBand> = emptyList(),
    leftColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    rightColor: Color? = null,
    formatLeft: (Double) -> String,
    formatRight: (Double) -> String = formatLeft,
    height: Dp = 132.dp,
) {
    val sortedSeries = series.map { item -> item.copy(points = item.points.sortedBy(TimeValue::timestamp)) }
    val allPoints = sortedSeries.flatMap(TimeSeries::points)
    if (allPoints.isEmpty() && bands.isEmpty()) return
    val dataStart = allPoints.minOfOrNull(TimeValue::timestamp) ?: window.startMillis ?: window.endMillis ?: 0L
    val dataEnd = allPoints.maxOfOrNull(TimeValue::timestamp) ?: window.endMillis ?: dataStart + 1L
    val visibleRange = calculateVisibleTimeRange(dataStart, dataEnd, window)
    val visibleStart = visibleRange.start
    val visibleEnd = visibleRange.end
    fun visibleValues(axis: PlotAxis) = sortedSeries.filter { it.axis == axis }
        .flatMap(TimeSeries::points)
        .filter { it.timestamp in visibleStart..visibleEnd }
        .map(TimeValue::value)
    val bandValues = bands.flatMap { listOf(it.center - it.tolerance, it.center, it.center + it.tolerance) }
    val leftRange = chartAxisRange(visibleValues(PlotAxis.LEFT) + bandValues)
    val rightRange = chartAxisRange(visibleValues(PlotAxis.RIGHT))
    val independentRange = chartAxisRange(visibleValues(PlotAxis.INDEPENDENT))
    val hasRightAxis = rightColor != null && sortedSeries.any { it.axis == PlotAxis.RIGHT }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
    val xAxisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f)
    val leftAxisColor = leftColor.copy(alpha = 0.95f)
    val rightAxisColor = (rightColor ?: xAxisColor).copy(alpha = 0.95f)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelWidth = 39.dp
        val tickWidth = 9.dp
        val rightLabelWidth = if (hasRightAxis) 39.dp else 0.dp
        val rightTickWidth = if (hasRightAxis) tickWidth else 0.dp
        val plotWidth = (maxWidth - labelWidth - tickWidth - rightTickWidth - rightLabelWidth).coerceAtLeast(1.dp)
        Column {
            Row {
                ChartAxisLabels(leftRange, leftColor, formatLeft, labelWidth, height, true)
                Canvas(Modifier.width(tickWidth).height(height)) {
                    repeat(Y_TICK_COUNT) { index ->
                        val y = CHART_INSET + index * (size.height - CHART_INSET * 2f) / (Y_TICK_COUNT - 1)
                        drawLine(leftAxisColor, Offset(size.width, y), Offset(0f, y), strokeWidth = 3.5f)
                    }
                }
                Box(Modifier.width(plotWidth).height(height)) {
                    Canvas(Modifier.fillMaxSize()) {
                        repeat(Y_TICK_COUNT) { index ->
                            val y = CHART_INSET + index * (size.height - CHART_INSET * 2f) / (Y_TICK_COUNT - 1)
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.2f)
                        }
                        repeat(X_GRID_COUNT) { index ->
                            val x = index * size.width / (X_GRID_COUNT - 1)
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.2f)
                        }
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        fun x(timestamp: Long) =
                            ((timestamp - visibleStart).toDouble() / visibleRange.duration * size.width).toFloat()
                        fun y(value: Double, range: AxisRange) = size.height - CHART_INSET -
                            ((value - range.min) / range.span).toFloat() * (size.height - CHART_INSET * 2f)
                        bands.forEach { band ->
                            val top = y(band.center + band.tolerance, leftRange)
                            val bottom = y(band.center - band.tolerance, leftRange)
                            drawRect(
                                band.color.copy(alpha = 0.16f),
                                Offset(0f, top),
                                Size(size.width, (bottom - top).coerceAtLeast(1f)),
                            )
                            drawLine(
                                band.color,
                                Offset(0f, y(band.center, leftRange)),
                                Offset(size.width, y(band.center, leftRange)),
                                strokeWidth = 3.2f,
                            )
                        }
                        sortedSeries.forEach { item ->
                            val axisRange = when (item.axis) {
                                PlotAxis.LEFT -> leftRange
                                PlotAxis.RIGHT -> rightRange
                                PlotAxis.INDEPENDENT -> independentRange
                            }
                            val points = item.points.filter { it.timestamp in visibleStart..visibleEnd }
                                .map { Offset(x(it.timestamp), y(it.value, axisRange)) }
                            points.zipWithNext().forEach { (start, end) ->
                                drawLine(
                                    item.color,
                                    start,
                                    end,
                                    strokeWidth = item.strokeWidth,
                                    cap = StrokeCap.Round,
                                    pathEffect = if (item.dashed) {
                                        PathEffect.dashPathEffect(floatArrayOf(9f, 7f))
                                    } else {
                                        null
                                    },
                                )
                            }
                            if (item.showPoints) points.forEach { drawCircle(item.color, item.pointRadius, it) }
                        }
                    }
                }
                if (hasRightAxis) {
                    Canvas(Modifier.width(rightTickWidth).height(height)) {
                        repeat(Y_TICK_COUNT) { index ->
                            val y = CHART_INSET + index * (size.height - CHART_INSET * 2f) / (Y_TICK_COUNT - 1)
                            drawLine(rightAxisColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.5f)
                        }
                    }
                    ChartAxisLabels(rightRange, rightColor!!, formatRight, rightLabelWidth, height, false)
                }
            }
            Row {
                Box(Modifier.width(labelWidth + tickWidth))
                Column {
                    Canvas(Modifier.width(plotWidth).height(9.dp)) {
                        repeat(X_GRID_COUNT) { index ->
                            val x = index * size.width / (X_GRID_COUNT - 1)
                            drawLine(xAxisColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.5f)
                        }
                    }
                    ChartDateLabels(visibleStart, visibleEnd, plotWidth)
                }
            }
        }
    }
}
