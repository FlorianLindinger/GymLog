package com.gymlog.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyPlotLayoutTest {
    @Test
    fun defaultsPutMuscleMassSecondAndFatMassThirdWithAllPlotsVisible() {
        val layout = bodyPlotLayout(null, emptySet())
        assertEquals(listOf(BodyMetric.WEIGHT, BodyMetric.MUSCLE_MASS, BodyMetric.FAT_MASS,
            BodyMetric.FAT_PERCENTAGE, BodyMetric.MUSCLE_PERCENTAGE), layout.visibleOrder)
        assertTrue(layout.hidden.isEmpty())
    }

    @Test
    fun savedOrderAndVisibilitySurviveSerializationWithoutLosingHiddenPositions() {
        val original = BodyPlotLayout(defaultBodyPlotOrder.reversed(), setOf(BodyMetric.FAT_MASS))
        val restored = bodyPlotLayout(original.order.joinToString(",") { it.name }, original.hidden.map { it.name }.toSet())
        assertEquals(original, restored)
        assertEquals(original.order.filterNot { it == BodyMetric.FAT_MASS }, restored.visibleOrder)
        assertEquals(original.order, restored.copy(hidden = emptySet()).visibleOrder)
    }

    @Test
    fun incompleteOrObsoleteSavedOrderKeepsValidChoicesAndAppendsMissingPlots() {
        val layout = bodyPlotLayout("FAT_MASS,OBSOLETE,FAT_MASS,WEIGHT", setOf("OBSOLETE", "MUSCLE_PERCENTAGE"))
        assertEquals(listOf(BodyMetric.FAT_MASS, BodyMetric.WEIGHT, BodyMetric.MUSCLE_MASS,
            BodyMetric.FAT_PERCENTAGE, BodyMetric.MUSCLE_PERCENTAGE), layout.order)
        assertEquals(setOf(BodyMetric.MUSCLE_PERCENTAGE), layout.hidden)
        assertEquals(BodyMetric.entries.toSet(), layout.order.toSet())
    }

    @Test
    fun allPlotsCanBeHiddenAndRestoredWithoutRemovingThemFromOrder() {
        val layout = bodyPlotLayout("", BodyMetric.entries.map { it.name }.toSet())
        assertTrue(layout.visibleOrder.isEmpty())
        assertEquals(defaultBodyPlotOrder, layout.order)
        assertEquals(defaultBodyPlotOrder, layout.copy(hidden = emptySet()).visibleOrder)
    }
}
