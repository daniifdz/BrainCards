package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.PullApplyReport
import org.braincards.app.domain.models.Topic

interface TopicsRepository {
    suspend fun addTopic(topic: Topic)
    suspend fun updateTopic(topic: Topic)
    suspend fun deleteTopic(topic: Topic)
    fun getTopicsByDeckId(deckId: String): Flow<List<Topic>>
    suspend fun getTopicById(id: String): Topic?
    suspend fun claimOrphanedTopics(userId: String): Int

    // Sync: mismo criterio que DecksRepository. getPendingSync incluye tombstones (deletedAt != null) y applyRemotePull
    // sincroniza lastSyncedAt en local.
    suspend fun getPendingSync(userId: String): List<Topic>

    // LWW: ver DecksRepository.applyRemotePull.
    suspend fun applyRemotePull(topics: List<Topic>): Int

    // Igual que applyRemotePull, pero informa tambien de las filas descartadas por no tener
    // su deck en la BD local. Por defecto delega en applyRemotePull (sin descartes): solo las
    // implementaciones con guarda de padres lo sobrescriben.
    suspend fun applyRemotePullReporting(topics: List<Topic>): PullApplyReport =
        PullApplyReport(applied = applyRemotePull(topics))

    suspend fun markPushed(ids: List<String>, syncedAt: Instant)

    // Ver DecksRepository.releaseFromUser.
    suspend fun releaseFromUser(userId: String): Int
}