package org.braincards.app.data.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.braincards.app.data.local.dao.DeckDao
import org.braincards.app.data.local.entity.DeckEntity

/**
 * Fake en memoria de DeckDao, sin Room de verdad -- mismo patron que los fakes de
 * repositorio ya existentes, un nivel mas abajo (DAO), para poder probar la logica
 * real de DeckRepositoryImpl.applyRemotePull (LWW, Bloque E).
 */
class FakeDeckDao : DeckDao {
    private val decks = mutableMapOf<String, DeckEntity>()

    fun seed(entity: DeckEntity) {
        decks[entity.id] = entity
    }

    fun get(id: String): DeckEntity? = decks[id]

    override suspend fun addDeck(deck: DeckEntity) {
        decks[deck.id] = deck
    }

    override suspend fun updateDeck(deck: DeckEntity) {
        decks[deck.id] = deck
    }

    override suspend fun softdeleteDeck(id: String, deletedAt: Instant) {
        decks[id]?.let { decks[id] = it.copy(deletedAt = deletedAt, updatedAt = deletedAt) }
    }

    override fun getAllDecks(): Flow<List<DeckEntity>> =
        flowOf(decks.values.filter { it.deletedAt == null })

    override suspend fun getDeckById(id: String): DeckEntity? =
        decks[id]?.takeIf { it.deletedAt == null }

    override suspend fun getDeckByIdIncludingDeleted(id: String): DeckEntity? = decks[id]

    override suspend fun claimOrphanedDecks(userId: String): Int {
        val orphaned = decks.values.filter { it.userId == null }
        orphaned.forEach { decks[it.id] = it.copy(userId = userId) }
        return orphaned.size
    }

    override suspend fun getPendingSyncDecks(userId: String): List<DeckEntity> =
        decks.values.filter { it.userId == userId && (it.lastSyncedAt == null || it.updatedAt > it.lastSyncedAt!!) }

    override suspend fun insertAllDecks(decks: List<DeckEntity>) {
        decks.forEach { this.decks[it.id] = it }
    }

    override suspend fun markDecksPushed(ids: List<String>, syncedAt: Instant) {
        ids.forEach { id -> decks[id]?.let { decks[id] = it.copy(lastSyncedAt = syncedAt) } }
    }

    override suspend fun releaseDecksFromUser(userId: String): Int {
        val owned = decks.values.filter { it.userId == userId }
        owned.forEach { decks[it.id] = it.copy(userId = null, lastSyncedAt = null) }
        return owned.size
    }
}
