package org.braincards.app.domain.usecase.Stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.braincards.app.domain.models.StudyStats
import org.braincards.app.domain.repository.FlashcardRepository

class GetStudyStatsUseCase(private val repository: FlashcardRepository) {

    operator fun invoke(): Flow<StudyStats> {
        return repository.getAllFlashcards().map { allCards ->
            val total = allCards.size
            val new = allCards.count { it.repetitions == 0 }
            val learning = allCards.count { it.repetitions > 0 && it.repetitions < 3 }
            val mature = allCards.count { it.repetitions >= 3 }

            StudyStats(
                totalCards = total,
                newCards = new,
                learningCards = learning,
                matureCards = mature
            )
        }
    }
}