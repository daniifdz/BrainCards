package org.braincards.app.domain.usecase

import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository

class DeleteDeckUseCase(private val repository: DecksRepository) {
    suspend operator fun invoke(deck: Deck) {
        repository.deleteDeck(deck)
    }
}