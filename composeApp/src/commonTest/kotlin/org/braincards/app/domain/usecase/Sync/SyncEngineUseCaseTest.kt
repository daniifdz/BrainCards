package org.braincards.app.domain.usecase.Sync

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.PullApplyReport
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Decoradores locales a este test, delegando en los fakes de repositorio que ya
// existen (FakeDecksRepository/FakeTopicsRepository/FakeFlashcardRepository, sin
// tocarlos) -- solo anaden el registro de orden en callLog para los 3 metodos que
// marcan cada fase (backfill / pull / push), usando delegacion de interfaz de
// Kotlin (`by`) para el resto.
private class OrderTrackingDecksRepository(
    private val delegate: DecksRepository,
    private val callLog: MutableList<String>
) : DecksRepository by delegate {
    override suspend fun claimOrphanedDecks(userId: String): Int {
        callLog.add("backfill:decks")
        return delegate.claimOrphanedDecks(userId)
    }
    override suspend fun applyRemotePull(decks: List<Deck>): Int {
        callLog.add("pull:decks")
        return delegate.applyRemotePull(decks)
    }
    override suspend fun getPendingSync(userId: String): List<Deck> {
        callLog.add("push:decks")
        return delegate.getPendingSync(userId)
    }
}

private class OrderTrackingTopicsRepository(
    private val delegate: TopicsRepository,
    private val callLog: MutableList<String>
) : TopicsRepository by delegate {
    override suspend fun claimOrphanedTopics(userId: String): Int {
        callLog.add("backfill:topics")
        return delegate.claimOrphanedTopics(userId)
    }
    override suspend fun applyRemotePull(topics: List<Topic>): Int {
        callLog.add("pull:topics")
        return delegate.applyRemotePull(topics)
    }
    // El motor aplica el pull de topics con applyRemotePullReporting (guarda de padres).
    override suspend fun applyRemotePullReporting(topics: List<Topic>): PullApplyReport {
        callLog.add("pull:topics")
        return delegate.applyRemotePullReporting(topics)
    }
    override suspend fun getPendingSync(userId: String): List<Topic> {
        callLog.add("push:topics")
        return delegate.getPendingSync(userId)
    }
}

private class OrderTrackingFlashcardRepository(
    private val delegate: FlashcardRepository,
    private val callLog: MutableList<String>
) : FlashcardRepository by delegate {
    override suspend fun claimOrphanedFlashcards(userId: String): Int {
        callLog.add("backfill:flashcards")
        return delegate.claimOrphanedFlashcards(userId)
    }
    override suspend fun applyRemotePull(flashcards: List<Flashcard>): Int {
        callLog.add("pull:flashcards")
        return delegate.applyRemotePull(flashcards)
    }
    // El motor aplica el pull de flashcards con applyRemotePullReporting (guarda de padres).
    override suspend fun applyRemotePullReporting(flashcards: List<Flashcard>): PullApplyReport {
        callLog.add("pull:flashcards")
        return delegate.applyRemotePullReporting(flashcards)
    }
    override suspend fun getPendingSync(userId: String): List<Flashcard> {
        callLog.add("push:flashcards")
        return delegate.getPendingSync(userId)
    }
}

private class OrderTrackingCardSchedulingRepository(
    private val delegate: CardSchedulingRepository,
    private val callLog: MutableList<String>
) : CardSchedulingRepository by delegate {
    override suspend fun claimOrphanedScheduling(userId: String): Int {
        callLog.add("backfill:scheduling")
        return delegate.claimOrphanedScheduling(userId)
    }
    override suspend fun applyRemotePull(schedulings: List<CardScheduling>): Int {
        callLog.add("pull:scheduling")
        return delegate.applyRemotePull(schedulings)
    }
    // El motor aplica el pull de scheduling con applyRemotePullReporting (guarda de padres).
    override suspend fun applyRemotePullReporting(schedulings: List<CardScheduling>): PullApplyReport {
        callLog.add("pull:scheduling")
        return delegate.applyRemotePullReporting(schedulings)
    }
    override suspend fun getPendingSync(userId: String): List<CardScheduling> {
        callLog.add("push:scheduling")
        return delegate.getPendingSync(userId)
    }
}

class SyncEngineUseCaseTest {

    private val userId = "user-1"

    private fun buildDeck(
        id: String,
        userId: String? = this.userId,
        updatedAt: Instant = Instant.fromEpochMilliseconds(2000),
        lastSyncedAt: Instant? = null
    ) = Deck(
        id = id,
        name = "Deck $id",
        createdAt = Instant.fromEpochMilliseconds(1000),
        updatedAt = updatedAt,
        description = null,
        userId = userId,
        lastSyncedAt = lastSyncedAt
    )

    private fun buildUseCase(
        authRepository: FakeAuthRepository,
        remoteDataSource: FakeRemoteDataSource,
        decksRepository: DecksRepository,
        topicsRepository: TopicsRepository,
        flashcardRepository: FlashcardRepository,
        cardSchedulingRepository: CardSchedulingRepository,
        backfillLocalDataUseCase: BackfillLocalDataUseCase
    ) = SyncEngineUseCase(
        authRepository = authRepository,
        remoteDataSource = remoteDataSource,
        decksRepository = decksRepository,
        topicsRepository = topicsRepository,
        flashcardRepository = flashcardRepository,
        cardSchedulingRepository = cardSchedulingRepository,
        backfillLocalDataUseCase = backfillLocalDataUseCase
    )

