package com.wger.wear.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RoutineCacheEntity::class,
        RoutineExerciseSlotEntity::class,
        LoggedWorkoutSessionEntity::class,
        LoggedSetEntryEntity::class
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
    abstract fun workoutSessionDao(): WorkoutSessionDao

    companion object {
        const val DB_NAME = "wger_wear.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = build(context)
                INSTANCE = instance
                instance
            }
        }

        // Configuración real de la BD; los tests la usan con otro nombre para no pisar la de la app.
        internal fun build(context: Context, name: String = DB_NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name).build()
    }
}
