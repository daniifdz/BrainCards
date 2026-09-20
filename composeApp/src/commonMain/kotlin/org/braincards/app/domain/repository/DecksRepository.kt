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
    suspend fun applyRemotePull(decks: List<Deck>)
    suspend fun markPushed(ids: List<String>, syncedAt: Instant)
}


