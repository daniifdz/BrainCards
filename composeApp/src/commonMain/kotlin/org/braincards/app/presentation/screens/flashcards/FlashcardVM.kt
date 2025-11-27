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
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.usecase.Flashcards.AddFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.DeleteFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.UpdateFlashcardUseCase
import org.braincards.app.domain.usecase.Topics.AddTopicUseCase
import org.braincards.app.domain.usecase.Topics.DeleteTopicUseCase
import org.braincards.app.domain.usecase.Topics.GetTopicsUseCase
import org.braincards.app.domain.usecase.Topics.UpdateTopicUseCase

class FlashCardViewModel(
    // Casos de uso de Flashcards
    private val addFlashcardUseCase: AddFlashcardUseCase,
    private val getFlashcardUseCase: GetFlashcardUseCase,
    private val updateFlashcardUseCase: UpdateFlashcardUseCase,
    private val deleteFlashcardUseCase: DeleteFlashcardUseCase,
    // Casos de uso de Temas
    private val addTopicUseCase: AddTopicUseCase,
    private val getTopicsUseCase: GetTopicsUseCase,
    private val updateTopicUseCase: UpdateTopicUseCase,
    private val deleteTopicUseCase: DeleteTopicUseCase
) : ViewModel() {

    // Estado para las Flashcards
    private val _flashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    val flashcards: StateFlow<List<Flashcard>> = _flashcards.asStateFlow()
    private var loadFlashcardsJob: Job? = null

    // Estado para los Temas
    private val _topics = MutableStateFlow<List<Topic>>(emptyList())
    val topics: StateFlow<List<Topic>> = _topics.asStateFlow()
    private var loadTopicsJob: Job? = null

    // Estado de selección de Tema
    private val _selectedTopicId = MutableStateFlow<Int?>(null)
    val selectedTopicId: StateFlow<Int?> = _selectedTopicId.asStateFlow()

    fun loadFlashcards(deckId: Int) {
        loadFlashcardsJob?.cancel()
        loadFlashcardsJob = viewModelScope.launch {
            getFlashcardUseCase(deckId)
                .catch { e -> println("Error loading flashcards: ${e.message}") }
                .collectLatest { list -> _flashcards.value = list }
        }
    }

    fun loadTopics(deckId: Int) {
        loadTopicsJob?.cancel()
        loadTopicsJob = viewModelScope.launch {
            getTopicsUseCase(deckId)
                .catch { e -> println("Error loading topics: ${e.message}") }
                .collectLatest { list -> _topics.value = list }
        }
    }

    fun selectTopic(topicId: Int?) {
        _selectedTopicId.value = topicId
    }

    //flashcards
    fun addFlashcard(deckId: Int, question: String, answer: String, topicId: Int? = null) {
        viewModelScope.launch {
            val now = Clock.System.now()
            addFlashcardUseCase(Flashcard(
                id = 0, deckId = deckId, topicId = topicId, question = question, answer = answer,
                createdAt = now, updatedAt = now, nextReview = now,
                easeFactor = 2.5, interval = 0, repetitions = 0
            ))
        }
    }

    fun updateFlashcard(flashcard: Flashcard, newQuestion: String, newAnswer: String) {
        viewModelScope.launch {
            updateFlashcardUseCase(flashcard.copy(question = newQuestion, answer = newAnswer, updatedAt = Clock.System.now()))
        }
    }

    fun deleteFlashcard(flashcard: Flashcard) {
        viewModelScope.launch { deleteFlashcardUseCase(flashcard) }
    }

    //topics
    fun createTopic(deckId: Int, name: String) {
        viewModelScope.launch {
            val now = Clock.System.now()
            addTopicUseCase(Topic(id = 0, deckId = deckId, name = name, createdAt = now, updatedAt = now))
        }
    }

    fun updateTopic(topic: Topic, newName: String) {
        viewModelScope.launch {
            updateTopicUseCase(topic.copy(name = newName, updatedAt = Clock.System.now()))
        }
    }

    fun deleteTopic(topic: Topic) {
        viewModelScope.launch { deleteTopicUseCase(topic) }
    }
}