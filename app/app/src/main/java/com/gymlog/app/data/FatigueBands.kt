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

/** Percent of a full exercise that each exercise from an earlier training day adds, newest day first. */
data class PriorDayFatigue(val yesterday: Int = 40, val twoDaysAgo: Int = 20, val threeDaysAgo: Int = 10) {
    init {
        require(validPriorDayFatigue(yesterday, twoDaysAgo, threeDaysAgo)) { "Previous-day fatigue must be 0 to 100%" }
    }

    /** Fractions for one, two and three training days ago. */
    val weights: List<Double> get() = listOf(yesterday, twoDaysAgo, threeDaysAgo).map { it / 100.0 }
}

private fun validPriorDayFatigue(vararg values: Int): Boolean = values.all { it in 0..100 }

internal fun parsePriorDayFatigue(yesterday: String, twoDaysAgo: String, threeDaysAgo: String): PriorDayFatigue? {
    val values = listOf(yesterday, twoDaysAgo, threeDaysAgo).map { it.toIntOrNull() ?: return null }
    return if (validPriorDayFatigue(*values.toIntArray())) PriorDayFatigue(values[0], values[1], values[2]) else null
}

internal fun priorDayFatigueOrDefault(yesterday: Int, twoDaysAgo: Int, threeDaysAgo: Int): PriorDayFatigue =
    if (validPriorDayFatigue(yesterday, twoDaysAgo, threeDaysAgo)) PriorDayFatigue(yesterday, twoDaysAgo, threeDaysAgo)
    else PriorDayFatigue()
