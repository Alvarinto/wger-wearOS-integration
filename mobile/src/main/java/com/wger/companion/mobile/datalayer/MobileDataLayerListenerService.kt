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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MobileDataLayerListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val apiClient by lazy { WgerApiClient() }
    private val phoneSyncManager by lazy { PhoneSyncManager(applicationContext) }
    private val sessionUploader by lazy {
        val prefs = applicationContext.getSharedPreferences("wger_sync", MODE_PRIVATE)
        SessionUploader(apiClient, { prefs.getString(it, null) }, { k, v -> prefs.edit().putString(k, v).commit() }) {
            phoneSyncManager.sendSessionAckToWatch(it)
        }
    }

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
            // Serializado: dos entregas simultáneas de la misma sesión verían ambas "no subida"
            uploadMutex.withLock {
                sessionUploader.upload(localSessionId, startTimestamp, endTimestamp, avgHeartRate, setsJson)
            }
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
        private val uploadMutex = Mutex()
    }
}
