package com.wger.companion.mobile.datalayer

import android.util.Log
import com.wger.companion.mobile.network.WgerApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Sube a wger una sesión recibida del reloj. `get`/`put`: almacén persistente (SharedPreferences).
 * `ack`: confirma la sesión al reloj.
 */
class SessionUploader(
    private val apiClient: WgerApiClient,
    private val get: (String) -> String?,
    private val put: (String, String) -> Unit,
    private val ack: suspend (Long) -> Unit
) {

    /** @return true si la sesión y todas sus series están en wger. */
    suspend fun upload(
        localSessionId: Long,
        startTimestamp: Long,
        endTimestamp: Long,
        avgHeartRate: Int,
        setsJson: String
    ): Boolean {
        val durationMinutes = ((endTimestamp - startTimestamp) / 60000).coerceAtLeast(1)
        val notes = "Sesión completada desde Pixel Watch 3. Duración: $durationMinutes min. Pulso medio: $avgHeartRate bpm."

        // localSessionId solo no basta: Room reinicia el autoincrement si se reinstala el reloj
        val key = "$localSessionId:$startTimestamp"

        // 1. Crear sesión en wger (o reutilizar la ya creada)
        val remoteSessionId = get("s:$key") ?: apiClient.createWorkoutSession(
            startMs = startTimestamp,
            endMs = endTimestamp,
            notes = notes,
            impression = 2
        ).getOrThrow().also {
            put("s:$key", it)
            Log.d(TAG, "Sesión creada en wger con ID remoto: $it")
        }

        // 2. Registrar cada serie en wger
        var allUploaded = true
        Json.parseToJsonElement(setsJson).jsonArray.forEachIndexed { i, element ->
            val set = element.jsonObject
            fun long(name: String) = set[name]?.jsonPrimitive?.longOrNull ?: 0L
            val setKey = "l:$key:${long("timestamp")}"
            if (get(setKey) != null) return@forEachIndexed
            val slotEntryId = long("slotEntryId")

            val logResult = apiClient.logWorkoutSet(
                sessionId = remoteSessionId,
                exerciseId = long("exerciseId"),
                slotEntryId = if (slotEntryId > 0) slotEntryId else null,
                reps = set.getValue("reps").jsonPrimitive.int,
                weight = set.getValue("weightKg").jsonPrimitive.double
            )
            if (logResult.isSuccess) {
                put(setKey, logResult.getOrThrow())
                Log.d(TAG, "Serie #${i + 1} registrada en wger con éxito")
            } else {
                allUploaded = false
                Log.w(TAG, "Fallo al registrar serie #${i + 1}: ${logResult.exceptionOrNull()?.message}")
            }
        }
        // Sin ACK la sesión sigue PENDING en el reloj y podrá reintentarse (#3)
        if (allUploaded) ack(localSessionId) else Log.w(TAG, "Sesión $localSessionId incompleta en wger: sin ACK")
        return allUploaded
    }

    companion object {
        private const val TAG = "SessionUploader"
    }
}
