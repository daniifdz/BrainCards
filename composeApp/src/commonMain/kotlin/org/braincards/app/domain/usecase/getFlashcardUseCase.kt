package org.braincards.app.domain.usecase

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.repository.FlashcardRepository

class GetFlashcardUseCase(private val repository: FlashcardRepository) {


    operator fun invoke(deckId: Int): Flow<List<Flashcard>> {
        return repository.getFlashcardsByDeckId(deckId)
    }
}