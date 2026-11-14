package org.braincards.app.domain.usecase.Flashcards

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.domain.repository.DueCardsObserver
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Cada cuanto se reevalua "vencida" por el simple paso del tiempo. */
val DUE_COUNT_TICK: Duration = 60.seconds

/**
 * Tarjetas pendientes de repaso (todas), reactivo. Mismo criterio que GetDueFlashcardsUseCase(StudyScope.All, now),
 * con [clock] como fuente de "now" (la sesion usa Clock.System.now()). Reloj y periodo inyectables para los tests.
 */
class ObserveDueCountUseCase(
    private val observer: DueCardsObserver,
    private val clock: Clock = Clock.System,
    private val tick: Duration = DUE_COUNT_TICK
) {
    operator fun invoke(): Flow<Int> = observer.observeDueCount(ticker())

    /** Hora actual al suscribirse y despues cada [tick]. */
    internal fun ticker(): Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(tick)
        }
    }
}
