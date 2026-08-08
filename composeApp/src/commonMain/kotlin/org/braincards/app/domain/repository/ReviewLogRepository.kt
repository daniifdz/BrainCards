package org.braincards.app.domain.repository

import org.braincards.app.domain.models.ReviewLog

interface ReviewLogRepository {

    suspend fun addReviewLog (reviewLog: ReviewLog)
}