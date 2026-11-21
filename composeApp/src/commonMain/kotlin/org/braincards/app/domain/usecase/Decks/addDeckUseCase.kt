package org.braincards.app.domain.usecase.Decks

import kotlinx.coroutines.flow.first
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository
import kotlinx.datetime.Clock
import org.braincards.app.domain.util.IdGenerator

class AddDeckUseCase(private val repository: DecksRepository){

    /**
     * Crea un mazo (sin descripcion: la UI ya no la pide, se guarda null). Lanza [DeckNameException] si el nombre
     * esta en blanco o ya existe un mazo vivo con ese nombre (ver [checkDeckName]).
     */
    suspend operator fun invoke(name: String) {
        checkDeckName(name, existing = repository.getAllDecks().first())

        val now = Clock.System.now()

        val newDeck = Deck(
            id = IdGenerator.newId(),
            name = name.trim(),
            description = null,
            createdAt = now,
            updatedAt = now
        )

        repository.addDeck(newDeck)
    }
}