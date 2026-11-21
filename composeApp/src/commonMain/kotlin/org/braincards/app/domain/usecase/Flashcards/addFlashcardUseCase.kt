package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

class AddFlashcardUseCase(private val repository: FlashcardRepository) {

    /** Lanza [FlashcardFieldException] si el anverso o el reverso estan en blanco (ver [checkFlashcardFields]). */
    suspend operator fun invoke(flashcard: Flashcard) {
        checkFlashcardFields(flashcard.question, flashcard.answer)
        repository.addFlashcard(flashcard)
    }
}
