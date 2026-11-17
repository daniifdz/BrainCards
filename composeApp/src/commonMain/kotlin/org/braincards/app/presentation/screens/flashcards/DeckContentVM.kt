package org.braincards.app.presentation.screens.flashcards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.usecase.Decks.GetDeckUseCase
import org.braincards.app.domain.usecase.Flashcards.AddFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.DeleteFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.UpdateFlashcardUseCase
import org.braincards.app.domain.usecase.Topics.AddTopicUseCase
import org.braincards.app.domain.usecase.Topics.CardHit
import org.braincards.app.domain.usecase.Topics.DeckContent
import org.braincards.app.domain.usecase.Topics.DeleteTopicUseCase
import org.braincards.app.domain.usecase.Topics.GetTopicsUseCase
import org.braincards.app.domain.usecase.Topics.TopicNameError
import org.braincards.app.domain.usecase.Topics.TopicNameException
import org.braincards.app.domain.usecase.Topics.TopicWithCount
import org.braincards.app.domain.usecase.Topics.UpdateTopicUseCase
import org.braincards.app.domain.util.IdGenerator
import org.braincards.app.util.logError

/** Lo que muestra Contenido de un mazo (handoff 5.1c). */
sealed interface DeckContentUiState {
    /** Esqueleto: aun no han llegado el mazo, sus temas y sus tarjetas. */
    data object Loading : DeckContentUiState

    /** Fallo al leer el contenido (la UI ofrece Reintentar). */
    data object Error : DeckContentUiState

    /** El mazo ya no existe (borrado, p. ej. por sincronizacion): la pantalla sale con popBackStackSafely. */
    data object DeckGone : DeckContentUiState

    /** Ni temas ni tarjetas. Sin buscador. */
    data class Empty(val deckName: String) : DeckContentUiState

    /** Hay contenido pero nada coincide con la busqueda [query] (ya sin espacios sobrantes). */
    data class NoResults(val deckName: String, val query: String) : DeckContentUiState

    /** Nivel del mazo: seccion "Temas" (con contador) y "Tarjetas sin tema". [hasCards]: hay alguna tarjeta viva. */
    data class Root(
        val deckName: String,
        val topics: List<TopicWithCount>,
        val looseCards: List<Flashcard>,
        val hasCards: Boolean
    ) : DeckContentUiState

    /** Resultados de la busqueda: temas por nombre y tarjetas (de todo el mazo) con su tema como segunda linea. */
    data class Search(
        val deckName: String,
        val topics: List<TopicWithCount>,
        val cards: List<CardHit>
    ) : DeckContentUiState

    /** Subnivel de un tema. Con [cards] vacio la UI dibuja "Este tema todavia no tiene tarjetas". */
    data class TopicLevel(
        val deckName: String,
        val topic: Topic,
        val cards: List<Flashcard>
    ) : DeckContentUiState
}

/** Mensajes de fallo general (Snackbar); el texto vive en recursos. Los errores de campo van por el VM. */
enum class ContentMessage { SaveCardFailed, SaveTopicFailed, DeleteFailed }

/**
 * Decide el estado a partir de lo leido. Pura (sin VM) para probarla sola. Un [openTopicId] que ya no corresponde a un
 * tema vivo (borrado aqui o por sincronizacion) cae al nivel del mazo.
 */
internal fun buildDeckContentState(
    deck: Deck?,
    topics: List<Topic>,
    cards: List<Flashcard>,
    openTopicId: String?,
    query: String
): DeckContentUiState {
    if (deck == null || deck.deletedAt != null) return DeckContentUiState.DeckGone
    val content = DeckContent.of(topics, cards)

    val open = openTopicId?.let { content.topic(it) }
    if (open != null) return DeckContentUiState.TopicLevel(deck.name, open, content.cardsOf(open.id))

    if (content.isEmpty) return DeckContentUiState.Empty(deck.name)
    if (query.isBlank()) {
        return DeckContentUiState.Root(deck.name, content.topics, content.looseCards, content.hasCards)
    }
    val result = content.search(query)
    return if (result.isEmpty) {
        DeckContentUiState.NoResults(deck.name, query.trim())
    } else {
        DeckContentUiState.Search(deck.name, result.topics, result.cards)
    }
}

/**
 * Contenido de UN mazo ([deckId]): temas + tarjetas, buscador y subnivel de tema. Sustituye a FlashCardViewModel y
 * TopicVM. El tema abierto vive aqui (no en la ruta): handoff 5.1e.
 *
 * Prioridad de estados: Loading -> Error -> DeckGone -> Tema abierto -> Empty -> NoResults / Search -> Root.
 *
 * Crear / editar / eliminar son mutaciones con callback de exito: el dialogo SOLO se cierra si la escritura salio bien.
 * Un nombre de tema repetido o en blanco se publica en [topicNameError] y el dialogo sigue abierto; cualquier otro
 * fallo (disco, base de datos) se registra sin datos sensibles y llega como [ContentMessage] sin tumbar la app.
 */
