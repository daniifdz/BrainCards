package org.braincards.app.data.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.braincards.app.data.local.dao.CardSchedulingDao
import org.braincards.app.data.local.entity.CardSchedulingEntity

/**
 * Fake en memoria de CardSchedulingDao (ver FakeDeckDao). Simula ademas la tabla flashcards
 * (solo id, userId y lastSyncedAt) porque el guard del pull, el reclamo y los pendientes
 * dependen de ella en el SQL real. OJO: la semantica de esas consultas esta replicada aqui a
 * mano; el SQL de verdad se prueba en jvmTest (CardSchedulingDaoSyncTest) con Room real.
 */
class FakeCardSchedulingDao : CardSchedulingDao {
    private val schedulings = mutableMapOf<String, CardSchedulingEntity>()
    private data class FlashcardRow(val userId: String?, val lastSyncedAt: Instant?)
    private val flashcards = mutableMapOf<String, FlashcardRow>()

    fun seed(entity: CardSchedulingEntity) {
        schedulings[entity.cardId] = entity
    }

    fun seedFlashcard(id: String, userId: String? = null, lastSyncedAt: Instant? = null) {
        flashcards[id] = FlashcardRow(userId, lastSyncedAt)
    }

    fun get(cardId: String): CardSchedulingEntity? = schedulings[cardId]

    override suspend fun insert(cardScheduling: CardSchedulingEntity) {
        schedulings[cardScheduling.cardId] = cardScheduling
    }

    override suspend fun update(cardScheduling: CardSchedulingEntity) {
        schedulings[cardScheduling.cardId] = cardScheduling
    }

    override suspend fun getByCardId(cardId: String): CardSchedulingEntity? = schedulings[cardId]

    override fun getAll(): Flow<List<CardSchedulingEntity>> = flowOf(schedulings.values.toList())

    override suspend fun claimOrphanedScheduling(userId: String): Int {
        val orphaned = schedulings.values.filter { it.userId == null && flashcards[it.cardId]?.userId == userId }
        orphaned.forEach { schedulings[it.cardId] = it.copy(userId = userId) }
        return orphaned.size
    }

    override suspend fun getPendingSyncScheduling(userId: String): List<CardSchedulingEntity> =
        schedulings.values.filter { cs ->
            val f = flashcards[cs.cardId]
            cs.userId == userId && f != null && f.userId == cs.userId && f.lastSyncedAt != null &&
                (cs.lastSyncedAt == null || cs.updatedAt > cs.lastSyncedAt!!)
        }

    override suspend fun insertAllScheduling(schedulings: List<CardSchedulingEntity>) {
        schedulings.forEach { this.schedulings[it.cardId] = it }
    }

    override suspend fun markSchedulingPushed(ids: List<String>, syncedAt: Instant) {
        ids.forEach { id -> schedulings[id]?.let { schedulings[id] = it.copy(lastSyncedAt = syncedAt) } }
    }

    override suspend fun releaseSchedulingFromUser(userId: String): Int {
        val owned = schedulings.values.filter { it.userId == userId }
        owned.forEach { schedulings[it.cardId] = it.copy(userId = null, lastSyncedAt = null) }
        return owned.size
    }

    override suspend fun getExistingFlashcardIds(ids: List<String>): List<String> =
        ids.filter { it in flashcards }

    override suspend fun deleteAllScheduling() {
        schedulings.clear()
    }

    override suspend fun countUnownedScheduling(): Int = schedulings.values.count { it.userId == null }

    override suspend fun hasAnyScheduling(): Boolean = schedulings.isNotEmpty()
}
