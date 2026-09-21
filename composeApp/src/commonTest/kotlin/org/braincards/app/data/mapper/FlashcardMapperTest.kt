package org.braincards.app.data.mapper

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import kotlin.test.Test
import kotlin.test.assertEquals

class FlashcardMapperTest {

    // Ver comentario en DeckMapperTest. Usa CardType.MULTIPLE_CHOICE con
    // options/correctOptionIndex rellenos a proposito -- son campos sin usar hoy en
    // la UI (ver CLAUDE.md, decision de producto), pero siguen siendo parte del
    // modelo/entidad y el mapper tiene que llevarlos igual.
    @Test
    fun `Flashcard round-trip through toEntity and toDomain preserves every field`() {
        val flashcard = Flashcard(
            id = "card-1",
            deckId = "deck-1",
            question = "Pregunta de prueba",
            answer = "Respuesta de prueba",
            createdAt = Instant.fromEpochMilliseconds(1000),
            updatedAt = Instant.fromEpochMilliseconds(2000),
            cardType = CardType.MULTIPLE_CHOICE,
            topicId = "topic-1",
            deletedAt = Instant.fromEpochMilliseconds(3000),
            lastSyncedAt = Instant.fromEpochMilliseconds(4000),
            userId = "user-123",
            options = listOf("A", "B", "C"),
            correctOptionIndex = 1
        )

        val result = flashcard.toEntity().toDomain()

        assertEquals(flashcard, result)
    }
}
