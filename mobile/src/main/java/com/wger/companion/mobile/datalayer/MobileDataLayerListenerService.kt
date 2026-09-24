package com.wger.companion.mobile.datalayer

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.wger.companion.mobile.network.WgerApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MobileDataLayerListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val apiClient by lazy { WgerApiClient() }
    private val phoneSyncManager by lazy { PhoneSyncManager(applicationContext) }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val path = uri.path ?: continue

                if (path.startsWith("/wger/completed_session/")) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val localSessionId = dataMap.getLong("localSessionId")
                    val startTimestamp = dataMap.getLong("startTimestamp")
                    val endTimestamp = dataMap.getLong("endTimestamp")
                    val avgHeartRate = dataMap.getInt("avgHeartRate")
                    val setsJson = dataMap.getString("setsJson", "[]")

                    serviceScope.launch {
                        processCompletedSession(
                            localSessionId = localSessionId,
                            startTimestamp = startTimestamp,
                            endTimestamp = endTimestamp,
                            avgHeartRate = avgHeartRate,
                            setsJson = setsJson
                        )
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/wger/request_routine") {
            Log.d(TAG, "Petición de rutina recibida desde el reloj")
            serviceScope.launch {
                refreshAndSendRoutineToWatch()
            }
        }
    }

    private suspend fun processCompletedSession(
        localSessionId: Long,
        startTimestamp: Long,
        endTimestamp: Long,
        avgHeartRate: Int,
        setsJson: String
    ) {
        try {
            Log.d(TAG, "Procesando sesión completada $localSessionId recibida del reloj...")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = dateFormat.format(Date(startTimestamp))

            val durationMinutes = ((endTimestamp - startTimestamp) / 60000).coerceAtLeast(1)
            val notes = "Sesión completada desde Pixel Watch 3. Duración: $durationMinutes min. Pulso medio: $avgHeartRate bpm."

            // 1. Crear sesión en wger
            val sessionResult = apiClient.createWorkoutSession(
                dateStr = dateStr,
                notes = notes,
                impression = 2
            )

            val remoteSessionId = sessionResult.getOrThrow()
            Log.d(TAG, "Sesión creada en wger con ID remoto: $remoteSessionId")

            // 2. Registrar cada serie en wger
            val setsArray = JSONArray(setsJson)
            for (i in 0 until setsArray.length()) {
                val setObj = setsArray.getJSONObject(i)
                val slotEntryId = setObj.optLong("slotEntryId", 0L)
                val reps = setObj.getInt("reps")
                val weightKg = setObj.getDouble("weightKg")

                val logResult = apiClient.logWorkoutSet(
                    sessionId = remoteSessionId,
                    slotEntryId = if (slotEntryId > 0) slotEntryId else null,
                    reps = reps,
                    weight = weightKg
                )
                if (logResult.isSuccess) {
                    Log.d(TAG, "Serie #${i + 1} registrada en wger con éxito")
                } else {
                    Log.w(TAG, "Fallo al registrar serie #${i + 1}: ${logResult.exceptionOrNull()?.message}")
                }
            }

            // 3. Enviar confirmación ACK al reloj
            phoneSyncManager.sendSessionAckToWatch(localSessionId)
            Log.d(TAG, "Sincronización completa para sesión local $localSessionId")

        } catch (e: Exception) {
            Log.e(TAG, "Error procesando sesión completada en servidor wger", e)
        }
    }

    private suspend fun refreshAndSendRoutineToWatch() {
        val routinesResult = apiClient.getActiveRoutines()
        val routines = routinesResult.getOrNull() ?: return
        val activeRoutine = routines.firstOrNull { it.is_active } ?: routines.firstOrNull() ?: return

        val slotsResult = apiClient.getRoutineDateSequenceGym(activeRoutine.id)
        val slots = slotsResult.getOrNull() ?: emptyList()

        phoneSyncManager.sendRoutineToWatch(
            routineId = activeRoutine.id,
            routineName = activeRoutine.name,
            routineDescription = activeRoutine.description,
            slots = slots
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "MobileDataLayerListener"
    }
}
