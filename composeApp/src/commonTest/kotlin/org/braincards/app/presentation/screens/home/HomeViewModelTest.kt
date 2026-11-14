package org.braincards.app.presentation.screens.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.domain.repository.DueCardsObserver
import org.braincards.app.domain.usecase.Flashcards.DUE_COUNT_TICK
import org.braincards.app.domain.usecase.Flashcards.ObserveDueCountUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Reloj que lee el tiempo VIRTUAL de runTest: sin esperas reales. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SchedulerClock(private val scheduler: TestCoroutineScheduler) : Clock {
    private val base = Instant.parse("2026-11-01T08:00:00Z")
    override fun now(): Instant = base + scheduler.currentTime.milliseconds
}

/** Observador falso: las tarjetas vencen en las horas dadas; el recuento se recalcula con cada hora de `now`. */
internal class FakeDueCardsObserver(var dueAt: List<Instant> = emptyList()) : DueCardsObserver {
    val nows = mutableListOf<Instant>()
    override fun observeDueCount(now: Flow<Instant>): Flow<Int> =
        now.map { n ->
            nows += n
            dueAt.count { it <= n }
        }.distinctUntilChanged()
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.vmWith(observer: DueCardsObserver): HomeViewModel =
        HomeViewModel(ObserveDueCountUseCase(observer, SchedulerClock(testScheduler), DUE_COUNT_TICK))

    private fun TestScope.collect(vm: HomeViewModel): List<HomeUiState> {
        val seen = mutableListOf<HomeUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
        return seen
    }

    /** Observador controlado a mano: el test emite los recuentos (o un error) cuando quiere. */
    private class ManualObserver : DueCardsObserver {
        var subscriptions = 0
        val counts = MutableSharedFlow<Result<Int>>(extraBufferCapacity = 8)
        override fun observeDueCount(now: Flow<Instant>): Flow<Int> = flow {
            subscriptions++
            counts.collect { emit(it.getOrThrow()) }
        }
    }

    @Test
    fun antes_del_primer_recuento_es_Loading() = runTest {
        val vm = vmWith(ManualObserver())
        val seen = collect(vm)
        runCurrent()
        assertEquals(listOf<HomeUiState>(HomeUiState.Loading), seen)
    }

    @Test
    fun con_tarjetas_pendientes_es_Content() = runTest {
        val observer = ManualObserver()
        val vm = vmWith(observer)
        val seen = collect(vm)

        observer.counts.emit(Result.success(7))
        runCurrent()

        assertEquals(HomeUiState.Content(7), seen.last())
    }

    @Test
    fun con_cero_pendientes_es_Empty_y_vuelve_a_Content_al_haber_mas() = runTest {
        val observer = ManualObserver()
        val vm = vmWith(observer)
        val seen = collect(vm)

        observer.counts.emit(Result.success(3))
        observer.counts.emit(Result.success(0))
        runCurrent()
        assertEquals(HomeUiState.Empty, seen.last())

        observer.counts.emit(Result.success(2))
        runCurrent()
        assertEquals(HomeUiState.Content(2), seen.last())
    }

    @Test
    fun un_fallo_del_contador_es_Error() = runTest {
        val observer = ManualObserver()
        val vm = vmWith(observer)
        val seen = collect(vm)

        observer.counts.emit(Result.failure(IllegalStateException("boom")))
        runCurrent()

        assertEquals(HomeUiState.Error, seen.last())
    }

    @Test
    fun reintentar_tras_un_error_vuelve_a_suscribir_y_pasa_por_Loading_hasta_los_datos() = runTest {
        val observer = ManualObserver()
        val vm = vmWith(observer)
        val seen = collect(vm)
        observer.counts.emit(Result.failure(IllegalStateException("boom")))
        runCurrent()
        assertEquals(HomeUiState.Error, seen.last())
        val before = observer.subscriptions

        vm.retry()
        runCurrent()
        assertEquals(HomeUiState.Loading, seen.last())
        assertEquals(before + 1, observer.subscriptions)

        observer.counts.emit(Result.success(4))
        runCurrent()
        assertEquals(HomeUiState.Content(4), seen.last())
    }

    @Test
    fun una_tarjeta_que_vence_con_el_paso_del_tiempo_actualiza_Home_sin_tocar_ninguna_tabla() = runTest {
        val clock = SchedulerClock(testScheduler)
        val observer = FakeDueCardsObserver(dueAt = listOf(clock.now() + 90.seconds))
        val vm = vmWith(observer)
        val seen = collect(vm)
        runCurrent()
        assertEquals(HomeUiState.Empty, seen.last())

        advanceTimeBy(DUE_COUNT_TICK * 2 + 1.seconds)
        runCurrent()

        assertEquals(HomeUiState.Content(1), seen.last())
    }
}
