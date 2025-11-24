package org.braincards.app.presentation.screens.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.usecase.Flashcards.AddFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.DeleteFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.UpdateFlashcardUseCase

class FlashCardViewModel(
    private val addFlashcardUseCase: AddFlashcardUseCase,
    private val getFlashcardUseCase: GetFlashcardUseCase,
    private val updateFlashcardUseCase: UpdateFlashcardUseCase,
    private val deleteFlashcardUseCase: DeleteFlashcardUseCase
) : ViewModel() {

    private val _flashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    val flashcards: StateFlow<List<Flashcard>> = _flashcards.asStateFlow()

    private var loadJob: Job? = null


    fun loadFlashcards(deckId: Int) {
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            getFlashcardUseCase(deckId)
                .catch { e ->
                    println("Error al cargar las flashcards: ${e.message}")
                }
                .collectLatest { list ->
                    _flashcards.value = list
                }
        }
    }

    fun addFlashcard(deckId: Int, question: String, answer: String) {
        viewModelScope.launch {
            val now = Clock.System.now()
            val newCard = Flashcard(
                id = 0,
                deckId = deckId,
                question = question,
                answer = answer,
                
                easeFactor = 2.5,
                interval = 0,
                repetitions = 0,
                
                createdAt = now,
                updatedAt = now,
                nextReview = now
            )

            try {
                addFlashcardUseCase(newCard)
            } catch (e: Exception) {
                println("Error al añadir flashcard: ${e.message}")
            }
        }
    }

    fun updateFlashcard(flashcard: Flashcard, newQuestion: String, newAnswer: String) {
        viewModelScope.launch {
            try {
                val updatedCard = flashcard.copy(
                question = newQuestion,
                answer = newAnswer,
                updatedAt = Clock.System.now()
            )
                updateFlashcardUseCase(updatedCard)
            } catch (e: Exception) {
                println("Error al actualizar flashcard: ${e.message}")
            }
        }
    }


    fun deleteFlashcard(flashcard: Flashcard) {
        viewModelScope.launch {
            try {
                deleteFlashcardUseCase(flashcard)
            } catch (e: Exception) {
                println("Error al eliminar flashcard: ${e.message}")
            }
        }
    }
}


