package org.braincards.app.domain.models

/**
 * Madurez de las tarjetas activas:
 * - [newCards]: sin fila en card_scheduling (nunca repasadas).
 * - [learningCards]: con fila e `interval < 21` días (incluye la recién fallada, intervalo 0).
 * - [matureCards]: `interval >= 21` días.
 * «Tarjetas totales» es la suma de las tres, así la casilla y el donut siempre coinciden.
 */
data class StudyStats(
    val newCards: Int,
    val learningCards: Int,
    val matureCards: Int
) {
    val totalCards: Int get() = newCards + learningCards + matureCards
}
