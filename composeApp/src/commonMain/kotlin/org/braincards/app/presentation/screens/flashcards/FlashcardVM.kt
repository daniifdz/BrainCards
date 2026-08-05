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

    private val _flashcards = MutableStateFlow<List<Flashcard>>(emptyList())
    

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // combinamos la lista original con el texto de búsqueda
    val filteredFlashcards: StateFlow<List<Flashcard>> = combine(
        _flashcards,
        _searchQuery
    ) { cards, query ->
        if (query.isBlank()) {
            cards
        } else {
            cards.filter {
                it.question.contains(query, ignoreCase = true) ||
                it.answer.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )


    val flashcards: StateFlow<List<Flashcard>> = filteredFlashcards

    private var loadFlashcardsJob: Job? = null

    // Estado para los Temas
    private val _topics = MutableStateFlow<List<Topic>>(emptyList())
    val topics: StateFlow<List<Topic>> = _topics.asStateFlow()
    private var loadTopicsJob: Job? = null

    private val _selectedTopicId = MutableStateFlow<String?>(null)
    val selectedTopicId: StateFlow<String?> = _selectedTopicId.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun loadFlashcards(deckId: String) {
        loadFlashcardsJob?.cancel()
        loadFlashcardsJob = viewModelScope.launch {
            getFlashcardUseCase(deckId)
                .catch { e -> println("Error loading flashcards: ${e.message}") }
                .collectLatest { list -> _flashcards.value = list }
        }
    }

    fun loadTopics(deckId: String) {
        loadTopicsJob?.cancel()
        loadTopicsJob = viewModelScope.launch {
            getTopicsUseCase(deckId)
                .catch { e -> println("Error loading topics: ${e.message}") }
                .collectLatest { list -> _topics.value = list }
        }
    }

    fun selectTopic(topicId: String?) {
        _selectedTopicId.value = topicId
        _searchQuery.value = ""
    }

    //flashcards
    fun addFlashcard(deckId: String, question: String, answer: String, topicId: String? = null) {
        viewModelScope.launch {
            val now = Clock.System.now()
            addFlashcardUseCase(Flashcard(
                id = IdGenerator.newId(), deckId = deckId, topicId = topicId,
                question = question, answer = answer,
                createdAt = now, updatedAt = now, nextReview = now,
                easeFactor = 2.5, interval = 0, repetitions = 0,
                cardType = CardType.FLIP
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
    fun createTopic(deckId: String, name: String) {
        viewModelScope.launch {
            val now = Clock.System.now()
            addTopicUseCase(Topic(id = IdGenerator.newId(), deckId = deckId, name = name, createdAt = now, updatedAt = now))
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