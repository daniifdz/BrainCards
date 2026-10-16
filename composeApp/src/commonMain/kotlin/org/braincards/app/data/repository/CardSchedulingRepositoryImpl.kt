package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import org.braincards.app.data.local.TransactionRunner
import org.braincards.app.data.local.dao.CardSchedulingDao
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toEntity
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.repository.CardSchedulingRepository

class CardSchedulingRepositoryImpl(
    private val cardSchedulingDao: CardSchedulingDao,
    private val transactionRunner: TransactionRunner
) : CardSchedulingRepository {

    override suspend fun getByCardId(cardId: String): CardScheduling? {
        return cardSchedulingDao.getByCardId(cardId)?.toDomain()
    }

    // insert() esta anotado @Insert(onConflict = REPLACE) -- ya es upsert: crea la fila
    // si el cardId no existe, la reemplaza entera si ya existe (cardId es la PK).
    override suspend fun upsert(scheduling: CardScheduling) {
        cardSchedulingDao.insert(scheduling.toEntity())
    }

    override fun getAll(): Flow<List<CardScheduling>> {
        return cardSchedulingDao.getAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun claimOrphanedScheduling(userId: String): Int {
        return cardSchedulingDao.claimOrphanedScheduling(userId)
    }

    override suspend fun getPendingSync(userId: String): List<CardScheduling> {
        return cardSchedulingDao.getPendingSyncScheduling(userId).map { it.toDomain() }
    }

    // LWW por fila (ver DeckRepositoryImpl.applyRemotePull) + dos diferencias:
    //  - guard: se descartan las filas cuya flashcard no existe localmente (con foreign_keys = ON
    //    una sola fila huerfana abortaria el insert de todo el lote);
    //  - todo (lectura local, decision LWW, escritura) va en UNA transaccion de escritura, para
    //    que un repaso concurrente (ReviewFlashcardUseCase.upsert) no quede pisado por un dato
    //    remoto mas viejo entre la lectura y la escritura.
    override suspend fun applyRemotePull(schedulings: List<CardScheduling>): Int {
        if (schedulings.isEmpty()) return 0
        return transactionRunner.inTransaction {
            val existingFlashcardIds = schedulings.map { it.cardId }
                .chunked(EXISTING_IDS_CHUNK)
                .flatMap { cardSchedulingDao.getExistingFlashcardIds(it) }
                .toSet()

            val toApply = schedulings.filter { remote ->
                remote.cardId in existingFlashcardIds &&
                    remoteWins(remote, cardSchedulingDao.getByCardId(remote.cardId))
            }
            if (toApply.isEmpty()) return@inTransaction 0

            cardSchedulingDao.insertAllScheduling(
                toApply.map { it.copy(lastSyncedAt = it.updatedAt).toEntity() }
            )
            toApply.size
        }
    }

    override suspend fun markPushed(ids: List<String>, syncedAt: Instant) {
        cardSchedulingDao.markSchedulingPushed(ids, syncedAt)
    }

    override suspend fun releaseFromUser(userId: String): Int {
        return cardSchedulingDao.releaseSchedulingFromUser(userId)
    }

    private companion object {
        // Limite de variables de SQLite por sentencia (conservador).
        const val EXISTING_IDS_CHUNK = 500
    }
}

/**
 * LWW estricto por updatedAt. Solo si son exactamente iguales desempata de forma
 * determinista (mayor repetitions, luego mayor interval, luego mayor nextReview) para que
 * dos dispositivos converjan al mismo valor. Todo igual -> el remoto no gana (nada que aplicar).
 */
private fun remoteWins(remote: CardScheduling, local: CardSchedulingEntity?): Boolean {
    if (local == null) return true
    if (remote.updatedAt > local.updatedAt) return true
    if (remote.updatedAt < local.updatedAt) return false
    if (remote.repetitions != local.repetitions) return remote.repetitions > local.repetitions
    if (remote.interval != local.interval) return remote.interval > local.interval
    return remote.nextReview > local.nextReview
}
