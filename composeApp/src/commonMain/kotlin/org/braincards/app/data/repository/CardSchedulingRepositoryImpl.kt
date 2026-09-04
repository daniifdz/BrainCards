package org.braincards.app.data.repository

import org.braincards.app.data.local.dao.CardSchedulingDao
import org.braincards.app.data.mapper.toDomain
import org.braincards.app.data.mapper.toEntity
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.repository.CardSchedulingRepository

class CardSchedulingRepositoryImpl(
    private val cardSchedulingDao: CardSchedulingDao
) : CardSchedulingRepository {

    override suspend fun getByCardId(cardId: String): CardScheduling? {
        return cardSchedulingDao.getByCardId(cardId)?.toDomain()
    }

    // insert() esta anotado @Insert(onConflict = REPLACE) -- ya es upsert: crea la fila
    // si el cardId no existe, la reemplaza entera si ya existe (cardId es la PK).
    override suspend fun upsert(scheduling: CardScheduling) {
        cardSchedulingDao.insert(scheduling.toEntity())
    }
}
