package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toEntity
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

class FlashcardRepositoryImpl(
    private val flashcardDao: FlashcardDao
) : FlashcardRepository {

    override suspend fun addFlashcard(flashcard: Flashcard) {
        flashcardDao.addFlashcard(flashcard.toEntity())
    }

    override suspend fun getFlashcardById(id: String): Flashcard? {
        return flashcardDao.getFlashcardById(id)?.toDomain()
    }

    override fun getFlashcardsByDeckId(deckId: String): Flow<List<Flashcard>> {
        return flashcardDao.getFlashcardsByDeckId(deckId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun deleteFlashcard(flashcard: Flashcard) {
        flashcardDao.softdeleteFlashcardById(flashcard.id, Clock.System.now())
    }

    override suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant): List<Flashcard> {
        return flashcardDao.getDueFlashcardsByDeck(deckId, now).map { it.toDomain() }
    }

    override suspend fun updateFlashcard(flashcard: Flashcard) {
        flashcardDao.updateFlashcard(flashcard.toEntity())
    }

    override fun getAllFlashcards(): Flow<List<Flashcard>> {
        return flashcardDao.getAllFlashcards().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllDueFlashcards(now: Instant): List<Flashcard> =
        flashcardDao.getAllDueFlashcards(now).map { it.toDomain() }

}