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
    val pinned: Boolean = false,
    val rankingPriority: Int = 0,
    val excludedFromAutoRanking: Boolean = false,
    val showTrend: Boolean = true,
)

enum class BodyArea(val label: String, val color: Long) {
    CHEST("Chest / Brust", 0xFFE6194B),
    UPPER_BACK("Lats / upper back / Latissimus / oberer Rücken", 0xFF4363D8),
    LOWER_BACK("Lower back / unterer Rücken", 0xFF00A6D6),
    TRAPS("Traps / Trapezmuskel", 0xFF3F007D),
    SHOULDERS("Shoulders / Schultern", 0xFFF58231),
    BICEPS("Biceps / Bizeps", 0xFF911EB4),
    TRICEPS("Triceps / Trizeps", 0xFFF032E6),
    FOREARMS("Forearms / Unterarme", 0xFF800000),
    CORE("Core / Rumpf", 0xFFFFD700),
    GLUTES("Glutes / Gesäß", 0xFF008080),
    QUADS("Quads / Quadrizeps", 0xFF3CB44B),
    ADDUCTORS("Adductors / Adduktoren", 0xFF9A6324),
    ABDUCTORS("Hip abductors / Hüftabduktoren", 0xFF000075),
    HAMSTRINGS("Hamstrings / hintere Oberschenkel", 0xFFE64A19),
    CALVES("Calves / Waden", 0xFFBFEF45),
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
    val volumeMinimumIncreasePercent: Int = 5,
)

enum class ExerciseOrderingMode {
    AUTOMATIC,
    MIXED,
    FIXED,
}

data class BackupSyncStatus(
    val automaticBackupEnabled: Boolean,
    val lastBackupAt: Long?,
    val hasChangesSinceBackup: Boolean,
)

data class TrainingPlan(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val notes: String,
    val notesPinned: Boolean,
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
