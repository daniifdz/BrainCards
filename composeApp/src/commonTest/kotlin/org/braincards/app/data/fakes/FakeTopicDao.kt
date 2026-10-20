package org.braincards.app.data.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.braincards.app.data.local.dao.TopicDao
import org.braincards.app.data.local.entity.TopicEntity

/** Ver comentario en FakeDeckDao. */
class FakeTopicDao : TopicDao {
    private val topics = mutableMapOf<String, TopicEntity>()

    // Guarda de padres del pull. Por defecto PERMISIVO (todos los decks "existen") para no
    // alterar los tests de LWW; con enforceParents = true solo existen los sembrados con
    // seedDeck. El SQL real de la guarda se prueba en jvmTest (PullOrphanGuardRoomTest).
    var enforceParents = false
    private val deckIds = mutableSetOf<String>()

    fun seedDeck(id: String) {
        deckIds.add(id)
    }

    fun seed(entity: TopicEntity) {
        topics[entity.id] = entity
    }

    fun get(id: String): TopicEntity? = topics[id]

    override suspend fun addTopic(topic: TopicEntity) {
        topics[topic.id] = topic
    }

    override suspend fun updateTopic(topic: TopicEntity) {
        topics[topic.id] = topic
    }

    override suspend fun softdeleteTopic(id: String, deletedAt: Instant) {
        topics[id]?.let { topics[id] = it.copy(deletedAt = deletedAt, updatedAt = deletedAt) }
    }

    override fun getTopicsByDeckId(deckId: String): Flow<List<TopicEntity>> =
        flowOf(topics.values.filter { it.deckId == deckId && it.deletedAt == null })

    override suspend fun getTopicById(id: String): TopicEntity? =
        topics[id]?.takeIf { it.deletedAt == null }

    override suspend fun getTopicByIdIncludingDeleted(id: String): TopicEntity? = topics[id]

    override suspend fun claimOrphanedTopics(userId: String): Int {
        val orphaned = topics.values.filter { it.userId == null }
        orphaned.forEach { topics[it.id] = it.copy(userId = userId) }
        return orphaned.size
    }

    override suspend fun getPendingSyncTopics(userId: String): List<TopicEntity> =
        topics.values.filter { it.userId == userId && (it.lastSyncedAt == null || it.updatedAt > it.lastSyncedAt!!) }

    override suspend fun insertAllTopics(topics: List<TopicEntity>) {
        topics.forEach { this.topics[it.id] = it }
    }

    override suspend fun markTopicsPushed(ids: List<String>, syncedAt: Instant) {
        ids.forEach { id -> topics[id]?.let { topics[id] = it.copy(lastSyncedAt = syncedAt) } }
    }

    override suspend fun getExistingDeckIds(ids: List<String>): List<String> =
        if (enforceParents) ids.filter { it in deckIds } else ids

    override suspend fun releaseTopicsFromUser(userId: String): Int {
        val owned = topics.values.filter { it.userId == userId }
        owned.forEach { topics[it.id] = it.copy(userId = null, lastSyncedAt = null) }
        return owned.size
    }

    override suspend fun deleteAllTopics() {
        topics.clear()
    }

    override suspend fun countUnownedTopics(): Int = topics.values.count { it.userId == null }
}
