package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.Deck

//adddeck, getAllDecks, getDeckbyId, deleteDeck, updatedeck


interface DecksRepository{
    suspend fun addDeck (deck: Deck)
    suspend fun deleteDeck (deck: Deck)
    suspend fun updateDeck (deck: Deck)
    fun getAllDecks(): Flow<List<Deck>>
    suspend fun getDeckById(id: Int): Deck?
}


