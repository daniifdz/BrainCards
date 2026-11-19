package org.braincards.app.data.repository

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeFlashcardDao
import org.braincards.app.data.fakes.FakeTopicDao
import org.braincards.app.data.fakes.FakeTransactionRunner
import org.braincards.app.data.local.entity.TopicEntity
import org.braincards.app.domain.models.Topic
import kotlin.test.Test
import kotlin.test.assertEquals

// LWW por fila: ver DeckRepositoryImpl.applyRemotePull.
class TopicRepositoryImplTest {

    private fun localEntity(id: String, updatedAt: Instant, name: String = "Local") = TopicEntity(
        id = id,
        deckId = "deck-1",
        name = name,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = updatedAt,
        userId = "user-1"
    )

    private fun remoteTopic(id: String, updatedAt: Instant, name: String = "Remote") = Topic(
        id = id,
        deckId = "deck-1",
        name = name,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = updatedAt,
        userId = "user-1"
    )

    @Test
    fun `applyRemotePull sobrescribe cuando el remoto es mas nuevo`() = runBlocking {
        val topicDao = FakeTopicDao().apply {
            seed(localEntity(id = "topic-1", updatedAt = Instant.fromEpochMilliseconds(1000)))
        }
        val repository = TopicRepositoryImpl(topicDao, FakeFlashcardDao(), FakeTransactionRunner())

        repository.applyRemotePull(listOf(remoteTopic(id = "topic-1", updatedAt = Instant.fromEpochMilliseconds(2000))))

        val result = topicDao.get("topic-1")!!
        assertEquals("Remote", result.name)
        assertEquals(Instant.fromEpochMilliseconds(2000), result.lastSyncedAt)
    }

    @Test
    fun `applyRemotePull conserva la fila local cuando es mas nueva que el remoto`() = runBlocking {
        val topicDao = FakeTopicDao().apply {
            seed(localEntity(id = "topic-1", updatedAt = Instant.fromEpochMilliseconds(3000)))
        }
        val repository = TopicRepositoryImpl(topicDao, FakeFlashcardDao(), FakeTransactionRunner())

        repository.applyRemotePull(listOf(remoteTopic(id = "topic-1", updatedAt = Instant.fromEpochMilliseconds(2000))))

        val result = topicDao.get("topic-1")!!
        assertEquals("Local", result.name)
        assertEquals(null, result.lastSyncedAt)
    }

    @Test
    fun `applyRemotePull inserta la fila cuando no existe local`() = runBlocking {
        val topicDao = FakeTopicDao()
        val repository = TopicRepositoryImpl(topicDao, FakeFlashcardDao(), FakeTransactionRunner())

        repository.applyRemotePull(listOf(remoteTopic(id = "topic-new", updatedAt = Instant.fromEpochMilliseconds(1000))))

        val result = topicDao.get("topic-new")!!
        assertEquals("Remote", result.name)
        assertEquals(Instant.fromEpochMilliseconds(1000), result.lastSyncedAt)
    }
}
