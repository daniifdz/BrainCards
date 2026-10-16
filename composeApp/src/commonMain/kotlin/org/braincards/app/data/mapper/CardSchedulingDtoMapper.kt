package org.braincards.app.data.mapper

import org.braincards.app.data.remote.dto.CardSchedulingDto
import org.braincards.app.domain.models.CardScheduling

fun CardSchedulingDto.toDomain(): CardScheduling = CardScheduling(
    cardId = cardId,
    easeFactor = easeFactor,
    interval = intervalDays,
    repetitions = repetitions,
    nextReview = nextReview,
    updatedAt = updatedAt,
    userId = userId,
    lastSyncedAt = lastSyncedAt
)

// userId no nullable en el DTO/Postgres (NOT NULL) -- mismo criterio que DeckDtoMapper:
// si salta, se intento subir una fila que no debio pasar el filtro de getPendingSync(userId).
// lastSyncedAt se copia tal cual del dominio, igual que en los otros tres DTO.
fun CardScheduling.toDto(): CardSchedulingDto = CardSchedulingDto(
    cardId = cardId,
    userId = requireNotNull(userId) { "No se puede subir un card_scheduling sin userId" },
    easeFactor = easeFactor,
    intervalDays = interval,
    repetitions = repetitions,
    nextReview = nextReview,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt
)
