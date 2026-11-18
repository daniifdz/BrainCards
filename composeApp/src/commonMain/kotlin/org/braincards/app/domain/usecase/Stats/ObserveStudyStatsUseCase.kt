package org.braincards.app.domain.usecase.Stats

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.StudyStats
import org.braincards.app.domain.repository.StudyStatsObserver

/** Madurez de las tarjetas activas (Nuevas / Aprendiendo / Maduras). Sustituye al antiguo GetStudyStatsUseCase. */
class ObserveStudyStatsUseCase(private val observer: StudyStatsObserver) {
    operator fun invoke(): Flow<StudyStats> = observer.observeStudyStats()
}
