package com.wger.companion.mobile.datalayer

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.wger.companion.mobile.data.model.WgerExerciseSlot
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class PhoneSyncManager(context: Context) {
    private val dataClient: DataClient = Wearable.getDataClient(context)

    suspend fun sendRoutineToWatch(
        routineId: Long,
        routineName: String,
        routineDescription: String,
        slots: List<WgerExerciseSlot>
    ): Boolean {
        return try {
            val putDataMapRequest = PutDataMapRequest.create("/wger/routine_update").apply {
                val array = JSONArray()
                slots.forEach { slot ->
                    val obj = JSONObject().apply {
                        put("slotEntryId", slot.slotEntryId)
                        put("exerciseName", slot.exerciseName)
                        put("setNumber", slot.setNumber)
                        put("totalSetsForExercise", slot.totalSetsForExercise)
                        put("executionOrder", slot.executionOrder)
                        put("targetReps", slot.targetReps)
                        put("defaultWeightKg", slot.defaultWeightKg.toDouble())
                        put("restDurationSeconds", slot.restDurationSeconds)
                    }
                    array.put(obj)
                }

                dataMap.putLong("routineId", routineId)
                dataMap.putString("routineName", routineName)
                dataMap.putString("routineDescription", routineDescription)
                dataMap.putString("slotsJson", array.toString())
                dataMap.putLong("timestamp", System.currentTimeMillis())

                // CRÍTICO: setUrgent() para replicación inmediata
                setUrgent()
            }

            val request = putDataMapRequest.asPutDataRequest()
            dataClient.putDataItem(request).await()
            Log.d(TAG, "Rutina $routineName enviada con éxito al reloj (${slots.size} slots)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando rutina al reloj", e)
            false
        }
    }

    suspend fun sendSessionAckToWatch(sessionId: Long): Boolean {
        return try {
            val putDataMapRequest = PutDataMapRequest.create("/wger/session_synced/$sessionId").apply {
                dataMap.putLong("sessionId", sessionId)
                dataMap.putLong("syncedAt", System.currentTimeMillis())
                setUrgent()
            }
            dataClient.putDataItem(putDataMapRequest.asPutDataRequest()).await()
            Log.d(TAG, "ACK de sincronización enviado al reloj para sesión $sessionId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando ACK de sesión al reloj", e)
            false
        }
    }

    companion object {
        private const val TAG = "PhoneSyncManager"
    }
}
