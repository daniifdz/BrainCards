package org.braincards.app.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
}
