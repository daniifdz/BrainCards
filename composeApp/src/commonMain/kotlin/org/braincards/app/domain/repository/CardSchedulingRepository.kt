package org.braincards.app.domain.repository

import org.braincards.app.domain.models.CardScheduling

interface CardSchedulingRepository {
    suspend fun getByCardId(cardId: String): CardScheduling?
    suspend fun upsert(scheduling: CardScheduling)
}
