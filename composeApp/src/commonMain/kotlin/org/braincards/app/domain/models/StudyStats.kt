package org.braincards.app.domain.models

/**
 * Madurez de las tarjetas ACTIVAS (Estadisticas, handoff v4.15):
 *  - [newCards]: sin fila en card_scheduling (nunca repasadas);
 *  - [learningCards]: con fila e `interval < 21` dias (incluye la recien fallada, intervalo 0);
 *  - [matureCards]: `interval >= 21` dias.
 * "Tarjetas totales" es la SUMA de las tres, asi la casilla y el donut siempre coinciden.
 */
data class StudyStats(
    val newCards: Int,
    val learningCards: Int,
    val matureCards: Int
) {
    val totalCards: Int get() = newCards + learningCards + matureCards
}
