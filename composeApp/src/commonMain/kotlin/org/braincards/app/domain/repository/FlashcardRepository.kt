package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.PullApplyReport

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

    // Sync: mismo criterio que en DecksRepository y TopicsRepository.
    suspend fun getPendingSync(userId: String): List<Flashcard>

    // LWW: ver DecksRepository.applyRemotePull.
    suspend fun applyRemotePull(flashcards: List<Flashcard>): Int

    // Igual que applyRemotePull, pero informa tambien de las filas descartadas por no tener
    // su deck (o su topic) en la BD local. Por defecto delega en applyRemotePull (sin
    // descartes): solo las implementaciones con guarda de padres lo sobrescriben.
    suspend fun applyRemotePullReporting(flashcards: List<Flashcard>): PullApplyReport =
        PullApplyReport(applied = applyRemotePull(flashcards))

    suspend fun markPushed(ids: List<String>, syncedAt: Instant)

    // Ver DecksRepository.releaseFromUser.
    suspend fun releaseFromUser(userId: String): Int

}