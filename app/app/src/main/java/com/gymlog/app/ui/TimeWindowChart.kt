package com.gymlog.app.ui

import com.gymlog.app.data.ChartRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

private fun pointsInWindow(points: List<TimeValue>, range: VisibleTimeRange): List<TimeValue> {
    val visible = points.filter { it.timestamp in range.start..range.end }
    val firstVisible = visible.firstOrNull() ?: return visible
    if (firstVisible.timestamp == range.start) return visible
    val previous = points.lastOrNull { it.timestamp < range.start } ?: return visible
    val elapsed = (firstVisible.timestamp - previous.timestamp).coerceAtLeast(1L)
    val fraction = (range.start - previous.timestamp).toDouble() / elapsed
    val boundaryValue = previous.value + (firstVisible.value - previous.value) * fraction
    return listOf(TimeValue(range.start, boundaryValue)) + visible
}

internal fun trendTimeSeries(
    points: List<TimeValue>,
    color: Color,
    axis: PlotAxis,
    window: ChartWindow? = null,
): TimeSeries {
    val sorted = points.sortedBy(TimeValue::timestamp)
    val range = window?.let {
        sorted.firstOrNull()?.let { first -> calculateVisibleTimeRange(first.timestamp, sorted.last().timestamp, it) }
    }
    val fittedPoints = range?.let { selected -> sorted.filter { it.timestamp in selected.start..selected.end } } ?: sorted
    if (fittedPoints.size < 2) return TimeSeries(emptyList(), color, axis, showPoints = false)
    val origin = fittedPoints.first().timestamp
    val x = fittedPoints.map { (it.timestamp - origin) / DAY_MILLIS_SHARED.toDouble() }
    val meanX = x.average()
    val meanY = fittedPoints.map(TimeValue::value).average()
    val denominator = x.sumOf { value -> (value - meanX) * (value - meanX) }
    if (denominator == 0.0) return TimeSeries(emptyList(), color, axis, showPoints = false)
    val slope = fittedPoints.indices.sumOf { index ->
        (x[index] - meanX) * (fittedPoints[index].value - meanY)
    } / denominator
    val intercept = meanY - slope * meanX
    val lineTimes = range?.let { listOf(it.start, it.end) }
        ?: listOf(fittedPoints.first().timestamp, fittedPoints.last().timestamp)
    return TimeSeries(
        points = lineTimes.map { timestamp ->
            TimeValue(timestamp, intercept + slope * (timestamp - origin) / DAY_MILLIS_SHARED.toDouble())
        },
        color = color.copy(alpha = 0.62f),
        axis = axis,
        strokeWidth = 2.5f,
        dashed = true,
        showPoints = false,
    )
}

internal fun chartWindowDuration(window: ChartRange): Long? {
    val days = when (window) {
        ChartRange.ONE_WEEK -> 7L
        ChartRange.TWO_WEEKS -> 14L
        ChartRange.THREE_WEEKS -> 21L
        ChartRange.ONE_MONTH -> 30L
        ChartRange.TWO_MONTHS -> 61L
        ChartRange.THREE_MONTHS -> 91L
        ChartRange.FOUR_MONTHS -> 122L
        ChartRange.SIX_MONTHS -> 183L
        ChartRange.ONE_YEAR -> 365L
        ChartRange.TWO_YEARS -> 730L
        ChartRange.THREE_YEARS -> 1_095L
        ChartRange.ALL, ChartRange.CUSTOM -> return null
    }
    return days * DAY_MILLIS_SHARED
}

