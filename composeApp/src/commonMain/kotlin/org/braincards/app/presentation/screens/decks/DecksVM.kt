package org.braincards.app.presentation.screens.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.usecase.Decks.AddDeckUseCase
import org.braincards.app.domain.usecase.Decks.GetDeckUseCase
import org.braincards.app.domain.usecase.Decks.UpdateDeckUseCase
import org.braincards.app.domain.usecase.DeleteDeckUseCase

class DecksViewModel(
    private val addDeckUseCase: AddDeckUseCase,
    getDecksUseCase: GetDeckUseCase,
    private val updateDeckUseCase: UpdateDeckUseCase,
    private val deleteDeckUseCase: DeleteDeckUseCase
) : ViewModel() {

    // Lista de mazos actualizada
    val decks: StateFlow<List<Deck>> = getDecksUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Crear mazo
    fun createDeck(name: String, description: String?) {
        viewModelScope.launch {
            addDeckUseCase(name, description)
        }
    }

    // Borrar mazo
    fun deleteDeck(deck: Deck) {
        viewModelScope.launch {
            deleteDeckUseCase(deck)
        }
    }

    // Editar mazo
    fun updateDeck(deck: Deck, newName: String, newDescription: String?) {
        viewModelScope.launch {
            val updatedDeck = deck.copy(
                name = newName,
                description = newDescription,
                updatedAt = Clock.System.now()
            )
            updateDeckUseCase(updatedDeck)
        }
    }
}