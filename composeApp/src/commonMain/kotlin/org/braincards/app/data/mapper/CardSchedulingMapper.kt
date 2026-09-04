package org.braincards.app.data.mapper

import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.domain.models.CardScheduling

fun CardSchedulingEntity.toDomain(): CardScheduling {
    return CardScheduling(
        cardId = cardId,
        easeFactor = easeFactor,
        interval = interval,
        repetitions = repetitions,
        nextReview = nextReview,
        updatedAt = updatedAt
    )
}

fun CardScheduling.toEntity(): CardSchedulingEntity {
    return CardSchedulingEntity(
        cardId = cardId,
        easeFactor = easeFactor,
        interval = interval,
        repetitions = repetitions,
        nextReview = nextReview,
        updatedAt = updatedAt
    )
}
