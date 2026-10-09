package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.local.dao.DeckDao
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.local.dao.TopicDao
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toEntity
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.TopicsRepository

class TopicRepositoryImpl(private val topicDao: TopicDao, private val flashcardDao: FlashcardDao): TopicsRepository {

    override suspend fun addTopic(topic: Topic) {
        topicDao.addTopic(topic.toEntity())
    }

    override suspend fun updateTopic(topic: Topic) {
        topicDao.updateTopic(topic.toEntity())

    }

    override suspend fun deleteTopic(topic: Topic) {
        val now = Clock.System.now()
        topicDao.softdeleteTopic(topic.id, now)


        val flashcardsOfDeck = flashcardDao.getFlashcardsByDeckId(topic.deckId).first()
        val flashcardsOfThisTopic = flashcardsOfDeck.filter{it.topicId == topic.id}
        for (flashcard in flashcardsOfThisTopic){
            flashcardDao.softdeleteFlashcardById(flashcard.id, now)
        }
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

    override suspend fun claimOrphanedTopics(userId: String): Int {
        return topicDao.claimOrphanedTopics(userId)
    }

    override suspend fun getPendingSync(userId: String): List<Topic> {
        return topicDao.getPendingSyncTopics(userId).map { it.toDomain() }
    }

    // LWW por fila (Bloque E) -- ver comentario detallado en DeckRepositoryImpl.applyRemotePull.
    override suspend fun applyRemotePull(topics: List<Topic>): Int {
        val toApply = topics.filter { remote ->
            val local = topicDao.getTopicByIdIncludingDeleted(remote.id)
            local == null || remote.updatedAt > local.updatedAt
        }
        if (toApply.isEmpty()) return 0
        val entities = toApply.map { it.copy(lastSyncedAt = it.updatedAt).toEntity() }
        topicDao.insertAllTopics(entities)
        return toApply.size
    }

    override suspend fun markPushed(ids: List<String>, syncedAt: Instant) {
        topicDao.markTopicsPushed(ids, syncedAt)
    }

    override suspend fun releaseFromUser(userId: String): Int {
        return topicDao.releaseTopicsFromUser(userId)
    }
}


