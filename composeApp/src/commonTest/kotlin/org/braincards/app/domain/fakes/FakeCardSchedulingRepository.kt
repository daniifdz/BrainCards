package org.braincards.app.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.repository.CardSchedulingRepository

/**
 * Fake en memoria de CardSchedulingRepository, sin libreria de mocking.
 * upsertCalls registra cada llamada a upsert(...) (seeds de test incluidos) para poder
 * comprobar en los tests cuantas veces se llamo y con que datos exactos.
 *
 * Los metodos de sync son una simulacion simple para los tests del SyncEngine (paso 5): NO
 * replican el LWW ni el JOIN con flashcards (eso se prueba en CardSchedulingRepositoryImplTest
 * y CardSchedulingDaoSyncTest). Aqui: pendientes = userId igual y lastSyncedAt nulo o anterior.
 */
class FakeCardSchedulingRepository : CardSchedulingRepository {

    private val schedulings = mutableMapOf<String, CardScheduling>()
    val upsertCalls = mutableListOf<CardScheduling>()

    val applyRemotePullCalls = mutableListOf<List<CardScheduling>>()
    val markPushedCalls = mutableListOf<Pair<List<String>, Instant>>()
    var claimOrphanedCalls = 0
        private set
    val releaseFromUserCalls = mutableListOf<String>()

    override suspend fun getByCardId(cardId: String): CardScheduling? = schedulings[cardId]

    override suspend fun upsert(scheduling: CardScheduling) {
        schedulings[scheduling.cardId] = scheduling
        upsertCalls.add(scheduling)
    }

    override fun getAll(): Flow<List<CardScheduling>> = flowOf(schedulings.values.toList())

    override suspend fun claimOrphanedScheduling(userId: String): Int {
        claimOrphanedCalls++
        val orphaned = schedulings.values.filter { it.userId == null }
        orphaned.forEach { schedulings[it.cardId] = it.copy(userId = userId) }
        return orphaned.size
    }

    override suspend fun getPendingSync(userId: String): List<CardScheduling> =
        schedulings.values.filter {
            it.userId == userId && (it.lastSyncedAt == null || it.updatedAt > it.lastSyncedAt!!)
        }

    override suspend fun applyRemotePull(schedulings: List<CardScheduling>): Int {
        applyRemotePullCalls.add(schedulings)
        schedulings.forEach { this.schedulings[it.cardId] = it.copy(lastSyncedAt = it.updatedAt) }
        return schedulings.size
    }

    override suspend fun markPushed(ids: List<String>, syncedAt: Instant) {
        markPushedCalls.add(ids to syncedAt)
        ids.forEach { id -> schedulings[id]?.let { schedulings[id] = it.copy(lastSyncedAt = syncedAt) } }
    }

    override suspend fun releaseFromUser(userId: String): Int {
        releaseFromUserCalls.add(userId)
        val owned = schedulings.values.filter { it.userId == userId }
        owned.forEach { schedulings[it.cardId] = it.copy(userId = null, lastSyncedAt = null) }
        return owned.size
    }
}
