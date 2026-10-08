package org.braincards.app.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.TopicsRepository

class FakeTopicsRepository : TopicsRepository {
    private val topics = mutableMapOf<String, Topic>()
    val addedTopics = mutableListOf<Topic>()

    override suspend fun addTopic(topic: Topic) {
        topics[topic.id] = topic
        addedTopics.add(topic)
    }

    override suspend fun updateTopic(topic: Topic) {
        topics[topic.id] = topic
    }

    override suspend fun deleteTopic(topic: Topic) {
        topics.remove(topic.id)
    }

    override fun getTopicsByDeckId(deckId: String): Flow<List<Topic>> = 
        flowOf(topics.values.filter { it.deckId == deckId })

    override suspend fun getTopicById(id: String): Topic? = topics[id]

    override suspend fun claimOrphanedTopics(userId: String): Int {
        val orphaned = topics.values.filter { it.userId == null }
        orphaned.forEach { topics[it.id] = it.copy(userId = userId) }
        return orphaned.size
    }

    override suspend fun getPendingSync(userId: String): List<Topic> =
        topics.values.filter { it.userId == userId && (it.lastSyncedAt == null || it.updatedAt > it.lastSyncedAt!!) }

    override suspend fun applyRemotePull(topics: List<Topic>): Int {
        topics.forEach { this.topics[it.id] = it.copy(lastSyncedAt = it.updatedAt) }
        return topics.size
    }

    override suspend fun markPushed(ids: List<String>, syncedAt: Instant) {
        ids.forEach { id -> topics[id]?.let { topics[id] = it.copy(lastSyncedAt = syncedAt) } }
    }
}
