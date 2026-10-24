package com.gymlog.app.ui

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

internal data class AxisRange(val min: Double, val max: Double, val ticks: List<Double>) {
    val span: Double get() = max - min
}

internal fun chartAxisRange(values: List<Double>): AxisRange {
    val preferredCount = 5
    val finite = values.filter(Double::isFinite).map { it.coerceAtLeast(0.0) }
    val rawMin = finite.minOrNull() ?: 0.0
    val rawMax = finite.maxOrNull() ?: 0.0
    val rawSpan = rawMax - rawMin
    val padding = if (rawSpan == 0.0) maxOf(rawMax * 0.01, 0.1) else rawSpan * 0.025
    val minimum = (rawMin - padding).coerceAtLeast(0.0)
    val maximum = if (rawMax == 0.0) 1.0 else rawMax + padding
    val paddedSpan = maximum - minimum
    val exponent = floor(log10(paddedSpan / (preferredCount - 1))).toInt()
    val minimumDecimal = BigDecimal.valueOf(minimum)
    val maximumDecimal = BigDecimal.valueOf(maximum)

    // Align both bounds to a round step, then choose a compact range near five ticks.
    // Decimal arithmetic keeps ticks such as 0.6 from becoming 0.6000000000000001.
    val candidates = buildList {
        for (power in exponent - 1..exponent + 1) {
            for (multiple in listOf("1", "2", "2.5", "5")) {
                val step = BigDecimal(multiple).scaleByPowerOfTen(power)
                val firstIndex = minimumDecimal.divide(step, 0, RoundingMode.FLOOR)
                val lastIndex = maximumDecimal.divide(step, 0, RoundingMode.CEILING)
                val count = lastIndex.subtract(firstIndex).add(BigDecimal.ONE)
                if (count > BigDecimal.valueOf(6L)) continue
                val tickCount = count.toInt().coerceAtLeast(4)
                val ticks = List(tickCount) { index ->
                    firstIndex.add(BigDecimal.valueOf(index.toLong())).multiply(step).toDouble()
                }
                add(AxisRange(ticks.first(), ticks.last(), ticks))
            }
        }
    }
    return candidates.minBy { range ->
        (range.span - paddedSpan) / paddedSpan + 0.15 * abs(range.ticks.size - preferredCount)
    }
}

internal fun formatChartAxisValue(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
