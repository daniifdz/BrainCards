package org.braincards.app.presentation.screens.studyStats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.DeckSummary
import org.braincards.app.domain.models.StudyStats
import org.braincards.app.domain.repository.DeckSummariesObserver
import org.braincards.app.domain.repository.DueCardsObserver
import org.braincards.app.domain.repository.StudyStatsObserver
import org.braincards.app.domain.usecase.Decks.ObserveDeckSummariesUseCase
import org.braincards.app.domain.usecase.Flashcards.DUE_COUNT_TICK
import org.braincards.app.domain.usecase.Flashcards.ObserveDueCountUseCase
import org.braincards.app.domain.usecase.Stats.ObserveStudyStatsUseCase
import org.braincards.app.presentation.screens.home.SchedulerClock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class StudyStatsVMTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun deck(id: String, deleted: Boolean = false) = Deck(
        id = id, name = id, createdAt = Instant.fromEpochMilliseconds(0), updatedAt = Instant.fromEpochMilliseconds(0),
        description = null, deletedAt = if (deleted) Instant.fromEpochMilliseconds(1) else null
    )

    private class Fakes(
        val summaries: MutableStateFlow<List<DeckSummary>> = MutableStateFlow(listOf(DeckSummary(Deck("d", "d", Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), null), 1))),
        val stats: MutableStateFlow<StudyStats> = MutableStateFlow(StudyStats(1, 2, 3)),
        val due: MutableStateFlow<Int> = MutableStateFlow(4)
    ) {
        /** Si no es null, el flujo de madurez lanza esta excepcion al suscribirse (una vez por suscripcion mientras siga puesto). */
        var statsFailure: Throwable? = null
        var statsSubscriptions = 0
    }

    private fun TestScope.vm(f: Fakes): StudyStatsVM {
        val statsObserver = object : StudyStatsObserver {
            override fun observeStudyStats(): Flow<StudyStats> = flow {
                f.statsSubscriptions++
                f.statsFailure?.let { throw it }
                f.stats.collect { emit(it) }
            }
        }
        val summariesObserver = object : DeckSummariesObserver {
            override fun observeDeckSummaries(): Flow<List<DeckSummary>> = f.summaries
        }
        val dueObserver = object : DueCardsObserver {
            override fun observeDueCount(now: Flow<Instant>): Flow<Int> = f.due
        }
        return StudyStatsVM(
            ObserveStudyStatsUseCase(statsObserver),
            ObserveDeckSummariesUseCase(summariesObserver),
            ObserveDueCountUseCase(dueObserver, SchedulerClock(testScheduler), DUE_COUNT_TICK)
        )
    }

    private fun TestScope.collect(vm: StudyStatsVM): List<StatsUiState> {
        val seen = mutableListOf<StatsUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
        return seen
    }

    @Test
    fun con_mazos_y_tarjetas_llega_Content_con_los_tres_datos() = runTest {
        val f = Fakes()
        val seen = collect(vm(f))
        val content = assertIs<StatsUiState.Content>(seen.last())
        assertEquals(1, content.deckCount)
        assertEquals(StudyStats(1, 2, 3), content.stats)
        assertEquals(4, content.dueCount)
    }

    @Test
    fun el_estado_inicial_es_Loading() = runTest {
        val f = Fakes()
        val seen = collect(vm(f))
        assertEquals(StatsUiState.Loading, seen.first())
    }

    @Test
    fun sin_mazos_es_Empty_y_al_crear_el_primero_pasa_a_Content() = runTest {
        val f = Fakes(summaries = MutableStateFlow(emptyList()), stats = MutableStateFlow(StudyStats(0, 0, 0)), due = MutableStateFlow(0))
        val seen = collect(vm(f))
        assertEquals(StatsUiState.Empty, seen.last())
        f.summaries.value = listOf(DeckSummary(deck("a"), 0))
        val content = assertIs<StatsUiState.Content>(seen.last())
        assertEquals(0, content.totalCards)
    }

    @Test
    fun un_mazo_borrado_por_sync_con_la_pantalla_abierta_lleva_a_Empty() = runTest {
        val f = Fakes()
        val seen = collect(vm(f))
        assertIs<StatsUiState.Content>(seen.last())
        f.summaries.value = listOf(DeckSummary(deck("d", deleted = true), 5)) // en tombstone: no cuenta
        assertEquals(StatsUiState.Empty, seen.last())
    }

    @Test
    fun estudiar_una_tarjeta_actualiza_la_madurez_en_vivo() = runTest {
        val f = Fakes(stats = MutableStateFlow(StudyStats(1, 0, 0)))
        val seen = collect(vm(f))
        f.stats.value = StudyStats(0, 1, 0)
        assertEquals(StudyStats(0, 1, 0), assertIs<StatsUiState.Content>(seen.last()).stats)
    }

    @Test
    fun los_pendientes_se_actualizan_sin_tocar_el_resto() = runTest {
        val f = Fakes()
        val seen = collect(vm(f))
        f.due.value = 0
        val content = assertIs<StatsUiState.Content>(seen.last())
        assertEquals(0, content.dueCount)
        assertEquals(StudyStats(1, 2, 3), content.stats)
    }

    @Test
    fun un_fallo_de_lectura_da_Error_y_reintentar_vuelve_a_cargar() = runTest {
        val f = Fakes()
        f.statsFailure = IllegalStateException("boom")
        val vm = vm(f)
        val seen = collect(vm)
        assertEquals(StatsUiState.Error, seen.last())

        f.statsFailure = null
        vm.retry()
        assertIs<StatsUiState.Content>(seen.last())
        assertEquals(2, f.statsSubscriptions)
    }
}
