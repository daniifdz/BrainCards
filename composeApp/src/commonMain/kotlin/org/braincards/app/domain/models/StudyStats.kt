package org.braincards.app.domain.models

data class StudyStats(
    val totalCards: Int,
    val newCards: Int,      // Repetitions == 0
    val learningCards: Int, // Repetitions < 3
    val matureCards: Int    // Repetitions >= 3
)