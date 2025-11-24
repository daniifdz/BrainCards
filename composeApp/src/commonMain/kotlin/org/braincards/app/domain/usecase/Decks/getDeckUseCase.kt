package org.braincards.app.domain.usecase.Decks

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository

class GetDeckUseCase(
    private val repository: DecksRepository
) {
    operator fun invoke(): Flow<List<Deck>> {
        return repository.getAllDecks()
    }
}
