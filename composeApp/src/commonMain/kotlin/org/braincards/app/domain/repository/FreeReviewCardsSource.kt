package org.braincards.app.domain.repository

import org.braincards.app.domain.models.Flashcard

/**
 * Tarjetas para el REPASO LIBRE: todas las tarjetas ACTIVAS del origen (viva Y de un mazo vivo: la misma regla unica que Home y la
 * sesion normal, `LIVE_DECK_JOIN`), SIN filtrar por pendientes. Interfaz aparte, aditiva: no toca FlashcardRepository (igual que
 * DueCardsObserver). Solo lectura.
 */
interface FreeReviewCardsSource {
    /** Todas las tarjetas activas de todos los mazos ("Estudiar Todo"). */
    suspend fun getAll(): List<Flashcard>

    /** Todas las tarjetas activas del mazo [deckId] (de todos sus temas y sin tema). */
    suspend fun getByDeck(deckId: String): List<Flashcard>
}
