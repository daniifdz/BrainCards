package org.braincards.app.domain.usecase.Sync

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
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
import org.braincards.app.data.remote.RemoteDataSourceImpl
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Caracterización de cómo el motor de sync clasifica los fallos de red, con el motor REAL y excepciones REALES (las de PostgREST y las de
 * Auth salen de un cliente de Supabase contra un motor Ktor falso, sin red y sin sesión real; la sesión de Auth es en memoria).
 *
 * Una sola tabla de excepciones fija DOS decisiones del motor:
 * - al DESCARGAR: la causa del pull abortado (`PullTableResult.failure`);
 * - al SUBIR: si el fallo cuenta como «red» (se corta tras 3 fallos consecutivos) o no (se siguen intentando las demás filas).
 * Además fija que una `CancellationException` se relanza tal cual en ambos caminos, sin clasificarse ni contarse como fallo.
 *
 * Este test describe el comportamiento, no la implementación: no debe cambiar cuando la clasificación se mueva de sitio.
 */
@OptIn(InternalAPI::class)
class SyncFailureCharacterizationTest {

    private val userId = "user-1"

    private class StatusEngine(private val status: HttpStatusCode, private val body: String) :
        HttpClientEngineBase("status-characterization-engine") {
        override val config = HttpClientEngineConfig()
        override val supportedCapabilities = setOf<HttpClientEngineCapability<*>>(HttpTimeoutCapability)

        override suspend fun execute(data: HttpRequestData): HttpResponseData = HttpResponseData(
            status, GMTDate(), headersOf(HttpHeaders.ContentType, "application/json"),
            HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body.toByteArray()), callContext()
        )
    }

    /** Excepción REAL de PostgREST: la que lanza el pull cuando el servidor responde [status]. */
    private suspend fun postgrestFailure(status: HttpStatusCode): RestException {
        val client = createSupabaseClient(supabaseUrl = "https://test.supabase.co", supabaseKey = "anon-key") {
            httpEngine = StatusEngine(status, """{"message":"fallo simulado","code":"PGRST301","details":null,"hint":null}""")
            install(Postgrest)
        }
        val failure = runCatching { RemoteDataSourceImpl(client).pullDecks(userId, Instant.fromEpochMilliseconds(0)) }.exceptionOrNull()
        val rest = assertIs<PostgrestRestException>(failure)
        assertEquals(status.value, rest.statusCode, "la excepción real lleva el código HTTP del servidor")
        return rest
    }

    /** Excepción REAL de Auth (AuthRestException): un inicio de sesión contra un servidor falso que responde [status]. Sesión en memoria. */
    private suspend fun authFailure(status: HttpStatusCode): RestException {
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
        val rest = assertIs<AuthRestException>(failure)
        assertEquals(status.value, rest.statusCode, "la excepción real lleva el código HTTP del servidor")
        return rest
    }

    /** [pull]: causa esperada del pull abortado (null = sin clasificar). [pushIsNetwork]: ¿el push lo trata como fallo de red? */
    private class Case(val name: String, val pull: PullFailure?, val pushIsNetwork: Boolean, val make: suspend () -> Exception)

    private fun cases(): List<Case> = listOf(
        Case("timeout de Ktor", PullFailure.NETWORK, true) { HttpRequestTimeoutException("https://test.supabase.co/rest/v1/decks", 10_000) },
        Case("fallo de transporte", PullFailure.NETWORK, true) { HttpRequestException("connection refused", HttpRequestBuilder()) },
        Case("PostgREST 500", PullFailure.NETWORK, true) { postgrestFailure(HttpStatusCode.InternalServerError) },
        Case("PostgREST 502", PullFailure.NETWORK, true) { postgrestFailure(HttpStatusCode.BadGateway) },
        Case("PostgREST 503", PullFailure.NETWORK, true) { postgrestFailure(HttpStatusCode.ServiceUnavailable) },
        Case("PostgREST 400", PullFailure.REJECTED, false) { postgrestFailure(HttpStatusCode.BadRequest) },
        Case("PostgREST 401", PullFailure.REJECTED, false) { postgrestFailure(HttpStatusCode.Unauthorized) },
        Case("PostgREST 403", PullFailure.REJECTED, false) { postgrestFailure(HttpStatusCode.Forbidden) },
        Case("PostgREST 404", PullFailure.REJECTED, false) { postgrestFailure(HttpStatusCode.NotFound) },
        Case("PostgREST 409", PullFailure.REJECTED, false) { postgrestFailure(HttpStatusCode.Conflict) },
        // RestException que NO es de PostgREST (Auth): un 4xx es un rechazo, pero un 5xx NO cuenta como red ni como rechazo.
        Case("Auth 401", PullFailure.REJECTED, false) { authFailure(HttpStatusCode.Unauthorized) },
        Case("Auth 500", null, false) { authFailure(HttpStatusCode.InternalServerError) },
        Case("error de decodificación", null, false) { SerializationException("campo desconocido") },
        Case("IllegalStateException", null, false) { IllegalStateException("estado inesperado") },
        Case("IOException genérica", null, false) { IOException("fallo de E/S") }
    )

    private fun engine(remote: FakeRemoteDataSource, decks: FakeDecksRepository = FakeDecksRepository()): SyncEngineUseCase {
        val auth = FakeAuthRepository(AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true))
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        return SyncEngineUseCase(
            auth, remote, decks, topics, flashcards, scheduling,
            BackfillLocalDataUseCase(decks, topics, flashcards, scheduling)
        )
    }

    private suspend fun pendingDecks(count: Int): FakeDecksRepository = FakeDecksRepository().apply {
        repeat(count) { i ->
            addDeck(Deck("d$i", "Deck $i", Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(2), null, userId = userId))
        }
    }

    @Test
    fun al_descargar_cada_excepcion_da_su_causa_de_pull() = runBlocking {
        for (case in cases()) {
            val remote = FakeRemoteDataSource().apply { pullDecksFailure = case.make() }

            val completed = assertIs<SyncResult.Completed>(engine(remote)(), case.name)
            val result = completed.pull.decks

            assertTrue(result.aborted, "${case.name}: el pull debe abortar")
            assertEquals(case.pull, result.failure, "${case.name}: causa del pull")
        }
    }

    @Test
    fun al_subir_solo_los_fallos_de_red_cortan_a_los_tres_intentos() = runBlocking {
        val rows = 4
        for (case in cases()) {
            val ids = (0 until rows).map { "d$it" }.toSet()
            val exception = case.make()
            val remote = FakeRemoteDataSource().apply {
                pushDeckIdsThatFail = ids
                pushFailureFactory = { exception }
            }

            val completed = assertIs<SyncResult.Completed>(engine(remote, pendingDecks(rows))(), case.name)
            val attempts = remote.calls.count { it == "pushDecks" }

            assertEquals(if (case.pushIsNetwork) 3 else rows, attempts, "${case.name}: intentos de subida")
            assertEquals(0, completed.push.decks.succeeded, "${case.name}: nada se sube")
            assertEquals(rows, completed.push.decks.failed, "${case.name}: todo queda pendiente")
        }
    }

    @Test
    fun una_CancellationException_al_descargar_se_relanza_y_no_se_convierte_en_fallo_de_pull() = runBlocking {
        val remote = FakeRemoteDataSource().apply { pullDecksFailure = CancellationException("cancelado") }

        val thrown = assertFailsWith<CancellationException> { engine(remote)() }

        assertEquals("cancelado", thrown.message)
        assertEquals(listOf("pullDecks"), remote.calls, "el motor no sigue con otras tablas ni con el push")
    }

    @Test
    fun una_CancellationException_al_subir_se_relanza_sin_contarse_como_fallo_ni_reintentarse() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            pushDeckIdsThatFail = setOf("d0", "d1", "d2", "d3")
            pushFailureFactory = { CancellationException("cancelado") }
        }

        val thrown = assertFailsWith<CancellationException> { engine(remote, pendingDecks(4))() }

        assertEquals("cancelado", thrown.message)
        assertEquals(1, remote.calls.count { it == "pushDecks" }, "se relanza en el primer intento: no se prueban más filas")
    }
}
