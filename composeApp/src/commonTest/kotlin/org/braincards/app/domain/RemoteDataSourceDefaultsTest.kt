package org.braincards.app.domain

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
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.PullCursor
import org.braincards.app.domain.models.RemoteRow
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.RemoteDataSource
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import org.braincards.app.domain.usecase.Sync.SyncEngineUseCase
import org.braincards.app.domain.usecase.Sync.SyncResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Los metodos nuevos de RemoteDataSource son ADITIVOS: la implementacion por defecto delega en el
 * pull completo con cursores nulos, y el motor de sync sigue usando solo el pull completo.
 */
class RemoteDataSourceDefaultsTest {

    private val t = Instant.fromEpochMilliseconds(1000)
    private val user = "user-1"

    @Test
    fun `la implementacion por defecto delega en el pull completo y devuelve cursores nulos`() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            decksToPull = listOf(Deck("d1", "Deck", t, t, null, null, user, null))
            topicsToPull = listOf(Topic("t1", "d1", "Topic", t, t, null, null, user))
            flashcardsToPull = listOf(
                Flashcard("f1", "d1", "Q", "A", t, t, CardType.FLIP, null, null, null, user, null, null)
            )
            cardSchedulingToPull = listOf(CardScheduling("f1", 2.5, 1, 1, t, t, user, null))
        }
        val watermark = PullCursor(t, "x")

        assertEquals(remote.decksToPull.map { RemoteRow(it, null) }, remote.pullDecksSince(user, watermark))
        assertEquals(remote.topicsToPull.map { RemoteRow(it, null) }, remote.pullTopicsSince(user, watermark))
        assertEquals(remote.flashcardsToPull.map { RemoteRow(it, null) }, remote.pullFlashcardsSince(user, watermark))
        assertEquals(remote.cardSchedulingToPull.map { RemoteRow(it, null) }, remote.pullCardSchedulingSince(user, null))
    }

    /** Falla si alguien llama a un metodo *Since: el motor de este paso NO debe usarlos. */
    private class NoSinceAllowed(private val delegate: RemoteDataSource) : RemoteDataSource by delegate {
        override suspend fun pullDecksSince(userId: String, watermark: PullCursor?): List<RemoteRow<Deck>> =
            throw AssertionError("el motor no debe usar pullDecksSince en este paso")

        override suspend fun pullTopicsSince(userId: String, watermark: PullCursor?): List<RemoteRow<Topic>> =
            throw AssertionError("el motor no debe usar pullTopicsSince en este paso")

        override suspend fun pullFlashcardsSince(userId: String, watermark: PullCursor?): List<RemoteRow<Flashcard>> =
            throw AssertionError("el motor no debe usar pullFlashcardsSince en este paso")

        override suspend fun pullCardSchedulingSince(userId: String, watermark: PullCursor?): List<RemoteRow<CardScheduling>> =
            throw AssertionError("el motor no debe usar pullCardSchedulingSince en este paso")
    }

    @Test
    fun `el motor de sync sigue haciendo pull completo y no usa los metodos Since`() = runBlocking {
        val auth = FakeAuthRepository(AuthState.Authenticated(user, "a@b.c", sessionConfirmed = true))
        val remote = FakeRemoteDataSource()
        val decks = FakeDecksRepository()
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        val engine = SyncEngineUseCase(
            auth, NoSinceAllowed(remote), decks, topics, flashcards, scheduling,
            BackfillLocalDataUseCase(decks, topics, flashcards, scheduling)
        )

        val result = engine()

        assertIs<SyncResult.Completed>(result)
        assertEquals(
            listOf("pullDecks", "pullTopics", "pullFlashcards", "pullCardScheduling"),
            remote.calls.filter { it.startsWith("pull") }
        )
    }
}
