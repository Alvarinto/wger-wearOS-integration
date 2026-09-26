package com.wger.wear.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutineDaoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "routine_dao_test.db"
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        db = AppDatabase.build(context, dbName)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    /** wger repite el mismo slot_entry_id en todas las series de un ejercicio (p. ej. 4 × Inverted row → id 2). */
    @Test
    fun keepsEverySetOfAnExerciseSharingSlotEntryId() = runBlocking {
        val sets = (1..4).map { n ->
            RoutineExerciseSlotEntity(
                slotEntryId = 2, routineId = 1, exerciseName = "Inverted row",
                setNumber = n, totalSetsForExercise = 4, executionOrder = n - 1,
                targetReps = 8, defaultWeightKg = 0f, restDurationSeconds = 90
            )
        }
        val dao = db.routineDao()
        dao.replaceRoutineWithSlots(RoutineCacheEntity(1, "Fase 1", "", true), sets)

        val stored = dao.getSlotsForRoutineSync(1)
        assertEquals("Deben guardarse las 4 series, no solo la última", listOf(1, 2, 3, 4), stored.map { it.setNumber })
    }
}
