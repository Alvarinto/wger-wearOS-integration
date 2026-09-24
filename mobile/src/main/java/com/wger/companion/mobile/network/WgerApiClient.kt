package com.wger.companion.mobile.network

import android.util.Log
import com.wger.companion.mobile.BuildConfig
import com.wger.companion.mobile.data.model.WgerExerciseSlot
import com.wger.companion.mobile.data.model.WgerRoutine
import com.wger.companion.mobile.data.model.WorkoutLogRequest
import com.wger.companion.mobile.data.model.WorkoutSessionRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class WgerApiClient(
    private val baseUrl: String = BuildConfig.WGER_SERVER_URL.trimEnd('/'),
    private val apiToken: String = BuildConfig.WGER_API_TOKEN
) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
            })
        }
    }

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun testConnection(): Result<String> {
        return try {
            val response = client.get("$baseUrl/api/v2/routine/") {
                header("Authorization", "Token $apiToken")
            }
            if (response.status.isSuccess()) {
                Result.success("Conexión exitosa con wger en $baseUrl")
            } else {
                Result.failure(Exception("Error HTTP ${response.status.value}: ${response.bodyAsText()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error conectando con wger", e)
            Result.failure(e)
        }
    }

    suspend fun getActiveRoutines(): Result<List<WgerRoutine>> {
        return try {
            val response = client.get("$baseUrl/api/v2/routine/") {
                header("Authorization", "Token $apiToken")
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("HTTP ${response.status.value}: ${response.bodyAsText()}"))
            }

            val body = response.bodyAsText()
            val root = jsonParser.parseToJsonElement(body).jsonObject
            val resultsArray = root["results"]?.jsonArray ?: JsonArray(emptyList())

            val routines = resultsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Rutina $id"
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val isActive = obj["is_active"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
                WgerRoutine(id, name, desc, isActive)
            }
            Result.success(routines)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo rutinas", e)
            Result.failure(e)
        }
    }

    suspend fun getRoutineDateSequenceGym(routineId: Long): Result<List<WgerExerciseSlot>> {
        return try {
            val url = "$baseUrl/api/v2/routine/$routineId/date-sequence-gym/"
            val response = client.get(url) {
                header("Authorization", "Token $apiToken")
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("HTTP ${response.status.value}: ${response.bodyAsText()}"))
            }

            val body = response.bodyAsText()
            val parsedElement = jsonParser.parseToJsonElement(body)
            val slotsList = mutableListOf<WgerExerciseSlot>()

            val itemsArray = if (parsedElement is JsonArray) {
                parsedElement
            } else if (parsedElement is JsonObject && parsedElement.containsKey("results")) {
                parsedElement["results"]?.jsonArray ?: JsonArray(emptyList())
            } else {
                JsonArray(emptyList())
            }

            var executionIndex = 0

            // Caso 1: Estructura nativa de wger date-sequence-gym (Array de iteraciones de días con 'slots' y 'sets')
            val firstDayWithSlots = itemsArray.firstOrNull { el ->
                el is JsonObject && el.jsonObject.containsKey("slots") && (el.jsonObject["slots"]?.jsonArray?.isNotEmpty() == true)
            }?.jsonObject

            if (firstDayWithSlots != null) {
                val slotsArray = firstDayWithSlots["slots"]?.jsonArray ?: JsonArray(emptyList())
                for (slotElement in slotsArray) {
                    val slotObj = slotElement.jsonObject
                    val comment = slotObj["comment"]?.jsonPrimitive?.contentOrNull ?: ""
                    val exerciseName = if (comment.contains(":")) {
                        comment.substringBefore(":").trim()
                    } else if (comment.isNotBlank()) {
                        comment.trim()
                    } else {
                        "Ejercicio #${executionIndex + 1}"
                    }

                    val setsArray = slotObj["sets"]?.jsonArray ?: JsonArray(emptyList())
                    val totalSets = if (setsArray.isNotEmpty()) setsArray.size else 3

                    if (setsArray.isNotEmpty()) {
                        for (setIdx in 0 until setsArray.size) {
                            val setObj = setsArray[setIdx].jsonObject
                            val slotEntryId = setObj["slot_entry_id"]?.jsonPrimitive?.longOrNull
                                ?: (executionIndex + 1L)
                            val reps = setObj["repetitions"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt()
                                ?: 10
                            val rest = setObj["rest"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt()
                                ?: 60
                            val weight = setObj["weight"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toFloat()
                                ?: 0f

                            slotsList.add(
                                WgerExerciseSlot(
                                    slotEntryId = slotEntryId,
                                    exerciseName = exerciseName,
                                    setNumber = setIdx + 1,
                                    totalSetsForExercise = totalSets,
                                    executionOrder = executionIndex++,
                                    targetReps = reps,
                                    defaultWeightKg = weight,
                                    restDurationSeconds = rest
                                )
                            )
                        }
                    }
                }
            } else {
                // Caso 2: Estructura aplanada directa
                for (element in itemsArray) {
                    val item = element.jsonObject
                    val slotId = item["id"]?.jsonPrimitive?.longOrNull
                        ?: item["slot_entry"]?.jsonPrimitive?.longOrNull
                        ?: item["slot_entry_id"]?.jsonPrimitive?.longOrNull
                        ?: (executionIndex + 1L)

                    val exerciseName = item["exercise_name"]?.jsonPrimitive?.contentOrNull
                        ?: item["exercise"]?.jsonPrimitive?.contentOrNull
                        ?: "Ejercicio #${executionIndex + 1}"

                    val setNumber = item["set_number"]?.jsonPrimitive?.intOrNull
                        ?: item["set"]?.jsonPrimitive?.intOrNull
                        ?: 1

                    val totalSets = item["total_sets"]?.jsonPrimitive?.intOrNull
                        ?: item["sets"]?.jsonPrimitive?.intOrNull
                        ?: 3

                    val reps = item["reps"]?.jsonPrimitive?.intOrNull
                        ?: item["target_reps"]?.jsonPrimitive?.intOrNull
                        ?: 10

                    val weight = item["weight"]?.jsonPrimitive?.doubleOrNull?.toFloat()
                        ?: item["default_weight"]?.jsonPrimitive?.doubleOrNull?.toFloat()
                        ?: 0f

                    val rest = item["rest_duration"]?.jsonPrimitive?.intOrNull
                        ?: item["rest"]?.jsonPrimitive?.intOrNull
                        ?: 60

                    slotsList.add(
                        WgerExerciseSlot(
                            slotEntryId = slotId,
                            exerciseName = exerciseName,
                            setNumber = setNumber,
                            totalSetsForExercise = totalSets,
                            executionOrder = executionIndex++,
                            targetReps = reps,
                            defaultWeightKg = weight,
                            restDurationSeconds = rest
                        )
                    )
                }
            }

            Result.success(slotsList)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo secuencia de rutina $routineId", e)
            Result.failure(e)
        }
    }

    suspend fun createWorkoutSession(dateStr: String, notes: String, impression: Int): Result<Long> {
        return try {
            val response = client.post("$baseUrl/api/v2/workoutsession/") {
                header("Authorization", "Token $apiToken")
                contentType(ContentType.Application.Json)
                setBody(
                    WorkoutSessionRequest(
                        date = dateStr,
                        notes = notes,
                        impression = impression
                    )
                )
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("HTTP ${response.status.value}: ${response.bodyAsText()}"))
            }

            val body = response.bodyAsText()
            val obj = jsonParser.parseToJsonElement(body).jsonObject
            val id = obj["id"]?.jsonPrimitive?.longOrNull
                ?: return Result.failure(Exception("No se encontró el ID en la respuesta de sesión: $body"))

            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creando sesión en wger", e)
            Result.failure(e)
        }
    }

    suspend fun logWorkoutSet(sessionId: Long, slotEntryId: Long?, reps: Int, weight: Double): Result<Long> {
        return try {
            val response = client.post("$baseUrl/api/v2/workoutlog/") {
                header("Authorization", "Token $apiToken")
                contentType(ContentType.Application.Json)
                setBody(
                    WorkoutLogRequest(
                        session = sessionId,
                        slot_entry = slotEntryId,
                        reps = reps,
                        weight = weight
                    )
                )
            }
            if (!response.status.isSuccess()) {
                return Result.failure(Exception("HTTP ${response.status.value}: ${response.bodyAsText()}"))
            }

            val body = response.bodyAsText()
            val obj = jsonParser.parseToJsonElement(body).jsonObject
            val id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L
            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando serie en wger", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "WgerApiClient"
    }
}
