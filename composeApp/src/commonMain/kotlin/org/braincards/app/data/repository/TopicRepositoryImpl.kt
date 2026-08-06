package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import org.braincards.app.data.local.dao.TopicDao
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toEntity
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.TopicsRepository

class TopicRepositoryImpl(private val topicDao: TopicDao): TopicsRepository {

    override suspend fun addTopic(topic: Topic) {
        topicDao.addTopic(topic.toEntity())
    }

    override suspend fun updateTopic(topic: Topic) {
        topicDao.updateTopic(topic.toEntity())

    }

    override suspend fun deleteTopic(topic: Topic) {
        topicDao.softdeleteTopic(topic.id, Clock.System.now())
    }

    override fun getTopicsByDeckId(deckId: String): Flow<List<Topic>> {
        return topicDao.getTopicsByDeckId(deckId).map { topics ->
            topics.map {
                it.toDomain()
            }
        }
    }

    override suspend fun getTopicById(id: String): Topic? {
        return topicDao.getTopicById(id)?.toDomain()
    }
}


