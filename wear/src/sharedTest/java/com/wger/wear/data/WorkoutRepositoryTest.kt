package com.wger.wear.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wger.wear.data.local.AppDatabase
import com.wger.wear.data.local.LoggedWorkoutSessionEntity
import com.wger.wear.data.local.RoutineCacheEntity
import com.wger.wear.data.local.RoutineExerciseSlotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "workout_repository_test.db"
    private lateinit var db: AppDatabase
    private var clock = 1_000_000L
    private val sent = mutableListOf<Pair<Long, Int>>() // (localSessionId, nº de series)

    private fun repo() = WorkoutRepository(db, { s, sets -> sent += s.localSessionId to sets.size }) { clock }

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(dbName)
        db = AppDatabase.build(context, dbName)
        val slots = listOf(90, 60, 0).mapIndexed { i, rest ->
            RoutineExerciseSlotEntity(
                slotEntryId = 2, routineId = 1, exerciseId = 1198, exerciseName = "Inverted row",
                setNumber = i + 1, totalSetsForExercise = 3, executionOrder = i,
                targetReps = 8, defaultWeightKg = 0f, restDurationSeconds = rest
            )
        }
        db.routineDao().replaceRoutineWithSlots(RoutineCacheEntity(1, "Fase 1", "", true), slots)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    /** En el reloj: al morir el servicio, la serie acabó en la sesión 1 (`?: 1L`) y no en la del entreno. */
    @Test
    fun workoutSurvivesReopeningTheDatabase() = runBlocking {
        val sessionId = repo().start(routineId = 1)
        repo().logSet(reps = 8, weightKg = 2.5f)

        db.close()
        db = AppDatabase.build(context, dbName)

        val active = repo().activeWorkout.first()!!
        assertEquals("El entreno sigue en curso en la misma sesión", sessionId, active.session.localSessionId)
        assertEquals(listOf(sessionId), active.sets.map { it.sessionId })
        assertEquals("Continúa por la serie 2", 2, active.currentSlot!!.setNumber)
    }

    /** Pulsar "Iniciar" con un entreno a medias creaba otra sesión y dejaba la anterior abierta. */
    @Test
    fun startResumesTheWorkoutInProgress() = runBlocking {
        val first = repo().start(routineId = 1)
        assertEquals(first, repo().start(routineId = 1))
    }

    @Test
    fun logSetWithoutWorkoutDoesNothing() = runBlocking {
        assertFalse(repo().logSet(reps = 8, weightKg = 0f))
        assertEquals(0, db.workoutSessionDao().getSetsForSession(1).size)
    }

    @Test
    fun logSetStopsWhenNoSetsAreLeft() = runBlocking {
        repo().start(routineId = 1)
        repeat(3) { repo().logSet(reps = 8, weightKg = 0f) }

        assertFalse(repo().logSet(reps = 8, weightKg = 0f))
        assertNull(repo().activeWorkout.first()!!.currentSlot)
    }

    @Test
    fun restEndsAfterTheRestOfTheLastLoggedSet() = runBlocking {
        repo().start(routineId = 1)
        assertNull("Sin series no hay descanso", repo().activeWorkout.first()!!.restEndsAtMs)

        clock = 2_000_000L
        repo().logSet(reps = 8, weightKg = 0f)
        assertEquals("Descanso de la serie 1: 90 s", 2_090_000L, repo().activeWorkout.first()!!.restEndsAtMs)
    }

    @Test
    fun finishClosesTheSessionAndSendsItWithItsSets() = runBlocking {
        val sessionId = repo().start(routineId = 1)
        repeat(2) { repo().logSet(reps = 8, weightKg = 0f) }
        clock = 5_000_000L

        val finished = repo().finish(avgHeartRateBpm = 120)!!

        assertEquals(5_000_000L, finished.endTimestampMs)
        assertEquals(120, finished.avgHeartRateBpm)
        assertEquals(listOf(sessionId to 2), sent)
        assertNull("Ya no hay entreno en curso", repo().activeWorkout.first())
    }

    /** #3: se reenvían las sesiones terminadas sin ACK; ni las SYNCED ni la que está en curso. */
    @Test
    fun resendPendingSendsOnlyFinishedUnsyncedSessions() = runBlocking {
        val dao = db.workoutSessionDao()
        val pending = dao.insertSession(LoggedWorkoutSessionEntity(routineId = 1, startTimestampMs = 1, endTimestampMs = 2, syncStatus = "PENDING"))
        val synced = dao.insertSession(LoggedWorkoutSessionEntity(routineId = 1, startTimestampMs = 3, endTimestampMs = 4, syncStatus = "PENDING"))
        repo().markSynced(synced)
        repo().start(routineId = 1) // en curso: endTimestampMs = 0

        repo().resendPending()

        assertEquals(listOf(pending to 0), sent)
    }
}
