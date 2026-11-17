package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.ReviewQuality
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Estado SM-2 de una tarjeta: factor de facilidad, intervalo en DIAS y numero de aciertos seguidos. Una tarjeta que
 * nunca se ha repasado parte de [NEW]. Precondicion: con `repetitions >= 1`, `interval >= 1` (siempre cierto con filas
 * creadas por este algoritmo: un fallo deja `repetitions = 0`).
 */
data class Sm2Schedule(val easeFactor: Double, val interval: Int, val repetitions: Int) {
    companion object {
        val NEW = Sm2Schedule(Sm2.DEFAULT_EASE_FACTOR, interval = 0, repetitions = 0)
    }
}

/** Constantes del algoritmo (variante estilo Anki de SM-2). */
object Sm2 {
    const val DEFAULT_EASE_FACTOR = 2.5
    const val MIN_EASE_FACTOR = 1.3

    // Variacion del factor de facilidad por calidad. "Bien" no lo cambia.
    const val EASE_DELTA_AGAIN = -0.20
    const val EASE_DELTA_HARD = -0.15
    const val EASE_DELTA_GOOD = 0.0
    const val EASE_DELTA_EASY = 0.15

    // Primeros pasos (dias) de las tarjetas que aciertan: 1 y 6 con "Bien". "Facil" salta el primero a 4 dias.
    const val FIRST_INTERVAL = 1
    const val SECOND_INTERVAL = 6
    const val EASY_FIRST_INTERVAL = 4

    // Multiplicadores sobre el intervalo previo / el de "Bien".
    const val HARD_MULTIPLIER = 1.2
    const val EASY_BONUS = 1.3
}

private fun roundEase(value: Double): Double = (value * 100).roundToInt() / 100.0

private fun easeDelta(quality: ReviewQuality): Double = when (quality) {
    ReviewQuality.AGAIN -> Sm2.EASE_DELTA_AGAIN
    ReviewQuality.HARD -> Sm2.EASE_DELTA_HARD
    ReviewQuality.GOOD -> Sm2.EASE_DELTA_GOOD
    ReviewQuality.EASY -> Sm2.EASE_DELTA_EASY
}

/**
 * Siguiente estado SM-2 tras puntuar una tarjeta con [quality]. Funcion pura (la fecha la pone quien llama).
 *
 *  - Factor de facilidad: `EF + delta(calidad)` (Otra vez -0.20, Dificil -0.15, Bien 0, Facil +0.15), con suelo en
 *    [Sm2.MIN_EASE_FACTOR] y redondeado a 2 decimales (sin arrastre de coma flotante).
 *  - Otra vez: `repetitions = 0` e `interval = 0` (se repasa ya).
 *  - Acierto (`repetitions' = repetitions + 1`):
 *      * Bien: 1 dia, 6 dias y despues `round(intervalo * EF)` (al menos `intervalo + 1`).
 *      * Dificil: nunca crece como Bien: `max(intervalo + 1, round(intervalo * 1.2))`, siempre por debajo de Bien.
 *      * Facil: 4 dias el primer acierto y despues `round(Bien * 1.3)`, siempre por encima de Bien.
 *
 * Invariante: desde el SEGUNDO acierto, Dificil < Bien < Facil estrictamente. En el primero, Facil > Bien pero Dificil
 * == Bien (1 dia): con intervalos en dias enteros no hay nada entre 0 ("ahora", un fallo) y 1.
 */
fun calculateNextSchedule(current: Sm2Schedule, quality: ReviewQuality): Sm2Schedule {
    val newEase = roundEase((current.easeFactor + easeDelta(quality)).coerceAtLeast(Sm2.MIN_EASE_FACTOR))

    if (quality == ReviewQuality.AGAIN) {
        return Sm2Schedule(easeFactor = newEase, interval = 0, repetitions = 0)
    }

    val repetitions = current.repetitions + 1
    val previous = current.interval
    val interval = if (repetitions == 1) {
        if (quality == ReviewQuality.EASY) Sm2.EASY_FIRST_INTERVAL else Sm2.FIRST_INTERVAL
    } else {
        // Lo que daria "Bien" desde este estado: Dificil y Facil se miden contra el.
        val goodEase = roundEase(current.easeFactor.coerceAtLeast(Sm2.MIN_EASE_FACTOR))
        val good = if (repetitions == 2) {
            Sm2.SECOND_INTERVAL
        } else {
            max((previous * goodEase).roundToInt(), previous + 1)
        }
        when (quality) {
            ReviewQuality.HARD -> min(max(previous + 1, (previous * Sm2.HARD_MULTIPLIER).roundToInt()), good - 1)
            ReviewQuality.EASY -> max(good + 1, (good * Sm2.EASY_BONUS).roundToInt())
            else -> good
        }
    }
    return Sm2Schedule(easeFactor = newEase, interval = interval, repetitions = repetitions)
}
