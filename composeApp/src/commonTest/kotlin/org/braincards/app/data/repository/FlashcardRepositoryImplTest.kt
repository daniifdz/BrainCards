package org.braincards.app.data.repository

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeFlashcardDao
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import kotlin.test.Test
import kotlin.test.assertEquals

// LWW por fila: ver DeckRepositoryImpl.applyRemotePull.
class FlashcardRepositoryImplTest {

    private fun localEntity(id: String, updatedAt: Instant, question: String = "Local") = FlashcardEntity(
        id = id,
        deckId = "deck-1",
        question = question,
        answer = "Respuesta",
        cardType = CardType.FLIP,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = updatedAt,
        userId = "user-1"
    )

    private fun remoteFlashcard(id: String, updatedAt: Instant, question: String = "Remote") = Flashcard(
        id = id,
        deckId = "deck-1",
        question = question,
        answer = "Respuesta",
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = updatedAt,
        cardType = CardType.FLIP,
        userId = "user-1"
    )

    @Test
    fun `applyRemotePull sobrescribe cuando el remoto es mas nuevo`() = runBlocking {
        val flashcardDao = FakeFlashcardDao().apply {
            seed(localEntity(id = "card-1", updatedAt = Instant.fromEpochMilliseconds(1000)))
        }
        val repository = FlashcardRepositoryImpl(flashcardDao)

        repository.applyRemotePull(listOf(remoteFlashcard(id = "card-1", updatedAt = Instant.fromEpochMilliseconds(2000))))

        val result = flashcardDao.get("card-1")!!
        assertEquals("Remote", result.question)
        assertEquals(Instant.fromEpochMilliseconds(2000), result.lastSyncedAt)
    }

    @Test
    fun `applyRemotePull conserva la fila local cuando es mas nueva que el remoto`() = runBlocking {
        val flashcardDao = FakeFlashcardDao().apply {
            seed(localEntity(id = "card-1", updatedAt = Instant.fromEpochMilliseconds(3000)))
        }
        val repository = FlashcardRepositoryImpl(flashcardDao)

        repository.applyRemotePull(listOf(remoteFlashcard(id = "card-1", updatedAt = Instant.fromEpochMilliseconds(2000))))

        val result = flashcardDao.get("card-1")!!
        assertEquals("Local", result.question)
        assertEquals(null, result.lastSyncedAt)
    }

    @Test
    fun `applyRemotePull inserta la fila cuando no existe local`() = runBlocking {
        val flashcardDao = FakeFlashcardDao()
        val repository = FlashcardRepositoryImpl(flashcardDao)

        repository.applyRemotePull(listOf(remoteFlashcard(id = "card-new", updatedAt = Instant.fromEpochMilliseconds(1000))))

        val result = flashcardDao.get("card-new")!!
        assertEquals("Remote", result.question)
        assertEquals(Instant.fromEpochMilliseconds(1000), result.lastSyncedAt)
    }
}
