package org.braincards.app.presentation.screens.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.usecase.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.GetFlashcardUseCase
import org.braincards.app.domain.usecase.ReviewFlashcardUseCase

sealed class StudyState {
    data object Loading : StudyState()
    data object Empty : StudyState()
    data class Reviewing(
        val currentCard: Flashcard,
        val isAnswerVisible: Boolean = false,
        val remainingCount: Int
    ) : StudyState()
}

class StudyViewModel(
    private val getDueFlashcardsUseCase: GetDueFlashcardsUseCase,
    private val reviewFlashcardUseCase: ReviewFlashcardUseCase,
    private val getFlashcardUseCase: GetFlashcardUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<StudyState>(StudyState.Loading)
    val state: StateFlow<StudyState> = _state.asStateFlow()

    private val sessionQueue = ArrayDeque<Flashcard>()

    fun initSession(deckId: Int) {
        viewModelScope.launch {
            _state.value = StudyState.Loading
            val now = Clock.System.now()

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
                isAnswerVisible = false,
                remainingCount = sessionQueue.size
            )
        }
    }

    fun showAnswer() {
        val currentState = _state.value
        if (currentState is StudyState.Reviewing) {
            _state.value = currentState.copy(isAnswerVisible = true)
        }
    }

    fun reviewAllCards(deckId: Int) {
        viewModelScope.launch {
            _state.value = StudyState.Loading

            val allCards = getFlashcardUseCase(deckId).first()

            if (allCards.isEmpty()) {
                _state.value = StudyState.Empty
            } else {
                sessionQueue.clear()
                sessionQueue.addAll(allCards)
                sessionQueue.shuffle()
                showNextCard()
            }
        }
    }

    fun rateCard(quality: ReviewQuality) {
        val currentState = _state.value
        if (currentState is StudyState.Reviewing) {
            val card = currentState.currentCard

            viewModelScope.launch {
                reviewFlashcardUseCase(card, quality)

                sessionQueue.removeFirst()

                showNextCard()
            }
        }
    }
}