package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality

/** Sesion normal: cada puntuacion se guarda (SM-2 en `card_scheduling` + `reviewlog`). Es el UNICO que usa [ReviewFlashcardUseCase]. */
class ScheduledRecorder(private val reviewFlashcardUseCase: ReviewFlashcardUseCase) : RatingRecorder {
    override suspend fun record(card: Flashcard, quality: ReviewQuality) = reviewFlashcardUseCase(card, quality)
}
