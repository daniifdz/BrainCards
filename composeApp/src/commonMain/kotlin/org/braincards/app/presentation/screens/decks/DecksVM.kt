package org.braincards.app.presentation.screens.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.usecase.AddDeckUseCase
import org.braincards.app.domain.usecase.GetDeckUseCase

class DecksViewModel(
    private val addDeckUseCase: AddDeckUseCase,
    getDecksUseCase: GetDeckUseCase
) : ViewModel() {

    //lista de mazos actualizada
    val decks: StateFlow<List<Deck>> = getDecksUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    //crear mazo desde ui
    fun createDeck(name: String, description: String?) {
        viewModelScope.launch {
            addDeckUseCase(name, description)
        }
    }
}