class DeckContentViewModel(
    private val deckId: String,
    private val getDecks: GetDeckUseCase,
    private val getTopics: GetTopicsUseCase,
    private val getFlashcards: GetFlashcardUseCase,
    private val addTopicUseCase: AddTopicUseCase,
    private val updateTopicUseCase: UpdateTopicUseCase,
    private val deleteTopicUseCase: DeleteTopicUseCase,
    private val addFlashcardUseCase: AddFlashcardUseCase,
    private val updateFlashcardUseCase: UpdateFlashcardUseCase,
    private val deleteFlashcardUseCase: DeleteFlashcardUseCase,
    private val clock: Clock = Clock.System
) : ViewModel() {

    private sealed interface Source {
        data object Loading : Source
        data object Failed : Source
        data class Data(val deck: Deck?, val topics: List<Topic>, val cards: List<Flashcard>) : Source
    }

    private val attempt = MutableStateFlow(0)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _openTopicId = MutableStateFlow<String?>(null)

    private val _topicNameError = MutableStateFlow<TopicNameError?>(null)
    /** Error de campo del dialogo de tema abierto (nombre repetido o en blanco); null = sin error. */
    val topicNameError: StateFlow<TopicNameError?> = _topicNameError.asStateFlow()

    private val _saving = MutableStateFlow(false)
    /** Hay una escritura en curso: la UI deshabilita los botones y se ignoran los toques repetidos. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val messages = Channel<ContentMessage>(Channel.BUFFERED)
    val messageEvent: Flow<ContentMessage> = messages.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DeckContentUiState> = combine(
        attempt.flatMapLatest { source() },
        _searchQuery,
        _openTopicId
    ) { source, query, openTopicId ->
        when (source) {
            Source.Loading -> DeckContentUiState.Loading
            Source.Failed -> DeckContentUiState.Error
            is Source.Data -> buildDeckContentState(source.deck, source.topics, source.cards, openTopicId, query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckContentUiState.Loading)

    private fun source(): Flow<Source> =
        combine(getDecks(), getTopics(deckId), getFlashcards(deckId)) { decks, topics, cards ->
            Source.Data(decks.firstOrNull { it.id == deckId }, topics, cards)
        }
            .map<Source.Data, Source> { it }
            .onStart { emit(Source.Loading) }
            // catch no atrapa la cancelacion. Solo la clase del error en el log (nunca message).
            .catch { e ->
                logError("No se pudo leer el contenido del mazo", e)
                emit(Source.Failed)
            }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /** Entra al subnivel de un tema; la busqueda se limpia (el subnivel no tiene buscador). */
    fun openTopic(topicId: String) {
        _searchQuery.value = ""
        _openTopicId.value = topicId
    }

    /** Vuelve del subnivel al nivel del mazo (flecha de la barra y atras del sistema). */
    fun closeTopic() {
        _openTopicId.value = null
    }

    /** "Reintentar" tras un error: reabre la suscripcion (vuelve a pasar por el esqueleto). */
    fun retry() {
        attempt.update { it + 1 }
    }

    /** El usuario edita el campo o abre/cierra un dialogo: el error de campo anterior ya no aplica. */
    fun clearErrors() {
        _topicNameError.value = null
    }

    // ---- temas ----

    fun createTopic(name: String, onSuccess: () -> Unit) =
        write(ContentMessage.SaveTopicFailed, "No se pudo crear el tema", onSuccess) {
            val now = clock.now()
            addTopicUseCase(Topic(id = IdGenerator.newId(), deckId = deckId, name = name, createdAt = now, updatedAt = now))
        }

    fun updateTopic(topic: Topic, newName: String, onSuccess: () -> Unit) =
        write(ContentMessage.SaveTopicFailed, "No se pudo guardar el tema", onSuccess) {
            updateTopicUseCase(topic.copy(name = newName, updatedAt = clock.now()))
        }

    fun deleteTopic(topic: Topic, onSuccess: () -> Unit) =
        write(ContentMessage.DeleteFailed, "No se pudo eliminar el tema", onSuccess) { deleteTopicUseCase(topic) }

    // ---- tarjetas ----

    /** Crea una tarjeta flip, opcionalmente dentro de [topicId]. */
    fun createCard(question: String, answer: String, topicId: String?, onSuccess: () -> Unit) =
        write(ContentMessage.SaveCardFailed, "No se pudo crear la tarjeta", onSuccess) {
            val now = clock.now()
            addFlashcardUseCase(
                Flashcard(
                    id = IdGenerator.newId(), deckId = deckId, topicId = topicId,
                    question = question, answer = answer,
                    createdAt = now, updatedAt = now, cardType = CardType.FLIP
                )
            )
        }

    /** Conserva todo lo demas de la tarjeta: solo cambian anverso, reverso, tema y updatedAt. */
    fun updateCard(card: Flashcard, question: String, answer: String, topicId: String?, onSuccess: () -> Unit) =
        write(ContentMessage.SaveCardFailed, "No se pudo guardar la tarjeta", onSuccess) {
            updateFlashcardUseCase(
                card.copy(question = question, answer = answer, topicId = topicId, updatedAt = clock.now())
            )
        }

    fun deleteCard(card: Flashcard, onSuccess: () -> Unit) =
        write(ContentMessage.DeleteFailed, "No se pudo eliminar la tarjeta", onSuccess) { deleteFlashcardUseCase(card) }

    private fun write(failure: ContentMessage, logContext: String, onSuccess: () -> Unit, action: suspend () -> Unit) {
        if (!_saving.compareAndSet(expect = false, update = true)) return // toque repetido: ya hay una escritura
        _topicNameError.value = null
        viewModelScope.launch {
            try {
                action()
                onSuccess()
            } catch (e: TopicNameException) {
                _topicNameError.value = e.reason
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fallos de disco / base de datos (IOException, SQLite...): no deben tumbar la app.
                logError(logContext, e)
                messages.trySend(failure)
            } finally {
                _saving.value = false
            }
        }
    }
}
