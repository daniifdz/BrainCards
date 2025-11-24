package org.braincards.app.domain.models

import kotlinx.datetime.Instant

data class Flashcard(
    val id: Int = 0,
    val deckId: Int,
    val question: String,
    val answer: String,

    //fechas
    val createdAt: Instant,
    val updatedAt: Instant,
    val nextReview: Instant,


    //metadata de estudio
    val easeFactor: Double = 2.5, //recordar la tarjeta
    val interval: Int, //dias hasta la proxima revision
    val repetitions: Int
)
