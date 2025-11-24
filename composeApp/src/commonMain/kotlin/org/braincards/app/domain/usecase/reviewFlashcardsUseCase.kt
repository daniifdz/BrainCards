package org.braincards.app.domain.usecase

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.repository.FlashcardRepository
import kotlin.math.max
import kotlin.math.roundToInt

class ReviewFlashcardUseCase(private val repository: FlashcardRepository) {

    suspend operator fun invoke(flashcard: Flashcard, quality: ReviewQuality) {
        val q = quality.value

        // 1. Calcular nuevo Ease Factor (Factor de facilidad)
        // Fórmula SM-2: EF' = EF + (0.1 - (5-q)*(0.08 + (5-q)*0.02))
        // El EF no suele bajar de 1.3 para evitar el "infierno de repeticiones".
        var newEaseFactor = flashcard.easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        if (newEaseFactor < 1.3) newEaseFactor = 1.3

        // 2. Calcular Intervalo (Días hasta el próximo repaso) y Repeticiones
        var newRepetitions = flashcard.repetitions
        var newInterval = flashcard.interval

        if (q < 3) {
            // Fallo (Again): Reseteamos repeticiones e intervalo
            newRepetitions = 0
            newInterval = 0 // 0 días = Repasar hoy mismo (o en 1 minuto en lógica más compleja)
        } else {
            // Acierto (Hard, Good, Easy)
            newRepetitions += 1

            newInterval = when (newRepetitions) {
                1 -> 1 // Primer acierto -> 1 día
                2 -> 6 // Segundo acierto -> 6 días
                else -> (newInterval * newEaseFactor).roundToInt() // Siguientes -> Expansión exponencial
            }
        }

        // 3. Calcular nueva fecha (Next Review)
        // Sumamos los días (newInterval) a la fecha actual
        val now = Clock.System.now()
        val nextReviewDate = if (newInterval == 0) {
            now // Si es 0, se repasa YA (o podrías sumar 10 minutos)
        } else {
            now.plus(newInterval, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
        }

        // 4. Crear copia actualizada y guardar
        val updatedCard = flashcard.copy(
            easeFactor = newEaseFactor,
            repetitions = newRepetitions,
            interval = newInterval,
            nextReview = nextReviewDate,
            updatedAt = now
        )

        repository.updateFlashcard(updatedCard)
    }
}