    @Test
    fun `sin sesion activa devuelve NotAuthenticated y no toca backfill, pull ni push`() = runBlocking {
        val authRepository = FakeAuthRepository(initialState = AuthState.NotAuthenticated)
        val remoteDataSource = FakeRemoteDataSource()
        val decksRepository = FakeDecksRepository()
        val topicsRepository = FakeTopicsRepository()
        val flashcardRepository = FakeFlashcardRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        val backfillLocalDataUseCase = BackfillLocalDataUseCase(decksRepository, topicsRepository, flashcardRepository, cardSchedulingRepository)

        // Fila huerfana sembrada a proposito: si el backfill se ejecutara por error
        // pese a no haber sesion, esto lo detectaria (seguiria con userId null).
        decksRepository.addDeck(buildDeck(id = "deck-orphan", userId = null))

        val useCase = buildUseCase(authRepository, remoteDataSource, decksRepository, topicsRepository, flashcardRepository, cardSchedulingRepository, backfillLocalDataUseCase)

        val result = useCase()

        assertEquals(SyncResult.NotAuthenticated, result)
        assertTrue(remoteDataSource.calls.isEmpty())
        assertEquals(null, decksRepository.getDeckById("deck-orphan")?.userId)
    }

    @Test
    fun `con sesion activa el backfill corre antes que el pull, y el pull antes que el push`() = runBlocking {
        val authRepository = FakeAuthRepository(initialState = AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true))
        val remoteDataSource = FakeRemoteDataSource()
        val callLog = mutableListOf<String>()
        val decksRepository = OrderTrackingDecksRepository(FakeDecksRepository(), callLog)
        val topicsRepository = OrderTrackingTopicsRepository(FakeTopicsRepository(), callLog)
        val flashcardRepository = OrderTrackingFlashcardRepository(FakeFlashcardRepository(), callLog)
        val cardSchedulingRepository = OrderTrackingCardSchedulingRepository(FakeCardSchedulingRepository(), callLog)
        val backfillLocalDataUseCase = BackfillLocalDataUseCase(decksRepository, topicsRepository, flashcardRepository, cardSchedulingRepository)

        val useCase = buildUseCase(authRepository, remoteDataSource, decksRepository, topicsRepository, flashcardRepository, cardSchedulingRepository, backfillLocalDataUseCase)

        useCase()

        // scheduling siempre el ULTIMO de cada fase: su reclamo exige la flashcard ya
        // reclamada, su pull descarta filas sin flashcard local y su push exige la flashcard subida.
        assertEquals(
            listOf(
                "backfill:decks", "backfill:topics", "backfill:flashcards", "backfill:scheduling",
                "pull:decks", "pull:topics", "pull:flashcards", "pull:scheduling",
                "push:decks", "push:topics", "push:flashcards", "push:scheduling"
            ),
            callLog
        )
    }

    @Test
    fun `si el pull falla el push se ejecuta igualmente`() = runBlocking {
        val authRepository = FakeAuthRepository(initialState = AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true))
        val remoteDataSource = FakeRemoteDataSource().apply {
            pullDecksFailure = RuntimeException("fallo de red simulado")
        }
        val decksRepository = FakeDecksRepository()
        val topicsRepository = FakeTopicsRepository()
        val flashcardRepository = FakeFlashcardRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        val backfillLocalDataUseCase = BackfillLocalDataUseCase(decksRepository, topicsRepository, flashcardRepository, cardSchedulingRepository)

        // Deck pendiente de subir -- si el push no se ejecutara tras el fallo del
        // pull, este deck nunca llegaria a remoteDataSource.pushedDeckIds.
        decksRepository.addDeck(buildDeck(id = "deck-pending"))

        val useCase = buildUseCase(authRepository, remoteDataSource, decksRepository, topicsRepository, flashcardRepository, cardSchedulingRepository, backfillLocalDataUseCase)

        val result = useCase()

        assertTrue(result is SyncResult.Completed)
        result as SyncResult.Completed
        assertTrue(result.pull.decks.aborted)
        assertEquals(0, result.pull.decks.applied)
        assertTrue("deck-pending" in remoteDataSource.pushedDeckIds)
        assertEquals(1, result.push.decks.succeeded)
    }

    @Test
    fun `un push con una fila fallida devuelve el resultado parcial correcto`() = runBlocking {
        val authRepository = FakeAuthRepository(initialState = AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true))
        val remoteDataSource = FakeRemoteDataSource().apply {
            pushDeckIdsThatFail = setOf("deck-fails")
        }
        val decksRepository = FakeDecksRepository()
        val topicsRepository = FakeTopicsRepository()
        val flashcardRepository = FakeFlashcardRepository()
        val cardSchedulingRepository = FakeCardSchedulingRepository()
        val backfillLocalDataUseCase = BackfillLocalDataUseCase(decksRepository, topicsRepository, flashcardRepository, cardSchedulingRepository)

        decksRepository.addDeck(buildDeck(id = "deck-ok"))
        decksRepository.addDeck(buildDeck(id = "deck-fails"))

        val useCase = buildUseCase(authRepository, remoteDataSource, decksRepository, topicsRepository, flashcardRepository, cardSchedulingRepository, backfillLocalDataUseCase)

        val result = useCase()

        assertTrue(result is SyncResult.Completed)
        result as SyncResult.Completed
        assertEquals(1, result.push.decks.succeeded)
        assertEquals(1, result.push.decks.failed)
        assertTrue("deck-ok" in remoteDataSource.pushedDeckIds)
        assertTrue("deck-fails" !in remoteDataSource.pushedDeckIds)
        // La fila fallida sigue pendiente para el siguiente sync -- no se marco pushed.
        assertEquals(null, decksRepository.getDeckById("deck-fails")?.lastSyncedAt)
    }
}
