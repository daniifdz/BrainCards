package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Flashcard

interface FlashcardRepository {
    suspend fun addFlashcard(flashcard: Flashcard)
    suspend fun getFlashcardById(id: Int): Flashcard?
    fun getFlashcardsByDeckId(deckId: Int): Flow<List<Flashcard>>
    suspend fun deleteFlashcard(flashcard: Flashcard)
    suspend fun getDueFlashcardsByDeck(deckId: Int, now: Instant): List<Flashcard>
    suspend fun updateFlashcard(flashcard: Flashcard)
    
    fun getAllFlashcards(): Flow<List<Flashcard>>

    suspend fun getAllDueFlashcards(now: Instant): List<Flashcard>

}