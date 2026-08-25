package org.braincards.app.presentation.screens.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.usecase.Flashcards.AddFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.DeleteFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.UpdateFlashcardUseCase
import org.braincards.app.domain.usecase.Topics.AddTopicUseCase
import org.braincards.app.domain.usecase.Topics.DeleteTopicUseCase
import org.braincards.app.domain.usecase.Topics.GetTopicsUseCase
import org.braincards.app.domain.usecase.Topics.UpdateTopicUseCase
import org.braincards.app.domain.util.IdGenerator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow


sealed class FlashcardsUiState {
    data object Loading : FlashcardsUiState()
    data class Success(val flashcards: List<Flashcard>) : FlashcardsUiState()
    data class Error(val message: String) : FlashcardsUiState()
}

class FlashCardViewModel(
    // Casos de uso de Flashcards
    private val addFlashcardUseCase: AddFlashcardUseCase,
    private val getFlashcardUseCase: GetFlashcardUseCase,
    private val updateFlashcardUseCase: UpdateFlashcardUseCase,
    private val deleteFlashcardUseCase: DeleteFlashcardUseCase
) : ViewModel() {

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private val _flashcardState = MutableStateFlow<FlashcardsUiState>(FlashcardsUiState.Loading)


    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // combinamos la lista original con el texto de búsqueda
    val filteredFlashcards: StateFlow<FlashcardsUiState> = combine(
        _flashcardState,
        _searchQuery
    ) { state, query ->
        when (state) {
            is FlashcardsUiState.Success -> {
                val filtered = if (query.isBlank()) {
                    state.flashcards
                } else {
                    state.flashcards.filter {
                        it.question.contains(query, ignoreCase = true) ||
                                it.answer.contains(query, ignoreCase = true)
                    }
                }
                FlashcardsUiState.Success(filtered)
            }
            else -> state
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FlashcardsUiState.Loading
    )


    val flashcards: StateFlow<FlashcardsUiState> = filteredFlashcards

    private var loadFlashcardsJob: Job? = null

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }


    fun loadFlashcards(deckId: String) {
        loadFlashcardsJob?.cancel()
        loadFlashcardsJob = viewModelScope.launch {
            _flashcardState.value = FlashcardsUiState.Loading
            getFlashcardUseCase(deckId)
                .catch { e ->
                    println("Error al cargar las tarjetas: $e")
                    _flashcardState.value = FlashcardsUiState.Error("Error loading flashcards: ${e.message}") }
                .collectLatest { list ->
                    _flashcardState.value = FlashcardsUiState.Success(list)
                }
        }
    }

    //flashcards
    fun addFlashcard(deckId: String, question: String, answer: String, topicId: String? = null) {
        viewModelScope.launch {
            try{
                val now = Clock.System.now()
                addFlashcardUseCase(Flashcard(
                    id = IdGenerator.newId(), deckId = deckId, topicId = topicId,
                    question = question, answer = answer,
                    createdAt = now, updatedAt = now, nextReview = now,
                    easeFactor = 2.5, interval = 0, repetitions = 0,
                    cardType = CardType.FLIP
                ))
            }catch (e: IllegalArgumentException){
                _errorEvent.emit(e.message ?: "Error al guardar la tarjeta")
            }

        }
    }

    fun updateFlashcard(flashcard: Flashcard, newQuestion: String, newAnswer: String) {
        viewModelScope.launch {
            try {
                updateFlashcardUseCase(
                    flashcard.copy(
                        question = newQuestion,
                        answer = newAnswer,
                        updatedAt = Clock.System.now()
                    )
                )
            } catch (e: IllegalArgumentException) {
                _errorEvent.emit(e.message ?: "Error al actualizar la tarjeta")
            }
        }
    }

    fun deleteFlashcard(flashcard: Flashcard) {
        viewModelScope.launch { deleteFlashcardUseCase(flashcard) }
    }
}