package org.braincards.app.presentation.screens.decks

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
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.DeckSummary
import org.braincards.app.domain.usecase.DeleteDeckUseCase
import org.braincards.app.domain.usecase.Decks.AddDeckUseCase
import org.braincards.app.domain.usecase.Decks.DeckNameError
import org.braincards.app.domain.usecase.Decks.DeckNameException
import org.braincards.app.domain.usecase.Decks.ObserveDeckSummariesUseCase
import org.braincards.app.domain.usecase.Decks.UpdateDeckUseCase
import org.braincards.app.domain.usecase.Decks.deckMatchesQuery
import org.braincards.app.util.logError

/** Lo que muestra la Lista de mazos. */
sealed interface DecksUiState {
    /** Esqueleto: aun no ha llegado la primera lista. */
    data object Loading : DecksUiState

    /** Fallo al leer los mazos (la UI ofrece Reintentar). */
    data object Error : DecksUiState

    /** 0 mazos (no borrados): invita a crear el primero. Sin buscador. */
    data object Empty : DecksUiState

    /** Hay mazos pero ninguno coincide con la busqueda [query] (ya sin espacios sobrantes). */
    data class NoResults(val query: String) : DecksUiState

    data class Content(val rows: List<DeckSummary>) : DecksUiState
}

/** Mensajes de fallo general (Snackbar); el texto vive en recursos. Los errores de campo van por [DecksViewModel.nameError]. */
enum class DeckMessage { SaveFailed, DeleteFailed }

/**
 * Prioridad de estados: Loading -> Error -> Empty (0 mazos) -> NoResults (busqueda sin coincidencias) -> Content.
 *
 * Crear / editar / eliminar son mutaciones con callback de exito: el dialogo SOLO se cierra si la escritura salio
 * bien. Un nombre repetido (o en blanco) se publica en [nameError] y el dialogo sigue abierto; cualquier otro fallo
 * (disco, base de datos) se registra sin datos sensibles y llega como [DeckMessage] sin tumbar la app.
 */
class DecksViewModel(
    private val addDeckUseCase: AddDeckUseCase,
    observeDeckSummaries: ObserveDeckSummariesUseCase,
    private val updateDeckUseCase: UpdateDeckUseCase,
    private val deleteDeckUseCase: DeleteDeckUseCase,
    private val clock: Clock = Clock.System
) : ViewModel() {

    private sealed interface Source {
        data object Loading : Source
        data object Failed : Source
        data class Data(val rows: List<DeckSummary>) : Source
    }

    private val attempt = MutableStateFlow(0)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _nameError = MutableStateFlow<DeckNameError?>(null)
    /** Error de campo del dialogo abierto (nombre repetido o en blanco); null = sin error. */
    val nameError: StateFlow<DeckNameError?> = _nameError.asStateFlow()

    private val _saving = MutableStateFlow(false)
    /** Hay una escritura en curso: la UI deshabilita los botones y se ignoran los toques repetidos. */
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val messages = Channel<DeckMessage>(Channel.BUFFERED)
    val messageEvent: Flow<DeckMessage> = messages.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DecksUiState> = combine(
        attempt.flatMapLatest { source(observeDeckSummaries()) },
        _searchQuery
    ) { source, query ->
        when (source) {
            Source.Loading -> DecksUiState.Loading
            Source.Failed -> DecksUiState.Error
            is Source.Data -> {
                val visible = source.rows.filter { deckMatchesQuery(it.deck.name, query) }
                when {
                    source.rows.isEmpty() -> DecksUiState.Empty
                    visible.isEmpty() -> DecksUiState.NoResults(query.trim())
                    else -> DecksUiState.Content(visible)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecksUiState.Loading)

    private fun source(summaries: Flow<List<DeckSummary>>): Flow<Source> =
        summaries
            .map<List<DeckSummary>, Source> { Source.Data(it) }
            .onStart { emit(Source.Loading) }
            // catch no atrapa la cancelacion. Solo la clase del error en el log (nunca message).
            .catch { e ->
                logError("No se pudo leer la lista de mazos", e)
                emit(Source.Failed)
            }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /** "Reintentar" tras un error: reabre la suscripcion (vuelve a pasar por el esqueleto). */
    fun retry() {
        attempt.update { it + 1 }
    }

    /** El usuario edita el campo o abre/cierra un dialogo: el error de campo anterior ya no aplica. */
    fun clearNameError() {
        _nameError.value = null
    }

    fun createDeck(name: String, onSuccess: () -> Unit) =
        write(DeckMessage.SaveFailed, "No se pudo crear el mazo", onSuccess) { addDeckUseCase(name) }

    /** Conserva todo lo demas del mazo (descripcion incluida): solo cambia el nombre y updatedAt. */
    fun updateDeck(deck: Deck, newName: String, onSuccess: () -> Unit) =
        write(DeckMessage.SaveFailed, "No se pudo guardar el mazo", onSuccess) {
            updateDeckUseCase(deck.copy(name = newName, updatedAt = clock.now()))
        }

    fun deleteDeck(deck: Deck, onSuccess: () -> Unit) =
        write(DeckMessage.DeleteFailed, "No se pudo eliminar el mazo", onSuccess) { deleteDeckUseCase(deck) }

    private fun write(failure: DeckMessage, logContext: String, onSuccess: () -> Unit, action: suspend () -> Unit) {
        if (!_saving.compareAndSet(expect = false, update = true)) return // toque repetido: ya hay una escritura
        _nameError.value = null
        viewModelScope.launch {
            try {
                action()
                onSuccess()
            } catch (e: DeckNameException) {
                _nameError.value = e.reason
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