@Composable
internal fun ChartRangeControls(
    selectedWindow: ChartRange,
    showTrend: Boolean,
    onWindowSelected: (ChartRange) -> Unit,
    onCustomSelected: () -> Unit,
    onTrendSelected: () -> Unit,
    modifier: Modifier = Modifier,
    trendLabel: String = "Trend",
    showTrendControl: Boolean = true,
) {
    val rows = listOf(
        listOf(ChartRange.ONE_WEEK, ChartRange.TWO_WEEKS, ChartRange.THREE_WEEKS, ChartRange.ONE_MONTH, ChartRange.TWO_MONTHS, ChartRange.THREE_MONTHS, ChartRange.FOUR_MONTHS),
        listOf(ChartRange.SIX_MONTHS, ChartRange.ONE_YEAR, ChartRange.TWO_YEARS, ChartRange.THREE_YEARS, ChartRange.ALL, ChartRange.CUSTOM, null),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { range ->
                    val selected = if (range == null) showTrend else selectedWindow == range
                    val label = when (range) {
                        ChartRange.ALL -> "All"
                        ChartRange.CUSTOM -> "Custom"
                        null -> trendLabel
                        else -> range.storageValue
                    }
                    if (range == null && !showTrendControl) Spacer(Modifier.weight(1f)) else Text(
                        text = label,
                        modifier = Modifier.weight(1f)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(7.dp),
                            )
                            .clickable {
                                when (range) {
                                    ChartRange.CUSTOM -> onCustomSelected()
                                    null -> onTrendSelected()
                                    else -> onWindowSelected(range)
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
    width: Dp,
    height: Dp,
    endAligned: Boolean,
) {
    val labels = range.ticks.map(::formatChartAxisValue)
    Column(
        Modifier.width(width).height(height),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = if (endAligned) Alignment.End else Alignment.Start,
    ) {
        labels.asReversed().forEach { label ->
            Text(label, color = color, maxLines = 1, style = MaterialTheme.typography.labelSmall,
                textAlign = if (endAligned) TextAlign.End else TextAlign.Start)
        }
    }
}

@Composable
private fun ChartDateLabels(start: Long, end: Long, width: Dp) {
    Box(Modifier.width(width).height(20.dp)) {
        Text(formatChartDate(start), Modifier.align(Alignment.TopStart), maxLines = 1, style = MaterialTheme.typography.labelSmall)
        Text(formatChartDate(start + (end - start) / 2L), Modifier.align(Alignment.TopCenter), maxLines = 1, style = MaterialTheme.typography.labelSmall)
        Text(formatChartDate(end), Modifier.align(Alignment.TopEnd), maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
}

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
    val calendarTicks = calendarTimeTicks(visibleRange)
    fun visibleValues(axis: PlotAxis) = sortedSeries.filter { it.axis == axis }
        .flatMap { pointsInWindow(it.points, visibleRange) }
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
        val labelWidth = 34.dp
        val labelGap = 3.dp
        val tickWidth = 6.dp
        val rightLabelWidth = if (hasRightAxis) 34.dp else 0.dp
        val rightLabelGap = if (hasRightAxis) labelGap else 0.dp
        val rightTickWidth = if (hasRightAxis) tickWidth else 0.dp
        val plotWidth = (
            maxWidth - labelWidth - labelGap - tickWidth - rightTickWidth - rightLabelGap - rightLabelWidth
        ).coerceAtLeast(1.dp)
        Column {
            Row {
                ChartAxisLabels(leftRange, leftColor, labelWidth, height, true)
                Box(Modifier.width(labelGap))
                Canvas(Modifier.width(tickWidth).height(height)) {
                    repeat(leftRange.ticks.size) { index ->
                        val y = CHART_INSET + index * (size.height - CHART_INSET * 2f) / (leftRange.ticks.size - 1)
                        drawLine(leftAxisColor, Offset(size.width, y), Offset(0f, y), strokeWidth = 3.5f)
                    }
                }
                Box(Modifier.width(plotWidth).height(height)) {
                    Canvas(Modifier.fillMaxSize()) {
                        repeat(leftRange.ticks.size) { index ->
                            val y = CHART_INSET + index * (size.height - CHART_INSET * 2f) / (leftRange.ticks.size - 1)
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.2f)
                        }
                        calendarTicks.forEach { tick ->
                            val x = ((tick.timestamp - visibleStart).toDouble() / visibleRange.duration * size.width).toFloat()
                            drawLine(gridColor.copy(alpha = if (tick.major) 0.72f else 0.28f), Offset(x, 0f), Offset(x, size.height), strokeWidth = if (tick.major) 2.2f else 1f)
                        }
                        drawRect(xAxisColor, style = Stroke(width = 2.6f))
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        fun x(timestamp: Long) =
                            ((timestamp - visibleStart).toDouble() / visibleRange.duration * size.width).toFloat()
                        fun y(value: Double, range: AxisRange) = size.height - CHART_INSET -
                            ((value - range.min) / range.span).toFloat() * (size.height - CHART_INSET * 2f)
                        clipRect(top = CHART_INSET, bottom = size.height - CHART_INSET) {
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
                                val visibleData = item.points.filter { it.timestamp in visibleStart..visibleEnd }
                                val points = pointsInWindow(item.points, visibleRange)
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
                                if (item.showPoints) visibleData.forEach {
                                    drawCircle(item.color, item.pointRadius, Offset(x(it.timestamp), y(it.value, axisRange)))
                                }
                            }
                        }
                    }
                }
                if (hasRightAxis) {
                    Canvas(Modifier.width(rightTickWidth).height(height)) {
                        repeat(rightRange.ticks.size) { index ->
                            val y = CHART_INSET + index * (size.height - CHART_INSET * 2f) / (rightRange.ticks.size - 1)
                            drawLine(rightAxisColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.5f)
                        }
                    }
                    Box(Modifier.width(rightLabelGap))
                    ChartAxisLabels(rightRange, rightColor!!, rightLabelWidth, height, false)
                }
            }
            Row {
                Box(Modifier.width(labelWidth + labelGap + tickWidth))
                Column {
                    Canvas(Modifier.width(plotWidth).height(8.dp)) {
                        calendarTicks.forEach { tick ->
                            val x = ((tick.timestamp - visibleStart).toDouble() / visibleRange.duration * size.width).toFloat()
                            drawLine(xAxisColor, Offset(x, 0f), Offset(x, if (tick.major) size.height else size.height * 0.5f),
                                strokeWidth = if (tick.major) 3.2f else 1.8f)
                        }
                    }
                    ChartDateLabels(visibleStart, visibleEnd, plotWidth)
                }
            }
        }
    }
}
