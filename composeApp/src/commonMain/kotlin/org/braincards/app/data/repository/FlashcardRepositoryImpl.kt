package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    override suspend fun getFlashcardById(id: Int): Flashcard? {
        return flashcardDao.getFlashcardById(id)?.toDomain()
    }

    override fun getFlashcardsByDeckId(deckId: Int): Flow<List<Flashcard>> {
        return flashcardDao.getFlashcardsByDeckId(deckId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun deleteFlashcard(flashcard: Flashcard) {
        flashcardDao.deleteFlashcard(flashcard.toEntity())
    }


}