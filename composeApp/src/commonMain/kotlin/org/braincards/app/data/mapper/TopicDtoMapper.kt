package org.braincards.app.data.mapper

import org.braincards.app.data.remote.dto.TopicDto
import org.braincards.app.domain.models.Topic

fun TopicDto.toDomain(): Topic = Topic(
    id = id,
    deckId = deckId,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    userId = userId,
    lastSyncedAt = lastSyncedAt
)

// userId no nullable en el DTO/Postgres (NOT NULL en el esquema) -- mismo criterio
// que DeckDtoMapper: fallar alto si se intenta subir un topic sin userId, en vez
// de mandarle a Postgres una fila que su propio NOT NULL va a rechazar igual.
fun Topic.toDto(): TopicDto = TopicDto(
    id = id,
    deckId = deckId,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    lastSyncedAt = lastSyncedAt,
    userId = requireNotNull(userId) { "No se puede subir un topic sin userId" }
)
