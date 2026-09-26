package com.wger.wear.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Un desajuste de versión de esquema sin Migration NO debe borrar las sesiones pendientes del reloj.
 *
 * Se simula poniendo `user_version = 2` en el fichero mientras el código está en v1: Room recorre el
 * mismo camino que en una subida sin migración (isMigrationRequired → destructivo si hay fallback).
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseSchemaChangeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "upgrade_test.db"

    @Before
    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun pendingSessionSurvivesSchemaVersionMismatch() {
        val db = AppDatabase.build(context, dbName)
        runBlocking {
            db.workoutSessionDao().insertSession(
                LoggedWorkoutSessionEntity(routineId = 1, startTimestampMs = 1_000, syncStatus = "PENDING")
            )
        }
        db.close()

        val path = context.getDatabasePath(dbName).path
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 2 }

        val reopened = AppDatabase.build(context, dbName)
        val openError = runCatching { reopened.openHelper.writableDatabase }.exceptionOrNull()
        reopened.close()

        val pending = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use {
            it.rawQuery("SELECT COUNT(*) FROM logged_workout_session WHERE syncStatus = 'PENDING'", null)
                .use { c -> c.moveToFirst(); c.getInt(0) }
        }

        assertEquals("La sesión PENDING debe seguir en disco tras el desajuste de esquema", 1, pending)
        assertTrue(
            "Room debe fallar por migración ausente en vez de abrir en silencio (error: $openError)",
            openError is IllegalStateException
        )
    }
}
