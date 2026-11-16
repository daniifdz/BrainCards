package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.braincards.app.data.local.dao.DeckDao
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.domain.models.DeckSummary
import org.braincards.app.domain.repository.DeckSummariesObserver

class DeckSummariesObserverImpl(private val deckDao: DeckDao) : DeckSummariesObserver {

    // distinctUntilChanged: Room re-emite con cada escritura en decks o flashcards (p. ej. un pull grande) aunque la
    // lista resultante sea la misma.
    override fun observeDeckSummaries(): Flow<List<DeckSummary>> =
        deckDao.observeDeckSummaries()
            .map { rows -> rows.map { DeckSummary(deck = it.deck.toDomain(), cardCount = it.cardCount) } }
            .distinctUntilChanged()
}
