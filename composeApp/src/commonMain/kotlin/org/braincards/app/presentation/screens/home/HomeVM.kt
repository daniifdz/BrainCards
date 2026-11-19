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
import org.braincards.app.domain.models.DeckSummary
import org.braincards.app.domain.usecase.Decks.ObserveDeckSummariesUseCase
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
     * Hay uno o más mazos vivos pero ninguna tarjeta viva en total. No es «Todo al día» (no hay nada que repasar): invita a entrar
     * en un mazo a crear la primera. Sin «Estudiar Todo».
     */
    data object NoCards : HomeUiState

    /** Hay tarjetas pero 0 pendientes: "Todo al dia". */
    data object Empty : HomeUiState

    data class Content(val dueCount: Int) : HomeUiState
}

/** Mazos vivos y tarjetas vivas en total (la suma de `cardCount` de los mazos vivos). */
data class HomeDeckStats(val deckCount: Int, val cardCount: Int)

/**
 * Resumen para Home a partir de la lista de mazos con su recuento (DeckSummariesObserver). Solo cuentan los mazos vivos
 * (`deletedAt == null`); `cardCount` ya cuenta unicamente tarjetas vivas (lo garantiza la consulta agregada). Sin
 * consultas nuevas.
 */
fun homeDeckStats(summaries: List<DeckSummary>): HomeDeckStats {
    val live = summaries.filter { it.deck.deletedAt == null }
    return HomeDeckStats(deckCount = live.size, cardCount = live.sumOf { it.cardCount })
}

/**
 * Seleccion del estado de Home (sin Loading ni Error, que dependen de la carga). Prioridad: [HomeUiState.NoDecks]
 * (0 mazos) -> [HomeUiState.NoCards] (mazos pero 0 tarjetas en total) -> [HomeUiState.Empty] (0 pendientes) ->
 * [HomeUiState.Content]. "Sin tarjetas" mira el TOTAL de tarjetas, no las pendientes: 0 pendientes con tarjetas es
 * "Todo al dia".
 */
/**
 * ¿Se ofrece «Repaso libre» en Home? Solo en «Todo al día» ([HomeUiState.Empty]: hay tarjetas pero 0 pendientes, así que
 * siempre hay algo que repasar). Con pendientes ([HomeUiState.Content]) no («Estudiar Todo» ya es la acción), ni en primer
 * uso (0 mazos), ni con mazos sin tarjetas (nada que repasar), ni cargando o con error.
 */
fun homeShowsFreeReview(state: HomeUiState): Boolean = state is HomeUiState.Empty

fun selectHomeState(stats: HomeDeckStats, dueCount: Int): HomeUiState = when {
    stats.deckCount == 0 -> HomeUiState.NoDecks
    stats.cardCount == 0 -> HomeUiState.NoCards
    dueCount == 0 -> HomeUiState.Empty
    else -> HomeUiState.Content(dueCount)
}

/**
 * Prioridad de estados: Loading (hasta que llegan los dos datos) -> Error (falla cualquiera) -> NoDecks (0 mazos no
 * borrados) -> NoCards (mazos pero 0 tarjetas) -> Empty (0 pendientes) -> Content. Ver [selectHomeState].
 */
class HomeViewModel(
    observeDueCount: ObserveDueCountUseCase,
    observeDeckSummaries: ObserveDeckSummariesUseCase
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = attempt
        .flatMapLatest {
            loadState(observeDeckSummaries().map(::homeDeckStats).distinctUntilChanged(), observeDueCount())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** "Reintentar" tras un error: reabre la suscripcion al contador (vuelve a pasar por el esqueleto). */
    fun retry() {
        attempt.update { it + 1 }
    }

    private fun loadState(stats: Flow<HomeDeckStats>, counts: Flow<Int>): Flow<HomeUiState> =
        combine(stats, counts) { deckStats, due -> selectHomeState(deckStats, due) }
            .onStart<HomeUiState> { emit(HomeUiState.Loading) }
            // catch no atrapa la cancelacion. Solo clase del error en el log (nunca message).
            .catch { e ->
                logError("No se pudo leer el contador de tarjetas pendientes ni los mazos", e)
                emit(HomeUiState.Error)
            }
}
