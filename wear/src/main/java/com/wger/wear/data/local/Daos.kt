package com.wger.wear.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_cache WHERE isCurrentActive = 1 LIMIT 1")
    fun getActiveRoutine(): Flow<RoutineCacheEntity?>

    @Query("SELECT * FROM routine_cache WHERE isCurrentActive = 1 LIMIT 1")
    suspend fun getActiveRoutineSync(): RoutineCacheEntity?

    @Query("SELECT * FROM routine_exercise_slot WHERE routineId = :routineId ORDER BY executionOrder ASC")
    fun getSlotsForRoutine(routineId: Long): Flow<List<RoutineExerciseSlotEntity>>

    @Query("SELECT * FROM routine_exercise_slot WHERE routineId = :routineId ORDER BY executionOrder ASC")
    suspend fun getSlotsForRoutineSync(routineId: Long): List<RoutineExerciseSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<RoutineExerciseSlotEntity>)

    @Query("DELETE FROM routine_cache")
    suspend fun clearRoutines()

    @Query("DELETE FROM routine_exercise_slot WHERE routineId = :routineId")
    suspend fun clearSlotsForRoutine(routineId: Long)

    @Transaction
    suspend fun replaceRoutineWithSlots(routine: RoutineCacheEntity, slots: List<RoutineExerciseSlotEntity>) {
        clearRoutines()
        insertRoutine(routine)
        clearSlotsForRoutine(routine.routineId)
        insertSlots(slots)
    }
}

@Dao
interface WorkoutSessionDao {
    @Insert
    suspend fun insertSession(session: LoggedWorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: LoggedWorkoutSessionEntity)

    @Query("UPDATE logged_workout_session SET syncStatus = :status WHERE localSessionId = :sessionId")
    suspend fun updateSessionSyncStatus(sessionId: Long, status: String)

    @Query("SELECT * FROM logged_workout_session WHERE localSessionId = :sessionId")
    suspend fun getSessionById(sessionId: Long): LoggedWorkoutSessionEntity?

    @Query("SELECT * FROM logged_workout_session WHERE syncStatus = 'PENDING' ORDER BY startTimestampMs ASC")
    suspend fun getPendingSessions(): List<LoggedWorkoutSessionEntity>

    @Query("SELECT * FROM logged_workout_session ORDER BY startTimestampMs DESC")
    fun getAllSessionsFlow(): Flow<List<LoggedWorkoutSessionEntity>>

    @Insert
    suspend fun insertSet(set: LoggedSetEntryEntity): Long

    @Query("SELECT * FROM logged_set_entry WHERE sessionId = :sessionId ORDER BY completedTimestampMs ASC")
    suspend fun getSetsForSession(sessionId: Long): List<LoggedSetEntryEntity>

    @Query("SELECT * FROM logged_set_entry WHERE sessionId = :sessionId ORDER BY completedTimestampMs ASC")
    fun getSetsForSessionFlow(sessionId: Long): Flow<List<LoggedSetEntryEntity>>
}
