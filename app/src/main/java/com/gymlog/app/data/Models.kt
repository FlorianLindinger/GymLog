package com.gymlog.app.data

data class Machine(
    val id: Long,
    val name: String,
    val titleNote: String,
    val notes: String,
    val pauseTime: String,
    val chartWindow: String,
    val trailingAverage: Int,
    val trackingMode: String,
    val currentWeight: Double,
    val sortOrder: Int,
)

enum class BodyArea(val label: String, val color: Long, val selectable: Boolean = true) {
    CHEST("Chest", 0xFFE85D75),
    UPPER_BACK("Upper back", 0xFF4D8DD6),
    LOWER_BACK("Lower back", 0xFF2F6690),
    BACK("Back (legacy)", 0xFF4D8DD6, false),
    SHOULDERS("Shoulders", 0xFFFFA62B),
    BICEPS("Biceps", 0xFF9B72CF),
    TRICEPS("Triceps", 0xFFCB6CE6),
    FOREARMS("Forearms", 0xFF8D6E63),
    CORE("Core", 0xFFF2C14E),
    GLUTES("Glutes", 0xFFE76FAD, false),
    QUADS("Quads", 0xFF43AA8B, false),
    HAMSTRINGS("Hamstrings", 0xFF277DA1, false),
    CALVES("Calves", 0xFF90BE6D, false),
    FULL_BODY("Full body", 0xFF577590, false),
}

enum class Strength(val label: String, val value: Double) {
    HALF("Half", 0.5),
    FULL("Full", 1.0),
}

data class AreaAssignment(
    val area: BodyArea,
    val strength: Strength,
    val displayColor: Long = area.color,
)

data class SetRecord(
    val id: Long,
    val machineId: Long,
    val reps: Int,
    val weight: Double,
    val performedAt: Long,
)

data class WeightChange(val weight: Double, val changedAt: Long)

data class MachineSummary(
    val machine: Machine,
    val areas: List<AreaAssignment>,
    val recentSets: List<SetRecord>,
    val weightChanges: List<WeightChange>,
    val setsLast30Days: Int,
)

data class HistorySet(
    val id: Long,
    val machineId: Long,
    val machineName: String,
    val trackingMode: String,
    val reps: Int,
    val weight: Double,
    val performedAt: Long,
) {
    val volume: Double get() = reps * weight
}
