package com.gymlog.app.data

/** Lower inclusive percentage boundaries, shared by Auto sorting and overview outline colors. */
data class FatigueBands(val yellowFrom: Int = 25, val orangeFrom: Int = 50, val redFrom: Int = 75) {
    init {
        require(validFatigueBands(yellowFrom, orangeFrom, redFrom)) { "Color boundaries must increase from 1 to 100" }
    }
}

private fun validFatigueBands(yellow: Int, orange: Int, red: Int): Boolean =
    yellow >= 1 && yellow < orange && orange < red && red <= 100

internal fun parseFatigueBands(yellow: String, orange: String, red: String): FatigueBands? {
    val y = yellow.toIntOrNull() ?: return null
    val o = orange.toIntOrNull() ?: return null
    val r = red.toIntOrNull() ?: return null
    return if (validFatigueBands(y, o, r)) FatigueBands(y, o, r) else null
}

internal fun fatigueBandsOrDefault(yellow: Int, orange: Int, red: Int): FatigueBands =
    if (validFatigueBands(yellow, orange, red)) FatigueBands(yellow, orange, red) else FatigueBands()
