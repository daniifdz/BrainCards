package org.braincards.app.data.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.local.dao.MaturityCountsRow
import org.braincards.app.data.local.dao.PendingStampRow
import org.braincards.app.data.local.entity.FlashcardEntity

/** Ver comentario en FakeDeckDao. */
class FakeFlashcardDao : FlashcardDao {
    private val flashcards = mutableMapOf<String, FlashcardEntity>()

    // Guarda de padres del pull. Por defecto PERMISIVO (todos los decks/topics "existen") para
    // no alterar los tests de LWW; con enforceParents = true solo existen los sembrados con
    // seedDeck/seedTopic. El SQL real de la guarda se prueba en jvmTest (PullOrphanGuardRoomTest).
    var enforceParents = false
    private val deckIds = mutableSetOf<String>()
    private val topicIds = mutableSetOf<String>()

    fun seedDeck(id: String) {
        deckIds.add(id)
    }

    fun seedTopic(id: String) {
        topicIds.add(id)
    }

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

    // Repaso libre: tarjetas no borradas, por id. La regla del mazo vivo (LIVE_DECK_JOIN) la prueba FreeReviewCardsRoomTest con Room real.
    override suspend fun getFreeReviewCards(): List<FlashcardEntity> =
        flashcards.values.filter { it.deletedAt == null }.sortedBy { it.id }

    override suspend fun getFreeReviewCardsByDeck(deckId: String): List<FlashcardEntity> =
        getFreeReviewCards().filter { it.deckId == deckId }

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

    override suspend fun getExistingDeckIds(ids: List<String>): List<String> =
        if (enforceParents) ids.filter { it in deckIds } else ids

    override suspend fun getExistingTopicIds(ids: List<String>): List<String> =
        if (enforceParents) ids.filter { it in topicIds } else ids

    override suspend fun releaseFlashcardsFromUser(userId: String): Int {
        val owned = flashcards.values.filter { it.userId == userId }
        owned.forEach { flashcards[it.id] = it.copy(userId = null, lastSyncedAt = null) }
        return owned.size
    }

    override suspend fun deleteAllFlashcards() {
        flashcards.clear()
    }

    override suspend fun countUnownedFlashcards(): Int = flashcards.values.count { it.userId == null }

    override suspend fun hasAnyFlashcard(): Boolean = flashcards.isNotEmpty()

    // Los tests con fakes no observan el contador de pendientes (se prueba contra Room real: DueCountRoomTest).
    override fun observeDueCount(now: Instant): Flow<Int> = flowOf(0)

    override fun observeMaturityCounts(): Flow<MaturityCountsRow> = flowOf(MaturityCountsRow(0, 0, 0))

    // Sync automatica: los tests con fakes no observan pendientes (eso se prueba contra Room real).
    override fun observePendingStamp(userId: String): Flow<PendingStampRow> = flowOf(PendingStampRow(0, 0))
}
