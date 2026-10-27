package org.braincards.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.braincards.app.data.local.dao.CardSchedulingDao
import org.braincards.app.data.local.dao.DeckDao
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.local.dao.TopicDao
import org.braincards.app.domain.models.PendingStamp
import org.braincards.app.domain.repository.PendingChangesObserver

class PendingChangesObserverImpl(
    private val deckDao: DeckDao,
    private val topicDao: TopicDao,
    private val flashcardDao: FlashcardDao,
    private val cardSchedulingDao: CardSchedulingDao
) : PendingChangesObserver {

    // Room re-emite cada vez que se invalida CUALQUIERA de las tablas de la consulta, aunque el resultado
    // sea el mismo (p. ej. markPushed sobre una fila ya limpia, o una fila de otro usuario): el
    // distinctUntilChanged evita que eso parezca "un cambio nuevo".
    override fun observe(userId: String): Flow<PendingStamp> = combine(
        deckDao.observePendingStamp(userId),
        topicDao.observePendingStamp(userId),
        flashcardDao.observePendingStamp(userId),
        cardSchedulingDao.observePendingStamp(userId)
    ) { decks, topics, flashcards, scheduling ->
        PendingStamp(
            count = decks.pendingCount + topics.pendingCount + flashcards.pendingCount + scheduling.pendingCount,
            newestUpdatedAtMillis = maxOf(
                decks.newestUpdatedAt, topics.newestUpdatedAt, flashcards.newestUpdatedAt, scheduling.newestUpdatedAt
            )
        )
    }.distinctUntilChanged()
}
