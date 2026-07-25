package com.gymlog.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

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

internal data class TimeBand(
    val center: Double,
    val tolerance: Double,
    val color: Color,
)

internal data class ChartWindow(
    val durationMillis: Long?,
    val initialEnd: Long? = null,
    val key: Any,
)

internal data class ChartTimeline(
    val contentStart: Long,
    val contentEnd: Long,
    val viewportDuration: Long,
    val initialStart: Long,
) {
    val latestStart: Long get() = (contentEnd - viewportDuration).coerceAtLeast(contentStart)
}

internal fun calculateChartTimeline(dataStart: Long, dataEnd: Long, window: ChartWindow): ChartTimeline {
    val requestedDuration = window.durationMillis?.coerceAtLeast(1L)
    val contentEnd = maxOf(dataEnd, window.initialEnd ?: dataEnd)
    val contentStart = if (requestedDuration == null) dataStart else minOf(dataStart, contentEnd - requestedDuration)
    val contentDuration = (contentEnd - contentStart).coerceAtLeast(1L)
    val viewportDuration = requestedDuration?.coerceAtMost(contentDuration) ?: contentDuration
    val latestStart = (contentEnd - viewportDuration).coerceAtLeast(contentStart)
    val initialEnd = window.initialEnd?.coerceIn(contentStart + viewportDuration, contentEnd) ?: contentEnd
    return ChartTimeline(
        contentStart = contentStart,
        contentEnd = contentEnd,
        viewportDuration = viewportDuration,
        initialStart = (initialEnd - viewportDuration).coerceIn(contentStart, latestStart),
    )
}

internal fun panChartViewport(
    currentStart: Long,
    dragPixels: Float,
    viewportWidthPixels: Float,
    timeline: ChartTimeline,
): Long {
    if (timeline.latestStart == timeline.contentStart) return timeline.contentStart
    val deltaMillis = (-dragPixels / viewportWidthPixels.coerceAtLeast(1f) * timeline.viewportDuration).toLong()
    return (currentStart + deltaMillis).coerceIn(timeline.contentStart, timeline.latestStart)
}

private data class AxisRange(val min: Double, val max: Double) {
    val middle: Double get() = (min + max) / 2.0
    val span: Double get() = max - min
}

