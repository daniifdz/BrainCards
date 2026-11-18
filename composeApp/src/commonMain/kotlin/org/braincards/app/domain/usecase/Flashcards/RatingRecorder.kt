package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality

/**
 * UNICO punto por el que una puntuacion de la sesion de estudio puede PERSISTIR algo. El StudyViewModel elige el recorder segun el modo:
 * la sesion normal usa [ScheduledRecorder] y el repaso libre usa [FreeRecorder], que no hace nada. Asi la invariante del repaso libre
 * («nunca escribe en card_scheduling ni en reviewlog») es ESTRUCTURAL: `ReviewFlashcardUseCase` solo se referencia desde
 * `ScheduledRecorder` (lo vigila `ReviewFlashcardUseCaseGuardTest`).
 */
interface RatingRecorder {
    suspend fun record(card: Flashcard, quality: ReviewQuality)
}

/** Repaso libre: la pulsacion se cuenta en memoria (Resultados) pero NO se guarda nada. */
object FreeRecorder : RatingRecorder {
    override suspend fun record(card: Flashcard, quality: ReviewQuality) = Unit
}
