package com.gymlog.app.data

import com.gymlog.app.ui.overlapOutlineColor
import org.junit.Assert.*
import org.junit.Test

class FatigueBandsTest {
    @Test fun customBoundariesAreInclusiveAndSharedWithFourDistinctColors() {
        val bands = FatigueBands(10, 40, 80)
        assertEquals(listOf(0, 1, 1, 2, 2, 3), listOf(9, 10, 39, 40, 79, 80).map { exerciseOverlapBin(it, bands) })
        assertEquals(0, exerciseOverlapBin(null, bands))
        assertEquals(4, listOf(0, 10, 40, 80).map { overlapOutlineColor(it, bands) }.toSet().size)
        assertEquals(overlapOutlineColor(0, FatigueBands(60, 80, 90)), overlapOutlineColor(50, FatigueBands(60, 80, 90)))
    }

    @Test fun invalidSettingsAreRejectedAndPersistedInvalidValuesUseDefaults() {
        assertEquals(FatigueBands(10, 40, 80), parseFatigueBands("10", "40", "80"))
        assertNull(parseFatigueBands("", "40", "80"))
        assertNull(parseFatigueBands("0", "40", "80"))
        assertNull(parseFatigueBands("40", "10", "80"))
        assertNull(parseFatigueBands("10", "40", "101"))
        assertNull(parseFatigueBands("999999999999999", "40", "80"))
        assertEquals(FatigueBands(), fatigueBandsOrDefault(40, 10, 80))
        assertThrows(IllegalArgumentException::class.java) { FatigueBands(25, 25, 75) }
    }
}
