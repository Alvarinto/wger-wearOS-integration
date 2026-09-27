package com.wger.wear.datalayer

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.wger.wear.data.WorkoutRepository
import com.wger.wear.data.local.AppDatabase
import com.wger.wear.data.local.LoggedSetEntryEntity
import com.wger.wear.data.local.LoggedWorkoutSessionEntity
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/** El repositorio del entreno en el reloj: las sesiones terminadas se envían al móvil por el Data Layer. */
fun workoutRepository(context: Context): WorkoutRepository {
    val syncManager = WearSyncManager(context)
    return WorkoutRepository(AppDatabase.getInstance(context), { s, sets -> syncManager.dispatchSessionToPhone(s, sets) })
}

class WearSyncManager(context: Context) {
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    suspend fun dispatchSessionToPhone(
        session: LoggedWorkoutSessionEntity,
        sets: List<LoggedSetEntryEntity>
    ): Boolean {
        return try {
            val requestUri = "/wger/completed_session/${session.localSessionId}"
            val putDataMapRequest = PutDataMapRequest.create(requestUri).apply {
                val setsArray = JSONArray()
                sets.forEach { set ->
                    val setObj = JSONObject().apply {
                        put("slotEntryId", set.slotEntryId)
                        put("exerciseId", set.exerciseId)
                        put("reps", set.completedReps)
                        put("weightKg", set.weightUsedKg.toDouble())
                        put("timestamp", set.completedTimestampMs)
                    }
                    setsArray.put(setObj)
                }

                dataMap.putLong("localSessionId", session.localSessionId)
                dataMap.putLong("routineId", session.routineId)
                dataMap.putLong("startTimestamp", session.startTimestampMs)
                dataMap.putLong("endTimestamp", session.endTimestampMs)
                dataMap.putInt("avgHeartRate", session.avgHeartRateBpm)
                dataMap.putString("setsJson", setsArray.toString())
                // Cambia en cada envío: un DataItem idéntico no dispara onDataChanged y el reintento no llegaría
                dataMap.putLong("dispatchedAt", System.currentTimeMillis())

                // CRÍTICO: Obliga a Wear OS 5 a no encolar el paquete por ahorro de batería
                setUrgent()
            }

            val putDataRequest = putDataMapRequest.asPutDataRequest()
            dataClient.putDataItem(putDataRequest).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun requestRoutineRefresh(): Boolean {
        return try {
            val nodes = nodeClient.connectedNodes.await()
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/wger/request_routine", ByteArray(0)).await()
            }
            nodes.isNotEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
