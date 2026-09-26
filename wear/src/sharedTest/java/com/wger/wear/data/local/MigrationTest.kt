package com.wger.wear.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cada migración conserva los datos del reloj. Añade un test por cada versión nueva. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration_test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2_keepsPendingSessionsAndSets() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO logged_workout_session (localSessionId, routineId, startTimestampMs, endTimestampMs, avgHeartRateBpm, syncStatus) VALUES (7, 1, 1000, 2000, 80, 'PENDING')")
            execSQL("INSERT INTO logged_set_entry (setId, sessionId, slotEntryId, exerciseName, completedReps, weightUsedKg, completedTimestampMs) VALUES (1, 7, 2, 'Inverted row', 8, 0, 1500)")
            execSQL("INSERT INTO routine_exercise_slot (slotEntryId, routineId, exerciseName, setNumber, totalSetsForExercise, executionOrder, targetReps, defaultWeightKg, restDurationSeconds) VALUES (2, 1, 'Inverted row', 4, 4, 3, 8, 0, 90)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true)

        db.query("SELECT syncStatus FROM logged_workout_session WHERE localSessionId = 7").use {
            it.moveToFirst(); assertEquals("PENDING", it.getString(0))
        }
        db.query("SELECT exerciseId, completedReps FROM logged_set_entry WHERE setId = 1").use {
            it.moveToFirst(); assertEquals(0L, it.getLong(0)); assertEquals(8, it.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM routine_exercise_slot").use {
            it.moveToFirst(); assertEquals(1, it.getInt(0))
        }
    }
}
