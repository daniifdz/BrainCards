package org.braincards.app.data.mapper

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.braincards.app.data.remote.dto.CardSchedulingDto
import org.braincards.app.domain.models.CardScheduling
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CardSchedulingDtoMapperTest {

    // Todos los valores distintos entre si y no nulos: un campo omitido o cruzado en silencio
    // por el mapper no puede "coincidir por azar" (ver DeckMapperTest / CardSchedulingMapperTest).
    private fun fullScheduling() = CardScheduling(
        cardId = "card-1",
        easeFactor = 2.36,
        interval = 17,
        repetitions = 4,
        nextReview = Instant.fromEpochMilliseconds(5_000),
        updatedAt = Instant.fromEpochMilliseconds(6_000),
        userId = "user-123",
        lastSyncedAt = Instant.fromEpochMilliseconds(7_000)
    )

    @Test
    fun `round-trip dominio a DTO y de vuelta conserva todos los campos`() {
        val scheduling = fullScheduling()

        assertEquals(scheduling, scheduling.toDto().toDomain())
    }

    @Test
    fun `round-trip conserva lastSyncedAt nulo`() {
        val scheduling = fullScheduling().copy(lastSyncedAt = null)

        assertEquals(scheduling, scheduling.toDto().toDomain())
    }

    // El round-trip no detecta un toDto Y toDomain que olviden el mismo campo; la direccion
    // inversa se comprueba campo a campo contra el DTO esperado.
    @Test
    fun `toDto mapea cada campo, interval va a intervalDays`() {
        val dto = fullScheduling().toDto()

        assertEquals(
            CardSchedulingDto(
                cardId = "card-1",
                userId = "user-123",
                easeFactor = 2.36,
                intervalDays = 17,
                repetitions = 4,
                nextReview = Instant.fromEpochMilliseconds(5_000),
                updatedAt = Instant.fromEpochMilliseconds(6_000),
                lastSyncedAt = Instant.fromEpochMilliseconds(7_000)
            ),
            dto
        )
    }

    @Test
    fun `toDomain mapea cada campo, intervalDays va a interval`() {
        val domain = CardSchedulingDto(
            cardId = "card-9",
            userId = "user-9",
            easeFactor = 1.9,
            intervalDays = 33,
            repetitions = 8,
            nextReview = Instant.fromEpochMilliseconds(100),
            updatedAt = Instant.fromEpochMilliseconds(200),
            lastSyncedAt = Instant.fromEpochMilliseconds(300)
        ).toDomain()

        assertEquals(
            CardScheduling(
                cardId = "card-9", easeFactor = 1.9, interval = 33, repetitions = 8,
                nextReview = Instant.fromEpochMilliseconds(100), updatedAt = Instant.fromEpochMilliseconds(200),
                userId = "user-9", lastSyncedAt = Instant.fromEpochMilliseconds(300)
            ),
            domain
        )
    }

    @Test
    fun `subir un scheduling sin userId falla alto`() {
        assertFailsWith<IllegalArgumentException> { fullScheduling().copy(userId = null).toDto() }
    }

    // Los nombres tienen que coincidir EXACTAMENTE con las columnas de public.card_scheduling.
    @Test
    fun `el DTO serializa exactamente las 8 columnas de la tabla de Supabase`() {
        val json = Json.parseToJsonElement(
            Json.encodeToString(CardSchedulingDto.serializer(), fullScheduling().toDto())
        ).jsonObject

        assertEquals(
            setOf(
                "card_id", "user_id", "ease_factor", "interval_days",
                "repetitions", "next_review", "updated_at", "last_synced_at"
            ),
            json.keys
        )
        assertEquals("\"1970-01-01T00:00:05Z\"", json.getValue("next_review").toString())
        assertEquals("\"1970-01-01T00:00:06Z\"", json.getValue("updated_at").toString())
    }

    @Test
    fun `el DTO decodifica una fila tal como la devuelve PostgREST`() {
        val row = """
            {"card_id":"c1","user_id":"u1","ease_factor":2.5,"interval_days":6,"repetitions":2,
             "next_review":"2025-01-02T03:04:05+00:00","updated_at":"2025-01-01T00:00:00Z","last_synced_at":null}
        """.trimIndent()

        val dto = Json.decodeFromString(CardSchedulingDto.serializer(), row)

        assertEquals("c1", dto.cardId)
        assertEquals(6, dto.intervalDays)
        assertEquals(Instant.parse("2025-01-02T03:04:05Z"), dto.nextReview)
        assertEquals(null, dto.lastSyncedAt)
    }
}
