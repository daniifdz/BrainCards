package org.braincards.app.domain.models

data class Flashcard(
    val id: String,
    val deckId: String,
    val createdAt: Long,
    val updatedAt: Long,





    val question: String,
    val answer: String,

    val easeFactor: Double, //recordar la tarjeta
    val interval: Int, //dias hasta la proxima revision
    val repetitions: Int,
    val nextReview: Long
)
