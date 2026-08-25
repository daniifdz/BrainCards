package org.braincards.app.domain.usecase.Decks

import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository

class UpdateDeckUseCase(private val repository: DecksRepository) {
    suspend operator fun invoke(deck: Deck) {
        require(deck.name.isNotBlank()) { "El nombre del mazo no puede estar vacío" }
        repository.updateDeck(deck)
    }
}