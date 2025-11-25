package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.Topic

interface TopicsRepository {
    suspend fun addTopic(topic: Topic)
    suspend fun updateTopic(topic: Topic)
    suspend fun deleteTopic(topic: Topic)
    fun getTopicsByDeckId(deckId: Int): Flow<List<Topic>>
    suspend fun getTopicById(id: Int): Topic?
}