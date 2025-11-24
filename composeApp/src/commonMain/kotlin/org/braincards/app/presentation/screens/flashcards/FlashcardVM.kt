package org.braincards.app.presentation.screens.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.usecase.AddFlashcardUseCase
import org.braincards.app.domain.usecase.GetFlashcardUseCase

class FlashCardViewModel(
    private val addFlashcardUseCase: AddFlashcardUseCase,
    private val getFlashcardUseCase: GetFlashcardUseCase
) : ViewModel() {

    private val _flashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    val flashcards: StateFlow<List<Flashcard>> = _flashcards.asStateFlow()

    fun loadFlashcards(deckId: Int) {
        viewModelScope.launch {
            getFlashcardUseCase(deckId)
                .catch { e ->
                    println("Error loading flashcards: ${e.message}")
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
                println("Error adding flashcard: ${e.message}")
            }
        }
    }
}