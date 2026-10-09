package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Topic

interface TopicsRepository {
    suspend fun addTopic(topic: Topic)
    suspend fun updateTopic(topic: Topic)
    suspend fun deleteTopic(topic: Topic)
    fun getTopicsByDeckId(deckId: String): Flow<List<Topic>>
    suspend fun getTopicById(id: String): Topic?
    suspend fun claimOrphanedTopics(userId: String): Int

    // sync (Bloque D) -- mismo criterio que DecksRepository: getPendingSync incluye
    // tombstones (deletedAt != null), applyRemotePull sincroniza lastSyncedAt localmente.
    suspend fun getPendingSync(userId: String): List<Topic>

    // Bloque E (LWW) -- ver comentario en DecksRepository.applyRemotePull.
    suspend fun applyRemotePull(topics: List<Topic>): Int
    suspend fun markPushed(ids: List<String>, syncedAt: Instant)

    // Bloque F (borrado de cuenta) -- ver comentario en DecksRepository.releaseFromUser.
    suspend fun releaseFromUser(userId: String): Int
}