@Composable
private fun ChartAxisLabels(
    range: AxisRange,
    color: Color,
    formatter: (Double) -> String,
    width: Dp,
    height: Dp,
    endAligned: Boolean = true,
) {
    Box(Modifier.width(width).height(height)) {
        val textAlign = if (endAligned) TextAlign.End else TextAlign.Start
        Text(
            formatter(range.max),
            Modifier.align(if (endAligned) Alignment.TopEnd else Alignment.TopStart),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            textAlign = textAlign,
        )
        Text(
            formatter(range.middle),
            Modifier.align(if (endAligned) Alignment.CenterEnd else Alignment.CenterStart),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            textAlign = textAlign,
        )
        Text(
            formatter(range.min),
            Modifier.align(if (endAligned) Alignment.BottomEnd else Alignment.BottomStart),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun ChartDateLabels(start: Long, end: Long, width: Dp) {
    val formatter = DateTimeFormatter.ofPattern("d MMM yy")
    fun label(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(formatter)

    Box(Modifier.width(width).height(22.dp)) {
        Text(label(start), Modifier.align(Alignment.TopStart), style = MaterialTheme.typography.labelSmall)
        Text(label(start + (end - start) / 2L), Modifier.align(Alignment.TopCenter), style = MaterialTheme.typography.labelSmall)
        Text(label(end), Modifier.align(Alignment.TopEnd), style = MaterialTheme.typography.labelSmall)
    }
}

private fun chartAxisRange(values: List<Double>): AxisRange {
    val finite = values.filter(Double::isFinite)
    if (finite.isEmpty()) return AxisRange(0.0, 1.0)
    val rawMin = finite.min()
    val rawMax = finite.max()
    val rawSpan = rawMax - rawMin
    val padding = if (rawSpan > 0.0) rawSpan * 0.035 else maxOf(abs(rawMax) * 0.02, 0.5)
    return AxisRange(rawMin - padding, rawMax + padding)
}

internal fun chartWindowDuration(window: String): Long? {
    val days = when (window) {
        "1W" -> 7L
        "2W" -> 14L
        "3W" -> 21L
        "1M" -> 30L
        "3M" -> 90L
        "6M" -> 182L
        "1Y" -> 365L
        "2Y" -> 730L
        "3Y" -> 1_095L
        "ALL" -> return null
        else -> return null
    }
    return days * DAY_MILLIS_SHARED
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
    formatLeft: (Double) -> String,
    formatRight: (Double) -> String = formatLeft,
    height: Dp = 132.dp,
) {
    val sortedSeries = series.map { item -> item.copy(points = item.points.sortedBy(TimeValue::timestamp)) }
    val allPoints = sortedSeries.flatMap(TimeSeries::points)
    if (allPoints.isEmpty()) return

    val dataStart = allPoints.minOf(TimeValue::timestamp)
    val dataEnd = allPoints.maxOf(TimeValue::timestamp)
    val timeline = calculateChartTimeline(dataStart, dataEnd, window)
    val contentStart = timeline.contentStart
    val contentEnd = timeline.contentEnd
    val viewportDuration = timeline.viewportDuration

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val hasRightAxis = rightColor != null && sortedSeries.any { it.axis == PlotAxis.RIGHT }
        val leftAxisWidth = 36.dp
        val rightAxisWidth = if (hasRightAxis) 36.dp else 0.dp
        val plotViewportWidth = (maxWidth - leftAxisWidth - rightAxisWidth).coerceAtLeast(1.dp)
        val plotViewportWidthPx = with(LocalDensity.current) { plotViewportWidth.toPx() }.coerceAtLeast(1f)
        var visibleStart by remember(window.key, contentStart, contentEnd, viewportDuration) {
            mutableLongStateOf(timeline.initialStart)
        }
        val scrollableState = rememberScrollableState { deltaPixels ->
            val previous = visibleStart
            visibleStart = panChartViewport(visibleStart, deltaPixels, plotViewportWidthPx, timeline)
            if (visibleStart == previous) 0f else deltaPixels
        }
        val visibleEnd = (visibleStart + viewportDuration).coerceAtMost(contentEnd)

        fun visibleValues(axis: PlotAxis): List<Double> = sortedSeries
            .filter { it.axis == axis }
            .flatMap(TimeSeries::points)
            .filter { it.timestamp in visibleStart..visibleEnd }
            .map(TimeValue::value)

        val leftRange = chartAxisRange(visibleValues(PlotAxis.LEFT))
        val rightRange = chartAxisRange(visibleValues(PlotAxis.RIGHT))
        val independentRange = chartAxisRange(visibleValues(PlotAxis.INDEPENDENT))
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)
        val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.95f)

        Column {
            Row {
                ChartAxisLabels(leftRange, leftColor, formatLeft, leftAxisWidth, height)
                Box(Modifier.width(plotViewportWidth).height(height)) {
                    Canvas(Modifier.fillMaxSize()) {
                        repeat(3) { index ->
                            val y = CHART_INSET + index * (size.height - CHART_INSET * 2f) / 2f
                            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
                            drawLine(axisColor, Offset(0f, y), Offset(11f, y), strokeWidth = 3f)
                            if (hasRightAxis) drawLine(axisColor, Offset(size.width, y), Offset(size.width - 11f, y), strokeWidth = 3f)
                        }
                        repeat(3) { index ->
                            val x = index * size.width / 2f
                            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                            drawLine(axisColor, Offset(x, size.height), Offset(x, size.height - 11f), strokeWidth = 3f)
                        }
                        drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2.5f)
                    }
                    Canvas(
                        Modifier.fillMaxSize().scrollable(
                            state = scrollableState,
                            orientation = Orientation.Horizontal,
                        ),
                    ) {
                            fun x(timestamp: Long): Float =
                                ((timestamp - visibleStart).toDouble() / viewportDuration * size.width).toFloat()

                            fun y(value: Double, range: AxisRange): Float =
                                size.height - CHART_INSET -
                                    ((value - range.min) / range.span).toFloat() * (size.height - CHART_INSET * 2f)

                            bands.forEach { band ->
                                val top = y(band.center + band.tolerance, leftRange)
                                val bottom = y(band.center - band.tolerance, leftRange)
                                drawRect(
                                    band.color.copy(alpha = 0.14f),
                                    Offset(0f, top),
                                    androidx.compose.ui.geometry.Size(size.width, (bottom - top).coerceAtLeast(1f)),
                                )
                                drawLine(
                                    band.color,
                                    Offset(0f, y(band.center, leftRange)),
                                    Offset(size.width, y(band.center, leftRange)),
                                    strokeWidth = 3f,
                                )
                            }
                            sortedSeries.forEach { item ->
                                val range = when (item.axis) {
                                    PlotAxis.LEFT -> leftRange
                                    PlotAxis.RIGHT -> rightRange
                                    PlotAxis.INDEPENDENT -> independentRange
                                }
                                val nearbyPoints = item.points.filter { point ->
                                    point.timestamp in (visibleStart - viewportDuration)..(visibleEnd + viewportDuration)
                                }
                                val points = nearbyPoints.map { point ->
                                    Offset(
                                        x(point.timestamp),
                                        y(point.value, range),
                                    )
                                }
                                points.zipWithNext().forEach { (start, end) ->
                                    drawLine(
                                        item.color,
                                        start,
                                        end,
                                        strokeWidth = item.strokeWidth,
                                        cap = StrokeCap.Round,
                                        pathEffect = if (item.dashed) PathEffect.dashPathEffect(floatArrayOf(9f, 7f)) else null,
                                    )
                                }
                                if (item.showPoints) {
                                    nearbyPoints.zip(points).forEach { (value, point) ->
                                        if (value.timestamp in visibleStart..visibleEnd) {
                                            drawCircle(item.color, item.pointRadius, point)
                                        }
                                    }
                                }
                            }
                    }
                }
                if (hasRightAxis) {
                    ChartAxisLabels(rightRange, rightColor!!, formatRight, rightAxisWidth, height, endAligned = false)
                }
            }
            Row {
                Box(Modifier.width(leftAxisWidth))
                ChartDateLabels(visibleStart, visibleEnd, plotViewportWidth)
            }
        }
    }
}
