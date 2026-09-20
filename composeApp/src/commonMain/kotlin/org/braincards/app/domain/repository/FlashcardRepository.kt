package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Flashcard

interface FlashcardRepository {
    suspend fun addFlashcard(flashcard: Flashcard)
    suspend fun getFlashcardById(id: String): Flashcard?
    fun getFlashcardsByDeckId(deckId: String): Flow<List<Flashcard>>
    suspend fun deleteFlashcard(flashcard: Flashcard)
    suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant): List<Flashcard>
    suspend fun updateFlashcard(flashcard: Flashcard)
    
    fun getAllFlashcards(): Flow<List<Flashcard>>

    suspend fun getAllDueFlashcards(now: Instant): List<Flashcard>

    suspend fun claimOrphanedFlashcards(userId: String): Int

    // sync (Bloque D) -- mismo criterio que DecksRepository/TopicsRepository.
    suspend fun getPendingSync(userId: String): List<Flashcard>
    suspend fun applyRemotePull(flashcards: List<Flashcard>)
    suspend fun markPushed(ids: List<String>, syncedAt: Instant)

}