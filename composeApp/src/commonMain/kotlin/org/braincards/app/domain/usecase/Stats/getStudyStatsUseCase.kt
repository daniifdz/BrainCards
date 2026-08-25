package org.braincards.app.domain.usecase.Stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.braincards.app.domain.models.StudyStats
import org.braincards.app.domain.repository.FlashcardRepository

// TODO(card_scheduling): roto a proposito, igual que ReviewFlashcardUseCase. "repetitions"
// vivia en Flashcard y ahora vive en CardScheduling; calcular new/learning/mature de verdad
// necesita esa data (via CardSchedulingDao, sin repositorio de dominio propio todavia), no
// solo el contenido de la tarjeta. No estaba en la lista de excepciones de esta sesion, pero
// arreglarlo de verdad exige la misma pieza pendiente que ReviewFlashcardUseCase/StudyQueue/
// StudyViewModel -- lo dejo anotado en vez de inventar una version que de numeros erroneos.
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