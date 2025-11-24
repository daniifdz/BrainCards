package org.braincards.app.data.mapper

import org.braincards.app.data.local.entity.FlashcardEntity
import org.braincards.app.domain.models.Flashcard

fun FlashcardEntity.toDomain(): Flashcard {
    return Flashcard(
        id = id,
        deckId = deckId,
        question = question,
        answer = answer,
        easeFactor = easeFactor,
        interval = interval,
        repetitions = repetitions,
        createdAt = createdAt,
        updatedAt = updatedAt,
        nextReview = nextReview
    )
}

fun Flashcard.toEntity(): FlashcardEntity {
    return FlashcardEntity(
        id = id,
        deckId = deckId,
        question = question,
        answer = answer,
        easeFactor = easeFactor,
        interval = interval,
        repetitions = repetitions,
        createdAt = createdAt,
        updatedAt = updatedAt,
        nextReview = nextReview
    )
}