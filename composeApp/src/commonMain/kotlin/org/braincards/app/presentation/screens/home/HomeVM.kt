package org.braincards.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.braincards.app.domain.usecase.Decks.GetDeckUseCase
import org.braincards.app.domain.usecase.Flashcards.ObserveDueCountUseCase
import org.braincards.app.util.logError

sealed interface HomeUiState {
    /** Esqueleto: aun no hay primer recuento. */
    data object Loading : HomeUiState

    /** Fallo al leer el recuento (la UI ofrece Reintentar). */
    data object Error : HomeUiState

    /** Primer uso: ningun mazo (no borrado). Sin "Estudiar Todo" ni "Todo al dia"; invita a crear el primero. */
    data object NoDecks : HomeUiState

    /**
     * Hay mazos y 0 tarjetas pendientes: "Todo al dia". DEUDA de diseno: tambien se ve con mazos pero 0 tarjetas en
     * total (no es del todo cierto); ese caso esta pendiente de disenar.
     */
    data object Empty : HomeUiState

    data class Content(val dueCount: Int) : HomeUiState
}

/**
 * Prioridad de estados: Loading (hasta que llegan los dos datos) -> Error (falla cualquiera) -> NoDecks (0 mazos no
 * borrados) -> Empty (0 pendientes) -> Content.
 */
class HomeViewModel(
    observeDueCount: ObserveDueCountUseCase,
    getDecks: GetDeckUseCase
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = attempt
        .flatMapLatest { loadState(getDecks().map { it.isEmpty() }.distinctUntilChanged(), observeDueCount()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** "Reintentar" tras un error: reabre la suscripcion al contador (vuelve a pasar por el esqueleto). */
    fun retry() {
        attempt.update { it + 1 }
    }

    private fun loadState(noDecks: Flow<Boolean>, counts: Flow<Int>): Flow<HomeUiState> =
        combine(noDecks, counts) { noDecksYet, due ->
            when {
                noDecksYet -> HomeUiState.NoDecks
                due == 0 -> HomeUiState.Empty
                else -> HomeUiState.Content(due)
            }
        }
            .onStart<HomeUiState> { emit(HomeUiState.Loading) }
            // catch no atrapa la cancelacion. Solo clase del error en el log (nunca message).
            .catch { e ->
                logError("No se pudo leer el contador de tarjetas pendientes ni los mazos", e)
                emit(HomeUiState.Error)
            }
}
