package com.wger.companion.mobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WgerRoutine(
    val id: Long,
    val name: String,
    val description: String = "",
    val is_active: Boolean = true
)

@Serializable
data class WgerRoutineListResponse(
    val count: Int = 0,
    val results: List<WgerRoutine> = emptyList()
)

@Serializable
data class WgerExerciseSlot(
    val slotEntryId: Long,
    val exerciseId: Long = 0,
    val exerciseName: String,
    val setNumber: Int,
    val totalSetsForExercise: Int,
    val executionOrder: Int,
    val targetReps: Int,
    val defaultWeightKg: Float,
    val restDurationSeconds: Int = 60
)

@Serializable
data class WorkoutSessionRequest(
    val datetime_start: String,
    val datetime_end: String,
    val notes: String = "",
    val impression: Int = 2 // 1: Very good, 2: Good, 3: Normal, etc.
)

@Serializable
data class WorkoutLogRequest(
    val session: String, // wger 2.x usa UUID
    val exercise: Long,
    val slot_entry: Long? = null,
    val repetitions: Int,
    val weight: Double
)
