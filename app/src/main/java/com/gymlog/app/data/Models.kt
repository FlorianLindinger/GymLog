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

enum class BodyArea(val label: String, val color: Long) {
    CHEST("Chest", 0xFFFF1744),
    UPPER_BACK("Upper back", 0xFF2979FF),
    LOWER_BACK("Lower back", 0xFF00E5FF),
    SHOULDERS("Shoulders", 0xFFFF9100),
    BICEPS("Biceps", 0xFF651FFF),
    TRICEPS("Triceps", 0xFFF500D4),
    CORE("Core", 0xFFFFEA00),
    GLUTES("Glutes", 0xFF00BFA5),
    QUADS("Quads", 0xFF00E676),
    HAMSTRINGS("Hamstrings", 0xFFFF6D00),
    CALVES("Calves", 0xFFAEEA00),
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

data class ProgressionSettings(
    val staleWeeks: Int = 4,
    val staleTrainings: Int = 5,
)

enum class AccentColor { BLUE, RED }

data class BackupSyncStatus(
    val automaticBackupEnabled: Boolean,
    val lastBackupAt: Long?,
    val hasChangesSinceBackup: Boolean,
)

data class TrainingPlan(
    val id: Long,
    val name: String,
    val sortOrder: Int,
)

data class WeightIncrease(
    val machineId: Long,
    val machineName: String,
    val fromWeight: Double,
    val toWeight: Double,
    val changedAt: Long,
)

data class MachineSummary(
    val machine: Machine,
    val areas: List<AreaAssignment>,
    val recentSets: List<SetRecord>,
    val weightChanges: List<WeightChange>,
    val setsLast30Days: Int,
    val setWheelValues: List<Int>? = null,
)

fun MachineSummary.weightIncreases(): List<WeightIncrease> = weightChanges
    .sortedBy { it.changedAt }
    .zipWithNext()
    .mapNotNull { (previous, current) ->
        current.takeIf { it.weight > previous.weight }?.let {
            WeightIncrease(machine.id, machine.name, previous.weight, current.weight, current.changedAt)
        }
    }

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
