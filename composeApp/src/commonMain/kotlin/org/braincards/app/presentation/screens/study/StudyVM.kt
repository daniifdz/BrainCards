package org.braincards.app.presentation.screens.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.usecase.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.ReviewFlashcardUseCase

// Estado UI Sellado (Clean Architecture UI Pattern)
sealed class StudyState {
    data object Loading : StudyState()
    data object Empty : StudyState() // No hay cartas pendientes
    data class Reviewing(
        val currentCard: Flashcard,
        val isAnswerVisible: Boolean = false, // false=Front, true=Back
        val remainingCount: Int // Cuántas quedan en la cola
    ) : StudyState()
}

class StudyViewModel(
    private val getDueFlashcardsUseCase: GetDueFlashcardsUseCase,
    private val reviewFlashcardUseCase: ReviewFlashcardUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<StudyState>(StudyState.Loading)
    val state: StateFlow<StudyState> = _state.asStateFlow()

    // Cola de cartas en memoria para esta sesión
    private val sessionQueue = ArrayDeque<Flashcard>()

    // ELIMINAMOS EL BLOQUE init { ... } PORQUE NECESITAMOS EL DECK ID

    // Cambiamos a 'public' y pedimos 'deckId'
    fun initSession(deckId: Int) {
        viewModelScope.launch {
            _state.value = StudyState.Loading
            val now = Clock.System.now()

            // Obtenemos las cartas de la DB FILTRADAS POR MAZO
            val dueCards = getDueFlashcardsUseCase(deckId, now)

            if (dueCards.isEmpty()) {
                _state.value = StudyState.Empty
            } else {
                sessionQueue.clear()
                sessionQueue.addAll(dueCards)
                showNextCard()
            }
        }
    }

    private fun showNextCard() {
        val nextCard = sessionQueue.firstOrNull()
        if (nextCard == null) {
            _state.value = StudyState.Empty
        } else {
            _state.value = StudyState.Reviewing(
                currentCard = nextCard,
                isAnswerVisible = false, // Empezamos viendo la pregunta
                remainingCount = sessionQueue.size
            )
        }
    }

    // Acción 1: El usuario quiere ver la respuesta
    fun showAnswer() {
        val currentState = _state.value
        if (currentState is StudyState.Reviewing) {
            _state.value = currentState.copy(isAnswerVisible = true)
        }
    }

    // Acción 2: El usuario califica la carta
    fun rateCard(quality: ReviewQuality) {
        val currentState = _state.value
        if (currentState is StudyState.Reviewing) {
            val card = currentState.currentCard

            viewModelScope.launch {
                // 1. Aplicar el algoritmo y guardar en BD
                reviewFlashcardUseCase(card, quality)

                // 2. Sacar de la cola
                sessionQueue.removeFirst()

                // 3. Pasar a la siguiente
                showNextCard()
            }
        }
    }
}