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

class ReviewFlashcardUseCase(
    private val reviewLogRepository: ReviewLogRepository,
    private val cardSchedulingRepository: CardSchedulingRepository
) {

    suspend operator fun invoke(flashcard: Flashcard, quality: ReviewQuality) {
        // Punto de partida: el scheduling ya guardado, o los valores por defecto de una
        // tarjeta que todavia no se ha repasado nunca (los mismos que se usaban al crearla).
        val existingScheduling = cardSchedulingRepository.getByCardId(flashcard.id)
        val current = existingScheduling?.let {
            Sm2Schedule(easeFactor = it.easeFactor, interval = it.interval, repetitions = it.repetitions)
        } ?: Sm2Schedule.NEW

        // El calculo (factor de facilidad, intervalo, repeticiones) es una funcion pura: ver calculateNextSchedule.
        val next = calculateNextSchedule(current, quality)

        // Nueva fecha (Next Review): hoy + intervalo en dias. Intervalo 0 = se repasa YA.
        val now = Clock.System.now()
        val nextReviewDate = if (next.interval == 0) {
            now
        } else {
            now.plus(next.interval, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
        }

        // Guardar el scheduling actualizado. upsert = crea la fila si la tarjeta nunca
        // se habia repasado, la reemplaza entera si ya existia.
        cardSchedulingRepository.upsert(
            CardScheduling(
                cardId = flashcard.id,
                easeFactor = next.easeFactor,
                interval = next.interval,
                repetitions = next.repetitions,
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
                grade = quality.value,
                previousInterval = current.interval,
                newInterval = next.interval,
                userId = flashcard.userId
            )
        )
    }
}
