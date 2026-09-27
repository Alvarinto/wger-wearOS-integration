package com.wger.wear.data

import androidx.room.withTransaction
import com.wger.wear.data.local.AppDatabase
import com.wger.wear.data.local.LoggedSetEntryEntity
import com.wger.wear.data.local.LoggedWorkoutSessionEntity
import com.wger.wear.data.local.RoutineExerciseSlotEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Único punto que modifica el entreno. Todo el estado sale de Room, así que sobrevive a que mueran
 * la pantalla, el servicio o el proceso. `send`: envía una sesión terminada (Data Layer en el reloj).
 */
class WorkoutRepository(
    private val db: AppDatabase,
    private val send: suspend (LoggedWorkoutSessionEntity, List<LoggedSetEntryEntity>) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val sessions = db.workoutSessionDao()
    private val routines = db.routineDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeWorkout: Flow<ActiveWorkout?> = sessions.getActiveSessionFlow().flatMapLatest { session ->
        if (session == null) flowOf(null)
        else combine(
            routines.getSlotsForRoutine(session.routineId),
            sessions.getSetsForSessionFlow(session.localSessionId)
        ) { slots, sets -> ActiveWorkout(session, slots, sets) }
    }

    /** Empieza un entreno, o devuelve el que ya está en curso. */
    suspend fun start(routineId: Long): Long = db.withTransaction {
        sessions.getActiveSession()?.localSessionId
            ?: sessions.insertSession(
                LoggedWorkoutSessionEntity(routineId = routineId, startTimestampMs = now(), syncStatus = "PENDING")
            )
    }

    /** Registra la serie actual del entreno en curso. false si no hay entreno o ya no quedan series. */
    suspend fun logSet(reps: Int, weightKg: Float): Boolean = db.withTransaction {
        val session = sessions.getActiveSession() ?: return@withTransaction false
        val done = sessions.getSetsForSession(session.localSessionId).size
        val slot = routines.getSlotsForRoutineSync(session.routineId).getOrNull(done)
            ?: return@withTransaction false
        sessions.insertSet(
            LoggedSetEntryEntity(
                sessionId = session.localSessionId,
                slotEntryId = slot.slotEntryId,
                exerciseId = slot.exerciseId,
                exerciseName = slot.exerciseName,
                completedReps = reps,
                weightUsedKg = weightKg,
                completedTimestampMs = now()
            )
        )
        true
    }

    /** Cierra el entreno en curso y lo envía. null si no había ninguno. */
    suspend fun finish(avgHeartRateBpm: Int): LoggedWorkoutSessionEntity? {
        val finished = db.withTransaction {
            sessions.getActiveSession()
                ?.copy(endTimestampMs = now(), avgHeartRateBpm = avgHeartRateBpm)
                ?.also { sessions.updateSession(it) }
        } ?: return null
        send(finished, sessions.getSetsForSession(finished.localSessionId))
        return finished
    }

    /** Reenvía las sesiones terminadas que aún no tienen ACK (#3). */
    suspend fun resendPending() {
        for (session in sessions.getPendingSessions()) {
            send(session, sessions.getSetsForSession(session.localSessionId))
        }
    }

    suspend fun markSynced(sessionId: Long) = sessions.updateSessionSyncStatus(sessionId, "SYNCED")
}

data class ActiveWorkout(
    val session: LoggedWorkoutSessionEntity,
    val slots: List<RoutineExerciseSlotEntity>,
    val sets: List<LoggedSetEntryEntity>
) {
    val currentSlot: RoutineExerciseSlotEntity? get() = slots.getOrNull(sets.size)

    /** Fin del descanso tras la última serie; null si aún no hay series. */
    val restEndsAtMs: Long?
        get() = sets.lastOrNull()?.let { last ->
            last.completedTimestampMs + (slots.getOrNull(sets.size - 1)?.restDurationSeconds ?: 0) * 1000L
        }
}
