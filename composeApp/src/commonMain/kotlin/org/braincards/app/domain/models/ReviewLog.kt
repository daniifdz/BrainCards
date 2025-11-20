package org.braincards.app.domain.models

data class ReviewLog(
    val id: Int,
    val cardId: String,
    val timestamp: Long,
    val grade: Int,
    val previousInterval: Int,
    val newInterval: Int
)