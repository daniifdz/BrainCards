package org.braincards.app.domain.usecase.Auth

import kotlinx.coroutines.runBlocking
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class BackfillLocalDataUseCaseTest {

    @Test
    fun `reclama decks, topics y flashcards ANTES que scheduling`() = runBlocking {
        val log = mutableListOf<String>()
        val decks = object : DecksRepository by FakeDecksRepository() {
            override suspend fun claimOrphanedDecks(userId: String): Int { log += "decks:$userId"; return 0 }
        }
        val topics = object : TopicsRepository by FakeTopicsRepository() {
            override suspend fun claimOrphanedTopics(userId: String): Int { log += "topics:$userId"; return 0 }
        }
        val flashcards = object : FlashcardRepository by FakeFlashcardRepository() {
            override suspend fun claimOrphanedFlashcards(userId: String): Int { log += "flashcards:$userId"; return 0 }
        }
        val scheduling = object : CardSchedulingRepository by FakeCardSchedulingRepository() {
            override suspend fun claimOrphanedScheduling(userId: String): Int { log += "scheduling:$userId"; return 0 }
        }

        BackfillLocalDataUseCase(decks, topics, flashcards, scheduling)("user-1")

        // scheduling el ultimo: su reclamo solo adopta filas cuya flashcard ya es del usuario.
        assertEquals(listOf("decks:user-1", "topics:user-1", "flashcards:user-1", "scheduling:user-1"), log)
    }
}
