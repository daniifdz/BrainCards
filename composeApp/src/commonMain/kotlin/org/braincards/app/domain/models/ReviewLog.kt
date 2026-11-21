package org.braincards.app.domain.models

import kotlinx.datetime.Instant

data class ReviewLog(
    val id: String,
    val cardId: String,
    val timestamp: Instant,
    val grade: Int,
    val previousInterval: Int,
    val newInterval: Int,
    val userId: String? = null
)