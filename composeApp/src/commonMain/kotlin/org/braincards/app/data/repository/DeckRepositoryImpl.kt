package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import org.braincards.app.data.local.dao.DeckDao
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toEntity
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository

class DeckRepositoryImpl (private val deckDao: DeckDao) : DecksRepository {
    override suspend fun addDeck(deck: Deck) {
        deckDao.addDeck(deck.toEntity())
    }

    override suspend fun deleteDeck(deck: Deck) {
        deckDao.softdeleteDeck(deck.id, Clock.System.now())
    }
    override suspend fun updateDeck(deck: Deck) {
        deckDao.updateDeck(deck.toEntity())
    }

    override fun getAllDecks(): Flow<List<Deck>> {
        return deckDao.getAllDecks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDeckById(id: String): Deck? {
        return deckDao.getDeckById(id)?.toDomain()
    }
}
