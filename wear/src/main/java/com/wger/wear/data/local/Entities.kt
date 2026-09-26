package com.wger.wear.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "routine_cache")
data class RoutineCacheEntity(
    @PrimaryKey val routineId: Long,
    val name: String,
    val description: String,
    val isCurrentActive: Boolean
)

// Una fila por serie planificada. wger repite slotEntryId en todas las series de un ejercicio,
// así que la clave es la posición en la rutina.
@Entity(
    tableName = "routine_exercise_slot",
    primaryKeys = ["routineId", "executionOrder"]
)
data class RoutineExerciseSlotEntity(
    val slotEntryId: Long,
    val routineId: Long,
    @ColumnInfo(defaultValue = "0") val exerciseId: Long = 0,
    val exerciseName: String,
    val setNumber: Int,
    val totalSetsForExercise: Int,
    val executionOrder: Int,
    val targetReps: Int,
    val defaultWeightKg: Float,
    val restDurationSeconds: Int
)

@Entity(tableName = "logged_workout_session")
data class LoggedWorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val localSessionId: Long = 0,
    val routineId: Long,
    val startTimestampMs: Long,
    val endTimestampMs: Long = 0,
    val avgHeartRateBpm: Int = 0,
    val syncStatus: String // PENDING, SYNCING, SYNCED
)

@Entity(
    tableName = "logged_set_entry",
    foreignKeys = [
        ForeignKey(
            entity = LoggedWorkoutSessionEntity::class,
            parentColumns = ["localSessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class LoggedSetEntryEntity(
    @PrimaryKey(autoGenerate = true) val setId: Long = 0,
    val sessionId: Long,
    val slotEntryId: Long,
    @ColumnInfo(defaultValue = "0") val exerciseId: Long = 0,
    val exerciseName: String,
    val completedReps: Int,
    val weightUsedKg: Float,
    val completedTimestampMs: Long
)
