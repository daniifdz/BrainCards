package org.braincards.app.data.mapper

import kotlinx.datetime.Instant
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.domain.models.CardScheduling
import kotlin.test.Test
import kotlin.test.assertEquals

class CardSchedulingMapperTest {

    // Ver comentario en DeckMapperTest: el assertEquals sobre la data class completa
    // es lo que detecta un campo omitido en silencio por el mapper. Todos los valores
    // son distintos entre si y no nulos, para que ningun campo pueda "coincidir por azar".
    @Test
    fun `CardScheduling round-trip through toEntity and toDomain preserves every field`() {
        val scheduling = CardScheduling(
            cardId = "card-1",
            easeFactor = 2.36,
            interval = 17,
            repetitions = 4,
            nextReview = Instant.fromEpochMilliseconds(5000),
            updatedAt = Instant.fromEpochMilliseconds(6000),
            userId = "user-123",
            lastSyncedAt = Instant.fromEpochMilliseconds(7000)
        )

        val result = scheduling.toEntity().toDomain()

        assertEquals(scheduling, result)
    }

    @Test
    fun `CardScheduling round-trip preserves null userId and lastSyncedAt`() {
        val scheduling = CardScheduling(
            cardId = "card-2",
            easeFactor = 1.3,
            interval = 0,
            repetitions = 0,
            nextReview = Instant.fromEpochMilliseconds(100),
            updatedAt = Instant.fromEpochMilliseconds(200),
            userId = null,
            lastSyncedAt = null
        )

        assertEquals(scheduling, scheduling.toEntity().toDomain())
    }

    // Direccion inversa, campo a campo: un toEntity que olvidara userId/lastSyncedAt no
    // lo detectaria el round-trip si toDomain los olvidara igual, asi que se comprueba
    // la entidad por separado.
    @Test
    fun `toEntity maps every field to the entity`() {
        val scheduling = CardScheduling(
            cardId = "card-1",
            easeFactor = 2.36,
            interval = 17,
            repetitions = 4,
            nextReview = Instant.fromEpochMilliseconds(5000),
            updatedAt = Instant.fromEpochMilliseconds(6000),
            userId = "user-123",
            lastSyncedAt = Instant.fromEpochMilliseconds(7000)
        )

        val entity = scheduling.toEntity()

        assertEquals(
            CardSchedulingEntity(
                cardId = "card-1",
                easeFactor = 2.36,
                interval = 17,
                repetitions = 4,
                nextReview = Instant.fromEpochMilliseconds(5000),
                updatedAt = Instant.fromEpochMilliseconds(6000),
                userId = "user-123",
                lastSyncedAt = Instant.fromEpochMilliseconds(7000)
            ),
            entity
        )
    }
}
