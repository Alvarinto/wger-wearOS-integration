package com.wger.wear.datalayer

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.wger.wear.data.local.AppDatabase
import com.wger.wear.data.local.RoutineCacheEntity
import com.wger.wear.data.local.RoutineExerciseSlotEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

class WearDataLayerListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val database = AppDatabase.getInstance(applicationContext)

        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val path = uri.path ?: continue

                when {
                    path == "/wger/routine_update" -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val routineId = dataMap.getLong("routineId")
                        val routineName = dataMap.getString("routineName", "Entrenamiento de hoy")
                        val routineDescription = dataMap.getString("routineDescription", "")
                        val slotsJson = dataMap.getString("slotsJson", "[]")

                        serviceScope.launch {
                            try {
                                val routineEntity = RoutineCacheEntity(
                                    routineId = routineId,
                                    name = routineName,
                                    description = routineDescription,
                                    isCurrentActive = true
                                )

                                val jsonArray = JSONArray(slotsJson)
                                val slotsList = mutableListOf<RoutineExerciseSlotEntity>()

                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    slotsList.add(
                                        RoutineExerciseSlotEntity(
                                            slotEntryId = obj.getLong("slotEntryId"),
                                            routineId = routineId,
                                            exerciseId = obj.optLong("exerciseId", 0),
                                            exerciseName = obj.getString("exerciseName"),
                                            setNumber = obj.getInt("setNumber"),
                                            totalSetsForExercise = obj.getInt("totalSetsForExercise"),
                                            executionOrder = obj.getInt("executionOrder"),
                                            targetReps = obj.getInt("targetReps"),
                                            defaultWeightKg = obj.getDouble("defaultWeightKg").toFloat(),
                                            restDurationSeconds = obj.optInt("restDurationSeconds", 60)
                                        )
                                    )
                                }

                                database.routineDao().replaceRoutineWithSlots(routineEntity, slotsList)
                                Log.d(TAG, "Rutina actualizada desde el móvil: $routineName con ${slotsList.size} slots")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error procesando routine_update", e)
                            }
                        }
                    }

                    path.startsWith("/wger/session_synced/") -> {
                        val sessionId = path.substringAfterLast("/").toLongOrNull()
                        if (sessionId != null) {
                            serviceScope.launch {
                                database.workoutSessionDao().updateSessionSyncStatus(sessionId, "SYNCED")
                                Log.d(TAG, "Sesión $sessionId confirmada como SYNCED por el servidor")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "WearDataLayerListener"
    }
}
