package org.braincards.app.data.mapper

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Deck
import kotlin.test.Test
import kotlin.test.assertEquals

class DeckMapperTest {

    // Round-trip con TODOS los campos rellenos (incluidos los nullable, con valores
    // no-default) -- un assertEquals sobre data class compara campo a campo, asi que
    // cualquier campo que el mapper omita en silencio (como paso con userId) revienta
    // este test en vez de tardar dias en verse como comportamiento raro en produccion.
    @Test
    fun `Deck round-trip through toEntity and toDomain preserves every field`() {
        val deck = Deck(
            id = "deck-1",
            name = "Vocabulario",
            createdAt = Instant.fromEpochMilliseconds(1000),
            updatedAt = Instant.fromEpochMilliseconds(2000),
            description = "Descripcion de prueba",
            deletedAt = Instant.fromEpochMilliseconds(3000),
            userId = "user-123",
            lastSyncedAt = Instant.fromEpochMilliseconds(4000)
        )

        val result = deck.toEntity().toDomain()

        assertEquals(deck, result)
    }
}
