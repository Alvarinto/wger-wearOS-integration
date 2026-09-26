package com.wger.companion.mobile.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato con la API real de wger 2.x (respuestas copiadas del servidor). */
class WgerApiClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private var lastBody: JsonObject? = null

    private fun client(responseJson: String) = WgerApiClient(
        baseUrl = "http://wger.test",
        apiToken = "t",
        engine = MockEngine { request ->
            val body = String(request.body.toByteArray())
            if (body.isNotEmpty()) lastBody = Json.parseToJsonElement(body).jsonObject
            respond(responseJson, HttpStatusCode.Created, jsonHeaders)
        }
    )

    @Test
    fun dateSequenceGym_keepsEverySetWithItsExerciseId() = runBlocking {
        val set = """{"slot_entry_id":2,"exercise":1198,"sets":1,"weight":null,"repetitions":"8","rest":"90"}"""
        val response = """[{"iteration":1,"date":"2026-09-24","label":null,"day":{"id":2,"name":"Lunes"},
            "slots":[{"comment":"Inverted row: Dominadas australianas.","is_superset":false,"exercises":[1198],
            "sets":[$set,$set,$set,$set]}]}]"""

        val slots = client(response).getRoutineDateSequenceGym(2).getOrThrow()

        assertEquals(listOf(1, 2, 3, 4), slots.map { it.setNumber })
        assertEquals(listOf(0, 1, 2, 3), slots.map { it.executionOrder })
        assertEquals(List(4) { 1198L }, slots.map { it.exerciseId })
        assertEquals("Inverted row", slots.first().exerciseName)
    }

    @Test
    fun createWorkoutSession_readsUuidId() = runBlocking {
        val uuid = "01a0ddaf-bd9d-7952-8369-26cbf5e928aa"
        val result = client("""{"id":"$uuid","notes":""}""")
            .createWorkoutSession(startMs = 1_790_000_000_000, endMs = 1_790_003_600_000, notes = "", impression = 2)

        assertEquals("wger devuelve el id de sesión como UUID (${result.exceptionOrNull()?.message})", uuid, result.getOrNull())
        assertEquals("\"2026-09-21T14:13:20Z\"", lastBody!!["datetime_start"].toString())
        assertTrue("'date' ya no existe en wger 2.x; body=$lastBody", "date" !in lastBody!!)
    }

    @Test
    fun logWorkoutSet_sendsFieldsWgerRequires() = runBlocking {
        val result = client("""{"id":"01a0dd00-0000-0000-0000-000000000001"}""")
            .logWorkoutSet(sessionId = "01a0ddaf-bd9d-7952-8369-26cbf5e928aa", exerciseId = 1198, slotEntryId = 2L, reps = 8, weight = 0.0)

        val body = lastBody!!
        assertTrue("workoutlog exige 'exercise'; body=$body", "exercise" in body)
        assertTrue("el campo es 'repetitions', no 'reps'; body=$body", "repetitions" in body && "reps" !in body)
        assertEquals("1198", body["exercise"]!!.jsonPrimitive.content)
        assertTrue(result.isSuccess)
    }
}
