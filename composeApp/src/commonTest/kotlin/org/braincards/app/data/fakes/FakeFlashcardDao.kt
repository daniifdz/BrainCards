package org.braincards.app.data.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.local.entity.FlashcardEntity

/** Ver comentario en FakeDeckDao. */
class FakeFlashcardDao : FlashcardDao {
    private val flashcards = mutableMapOf<String, FlashcardEntity>()

    fun seed(entity: FlashcardEntity) {
        flashcards[entity.id] = entity
    }

    fun get(id: String): FlashcardEntity? = flashcards[id]

    override suspend fun addFlashcard(flashcard: FlashcardEntity) {
        flashcards[flashcard.id] = flashcard
    }

    override suspend fun updateFlashcard(flashcard: FlashcardEntity) {
        flashcards[flashcard.id] = flashcard
    }

    override suspend fun getFlashcardById(id: String): FlashcardEntity? =
        flashcards[id]?.takeIf { it.deletedAt == null }

    override suspend fun getFlashcardByIdIncludingDeleted(id: String): FlashcardEntity? = flashcards[id]

    override fun getFlashcardsByDeckId(deckId: String): Flow<List<FlashcardEntity>> =
        flowOf(flashcards.values.filter { it.deckId == deckId && it.deletedAt == null })

    override suspend fun softdeleteFlashcardById(id: String, deletedAt: Instant) {
        flashcards[id]?.let { flashcards[id] = it.copy(deletedAt = deletedAt, updatedAt = deletedAt) }
    }

    // "due" depende de card_scheduling, que este fake no modela -- sin uso en los
    // tests de LWW (mismo criterio que FakeFlashcardRepository).
    override suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant): List<FlashcardEntity> = emptyList()

    override fun getAllFlashcards(): Flow<List<FlashcardEntity>> =
        flowOf(flashcards.values.filter { it.deletedAt == null })

    override suspend fun getAllDueFlashcards(now: Instant): List<FlashcardEntity> = emptyList()

    override suspend fun claimOrphanedFlashcards(userId: String): Int {
        val orphaned = flashcards.values.filter { it.userId == null }
        orphaned.forEach { flashcards[it.id] = it.copy(userId = userId) }
        return orphaned.size
    }

    override suspend fun getPendingSyncFlashcards(userId: String): List<FlashcardEntity> =
        flashcards.values.filter { it.userId == userId && (it.lastSyncedAt == null || it.updatedAt > it.lastSyncedAt!!) }

    override suspend fun insertAllFlashcards(flashcards: List<FlashcardEntity>) {
        flashcards.forEach { this.flashcards[it.id] = it }
    }

    override suspend fun markFlashcardsPushed(ids: List<String>, syncedAt: Instant) {
        ids.forEach { id -> flashcards[id]?.let { flashcards[id] = it.copy(lastSyncedAt = syncedAt) } }
    }

    override suspend fun releaseFlashcardsFromUser(userId: String): Int {
        val owned = flashcards.values.filter { it.userId == userId }
        owned.forEach { flashcards[it.id] = it.copy(userId = null, lastSyncedAt = null) }
        return owned.size
    }
}
