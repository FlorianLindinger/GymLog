package com.gymlog.app.data

/** Stable persisted values. Unknown legacy/backup text is safely normalised at the boundary. */
enum class TrackingMode {
    WEIGHT, STRENGTH, DURATION, WATTS;

    companion object {
        fun fromStorage(value: String?): TrackingMode = entries.firstOrNull { it.name == value } ?: WEIGHT
    }
}

fun TrackingMode.usesResistanceLevel(): Boolean = this == TrackingMode.STRENGTH || this == TrackingMode.WATTS

enum class ChartRange(val storageValue: String) {
    ONE_WEEK("1W"), TWO_WEEKS("2W"), THREE_WEEKS("3W"), ONE_MONTH("1M"), TWO_MONTHS("2M"),
    THREE_MONTHS("3M"), FOUR_MONTHS("4M"), SIX_MONTHS("6M"), ONE_YEAR("1Y"), TWO_YEARS("2Y"),
    THREE_YEARS("3Y"), ALL("ALL"), CUSTOM("CUSTOM");

    companion object {
        fun fromStorage(value: String?): ChartRange = entries.firstOrNull { it.storageValue == value } ?: THREE_MONTHS
    }
}
