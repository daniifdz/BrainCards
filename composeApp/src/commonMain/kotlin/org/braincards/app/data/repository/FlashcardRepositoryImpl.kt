package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toEntity
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.PullApplyReport
import org.braincards.app.domain.repository.FlashcardRepository

class FlashcardRepositoryImpl(
    private val flashcardDao: FlashcardDao
) : FlashcardRepository {

    override suspend fun addFlashcard(flashcard: Flashcard) {
        flashcardDao.addFlashcard(flashcard.toEntity())
    }

    override suspend fun getFlashcardById(id: String): Flashcard? {
        return flashcardDao.getFlashcardById(id)?.toDomain()
    }

    override fun getFlashcardsByDeckId(deckId: String): Flow<List<Flashcard>> {
        return flashcardDao.getFlashcardsByDeckId(deckId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun deleteFlashcard(flashcard: Flashcard) {
        flashcardDao.softdeleteFlashcardById(flashcard.id, Clock.System.now())
    }

    override suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant): List<Flashcard> {
        return flashcardDao.getDueFlashcardsByDeck(deckId, now).map { it.toDomain() }
    }

    override suspend fun updateFlashcard(flashcard: Flashcard) {
        flashcardDao.updateFlashcard(flashcard.toEntity())
    }

    override fun getAllFlashcards(): Flow<List<Flashcard>> {
        return flashcardDao.getAllFlashcards().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllDueFlashcards(now: Instant): List<Flashcard> =
        flashcardDao.getAllDueFlashcards(now).map { it.toDomain() }

    override suspend fun claimOrphanedFlashcards(userId: String): Int {
        return flashcardDao.claimOrphanedFlashcards(userId)
    }

    override suspend fun getPendingSync(userId: String): List<Flashcard> {
        return flashcardDao.getPendingSyncFlashcards(userId).map { it.toDomain() }
    }

    // LWW por fila: ver DeckRepositoryImpl.applyRemotePull.
    override suspend fun applyRemotePull(flashcards: List<Flashcard>): Int =
        applyRemotePullReporting(flashcards).applied

    // Guarda de padres: se descartan (y cuentan) las flashcards cuyo deck, o su topic si tienen,
    // no existe localmente, sin abortar la tabla -- con foreign_keys = ON, una sola huerfana
    // haria fallar el insert de todo el lote. Un padre borrado logicamente (tombstone) SI cuenta
    // como existente. Las filas descartadas no se escriben, asi que no se marcan como
    // sincronizadas (y su card_scheduling se descarta solo por la guarda de ese repositorio).
    override suspend fun applyRemotePullReporting(flashcards: List<Flashcard>): PullApplyReport {
        if (flashcards.isEmpty()) return PullApplyReport(applied = 0)
        val existingDeckIds = flashcards.map { it.deckId }.distinct()
            .chunked(EXISTING_IDS_CHUNK)
            .flatMap { flashcardDao.getExistingDeckIds(it) }
            .toSet()
        val existingTopicIds = flashcards.mapNotNull { it.topicId }.distinct()
            .chunked(EXISTING_IDS_CHUNK)
            .flatMap { flashcardDao.getExistingTopicIds(it) }
            .toSet()
        val (withParents, orphans) = flashcards.partition {
            it.deckId in existingDeckIds && (it.topicId == null || it.topicId in existingTopicIds)
        }
        val toApply = withParents.filter { remote ->
            val local = flashcardDao.getFlashcardByIdIncludingDeleted(remote.id)
            local == null || remote.updatedAt > local.updatedAt
        }
        if (toApply.isNotEmpty()) {
            val entities = toApply.map { it.copy(lastSyncedAt = it.updatedAt).toEntity() }
            flashcardDao.insertAllFlashcards(entities)
        }
        return PullApplyReport(applied = toApply.size, discarded = orphans.size, discardedIds = orphans.map { it.id })
    }

    override suspend fun markPushed(ids: List<String>, syncedAt: Instant) {
        flashcardDao.markFlashcardsPushed(ids, syncedAt)
    }

    override suspend fun releaseFromUser(userId: String): Int {
        return flashcardDao.releaseFlashcardsFromUser(userId)
    }

    private companion object {
        // Limite de variables de SQLite por sentencia (conservador), como CardSchedulingRepositoryImpl.
        const val EXISTING_IDS_CHUNK = 500
    }

}