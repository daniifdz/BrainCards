package org.braincards.app.data.mapper

import org.braincards.app.data.local.entity.ReviewLogEntity
import org.braincards.app.domain.models.ReviewLog

fun ReviewLogEntity.toDomain(): ReviewLog {
    return ReviewLog(
        id = id,
        cardId = cardId,
        timestamp = timestamp,
        grade = grade,
        previousInterval = previousInterval,
        newInterval = newInterval
    )
}

fun ReviewLog.toEntity(): ReviewLogEntity {
    return ReviewLogEntity(
        id = id,
        cardId = cardId,
        timestamp = timestamp,
        grade = grade,
        previousInterval = previousInterval,
        newInterval = newInterval
    )
}
