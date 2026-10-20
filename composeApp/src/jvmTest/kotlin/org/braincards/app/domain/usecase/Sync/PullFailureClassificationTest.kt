package org.braincards.app.domain.usecase.Sync

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
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
import org.braincards.app.data.remote.RemoteDataSourceImpl
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Clasificacion de la causa de un pull abortado (PullTableResult.failure) con el motor REAL y
 * excepciones REALES: los RestException de PostgREST (401/403/500) salen de un RemoteDataSourceImpl
 * contra un motor Ktor falso, no se fabrican a mano.
 */
@OptIn(InternalAPI::class)
class PullFailureClassificationTest {

    private val userId = "user-1"

    private class StatusEngine(private val status: HttpStatusCode, private val body: String) :
        HttpClientEngineBase("status-test-engine") {
        override val config = HttpClientEngineConfig()
        override val supportedCapabilities = setOf<HttpClientEngineCapability<*>>(HttpTimeoutCapability)

        override suspend fun execute(data: HttpRequestData): HttpResponseData = HttpResponseData(
            status, GMTDate(), headersOf(HttpHeaders.ContentType, "application/json"),
            HttpProtocolVersion.HTTP_1_1, ByteReadChannel(body.toByteArray()), callContext()
        )
    }

    /** Excepcion REAL que lanza el pull cuando el servidor responde [status]. */
    private suspend fun realRestFailure(status: HttpStatusCode): RestException {
        val client = createSupabaseClient(supabaseUrl = "https://test.supabase.co", supabaseKey = "anon-key") {
            httpEngine = StatusEngine(status, """{"message":"fallo simulado","code":"PGRST301","details":null,"hint":null}""")
            install(Postgrest)
        }
        val failure = runCatching {
            RemoteDataSourceImpl(client).pullDecks(userId, Instant.fromEpochMilliseconds(0))
        }.exceptionOrNull()
        return assertIs<RestException>(failure)
    }

    /** Decks cuyo applyRemotePull lanza: simula un fallo de Room al guardar las filas descargadas. */
    private class ApplyFailsDecksRepository(delegate: DecksRepository) : DecksRepository by delegate {
        override suspend fun applyRemotePull(decks: List<Deck>): Int =
            throw IllegalStateException("FOREIGN KEY constraint failed (code 787)")
    }

    private fun engine(remote: FakeRemoteDataSource, decks: DecksRepository = FakeDecksRepository()): SyncEngineUseCase {
        val auth = FakeAuthRepository(AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true))
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        return SyncEngineUseCase(
            auth, remote, decks, topics, flashcards, scheduling,
            BackfillLocalDataUseCase(decks, topics, flashcards, scheduling)
        )
    }

    private suspend fun decksPullResult(engine: SyncEngineUseCase): PullTableResult {
        val completed = assertIs<SyncResult.Completed>(engine())
        return completed.pull.decks
    }

    // --- fetch: red ---

    @Test
    fun un_timeout_al_descargar_es_NETWORK() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            pullDecksFailure = HttpRequestTimeoutException("https://test.supabase.co/rest/v1/decks", 10_000)
        }

        val result = decksPullResult(engine(remote))

        assertTrue(result.aborted)
        assertEquals(PullFailure.NETWORK, result.failure)
    }

    @Test
    fun un_fallo_de_transporte_al_descargar_es_NETWORK() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            pullDecksFailure = HttpRequestException("connection refused", HttpRequestBuilder())
        }

        assertEquals(PullFailure.NETWORK, decksPullResult(engine(remote)).failure)
    }

    @Test
    fun un_500_real_de_PostgREST_es_NETWORK() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            pullDecksFailure = realRestFailure(HttpStatusCode.InternalServerError)
        }

        assertEquals(PullFailure.NETWORK, decksPullResult(engine(remote)).failure)
    }

    // --- fetch: el servidor rechaza ---

    @Test
    fun un_401_real_de_PostgREST_es_REJECTED() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            pullDecksFailure = realRestFailure(HttpStatusCode.Unauthorized)
        }

        assertEquals(PullFailure.REJECTED, decksPullResult(engine(remote)).failure)
    }

    @Test
    fun un_403_real_de_PostgREST_es_REJECTED() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            pullDecksFailure = realRestFailure(HttpStatusCode.Forbidden)
        }

        assertEquals(PullFailure.REJECTED, decksPullResult(engine(remote)).failure)
    }

    // --- fetch: sin clasificar ---

    @Test
    fun una_excepcion_desconocida_al_descargar_queda_sin_clasificar() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            pullDecksFailure = kotlinx.serialization.SerializationException("campo desconocido")
        }

        val result = decksPullResult(engine(remote))

        assertTrue(result.aborted)
        assertNull(result.failure)
    }

    // --- apply: local ---

    @Test
    fun un_fallo_al_aplicar_las_filas_descargadas_es_LOCAL_aunque_la_descarga_fuera_bien() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            decksToPull = listOf(Deck("d1", "Deck", Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(2), null, userId = userId))
        }

        val result = decksPullResult(engine(remote, decks = ApplyFailsDecksRepository(FakeDecksRepository())))

        assertTrue(result.aborted)
        assertEquals(0, result.applied)
        assertEquals(PullFailure.LOCAL, result.failure)
    }

    // --- sin fallo ---

    @Test
    fun un_pull_correcto_no_tiene_causa_de_fallo() = runBlocking {
        val result = decksPullResult(engine(FakeRemoteDataSource()))

        assertFalse(result.aborted)
        assertNull(result.failure)
    }
}
