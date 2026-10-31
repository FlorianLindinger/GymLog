package com.gymlog.app.ui

internal val defaultBodyPlotOrder = listOf(
    BodyMetric.WEIGHT,
    BodyMetric.MUSCLE_MASS,
    BodyMetric.FAT_MASS,
    BodyMetric.FAT_PERCENTAGE,
    BodyMetric.MUSCLE_PERCENTAGE,
)

internal data class BodyPlotLayout(
    val order: List<BodyMetric> = defaultBodyPlotOrder,
    val hidden: Set<BodyMetric> = emptySet(),
) {
    val visibleOrder: List<BodyMetric> get() = order.filterNot { it in hidden }
}

/** Preserve saved choices, ignore obsolete names, and append any newly introduced plots. */
internal fun bodyPlotLayout(savedOrder: String?, hiddenNames: Set<String>): BodyPlotLayout {
    val byName = BodyMetric.entries.associateBy { it.name }
    val order = (savedOrder.orEmpty().split(',').mapNotNull { byName[it] } + defaultBodyPlotOrder).distinct()
    return BodyPlotLayout(order, hiddenNames.mapNotNull { byName[it] }.toSet())
}
