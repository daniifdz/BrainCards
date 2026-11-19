package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.local.TransactionRunner
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.local.dao.TopicDao
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toEntity
import org.braincards.app.domain.models.PullApplyReport
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.TopicsRepository

class TopicRepositoryImpl(
    private val topicDao: TopicDao,
    private val flashcardDao: FlashcardDao,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock = Clock.System
) : TopicsRepository {

    override suspend fun addTopic(topic: Topic) {
        topicDao.addTopic(topic.toEntity())
    }

    override suspend fun updateTopic(topic: Topic) {
        topicDao.updateTopic(topic.toEntity())

    }

    // Borrado logico en cascada (tema y sus tarjetas) en UNA transaccion: o se marca todo o no se marca nada.
    // Sin ella, un fallo o un cierre a mitad dejaba el tema borrado con tarjetas vivas bajo ese tema (invisibles en la
    // pantalla del mazo pero contadas en Home y en "Estudiar Todo"). Misma marca `now` para el tema y todas sus
    // tarjetas: el sync los trata como un unico cambio.
    override suspend fun deleteTopic(topic: Topic) {
        val now = clock.now()
        transactionRunner.inTransaction {
            topicDao.softdeleteTopic(topic.id, now)

            val flashcardsOfDeck = flashcardDao.getFlashcardsByDeckId(topic.deckId).first()
            val flashcardsOfThisTopic = flashcardsOfDeck.filter { it.topicId == topic.id }
            for (flashcard in flashcardsOfThisTopic) {
                flashcardDao.softdeleteFlashcardById(flashcard.id, now)
            }
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

    // LWW por fila: ver DeckRepositoryImpl.applyRemotePull.
    override suspend fun applyRemotePull(topics: List<Topic>): Int =
        applyRemotePullReporting(topics).applied

    // Guarda de padres: se descartan (y cuentan) los topics cuyo deck no existe localmente, sin
    // abortar la tabla -- con foreign_keys = ON, un solo topic huerfano haria fallar el insert
    // de todo el lote. Un deck borrado logicamente (tombstone) SI cuenta como existente.
    // Las filas descartadas no se escriben, asi que no se marcan como sincronizadas.
    override suspend fun applyRemotePullReporting(topics: List<Topic>): PullApplyReport {
        if (topics.isEmpty()) return PullApplyReport(applied = 0)
        val existingDeckIds = topics.map { it.deckId }.distinct()
            .chunked(EXISTING_IDS_CHUNK)
            .flatMap { topicDao.getExistingDeckIds(it) }
            .toSet()
        val (withParent, orphans) = topics.partition { it.deckId in existingDeckIds }
        val toApply = withParent.filter { remote ->
            val local = topicDao.getTopicByIdIncludingDeleted(remote.id)
            local == null || remote.updatedAt > local.updatedAt
        }
        if (toApply.isNotEmpty()) {
            val entities = toApply.map { it.copy(lastSyncedAt = it.updatedAt).toEntity() }
            topicDao.insertAllTopics(entities)
        }
        return PullApplyReport(applied = toApply.size, discarded = orphans.size, discardedIds = orphans.map { it.id })
    }

    override suspend fun markPushed(ids: List<String>, syncedAt: Instant) {
        topicDao.markTopicsPushed(ids, syncedAt)
    }

    override suspend fun releaseFromUser(userId: String): Int {
        return topicDao.releaseTopicsFromUser(userId)
    }

    private companion object {
        // Limite de variables de SQLite por sentencia (conservador), como CardSchedulingRepositoryImpl.
        const val EXISTING_IDS_CHUNK = 500
    }
}


