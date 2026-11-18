package org.braincards.app.domain.fakes

import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FreeReviewCardsSource

/** Fuente del repaso libre en memoria: [cards] son las tarjetas ACTIVAS (las borradas no estan). Cuenta las llamadas y puede fallar. */
class FakeFreeReviewCardsSource(var cards: List<Flashcard> = emptyList()) : FreeReviewCardsSource {
    var getAllCalls = 0
        private set
    val getByDeckCalls = mutableListOf<String>()
    var failure: Exception? = null

    override suspend fun getAll(): List<Flashcard> {
        getAllCalls++
        failure?.let { throw it }
        return cards
    }

    override suspend fun getByDeck(deckId: String): List<Flashcard> {
        getByDeckCalls += deckId
        failure?.let { throw it }
        return cards.filter { it.deckId == deckId }
    }
}
