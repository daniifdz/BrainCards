package org.braincards.app.domain.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.repository.CardSchedulingRepository

/**
 * Fake en memoria de CardSchedulingRepository, sin libreria de mocking.
 * upsertCalls registra cada llamada a upsert(...) (seeds de test incluidos) para poder
 * comprobar en los tests cuantas veces se llamo y con que datos exactos.
 */
class FakeCardSchedulingRepository : CardSchedulingRepository {

    private val schedulings = mutableMapOf<String, CardScheduling>()
    val upsertCalls = mutableListOf<CardScheduling>()

    override suspend fun getByCardId(cardId: String): CardScheduling? = schedulings[cardId]

    override suspend fun upsert(scheduling: CardScheduling) {
        schedulings[scheduling.cardId] = scheduling
        upsertCalls.add(scheduling)
    }

    override fun getAll(): Flow<List<CardScheduling>> = flowOf(schedulings.values.toList())
}
