package org.braincards.app.data.remote

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import org.braincards.app.domain.repository.SyncFailureKind
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * SupabaseFailureClassifier con excepciones REALES (PostgREST y Auth salen de un cliente de Supabase contra un motor Ktor falso, sin red
 * y sin sesión real; la sesión de Auth es en memoria). Es la misma tabla que fija el comportamiento del motor en
 * SyncFailureCharacterizationTest, ahora comprobada directamente sobre el clasificador.
 */
@OptIn(InternalAPI::class)
class SupabaseFailureClassifierTest {

    private class StatusEngine(private val status: HttpStatusCode, private val body: String) :
        HttpClientEngineBase("status-classifier-engine") {
        override val config = HttpClientEngineConfig()
        override val supportedCapabilities = setOf<HttpClientEngineCapability<*>>(HttpTimeoutCapability)

        override suspend fun execute(data: HttpRequestData): HttpResponseData = HttpResponseData(
            status, GMTDate(), headersOf(HttpHeaders.ContentType, "application/json"),
            HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body.toByteArray()), callContext()
        )
    }

    private suspend fun postgrest(status: HttpStatusCode): PostgrestRestException {
        val client = createSupabaseClient(supabaseUrl = "https://test.supabase.co", supabaseKey = "anon-key") {
            httpEngine = StatusEngine(status, """{"message":"fallo simulado","code":"PGRST301","details":null,"hint":null}""")
            install(Postgrest)
        }
        val failure = runCatching { RemoteDataSourceImpl(client).pullDecks("user-1", Instant.fromEpochMilliseconds(0)) }.exceptionOrNull()
        return assertIs<PostgrestRestException>(failure)
    }

    private suspend fun auth(status: HttpStatusCode): AuthRestException {
        val client = createSupabaseClient(supabaseUrl = "https://test.supabase.co", supabaseKey = "anon-key") {
            httpEngine = StatusEngine(status, """{"error_code":"invalid_credentials","msg":"fallo simulado"}""")
            install(Auth) {
                alwaysAutoRefresh = false
                autoLoadFromStorage = false
                autoSaveToStorage = false
                sessionManager = MemorySessionManager()
            }
        }
        val failure = runCatching { client.auth.signInWith(Email) { email = "a@b.co"; password = "secreto123" } }.exceptionOrNull()
        return assertIs<AuthRestException>(failure)
    }

    private fun kind(error: Exception) = SupabaseFailureClassifier.classify(error)

    @Test
    fun timeout_y_fallo_de_transporte_son_red() {
        assertEquals(SyncFailureKind.NETWORK, kind(HttpRequestTimeoutException("https://test.supabase.co/rest/v1/decks", 10_000)))
        assertEquals(SyncFailureKind.NETWORK, kind(HttpRequestException("connection refused", HttpRequestBuilder())))
    }

    @Test
    fun un_5xx_de_PostgREST_es_red() = runBlocking {
        for (status in listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable)) {
            assertEquals(SyncFailureKind.NETWORK, kind(postgrest(status)), "PostgREST ${status.value}")
        }
    }

    @Test
    fun un_4xx_de_PostgREST_es_rechazo() = runBlocking {
        val statuses = listOf(
            HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.NotFound, HttpStatusCode.Conflict
        )
        for (status in statuses) {
            assertEquals(SyncFailureKind.REJECTED, kind(postgrest(status)), "PostgREST ${status.value}")
        }
    }

    @Test
    fun un_4xx_de_Auth_es_rechazo_pero_un_5xx_de_Auth_queda_sin_clasificar() = runBlocking {
        assertEquals(SyncFailureKind.REJECTED, kind(auth(HttpStatusCode.Unauthorized)))
        assertEquals(SyncFailureKind.UNCLASSIFIED, kind(auth(HttpStatusCode.InternalServerError)))
    }

    @Test
    fun el_resto_de_excepciones_queda_sin_clasificar() {
        assertEquals(SyncFailureKind.UNCLASSIFIED, kind(SerializationException("campo desconocido")))
        assertEquals(SyncFailureKind.UNCLASSIFIED, kind(IllegalStateException("estado inesperado")))
        assertEquals(SyncFailureKind.UNCLASSIFIED, kind(IOException("fallo de E/S")))
    }
}
