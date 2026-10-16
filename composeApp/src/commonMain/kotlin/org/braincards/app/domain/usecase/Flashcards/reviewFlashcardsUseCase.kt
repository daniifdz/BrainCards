package org.braincards.app.domain.usecase.Flashcards

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewLog
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.ReviewLogRepository
import org.braincards.app.domain.util.IdGenerator
import kotlin.math.roundToInt

class ReviewFlashcardUseCase(
    private val reviewLogRepository: ReviewLogRepository,
    private val cardSchedulingRepository: CardSchedulingRepository
) {

    suspend operator fun invoke(flashcard: Flashcard, quality: ReviewQuality) {
        val q = quality.value

        // Punto de partida: el scheduling ya guardado, o los valores por defecto de una
        // tarjeta que todavia no se ha repasado nunca (los mismos que se usaban al crearla).
        val existingScheduling = cardSchedulingRepository.getByCardId(flashcard.id)
        val currentEaseFactor = existingScheduling?.easeFactor ?: 2.5
        val currentInterval = existingScheduling?.interval ?: 0
        val currentRepetitions = existingScheduling?.repetitions ?: 0

        // 1. Calcular nuevo Ease Factor (Factor de facilidad)
        // Fórmula SM-2: EF' = EF + (0.1 - (5-q)*(0.08 + (5-q)*0.02))
        // El EF no suele bajar de 1.3 para evitar el "infierno de repeticiones".
        var newEaseFactor = currentEaseFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        if (newEaseFactor < 1.3) newEaseFactor = 1.3

        // 2. Calcular Intervalo (Días hasta el próximo repaso) y Repeticiones
        var newRepetitions = currentRepetitions
        var newInterval = currentInterval

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

        // 4. Guardar el scheduling actualizado. upsert = crea la fila si la tarjeta nunca
        // se habia repasado, la reemplaza entera si ya existia.
        cardSchedulingRepository.upsert(
            CardScheduling(
                cardId = flashcard.id,
                easeFactor = newEaseFactor,
                interval = newInterval,
                repetitions = newRepetitions,
                nextReview = nextReviewDate,
                updatedAt = now,
                // El scheduling siempre hereda el dueno de su flashcard (la RLS de Supabase
                // exige que coincida), nunca el del scheduling previo. lastSyncedAt = null:
                // cada repaso deja la fila pendiente de subir (el upsert es REPLACE).
                userId = flashcard.userId,
                lastSyncedAt = null
            )
        )

        // Flashcard no se toca aqui: su contenido (question/answer) no cambio, y su
        // updatedAt se reserva para ediciones de contenido reales (LWW de sync en v1.1) --
        // tocarlo en cada repaso lo confundiria con una edicion.
        reviewLogRepository.addReviewLog(
            ReviewLog(
                id = IdGenerator.newId(),
                cardId = flashcard.id,
                timestamp = now,
                grade = q,
                previousInterval = currentInterval,
                newInterval = newInterval,
                userId = flashcard.userId
            )
        )
    }
}
