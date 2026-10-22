package org.braincards.app.data.repository

import org.braincards.app.data.local.TransactionRunner
import org.braincards.app.data.local.dao.CardSchedulingDao
import org.braincards.app.data.local.dao.DeckDao
import org.braincards.app.data.local.dao.FlashcardDao
import org.braincards.app.data.local.dao.ReviewLogDao
import org.braincards.app.data.local.dao.TopicDao
import org.braincards.app.domain.models.SyncTable
import org.braincards.app.domain.repository.LocalDataRepository
import org.braincards.app.domain.repository.LocalTableInspector

class LocalDataRepositoryImpl(
    private val deckDao: DeckDao,
    private val topicDao: TopicDao,
    private val flashcardDao: FlashcardDao,
    private val cardSchedulingDao: CardSchedulingDao,
    private val reviewLogDao: ReviewLogDao,
    private val transactionRunner: TransactionRunner
) : LocalDataRepository, LocalTableInspector {

    // Una sola transaccion: si cualquier DELETE falla, Room revierte todos. Orden hijos ->
    // padres porque foreign_keys esta activo (card_scheduling -> flashcards -> topics/decks).
    // reviewlog no tiene FK, va antes que flashcards por simetria con el resto de hijos.
    // OJO: si se anade una tabla nueva a AppDatabase, anadirla tambien aqui (el test
    // LocalDataRepositoryRoomTest falla si aparece una tabla que no conoce).
    override suspend fun wipeAll() {
        transactionRunner.inTransaction {
            cardSchedulingDao.deleteAllScheduling()
            reviewLogDao.deleteAllReviewLogs()
            flashcardDao.deleteAllFlashcards()
            topicDao.deleteAllTopics()
            deckDao.deleteAllDecks()
        }
    }

    // EXISTS en los DAO, tombstones incluidos (ver LocalTableInspector).
    override suspend fun isEmpty(table: SyncTable): Boolean = when (table) {
        SyncTable.DECKS -> !deckDao.hasAnyDeck()
        SyncTable.TOPICS -> !topicDao.hasAnyTopic()
        SyncTable.FLASHCARDS -> !flashcardDao.hasAnyFlashcard()
        SyncTable.CARD_SCHEDULING -> !cardSchedulingDao.hasAnyScheduling()
    }

    override suspend fun countUnownedRows(): Int =
        deckDao.countUnownedDecks() +
            topicDao.countUnownedTopics() +
            flashcardDao.countUnownedFlashcards() +
            cardSchedulingDao.countUnownedScheduling()
}
