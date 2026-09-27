package com.wger.companion.mobile.datalayer

import com.wger.companion.mobile.network.WgerApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #1: reprocesar un completed_session no debe duplicar nada en wger. Issue #2: ACK solo si todo subió. */
class SessionUploaderTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val store = HashMap<String, String>()
    private val sessionPosts = mutableListOf<String>()
    private val logPosts = mutableListOf<String>()
    private val acks = mutableListOf<Long>()
    /** Nº de POST /workoutlog/ (1-based) que responderá 500. */
    private var failLogPost = -1

    private val uploader = SessionUploader(
        WgerApiClient(
            baseUrl = "http://wger.test",
            apiToken = "t",
            engine = MockEngine { request ->
                val path = request.url.encodedPath
                if (path.endsWith("/workoutsession/")) {
                    sessionPosts += path
                    respond("""{"id":"01a0ddaf-bd9d-7952-8369-26cbf5e928a${sessionPosts.size}"}""", HttpStatusCode.Created, jsonHeaders)
                } else {
                    logPosts += path
                    if (logPosts.size == failLogPost) respond("boom", HttpStatusCode.InternalServerError)
                    else respond("""{"id":"01a0dd00-0000-0000-0000-00000000000${logPosts.size}"}""", HttpStatusCode.Created, jsonHeaders)
                }
            }
        ),
        get = { store[it] },
        put = { k, v -> store[k] = v },
        ack = { acks += it }
    )

    private val setsJson = (1..3).joinToString(",", "[", "]") {
        """{"slotEntryId":2,"exerciseId":1198,"reps":8,"weightKg":60.0,"timestamp":${1_790_000_000_000 + it}}"""
    }

    private suspend fun upload(localId: Long = 1, start: Long = 1_790_000_000_000) =
        uploader.upload(localId, start, start + 3_600_000, 120, setsJson)

    @Test
    fun sameSessionTwice_createsOneSessionAndNoDuplicateSets() = runBlocking {
        assertTrue(upload())
        assertTrue(upload())

        assertEquals("POST /workoutsession/", 1, sessionPosts.size)
        assertEquals("POST /workoutlog/", 3, logPosts.size)
    }

    @Test
    fun failedSet_isRetriedAloneOnReprocess() = runBlocking {
        failLogPost = 2
        assertFalse(upload())
        assertTrue(upload())

        assertEquals("POST /workoutsession/", 1, sessionPosts.size)
        assertEquals("3 intentos + 1 reintento de la serie fallida", 4, logPosts.size)
        assertEquals("ACK solo tras reprocesar", listOf(1L), acks)
    }

    @Test
    fun failedSet_sendsNoAck() = runBlocking {
        failLogPost = 2
        upload()

        assertTrue("sin ACK: la sesión debe seguir PENDING en el reloj", acks.isEmpty())
    }

    @Test
    fun allSetsUploaded_sendsAck() = runBlocking {
        upload()

        assertEquals(listOf(1L), acks)
    }

    @Test
    fun sameLocalIdWithOtherStart_isANewSession() = runBlocking {
        // Reloj reinstalado: Room vuelve a empezar en localSessionId = 1
        upload(localId = 1, start = 1_790_000_000_000)
        upload(localId = 1, start = 1_790_100_000_000)

        assertEquals("POST /workoutsession/", 2, sessionPosts.size)
        assertEquals("POST /workoutlog/", 6, logPosts.size)
    }
}
