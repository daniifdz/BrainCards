package org.braincards.app.data.repository

import org.braincards.app.data.local.dao.ReviewLogDao
import org.braincards.app.data.mapper.toEntity
import org.braincards.app.domain.models.ReviewLog
import org.braincards.app.domain.repository.ReviewLogRepository

class ReviewLogRepositoryImpl(private val reviewLogDao: ReviewLogDao
) : ReviewLogRepository {

    override suspend fun addReviewLog(reviewLog: ReviewLog){
        reviewLogDao.addReviewLog(reviewLog.toEntity())
    }

}