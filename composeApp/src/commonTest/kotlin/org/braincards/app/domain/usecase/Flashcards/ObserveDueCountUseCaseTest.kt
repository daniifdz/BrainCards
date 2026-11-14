package org.braincards.app.domain.usecase.Flashcards

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.braincards.app.presentation.screens.home.FakeDueCardsObserver
import org.braincards.app.presentation.screens.home.SchedulerClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** El tick que reevalua "vencida" por el paso del tiempo, con tiempo virtual y reloj inyectado. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDueCountUseCaseTest {

    @Test
    fun el_ticker_emite_la_hora_al_suscribirse_y_despues_cada_periodo() = runTest {
        val clock = SchedulerClock(testScheduler)
        val start = clock.now()
        val useCase = ObserveDueCountUseCase(FakeDueCardsObserver(), clock, tick = 60.seconds)

        val nows = mutableListOf<kotlinx.datetime.Instant>()
        backgroundScope.launch { useCase.ticker().collect { nows += it } }
        runCurrent()
        assertEquals(listOf(start), nows)

        advanceTimeBy(59.seconds)
        runCurrent()
        assertEquals(1, nows.size, "aun no ha pasado un periodo")

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(listOf(start, start + 60.seconds), nows)

        advanceTimeBy(120.seconds)
        runCurrent()
        assertEquals(listOf(start, start + 60.seconds, start + 120.seconds, start + 180.seconds), nows)
    }

    @Test
    fun el_recuento_sube_cuando_una_tarjeta_vence_por_el_paso_del_tiempo() = runTest {
        val clock = SchedulerClock(testScheduler)
        val observer = FakeDueCardsObserver(dueAt = listOf(clock.now() + 90.seconds, clock.now() + 200.seconds))
        val useCase = ObserveDueCountUseCase(observer, clock, tick = 60.seconds)

        val counts = mutableListOf<Int>()
        backgroundScope.launch { useCase().collect { counts += it } }
        runCurrent()
        assertEquals(listOf(0), counts)

        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(listOf(0), counts, "a los 60 s ninguna ha vencido (sin repetidos)")

        advanceTimeBy(60.seconds)
        runCurrent()
        assertEquals(listOf(0, 1), counts)

        advanceTimeBy(120.seconds)
        runCurrent()
        assertEquals(listOf(0, 1, 2), counts)
    }

    @Test
    fun el_periodo_por_defecto_es_un_minuto() {
        assertEquals(60.seconds, DUE_COUNT_TICK)
    }

    @Test
    fun usa_la_hora_del_reloj_inyectado_y_no_la_del_sistema() = runTest {
        val clock = SchedulerClock(testScheduler)
        val observer = FakeDueCardsObserver(dueAt = listOf(clock.now()))
        val useCase = ObserveDueCountUseCase(observer, clock)

        val first = useCase.ticker().take(1).toList()

        assertEquals(listOf(clock.now()), first)
    }
}
