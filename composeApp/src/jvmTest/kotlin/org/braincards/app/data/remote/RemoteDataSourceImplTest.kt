package org.braincards.app.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * RemoteDataSourceImpl REAL contra un motor Ktor falso (sin red): se comprueba la peticion que
 * construye (filtros, orden, rango, upsert) y como decodifica la respuesta. Regresion de la
 * migracion de los pulls de decks/topics/flashcards al helper de paginacion.
 */
@OptIn(InternalAPI::class)
class RemoteDataSourceImplTest {

    private class RecordingEngine(private val respond: (HttpRequestData) -> Pair<HttpStatusCode, String>) :
        HttpClientEngineBase("recording-test-engine") {
        override val config = HttpClientEngineConfig()

        // supabase-kt instala HttpTimeout (requestTimeout) y exige que el motor lo soporte.
        override val supportedCapabilities = setOf<HttpClientEngineCapability<*>>(HttpTimeoutCapability)
        val requests = mutableListOf<HttpRequestData>()

        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            requests += data
            val (status, body) = respond(data)
            return HttpResponseData(
                status, GMTDate(), headersOf(HttpHeaders.ContentType, "application/json"),
                HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body.toByteArray()), callContext()
            )
        }
    }

    private fun remoteWith(respond: (HttpRequestData) -> Pair<HttpStatusCode, String>): Pair<RemoteDataSourceImpl, RecordingEngine> {
        val engine = RecordingEngine(respond)
        val client = createSupabaseClient(supabaseUrl = "https://test.supabase.co", supabaseKey = "anon-key") {
            httpEngine = engine
            install(Postgrest)
        }
        return RemoteDataSourceImpl(client) to engine
    }

    private fun ok(json: String) = HttpStatusCode.OK to json

    private val since = Instant.parse("2025-01-01T00:00:00Z")

    private fun HttpRequestData.param(name: String) = url.parameters[name]
    private fun HttpRequestData.table() = url.encodedPath.substringAfterLast("/")

    /** Comprueba que la peticion de pull lleva los mismos filtros que antes + orden estable + rango. */
    private fun assertPullRequest(request: HttpRequestData, table: String, pk: String, offset: Long = 0) {
        assertEquals("GET", request.method.value)
        assertEquals(table, request.table())
        assertEquals("eq.user-1", request.param("user_id"))
        assertEquals("gt.$since", request.param("updated_at"))
        assertEquals("updated_at.asc.nullslast,$pk.asc.nullslast", request.param("order"))
        assertEquals(offset.toString(), request.param("offset"))
        assertEquals("1000", request.param("limit"))
    }

    @Test
    fun `pullDecks con pocas filas devuelve las mismas filas con una sola peticion`() = runBlocking {
        val (remote, engine) = remoteWith {
            ok(
                """[{"id":"d1","name":"Deck","description":null,"created_at":"2025-01-01T00:00:00+00:00",
                    "updated_at":"2025-01-02T00:00:00+00:00","deleted_at":null,"last_synced_at":null,"user_id":"user-1"}]"""
            )
        }

        val result = remote.pullDecks("user-1", since)

        assertEquals(
            listOf(
                Deck(
                    id = "d1", name = "Deck", description = null,
                    createdAt = Instant.parse("2025-01-01T00:00:00Z"), updatedAt = Instant.parse("2025-01-02T00:00:00Z"),
                    deletedAt = null, userId = "user-1", lastSyncedAt = null
                )
            ),
            result
        )
        assertEquals(1, engine.requests.size)
        assertPullRequest(engine.requests.single(), "decks", "id")
    }

    @Test
    fun `pullTopics con pocas filas devuelve las mismas filas con una sola peticion`() = runBlocking {
        val (remote, engine) = remoteWith {
            ok(
                """[{"id":"t1","deck_id":"d1","name":"Tema","created_at":"2025-01-01T00:00:00+00:00",
                    "updated_at":"2025-01-02T00:00:00+00:00","deleted_at":"2025-01-03T00:00:00+00:00",
                    "last_synced_at":"2025-01-04T00:00:00+00:00","user_id":"user-1"}]"""
            )
        }

        val result = remote.pullTopics("user-1", since)

        assertEquals(
            listOf(
                Topic(
                    id = "t1", deckId = "d1", name = "Tema",
                    createdAt = Instant.parse("2025-01-01T00:00:00Z"), updatedAt = Instant.parse("2025-01-02T00:00:00Z"),
                    deletedAt = Instant.parse("2025-01-03T00:00:00Z"), lastSyncedAt = Instant.parse("2025-01-04T00:00:00Z"),
                    userId = "user-1"
                )
            ),
            result
        )
        assertEquals(1, engine.requests.size)
        assertPullRequest(engine.requests.single(), "topics", "id")
    }

    @Test
    fun `pullFlashcards con pocas filas devuelve las mismas filas con una sola peticion`() = runBlocking {
        val (remote, engine) = remoteWith {
            ok(
                """[{"id":"f1","deck_id":"d1","topic_id":"t1","question":"Q","answer":"A","card_type":"FLIP",
                    "options":null,"correct_option_index":null,"created_at":"2025-01-01T00:00:00+00:00",
                    "updated_at":"2025-01-02T00:00:00+00:00","deleted_at":null,"last_synced_at":null,"user_id":"user-1"}]"""
            )
        }

        val result = remote.pullFlashcards("user-1", since)

        assertEquals(
            listOf(
                Flashcard(
                    id = "f1", deckId = "d1", question = "Q", answer = "A",
                    createdAt = Instant.parse("2025-01-01T00:00:00Z"), updatedAt = Instant.parse("2025-01-02T00:00:00Z"),
                    cardType = CardType.FLIP, topicId = "t1", userId = "user-1"
                )
            ),
            result
        )
        assertEquals(1, engine.requests.size)
        assertPullRequest(engine.requests.single(), "flashcards", "id")
    }

    @Test
    fun `pullCardScheduling con pocas filas ordena por card_id y decodifica interval_days`() = runBlocking {
        val (remote, engine) = remoteWith {
            ok(
                """[{"card_id":"c1","user_id":"user-1","ease_factor":2.36,"interval_days":17,"repetitions":4,
                    "next_review":"2025-02-01T00:00:00+00:00","updated_at":"2025-01-02T00:00:00+00:00","last_synced_at":null}]"""
            )
        }

        val result = remote.pullCardScheduling("user-1", since)

        assertEquals(
            listOf(
                CardScheduling(
                    cardId = "c1", easeFactor = 2.36, interval = 17, repetitions = 4,
                    nextReview = Instant.parse("2025-02-01T00:00:00Z"), updatedAt = Instant.parse("2025-01-02T00:00:00Z"),
                    userId = "user-1", lastSyncedAt = null
                )
            ),
            result
        )
        assertEquals(1, engine.requests.size)
        assertPullRequest(engine.requests.single(), "card_scheduling", "card_id")
    }

    private fun schedulingRows(from: Int, count: Int): String =
        (from until from + count).joinToString(prefix = "[", postfix = "]") {
            """{"card_id":"c$it","user_id":"user-1","ease_factor":2.5,"interval_days":1,"repetitions":1,
               "next_review":"2025-02-01T00:00:00+00:00","updated_at":"2025-01-02T00:00:00+00:00","last_synced_at":null}"""
        }

    @Test
    fun `1500 filas se bajan en 2 peticiones con offset 0 y 1000 sin duplicados`() = runBlocking {
        val total = 1500
        val (remote, engine) = remoteWith { request ->
            val offset = request.param("offset")!!.toInt()
            val limit = request.param("limit")!!.toInt()
            ok(schedulingRows(offset, minOf(limit, total - offset)))
        }

        val result = remote.pullCardScheduling("user-1", since)

        assertEquals(total, result.size)
        assertEquals(total, result.map { it.cardId }.toSet().size)
        assertEquals(listOf("0", "1000"), engine.requests.map { it.param("offset") })
    }

    @Test
    fun `una pagina exacta de 1000 filas pide una segunda`() = runBlocking {
        val (remote, engine) = remoteWith { request ->
            ok(if (request.param("offset") == "0") schedulingRows(0, 1000) else "[]")
        }

        val result = remote.pullCardScheduling("user-1", since)

        assertEquals(1000, result.size)
        assertEquals(2, engine.requests.size)
    }

    @Test
    fun `un error del servidor en la pagina 2 aborta el pull entero`() {
        val (remote, engine) = remoteWith { request ->
            if (request.param("offset") == "0") ok(schedulingRows(0, 1000))
            else HttpStatusCode.InternalServerError to """{"message":"boom"}"""
        }

        assertFailsWith<Exception> { runBlocking { remote.pullCardScheduling("user-1", since) } }
        assertEquals(2, engine.requests.size)
    }

    @Test
    fun `pushCardScheduling hace upsert con onConflict card_id y las columnas exactas`() = runBlocking {
        val (remote, engine) = remoteWith { ok("[]") }
        val scheduling = CardScheduling(
            cardId = "c1", easeFactor = 2.36, interval = 17, repetitions = 4,
            nextReview = Instant.parse("2025-02-01T00:00:00Z"), updatedAt = Instant.parse("2025-01-02T00:00:00Z"),
            userId = "user-1", lastSyncedAt = null
        )

        remote.pushCardScheduling(listOf(scheduling))

        val request = engine.requests.single()
        assertEquals("POST", request.method.value)
        assertEquals("card_scheduling", request.table())
        assertEquals("card_id", request.param("on_conflict"))
        val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        val row = Json.parseToJsonElement(body).jsonArray.single().jsonObject
        assertEquals("c1", row.getValue("card_id").jsonPrimitive.content)
        assertEquals(17, row.getValue("interval_days").jsonPrimitive.content.toInt())
        assertTrue("interval" !in row.keys)
    }

    @Test
    fun `pushCardScheduling con lista vacia no hace ninguna peticion`() = runBlocking {
        val (remote, engine) = remoteWith { ok("[]") }

        remote.pushCardScheduling(emptyList())

        assertEquals(0, engine.requests.size)
    }
}
