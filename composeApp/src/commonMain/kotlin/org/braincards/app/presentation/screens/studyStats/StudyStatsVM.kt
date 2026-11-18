package org.braincards.app.presentation.screens.studyStats

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
import org.braincards.app.domain.usecase.Decks.ObserveDeckSummariesUseCase
import org.braincards.app.domain.usecase.Flashcards.ObserveDueCountUseCase
import org.braincards.app.domain.usecase.Stats.ObserveStudyStatsUseCase
import org.braincards.app.presentation.screens.home.homeDeckStats
import org.braincards.app.util.logError

/**
 * Estadisticas (handoff 5.7): numero de mazos vivos, tarjetas pendientes (el MISMO contador que Home) y madurez de las
 * tarjetas activas. Patron de Home: funcion pura ([selectStatsState]) + este ViewModel.
 *
 * Prioridad: Loading (hasta que llegan los tres datos) -> Error (falla cualquiera) -> Empty (0 mazos) -> Content.
 */
class StudyStatsVM(
    observeStudyStats: ObserveStudyStatsUseCase,
    observeDeckSummaries: ObserveDeckSummariesUseCase,
    observeDueCount: ObserveDueCountUseCase
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<StatsUiState> = attempt
        .flatMapLatest {
            loadState(
                deckCount = observeDeckSummaries().map { homeDeckStats(it).deckCount }.distinctUntilChanged(),
                stats = observeStudyStats(),
                due = observeDueCount()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState.Loading)

    /** «Reintentar» tras un error: reabre las suscripciones (vuelve a pasar por el esqueleto). */
    fun retry() {
        attempt.update { it + 1 }
    }

    private fun loadState(
        deckCount: Flow<Int>,
        stats: Flow<org.braincards.app.domain.models.StudyStats>,
        due: Flow<Int>
    ): Flow<StatsUiState> =
        combine(deckCount, stats, due) { decks, madurez, pendientes -> selectStatsState(decks, madurez, pendientes) }
            .onStart<StatsUiState> { emit(StatsUiState.Loading) }
            // catch no atrapa la cancelacion. Solo la clase del error en el log (nunca message).
            .catch { e ->
                logError("No se pudieron leer las estadisticas", e)
                emit(StatsUiState.Error)
            }
}
