package org.braincards.app.domain.usecase.Decks

import kotlinx.coroutines.flow.first
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository

class UpdateDeckUseCase(private val repository: DecksRepository) {

    /**
     * Guarda el mazo. Lanza [DeckNameException] si el nombre esta en blanco o, al CAMBIARLO, coincide con otro mazo
     * vivo (el propio se excluye por id). Si el nombre normalizado no cambia no se valida duplicado: asi se puede
     * guardar un mazo que ya tuviera un duplicado llegado por sincronizacion.
     */
    suspend operator fun invoke(deck: Deck) {
        val current = repository.getDeckById(deck.id)
        val unchanged = current != null && normalizeDeckName(current.name) == normalizeDeckName(deck.name)
        if (unchanged) {
            checkDeckName(deck.name, existing = emptyList(), ownId = deck.id)
        } else {
            checkDeckName(deck.name, existing = repository.getAllDecks().first(), ownId = deck.id)
        }
        repository.updateDeck(deck.copy(name = deck.name.trim()))
    }
}