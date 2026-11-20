package org.braincards.app.domain.repository

import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic

/**
 * Guarda un mazo con sus temas y tarjetas de una sola vez. Interfaz aparte, aditiva: no toca los repositorios de dominio.
 *
 * La escritura es atómica: o se guarda todo (mazo, temas y tarjetas) o no queda nada. Las filas se guardan tal como llegan, con
 * las mismas reglas que el alta manual (sin dueño ni marca de sync: las adopta y sube el sync después).
 */
interface SampleDeckStore {
    suspend fun insert(deck: Deck, topics: List<Topic>, cards: List<Flashcard>)
}
