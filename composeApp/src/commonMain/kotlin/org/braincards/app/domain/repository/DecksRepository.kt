package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Deck

//adddeck, getAllDecks, getDeckbyId, deleteDeck, updatedeck


interface DecksRepository{
    suspend fun addDeck (deck: Deck)
    suspend fun deleteDeck (deck: Deck)
    suspend fun updateDeck (deck: Deck)
    fun getAllDecks(): Flow<List<Deck>>
    suspend fun getDeckById(id: String): Deck?
    suspend fun claimOrphanedDecks(userId: String): Int

    // Sync: incluye filas con deletedAt != null a propósito: un borrado lógico también es un cambio pendiente de subir
    // (tombstone).
    suspend fun getPendingSync(userId: String): List<Deck>

    // Aplica solo un subconjunto de lo recibido (LWW): se ignoran las filas cuya versión local es igual o más nueva. Devuelve
    // cuántas se aplicaron de verdad, para que SyncEngineUseCase pueda reportarlo con precisión.
    suspend fun applyRemotePull(decks: List<Deck>): Int
    suspend fun markPushed(ids: List<String>, syncedAt: Instant)

    // Inverso de claimOrphanedDecks: libera las filas de este usuario (userId = null, lastSyncedAt = null) para que una cuenta
    // futura pueda adoptarlas. Incluye tombstones a propósito: un mazo borrado lógicamente también debe quedar libre.
    suspend fun releaseFromUser(userId: String): Int
}


