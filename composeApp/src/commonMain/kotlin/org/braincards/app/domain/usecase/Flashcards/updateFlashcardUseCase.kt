package org.braincards.app.domain.usecase.Flashcards

import kotlinx.datetime.Clock
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

class UpdateFlashcardUseCase(
    private val repository: FlashcardRepository,
    private val clock: Clock = Clock.System
) {

    /**
     * Guarda la tarjeta. Lanza [FlashcardFieldException] si el anverso o el reverso estan en blanco.
     *
     * SIEMPRE fija `updatedAt = ahora`: el push del sync solo sube filas con `updatedAt > lastSyncedAt`, asi que una
     * edicion (texto o CAMBIO DE TEMA) que no avanzara `updatedAt` no se sincronizaria nunca. Vive aqui, no en quien
     * llama, para que no pueda olvidarse.
     */
    suspend operator fun invoke(flashcard: Flashcard) {
        checkFlashcardFields(flashcard.question, flashcard.answer)
        repository.updateFlashcard(flashcard.copy(updatedAt = clock.now()))
    }
}
