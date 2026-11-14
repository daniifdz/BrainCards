package org.braincards.app.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.Instant
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.domain.repository.DueCardsObserver

class DueCardsObserverImpl(private val flashcardDao: FlashcardDao) : DueCardsObserver {

    // Cada hora nueva de [now] cancela la consulta anterior y abre otra con ese "now". distinctUntilChanged: Room
    // re-emite aunque el resultado sea el mismo (cualquier escritura en las tablas de la consulta).
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDueCount(now: Flow<Instant>): Flow<Int> =
        now.flatMapLatest { flashcardDao.observeDueCount(it) }.distinctUntilChanged()
}
