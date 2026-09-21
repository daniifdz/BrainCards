package org.braincards.app.data.mapper

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Topic
import kotlin.test.Test
import kotlin.test.assertEquals

class TopicMapperTest {

    // Ver comentario en DeckMapperTest: el assertEquals sobre la data class completa
    // es lo que detecta un campo omitido en silencio por el mapper.
    @Test
    fun `Topic round-trip through toEntity and toDomain preserves every field`() {
        val topic = Topic(
            id = "topic-1",
            deckId = "deck-1",
            name = "Verbos irregulares",
            createdAt = Instant.fromEpochMilliseconds(1000),
            updatedAt = Instant.fromEpochMilliseconds(2000),
            deletedAt = Instant.fromEpochMilliseconds(3000),
            lastSyncedAt = Instant.fromEpochMilliseconds(4000),
            userId = "user-123"
        )

        val result = topic.toEntity().toDomain()

        assertEquals(topic, result)
    }
}
