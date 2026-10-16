package org.braincards.app.domain.models

import kotlinx.datetime.Instant

data class CardScheduling(
    val cardId: String,
    val easeFactor: Double,
    val interval: Int,
    val repetitions: Int,
    val nextReview: Instant,
    val updatedAt: Instant,
    // Dueno de la fila = dueno de su flashcard (la RLS de Supabase lo exige). Sin valores
    // por defecto a proposito: el compilador senala cada sitio que construye el modelo.
    val userId: String?,
    val lastSyncedAt: Instant?
)
