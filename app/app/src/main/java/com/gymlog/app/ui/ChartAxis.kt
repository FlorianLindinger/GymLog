package com.gymlog.app.ui

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.roundToLong

internal data class AxisRange(val min: Double, val max: Double, val ticks: List<Double>) {
    val span: Double get() = max - min
}

/** Infer the most common recent recorded change, ignoring unchanged sets and floating-point noise. */
internal fun recordedWeightStep(weights: List<Double>): Double? = weights
    .filter { it.isFinite() && it >= 0.0 }
    .zipWithNext { a, b -> (abs(b - a) * 100).roundToLong() }
    .filter { it >= 5L }
    .takeLast(30)
    .groupingBy { it }.eachCount().entries
    .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
    .firstOrNull()?.key?.div(100.0)

/** Align labels to actual recorded loads and integer multiples of the inferred machine increment. */
internal fun weightChartAxisRange(values: List<Double>, recordedWeights: List<Double>): AxisRange {
    val stepValue = recordedWeightStep(recordedWeights) ?: return chartAxisRange(values)
    val finite = values.filter { it.isFinite() && it >= 0.0 }
    if (finite.isEmpty()) return chartAxisRange(values)
    val rawMin = finite.min()
    val rawMax = finite.max()
    val padding = if (rawMax == rawMin) maxOf(rawMax * .01, .1) else (rawMax - rawMin) * .025
    val minimum = (rawMin - padding).coerceAtLeast(0.0)
    val maximum = rawMax + padding
    val span = maximum - minimum
    val base = BigDecimal.valueOf(stepValue)
    val anchor = BigDecimal.valueOf(recordedWeights.last { it.isFinite() && it >= 0.0 })
    val idealMultiple = span / (stepValue * 4)
    val factors = listOf(1.0, floor(idealMultiple), ceil(idealMultiple), ceil(idealMultiple) + 1)
        .map { it.coerceAtLeast(1.0) }.distinct()
    return factors.mapNotNull { factor ->
        val step = base.multiply(BigDecimal.valueOf(factor))
        val first = BigDecimal.valueOf(minimum).subtract(anchor).divide(step, 0, RoundingMode.FLOOR)
            .max(anchor.negate().divide(step, 0, RoundingMode.CEILING))
        val last = BigDecimal.valueOf(maximum).subtract(anchor).divide(step, 0, RoundingMode.CEILING)
        val count = last.subtract(first).add(BigDecimal.ONE)
        if (count > BigDecimal.valueOf(6L)) return@mapNotNull null
        val ticks = List(count.toInt().coerceAtLeast(4)) {
            anchor.add(first.add(BigDecimal.valueOf(it.toLong())).multiply(step)).toDouble()
        }
        AxisRange(minOf(minimum, ticks.first()), ticks.last(), ticks)
    }.minBy { (it.span - span) / span + .15 * abs(it.ticks.size - 5) }
}

internal fun chartAxisRange(values: List<Double>, compact: Boolean = false, yMin: Double? = null, yMax: Double? = null): AxisRange {
    if (compact || yMin != null || yMax != null) return compactChartAxisRange(values, yMin, yMax)
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

/** Keep a small margin around the data; round ticks do not have to sit on the frame. */
private fun compactChartAxisRange(values: List<Double>, yMin: Double?, yMax: Double?): AxisRange {
    val finite = values.filter(Double::isFinite).map { it.coerceAtLeast(0.0) }
    val rawMin = finite.minOrNull() ?: 0.0
    val rawMax = finite.maxOrNull() ?: 1.0
    val dataSpan = (rawMax - rawMin).takeIf { it > 0 } ?: maxOf(rawMax * .02, .2)
    val padding = dataSpan * .04
    val forcedMin = yMin?.takeIf { it.isFinite() && it >= 0 }
    val forcedMax = yMax?.takeIf { it.isFinite() && it > 0 && (forcedMin == null || it > forcedMin) }
    var minimum = forcedMin ?: (rawMin - padding).coerceAtLeast(0.0)
    val maximum = forcedMax ?: maxOf(rawMax + padding, minimum + maxOf(padding * 2, .001))
    if (minimum >= maximum) minimum = (maximum - dataSpan).coerceIn(0.0, maximum * .95)
    val idealStep = (maximum - minimum) / 4
    val exponent = floor(log10(idealStep)).toInt()
    val candidates = buildList {
        for (power in exponent - 1..exponent + 1) {
            for (multiple in listOf("1", "2", "2.5", "5")) {
                val step = BigDecimal(multiple).scaleByPowerOfTen(power)
                val first = BigDecimal.valueOf(minimum).divide(step, 0, RoundingMode.CEILING)
                val last = BigDecimal.valueOf(maximum).divide(step, 0, RoundingMode.FLOOR)
                val count = last.subtract(first).toInt() + 1
                if (count !in 2..7) continue
                val ticks = List(count) { first.add(BigDecimal.valueOf(it.toLong())).multiply(step).toDouble() }
                add(ticks to (abs(count - 5) * .4 + abs(log10(step.toDouble() / idealStep))))
            }
        }
    }
    return AxisRange(minimum, maximum, candidates.minBy { it.second }.first)
}

internal fun formatChartAxisValue(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/** Subdivide nice major steps: 2.5 -> 0.5, 2 -> 0.5, 5 -> 1, 1 -> 0.2. */
internal fun minorChartTicks(range: AxisRange): List<Double> {
    if (range.ticks.size < 2 || range.span <= 0) return emptyList()
    val major = BigDecimal.valueOf(range.ticks[1]).subtract(BigDecimal.valueOf(range.ticks[0]))
    if (major.signum() <= 0) return emptyList()
    val exponent = floor(log10(major.toDouble())).toInt()
    val normalized = major.scaleByPowerOfTen(-exponent).toDouble()
    val subdivisions = if (abs(normalized - 2.0) < .00001) 4 else 5
    val minor = major.divide(BigDecimal.valueOf(subdivisions.toLong()))
    val first = BigDecimal.valueOf(range.min).divide(minor, 0, RoundingMode.CEILING)
    val last = BigDecimal.valueOf(range.max).divide(minor, 0, RoundingMode.FLOOR)
    val count = last.subtract(first).toInt() + 1
    if (count !in 1..100) return emptyList()
    val origin = BigDecimal.valueOf(range.ticks.first())
    return (0 until count).map { first.add(BigDecimal.valueOf(it.toLong())).multiply(minor) }
        .filter { it.subtract(origin).remainder(major).signum() != 0 }.map(BigDecimal::toDouble)
}
