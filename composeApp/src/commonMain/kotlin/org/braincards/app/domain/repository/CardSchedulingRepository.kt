package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.PullApplyReport

interface CardSchedulingRepository {
    suspend fun getByCardId(cardId: String): CardScheduling?
    suspend fun upsert(scheduling: CardScheduling)
    fun getAll(): Flow<List<CardScheduling>>

    // --- sync (aditivo): mismo patron que Decks/Topics/Flashcards ---

    /**
     * Adopta las filas sin dueno (userId NULL) cuya FLASHCARD ya pertenece a [userId]. El
     * scheduling debe tener siempre el mismo userId que su flashcard (lo exige la RLS de
     * Supabase), asi que hay que reclamar las flashcards ANTES que el scheduling.
     */
    suspend fun claimOrphanedScheduling(userId: String): Int

    /** Filas de [userId] pendientes de subir, solo si su flashcard ya esta subida/bajada y es del mismo dueno. */
    suspend fun getPendingSync(userId: String): List<CardScheduling>

    /**
     * LWW estricto por updatedAt (desempate determinista si son iguales) en una unica
     * transaccion. Descarta las filas cuya flashcard no existe localmente. Devuelve cuantas aplico.
     */
    suspend fun applyRemotePull(schedulings: List<CardScheduling>): Int

    /**
     * Igual que [applyRemotePull], pero informa tambien de las filas DESCARTADAS por no tener su
     * flashcard en la BD local (con sus ids = cardId). Por defecto delega en [applyRemotePull] sin
     * descartes: solo las implementaciones con guarda de padres lo sobrescriben.
     */
    suspend fun applyRemotePullReporting(schedulings: List<CardScheduling>): PullApplyReport =
        PullApplyReport(applied = applyRemotePull(schedulings))

    suspend fun markPushed(ids: List<String>, syncedAt: Instant)

    suspend fun releaseFromUser(userId: String): Int
}
