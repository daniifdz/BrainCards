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
import org.braincards.app.domain.models.StudyQueue
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.domain.usecase.Flashcards.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.ReviewFlashcardUseCase

sealed class StudyState {
    //data object: para un caso sin datos propios
    data object Loading : StudyState()
    data object Empty : StudyState()

    //data classs: para un caso con datos propios, se acompaña de info concreta y
    //distinta cada vez que se usa
    data class Reviewing(
        val currentCard: Flashcard,
        val isAnswerVisible: Boolean = false,
        val remainingCount: Int
    ): StudyState()

    data class Finished(
        val totalReviewed: Int,
        val correctCount: Int,
        val failedCount: Int
    ): StudyState()
}

class StudyViewModel(
    private val getDueFlashcardsUseCase: GetDueFlashcardsUseCase,
    private val reviewFlashcardUseCase: ReviewFlashcardUseCase,
    private val getFlashcardUseCase: GetFlashcardUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<StudyState>(StudyState.Loading)
    val state: StateFlow<StudyState> = _state.asStateFlow()



    private var studyQueue: StudyQueue? = null

    private var isSubmitting :Boolean = false

    fun initSession(scope: StudyScope) {
        viewModelScope.launch {
            _state.value = StudyState.Loading
            val now = Clock.System.now()

            val dueCards = getDueFlashcardsUseCase(scope, now)

            if (dueCards.isEmpty()) {
                _state.value = StudyState.Empty
            } else {

                studyQueue = StudyQueue(
                    queue = dueCards,
                    correctCount = 0,
                    failedCount = 0
                )

                showNextCard()
            }
        }
    }

    private fun showNextCard() {
        val current = studyQueue
        val nextCard = studyQueue?.currentCard

        if (nextCard != null && current!=null) {
            _state.value = StudyState.Reviewing(
                currentCard = nextCard,
                isAnswerVisible = false,
                remainingCount = current.queue.size
            )

            //la cola esta vacia pero ya se puntuo algo, la sesión termino
        } else if(current != null && (current.correctCount + current.failedCount) >0){
            _state.value = StudyState.Finished(
                totalReviewed = current.correctCount + current.failedCount,
                correctCount = current.correctCount,
                failedCount = current.failedCount
            )
        }else{
            //cola vacia no se puntuo nada nunca, noy hay nada que estudiar
            _state.value = StudyState.Empty
        }
    }

    fun showAnswer() {
        val currentState = _state.value
        if (currentState is StudyState.Reviewing) {
            _state.value = currentState.copy(isAnswerVisible = true)
        }
    }

    fun reviewAllCards(deckId: String) {
        viewModelScope.launch {
            _state.value = StudyState.Loading

            val allCards = getFlashcardUseCase(deckId).first()

            if (allCards.isEmpty()) {
                _state.value = StudyState.Empty
            } else {
                studyQueue = StudyQueue(
                    queue = allCards.shuffled(),
                    correctCount = 0,
                    failedCount = 0
                )

                showNextCard()
            }
        }
    }

    fun rateCard(quality: ReviewQuality) {
        //usamos el valor de la variable antes de la llamada
        if(isSubmitting)return //puntuación en curso, ingora pulsación


        val currentState = _state.value
        if (currentState is StudyState.Reviewing) {
            val card = currentState.currentCard
            isSubmitting = true

                viewModelScope.launch {
                    reviewFlashcardUseCase(card, quality)


                    studyQueue = studyQueue?.submitResult(card,quality)

                    isSubmitting = false
                    showNextCard()
                }


        }
    }
}