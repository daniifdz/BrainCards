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

    // sync (Bloque D) -- incluye filas con deletedAt != null a proposito: un
    // borrado logico tambien es un cambio pendiente de subir (tombstone).
    suspend fun getPendingSync(userId: String): List<Deck>

    // Bloque E (LWW): puede aplicar solo un subconjunto de lo recibido -- las filas
    // donde la version local es igual o mas nueva se ignoran. Devuelve cuantas se
    // aplicaron de verdad, para que SyncEngineUseCase pueda reportarlo con precision.
    suspend fun applyRemotePull(decks: List<Deck>): Int
    suspend fun markPushed(ids: List<String>, syncedAt: Instant)

    // Bloque F (borrado de cuenta) -- inverso de claimOrphanedDecks: libera las filas
    // de este usuario (userId = null, lastSyncedAt = null) para que una cuenta futura
    // pueda adoptarlas con claimOrphanedDecks. Incluye tombstones a proposito: un deck
    // borrado logicamente tambien debe quedar libre, no solo los activos.
    suspend fun releaseFromUser(userId: String): Int
}


