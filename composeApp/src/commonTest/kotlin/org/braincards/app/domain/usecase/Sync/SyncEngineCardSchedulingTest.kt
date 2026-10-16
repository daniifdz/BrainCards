package org.braincards.app.domain.usecase.Sync

import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sync de card_scheduling en el motor: pull, push en lotes de 100 y su manejo de errores
 * (no-red -> reintento fila a fila del lote; red -> se aborta la tabla), y regresion de que
 * decks/topics/flashcards siguen subiendo fila a fila con el corte a 3 fallos de red.
 */
class SyncEngineCardSchedulingTest {

    private val userId = "user-1"

    private class Fixture(userId: String) {
        val auth = FakeAuthRepository(initialState = AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true))
        val remote = FakeRemoteDataSource()
        val decks = FakeDecksRepository()
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        val useCase = SyncEngineUseCase(
            authRepository = auth,
            remoteDataSource = remote,
            decksRepository = decks,
            topicsRepository = topics,
            flashcardRepository = flashcards,
            cardSchedulingRepository = scheduling,
            backfillLocalDataUseCase = BackfillLocalDataUseCase(decks, topics, flashcards, scheduling)
        )
    }

    private fun scheduling(cardId: String, userId: String? = this.userId) = CardScheduling(
        cardId = cardId, easeFactor = 2.5, interval = 1, repetitions = 1,
        nextReview = Instant.fromEpochMilliseconds(5000), updatedAt = Instant.fromEpochMilliseconds(2000),
        userId = userId, lastSyncedAt = null
    )

    private fun deck(id: String) = Deck(
        id = id, name = "Deck $id", createdAt = Instant.fromEpochMilliseconds(1000),
        updatedAt = Instant.fromEpochMilliseconds(2000), description = null, userId = userId, lastSyncedAt = null
    )

    private fun networkError() = HttpRequestException("sin conexion simulada", HttpRequestBuilder())

    private suspend fun Fixture.seedScheduling(count: Int) {
        for (i in 1..count) scheduling.upsert(scheduling("c${i.toString().padStart(3, '0')}"))
    }

    private suspend fun Fixture.sync(): SyncResult.Completed = useCase() as SyncResult.Completed

    // --- pull ---

    @Test
    fun `el pull de scheduling aplica las filas remotas y las cuenta`() = runBlocking {
        val f = Fixture(userId)
        f.remote.cardSchedulingToPull = listOf(scheduling("c1"), scheduling("c2"))

        val result = f.sync()

        assertEquals(PullTableResult(applied = 2, aborted = false), result.pull.cardScheduling)
        assertEquals(1, f.scheduling.applyRemotePullCalls.size)
        assertEquals(listOf("c1", "c2"), f.scheduling.applyRemotePullCalls.single().map { it.cardId })
    }

    @Test
    fun `si falla el pull de scheduling el push se ejecuta igualmente y el resultado lo refleja`() = runBlocking {
        val f = Fixture(userId)
        f.remote.pullCardSchedulingFailure = RuntimeException("fallo simulado")
        f.scheduling.upsert(scheduling("c1"))
        f.decks.addDeck(deck("d1"))

        val result = f.sync()

        assertEquals(PullTableResult(applied = 0, aborted = true), result.pull.cardScheduling)
        assertTrue(!result.pull.decks.aborted && !result.pull.topics.aborted && !result.pull.flashcards.aborted)
        assertEquals(1, result.push.cardScheduling.succeeded)
        assertEquals(1, result.push.decks.succeeded)
        assertEquals(listOf("c1"), f.remote.pushedCardSchedulingIds)
        assertEquals(1, result.pull.abortedTableCount())
        assertTrue(!result.isFullySuccessful())
    }

    @Test
    fun `si abortan los pulls anteriores el de scheduling se intenta igualmente`() = runBlocking {
        val f = Fixture(userId)
        f.remote.pullFlashcardsFailure = RuntimeException("fallo simulado")

        f.sync()

        assertTrue("pullCardScheduling" in f.remote.calls)
    }

    // --- push en lotes ---

    @Test
    fun `250 filas pendientes se suben en lotes de 100, 100 y 50 y se marcan todas en una sola llamada`() = runBlocking {
        val f = Fixture(userId)
        f.seedScheduling(250)

        val result = f.sync()

        assertEquals(listOf(100, 100, 50), f.remote.pushCardSchedulingAttempts.map { it.size })
        assertEquals(PushTableResult(succeeded = 250, failed = 0), result.push.cardScheduling)
        assertEquals(1, f.scheduling.markPushedCalls.size)
        assertEquals(250, f.scheduling.markPushedCalls.single().first.size)
    }

    @Test
    fun `exactamente 100 filas son un solo lote y 101 son dos`() = runBlocking {
        val f100 = Fixture(userId)
        f100.seedScheduling(100)
        f100.sync()
        assertEquals(listOf(100), f100.remote.pushCardSchedulingAttempts.map { it.size })

        val f101 = Fixture(userId)
        f101.seedScheduling(101)
        f101.sync()
        assertEquals(listOf(100, 1), f101.remote.pushCardSchedulingAttempts.map { it.size })
    }

    @Test
    fun `sin filas pendientes no se llama a push ni a markPushed`() = runBlocking {
        val f = Fixture(userId)

        val result = f.sync()

        assertEquals(PushTableResult(succeeded = 0, failed = 0), result.push.cardScheduling)
        assertTrue(f.remote.pushCardSchedulingAttempts.isEmpty())
        assertTrue(f.scheduling.markPushedCalls.isEmpty())
    }

    // --- error NO de red en un lote: reintento fila a fila solo de ese lote ---

    @Test
    fun `un lote rechazado por una fila mala se reintenta fila a fila y solo se marcan las que subieron`() = runBlocking {
        val f = Fixture(userId)
        f.seedScheduling(5) // c001..c005
        f.remote.pushCardSchedulingIdsThatFail = setOf("c003")

        val result = f.sync()

        // 1 lote de 5 (falla entero, es atomico) + 5 reintentos de 1 fila.
        assertEquals(listOf(5, 1, 1, 1, 1, 1), f.remote.pushCardSchedulingAttempts.map { it.size })
        assertEquals(PushTableResult(succeeded = 4, failed = 1), result.push.cardScheduling)
        assertEquals(listOf("c001", "c002", "c004", "c005"), f.remote.pushedCardSchedulingIds)
        // markPushed solo con las subidas: la mala sigue pendiente.
        assertEquals(1, f.scheduling.markPushedCalls.size)
        assertEquals(listOf("c001", "c002", "c004", "c005"), f.scheduling.markPushedCalls.single().first)
        assertNull(f.scheduling.getByCardId("c003")!!.lastSyncedAt)
        assertTrue(f.scheduling.getByCardId("c001")!!.lastSyncedAt != null)
    }

    @Test
    fun `el reintento fila a fila solo afecta al lote que fallo, no a los demas`() = runBlocking {
        val f = Fixture(userId)
        f.seedScheduling(150) // lote 1: c001..c100, lote 2: c101..c150
        f.remote.pushCardSchedulingIdsThatFail = setOf("c120")

        val result = f.sync()

        // Lote 1 bien (100), lote 2 falla (50) y se reintenta de 1 en 1 (50 llamadas).
        assertEquals(listOf(100, 50) + List(50) { 1 }, f.remote.pushCardSchedulingAttempts.map { it.size })
        assertEquals(149, result.push.cardScheduling.succeeded)
        assertEquals(1, result.push.cardScheduling.failed)
    }

    // --- error de red: se aborta la tabla, sin reintento fila a fila ---

    @Test
    fun `un error de red en el primer lote aborta la tabla sin reintento fila a fila ni markPushed`() = runBlocking {
        val f = Fixture(userId)
        f.seedScheduling(250)
        f.remote.pushCardSchedulingCallFailures = mapOf(1 to networkError())

        val result = f.sync()

        assertEquals(listOf(100), f.remote.pushCardSchedulingAttempts.map { it.size })
        assertEquals(PushTableResult(succeeded = 0, failed = 250), result.push.cardScheduling)
        assertTrue(f.scheduling.markPushedCalls.isEmpty())
        assertTrue(!result.isFullySuccessful())
    }

    @Test
    fun `un error de red en el segundo lote conserva el primero y deja pendiente el resto`() = runBlocking {
        val f = Fixture(userId)
        f.seedScheduling(250)
        f.remote.pushCardSchedulingCallFailures = mapOf(2 to networkError())

        val result = f.sync()

        assertEquals(listOf(100, 100), f.remote.pushCardSchedulingAttempts.map { it.size })
        assertEquals(PushTableResult(succeeded = 100, failed = 150), result.push.cardScheduling)
        assertEquals(1, f.scheduling.markPushedCalls.size)
        assertEquals(100, f.scheduling.markPushedCalls.single().first.size)
        assertNull(f.scheduling.getByCardId("c101")!!.lastSyncedAt)
    }

    @Test
    fun `un error de red durante el reintento fila a fila aborta la tabla`() = runBlocking {
        val f = Fixture(userId)
        f.seedScheduling(5)
        f.remote.pushCardSchedulingIdsThatFail = setOf("c003") // el lote falla por no-red
        f.remote.pushCardSchedulingCallFailures = mapOf(3 to networkError()) // y la 3.a llamada (fila c002) por red

        val result = f.sync()

        // lote (1) + c001 ok (2) + c002 red (3) -> se corta; c003..c005 no se intentan.
        assertEquals(listOf(5, 1, 1), f.remote.pushCardSchedulingAttempts.map { it.size })
        assertEquals(PushTableResult(succeeded = 1, failed = 4), result.push.cardScheduling)
        assertEquals(listOf("c001"), f.scheduling.markPushedCalls.single().first)
    }

    // --- regresion: decks/topics/flashcards NO cambian (una peticion por fila, corte a 3 fallos de red) ---

    @Test
    fun `los decks se siguen subiendo fila a fila y el corte es a 3 fallos de red consecutivos`() = runBlocking {
        val f = Fixture(userId)
        (1..5).forEach { f.decks.addDeck(deck("d$it")) }
        f.remote.pushDeckIdsThatFail = (1..5).map { "d$it" }.toSet()
        f.remote.pushFailureFactory = { networkError() }

        val result = f.sync()

        assertEquals(3, f.remote.calls.count { it == "pushDecks" })
        assertEquals(PushTableResult(succeeded = 0, failed = 5), result.push.decks)
    }

    @Test
    fun `un rechazo no de red en decks no corta y se intentan todas las filas`() = runBlocking {
        val f = Fixture(userId)
        (1..5).forEach { f.decks.addDeck(deck("d$it")) }
        f.remote.pushDeckIdsThatFail = (1..5).map { "d$it" }.toSet()
        f.remote.pushFailureFactory = { IllegalStateException("4xx simulado") }

        val result = f.sync()

        assertEquals(5, f.remote.calls.count { it == "pushDecks" })
        assertEquals(PushTableResult(succeeded = 0, failed = 5), result.push.decks)
    }

    @Test
    fun `los decks suben una peticion por fila aunque haya muchos`() = runBlocking {
        val f = Fixture(userId)
        (1..7).forEach { f.decks.addDeck(deck("d$it")) }

        f.sync()

        assertEquals(7, f.remote.calls.count { it == "pushDecks" })
    }
}
