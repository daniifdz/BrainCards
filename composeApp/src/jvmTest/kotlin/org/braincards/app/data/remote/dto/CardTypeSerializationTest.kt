package org.braincards.app.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.braincards.app.domain.models.CardType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * FlashcardDto.cardType usa el enum CardType directamente, sin transform ni serializador a medida. kotlinx-serialization
 * serializa un enum por su nombre de constante tal cual, pero es lo único de RemoteDataSourceImpl que toca un CHECK real de
 * Postgres (card_type in ('FLIP', 'MULTIPLE_CHOICE') en sql/supabase_schema.sql): un desajuste de formato rechazaría
 * cualquier INSERT/UPSERT. Test de regresión, no solo comprobación puntual.
 */
class CardTypeSerializationTest {

    private fun sampleDto(cardType: CardType): FlashcardDto = FlashcardDto(
        id = "11111111-1111-1111-1111-111111111111",
        deckId = "22222222-2222-2222-2222-222222222222",
        question = "q",
        answer = "a",
        cardType = cardType,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
        userId = "33333333-3333-3333-3333-333333333333"
    )

    @Test
    fun `FLIP serializa exactamente como el valor esperado por el CHECK de Postgres`() {
        val json = Json.encodeToString(FlashcardDto.serializer(), sampleDto(CardType.FLIP))

        assertEquals(true, json.contains("\"card_type\":\"FLIP\""))
    }

    @Test
    fun `MULTIPLE_CHOICE serializa exactamente como el valor esperado por el CHECK de Postgres`() {
        val json = Json.encodeToString(FlashcardDto.serializer(), sampleDto(CardType.MULTIPLE_CHOICE))

        assertEquals(true, json.contains("\"card_type\":\"MULTIPLE_CHOICE\""))
    }
}
