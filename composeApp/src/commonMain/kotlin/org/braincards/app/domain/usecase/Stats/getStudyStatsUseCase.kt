package org.braincards.app.domain.usecase.Stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.braincards.app.domain.models.StudyStats
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.FlashcardRepository

class GetStudyStatsUseCase(
    private val repository: FlashcardRepository,
    private val cardSchedulingRepository: CardSchedulingRepository
) {

    operator fun invoke(): Flow<StudyStats> {
        return combine(
            repository.getAllFlashcards(),
            cardSchedulingRepository.getAll()
        ) { allCards, allSchedulings ->
            val schedulingByCardId = allSchedulings.associateBy { it.cardId }

            val total = allCards.size
            val new = allCards.count { (schedulingByCardId[it.id]?.repetitions ?: 0) == 0 }
            val learning = allCards.count {
                val repetitions = schedulingByCardId[it.id]?.repetitions ?: 0
                repetitions > 0 && repetitions < 3
            }
            val mature = allCards.count { (schedulingByCardId[it.id]?.repetitions ?: 0) >= 3 }

            StudyStats(
                totalCards = total,
                newCards = new,
                learningCards = learning,
                matureCards = mature
            )
        }
    }
}
