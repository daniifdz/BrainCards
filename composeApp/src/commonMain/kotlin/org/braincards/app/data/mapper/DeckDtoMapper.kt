package org.braincards.app.data.mapper

import org.braincards.app.data.remote.dto.DeckDto
import org.braincards.app.domain.models.Deck

fun DeckDto.toDomain(): Deck = Deck(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    userId = userId,
    lastSyncedAt = lastSyncedAt
)

// userId no nullable en el DTO/Postgres (NOT NULL en el esquema) -- si esto salta,
// es que se intento subir una fila que nunca debio pasar el filtro de
// getPendingSync(userId) en primer lugar. Fallar alto es mejor que mandarle a
// Postgres una fila que la propia tabla va a rechazar igualmente por su NOT NULL.
fun Deck.toDto(): DeckDto = DeckDto(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    lastSyncedAt = lastSyncedAt,
    userId = requireNotNull(userId) { "No se puede subir un deck sin userId" }
)
