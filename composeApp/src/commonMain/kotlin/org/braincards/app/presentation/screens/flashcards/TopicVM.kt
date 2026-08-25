package org.braincards.app.presentation.screens.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.usecase.Topics.AddTopicUseCase
import org.braincards.app.domain.usecase.Topics.DeleteTopicUseCase
import org.braincards.app.domain.usecase.Topics.GetTopicsUseCase
import org.braincards.app.domain.usecase.Topics.UpdateTopicUseCase
import org.braincards.app.domain.util.IdGenerator

sealed class TopicUiState {
    data object Loading : TopicUiState()
    data class Success(val topics: List<Topic>) : TopicUiState()
    data class Error(val message: String) : TopicUiState()
}

class TopicVM(
    private val addTopicUseCase: AddTopicUseCase,
    private val getTopicsUseCase: GetTopicsUseCase,
    private val updateTopicUseCase: UpdateTopicUseCase,
    private val deleteTopicUseCase: DeleteTopicUseCase
) : ViewModel() {

    private val _topicState = MutableStateFlow<TopicUiState>(TopicUiState.Loading)
    val topics: StateFlow<TopicUiState> = _topicState.asStateFlow()
    private var loadTopicsJob: Job? = null

    private val _selectedTopicId = MutableStateFlow<String?>(null)
    val selectedTopicId: StateFlow<String?> = _selectedTopicId.asStateFlow()


    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    fun loadTopics(deckId: String) {
        loadTopicsJob?.cancel()
        loadTopicsJob = viewModelScope.launch {
            _topicState.value = TopicUiState.Loading
            getTopicsUseCase(deckId)
                .catch { e ->
                    _topicState.value = TopicUiState.Error("Error loading topics: ${e.message}")
                }
                .collectLatest { list ->
                    _topicState.value = TopicUiState.Success(list)
                }
        }
    }

    fun selectTopic(topicId: String?) {
        _selectedTopicId.value = topicId
    }

    fun createTopic(deckId: String, name: String) {
        viewModelScope.launch {
            try{
                val now = Clock.System.now()
                addTopicUseCase(
                    Topic(
                        id = IdGenerator.newId(),
                        deckId = deckId,
                        name = name,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }catch(e: IllegalArgumentException){
                _errorEvent.emit(e.message ?: "Error al guardar el tema")

            }

        }
    }

    fun updateTopic(topic: Topic, newName: String) {
        viewModelScope.launch {
            try {
                updateTopicUseCase(topic.copy(name = newName, updatedAt = Clock.System.now()))
            } catch (e: IllegalArgumentException) {
                _errorEvent.emit(e.message ?: "Error al actualizar el tema")
            }
        }
    }

    fun deleteTopic(topic: Topic) {
        viewModelScope.launch { deleteTopicUseCase(topic) }
    }
}