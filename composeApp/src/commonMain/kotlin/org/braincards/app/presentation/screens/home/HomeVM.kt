package org.braincards.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.braincards.app.domain.usecase.Flashcards.ObserveDueCountUseCase
import org.braincards.app.util.logError

sealed interface HomeUiState {
    /** Esqueleto: aun no hay primer recuento. */
    data object Loading : HomeUiState

    /** Fallo al leer el recuento (la UI ofrece Reintentar). */
    data object Error : HomeUiState

    /** 0 tarjetas pendientes: "Todo al dia". */
    data object Empty : HomeUiState

    data class Content(val dueCount: Int) : HomeUiState
}

class HomeViewModel(observeDueCount: ObserveDueCountUseCase) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = attempt
        .flatMapLatest { loadState(observeDueCount()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** "Reintentar" tras un error: reabre la suscripcion al contador (vuelve a pasar por el esqueleto). */
    fun retry() {
        attempt.update { it + 1 }
    }

    private fun loadState(counts: Flow<Int>): Flow<HomeUiState> = counts
        .map<Int, HomeUiState> { if (it == 0) HomeUiState.Empty else HomeUiState.Content(it) }
        .onStart { emit(HomeUiState.Loading) }
        // catch no atrapa la cancelacion. Solo clase del error en el log (nunca message).
        .catch { e ->
            logError("No se pudo leer el contador de tarjetas pendientes", e)
            emit(HomeUiState.Error)
        }
}
