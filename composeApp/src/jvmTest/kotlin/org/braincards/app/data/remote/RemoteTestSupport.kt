package org.braincards.app.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI

/**
 * Soporte compartido de los tests de la capa remota: un motor Ktor falso (sin red) que registra cada
 * peticion y responde lo que diga el test. Mismo patron que RemoteDataSourceImplTest, que tiene su
 * propia copia privada y no se toca.
 */
@OptIn(InternalAPI::class)
internal class StubHttpEngine(
    private val respond: (HttpRequestData) -> Pair<HttpStatusCode, String>
) : HttpClientEngineBase("stub-http-engine") {
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

internal fun stubRemote(
    pageSize: Int = DEFAULT_PAGE_SIZE,
    respond: (HttpRequestData) -> Pair<HttpStatusCode, String>
): Pair<RemoteDataSourceImpl, StubHttpEngine> {
    val engine = StubHttpEngine(respond)
    val client = createSupabaseClient(supabaseUrl = "https://test.supabase.co", supabaseKey = "anon-key") {
        httpEngine = engine
        install(Postgrest)
    }
    return RemoteDataSourceImpl(client, pageSize) to engine
}

internal fun okJson(json: String) = HttpStatusCode.OK to json

internal fun HttpRequestData.param(name: String): String? = url.parameters[name]

internal fun HttpRequestData.table(): String = url.encodedPath.substringAfterLast("/")

internal fun deckRow(id: String, serverUpdatedAt: String?, deletedAt: String? = null) = """
    {"id":"$id","name":"Deck","description":null,"created_at":"2025-01-01T00:00:00+00:00",
     "updated_at":"2025-01-02T00:00:00+00:00","deleted_at":${deletedAt.quoted()},"last_synced_at":null,
     "user_id":"user-1"${serverUpdatedAt.stamp()}}
""".trimIndent()

internal fun topicRow(id: String, serverUpdatedAt: String?) = """
    {"id":"$id","deck_id":"deck-1","name":"Topic","created_at":"2025-01-01T00:00:00+00:00",
     "updated_at":"2025-01-02T00:00:00+00:00","deleted_at":null,"last_synced_at":null,
     "user_id":"user-1"${serverUpdatedAt.stamp()}}
""".trimIndent()

internal fun flashcardRow(id: String, serverUpdatedAt: String?) = """
    {"id":"$id","deck_id":"deck-1","topic_id":null,"question":"Q","answer":"A","card_type":"FLIP",
     "options":null,"correct_option_index":null,"created_at":"2025-01-01T00:00:00+00:00",
     "updated_at":"2025-01-02T00:00:00+00:00","deleted_at":null,"last_synced_at":null,
     "user_id":"user-1"${serverUpdatedAt.stamp()}}
""".trimIndent()

internal fun schedulingRow(cardId: String, serverUpdatedAt: String?) = """
    {"card_id":"$cardId","user_id":"user-1","ease_factor":2.5,"interval_days":1,"repetitions":1,
     "next_review":"2025-02-01T00:00:00+00:00","updated_at":"2025-01-02T00:00:00+00:00",
     "last_synced_at":null${serverUpdatedAt.stamp()}}
""".trimIndent()

private fun String?.quoted() = if (this == null) "null" else "\"$this\""

private fun String?.stamp() = if (this == null) "" else ""","server_updated_at":"$this""""
