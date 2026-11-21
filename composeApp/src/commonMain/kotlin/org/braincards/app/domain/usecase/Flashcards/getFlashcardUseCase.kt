package org.braincards.app.domain.usecase.Flashcards

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

class GetFlashcardUseCase(private val repository: FlashcardRepository) {


    operator fun invoke(deckId: String): Flow<List<Flashcard>> {
        return repository.getFlashcardsByDeckId(deckId)
    }
}