package org.braincards.app.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository

class FakeDecksRepository : DecksRepository {
    private val decks = mutableMapOf<String, Deck>()
    val addedDecks = mutableListOf<Deck>()

    override suspend fun addDeck(deck: Deck) {
        decks[deck.id] = deck
        addedDecks.add(deck)
    }

    override suspend fun deleteDeck(deck: Deck) {
        decks.remove(deck.id)
    }

    override suspend fun updateDeck(deck: Deck) {
        decks[deck.id] = deck
    }

    override fun getAllDecks(): Flow<List<Deck>> = flowOf(decks.values.toList())

    override suspend fun getDeckById(id: String): Deck? = decks[id]

    override suspend fun claimOrphanedDecks(userId: String): Int {
        val orphaned = decks.values.filter { it.userId == null }
        orphaned.forEach { decks[it.id] = it.copy(userId = userId) }
        return orphaned.size
    }
}
