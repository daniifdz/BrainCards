package org.braincards.app.data.repository

import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FreeReviewCardsSource

class FreeReviewCardsSourceImpl(private val flashcardDao: FlashcardDao) : FreeReviewCardsSource {
    override suspend fun getAll(): List<Flashcard> = flashcardDao.getFreeReviewCards().map { it.toDomain() }

    override suspend fun getByDeck(deckId: String): List<Flashcard> =
        flashcardDao.getFreeReviewCardsByDeck(deckId).map { it.toDomain() }
}
