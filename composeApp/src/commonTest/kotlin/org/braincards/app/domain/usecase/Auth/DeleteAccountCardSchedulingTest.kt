package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.runBlocking
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** card_scheduling en el borrado de cuenta: se libera, y ANTES que sus flashcards. */
class DeleteAccountCardSchedulingTest {

    private val userId = "user-1"

    private fun auth() = FakeAuthRepository(
        initialState = AuthState.Authenticated(userId = userId, email = "a@b.com", sessionConfirmed = true)
    )

    @Test
    fun `un borrado exitoso libera el scheduling del usuario`() = runBlocking {
        val scheduling = FakeCardSchedulingRepository()
        val useCase = DeleteAccountUseCase(auth(), FakeDecksRepository(), FakeTopicsRepository(), FakeFlashcardRepository(), scheduling)

        useCase(userId)

        assertEquals(listOf(userId), scheduling.releaseFromUserCalls)
    }

    @Test
    fun `el scheduling se libera antes que flashcards, topics y decks`() = runBlocking {
        val log = mutableListOf<String>()
        val decks = object : DecksRepository by FakeDecksRepository() {
            override suspend fun releaseFromUser(userId: String): Int { log += "decks"; return 0 }
        }
        val topics = object : TopicsRepository by FakeTopicsRepository() {
            override suspend fun releaseFromUser(userId: String): Int { log += "topics"; return 0 }
        }
        val flashcards = object : FlashcardRepository by FakeFlashcardRepository() {
            override suspend fun releaseFromUser(userId: String): Int { log += "flashcards"; return 0 }
        }
        val scheduling = object : CardSchedulingRepository by FakeCardSchedulingRepository() {
            override suspend fun releaseFromUser(userId: String): Int { log += "scheduling"; return 0 }
        }

        DeleteAccountUseCase(auth(), decks, topics, flashcards, scheduling)(userId)

        assertEquals(listOf("scheduling", "flashcards", "topics", "decks"), log)
    }

    @Test
    fun `si falla la liberacion del scheduling se propaga, no se libera el resto ni se cierra sesion`() = runBlocking {
        val auth = auth()
        val scheduling = FakeCardSchedulingRepository().apply { releaseFailure = RuntimeException("fallo local simulado") }
        val flashcardsReleased = mutableListOf<String>()
        val flashcards = object : FlashcardRepository by FakeFlashcardRepository() {
            override suspend fun releaseFromUser(userId: String): Int { flashcardsReleased += userId; return 0 }
        }
        val useCase = DeleteAccountUseCase(auth, FakeDecksRepository(), FakeTopicsRepository(), flashcards, scheduling)

        assertFailsWith<RuntimeException> { useCase(userId) }

        assertTrue(auth.deleteAccountCalled)
        assertTrue(flashcardsReleased.isEmpty())
        assertTrue(!auth.signOutCalled)
    }

    @Test
    fun `si el RPC falla no se libera el scheduling`() = runBlocking {
        val auth = auth().apply { deleteAccountFailure = RuntimeException("fallo de red simulado") }
        val scheduling = FakeCardSchedulingRepository()
        val useCase = DeleteAccountUseCase(auth, FakeDecksRepository(), FakeTopicsRepository(), FakeFlashcardRepository(), scheduling)

        assertFailsWith<RuntimeException> { useCase(userId) }

        assertTrue(scheduling.releaseFromUserCalls.isEmpty())
    }
}
