package org.braincards.app.domain.fakes

import org.braincards.app.domain.models.ReviewLog
import org.braincards.app.domain.repository.ReviewLogRepository

/**
 * Fake en memoria de ReviewLogRepository, sin libreria de mocking.
 * addedLogs registra cada llamada a addReviewLog(...) para poder comprobar
 * en los tests cuantas veces se llamo y con que datos exactos.
 */
class FakeReviewLogRepository : ReviewLogRepository {

    val addedLogs = mutableListOf<ReviewLog>()

    override suspend fun addReviewLog(reviewLog: ReviewLog) {
        addedLogs.add(reviewLog)
    }
}
