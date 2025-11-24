package org.braincards.app.domain.usecase.Decks

import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository

class UpdateDeckUseCase(private val repository: DecksRepository) {
    suspend operator fun invoke(deck: Deck) {
        repository.updateDeck(deck)
    }
}