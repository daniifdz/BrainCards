package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.domain.models.StudyStats
import org.braincards.app.domain.repository.StudyStatsObserver

class StudyStatsObserverImpl(private val flashcardDao: FlashcardDao) : StudyStatsObserver {

    // distinctUntilChanged: Room re-emite con cualquier escritura en flashcards, card_scheduling o decks (p. ej. cada repaso)
    // aunque el recuento no cambie.
    override fun observeStudyStats(): Flow<StudyStats> =
        flashcardDao.observeMaturityCounts()
            .map { StudyStats(newCards = it.newCards, learningCards = it.learningCards, matureCards = it.matureCards) }
            .distinctUntilChanged()
}
