package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.CardScheduling

interface CardSchedulingRepository {
    suspend fun getByCardId(cardId: String): CardScheduling?
    suspend fun upsert(scheduling: CardScheduling)
    fun getAll(): Flow<List<CardScheduling>>
}
