package org.braincards.app.data.mapper

import org.braincards.app.data.remote.dto.FlashcardDto
import org.braincards.app.domain.models.Flashcard

fun FlashcardDto.toDomain(): Flashcard = Flashcard(
    id = id,
    deckId = deckId,
    question = question,
    answer = answer,
    createdAt = createdAt,
    updatedAt = updatedAt,
    cardType = cardType,
    topicId = topicId,
    deletedAt = deletedAt,
    lastSyncedAt = lastSyncedAt,
    userId = userId,
    options = options,
    correctOptionIndex = correctOptionIndex
)

// userId no nullable en el DTO/Postgres (NOT NULL en el esquema) -- mismo criterio
// que DeckDtoMapper/TopicDtoMapper: fallar alto si se intenta subir una flashcard
// sin userId, en vez de mandarle a Postgres una fila que su propio NOT NULL va a
// rechazar igual.
fun Flashcard.toDto(): FlashcardDto = FlashcardDto(
    id = id,
    deckId = deckId,
    topicId = topicId,
    question = question,
    answer = answer,
    cardType = cardType,
    options = options,
    correctOptionIndex = correctOptionIndex,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    lastSyncedAt = lastSyncedAt,
    userId = requireNotNull(userId) { "No se puede subir una flashcard sin userId" }
)
