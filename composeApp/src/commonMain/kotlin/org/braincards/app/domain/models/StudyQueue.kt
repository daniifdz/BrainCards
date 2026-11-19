package org.braincards.app.domain.models

/**
 * Desglose de una sesion de estudio: cuantas veces se pulso cada calidad. El total son PULSACIONES (suma de las 4
 * casillas), no tarjetas distintas: con el re-encolado de las falladas puede superar el total de tarjetas de la cola.
 */
data class StudyResults(val counts: Map<ReviewQuality, Int>) {
    fun countOf(quality: ReviewQuality): Int = counts[quality] ?: 0
    val totalReviews: Int get() = counts.values.sum()
}

/**
 * Cola de una sesión de estudio: una foto en memoria de las tarjetas pendientes al empezar.
 *
 * - [total] (M): tarjetas distintas de la sesión; fijo al empezar, solo baja con [skipCurrent].
 * - [remaining] (N): tarjetas por revisar, la actual incluida. Una fallada («Otra vez») pasa al final sin duplicarse, así que
 *   N <= M.
 * - [progress] = (M - N) / M: solo avanza cuando una tarjeta sale de la cola.
 * - [counts]: pulsaciones por calidad.
 */
data class StudyQueue(
    //val para no romper la inmutabilidad
    val queue: List<Flashcard>,
    val total: Int = queue.size,
    val counts: Map<ReviewQuality, Int> = emptyMap()
) {
    val currentCard: Flashcard? get() = queue.firstOrNull()
    val isFinished: Boolean get() = queue.isEmpty()
    val remaining: Int get() = queue.size
    val progress: Float get() = if (total <= 0) 0f else (total - remaining).toFloat() / total
    val totalReviews: Int get() = counts.values.sum()

    fun results(): StudyResults = StudyResults(counts)

    /** Registra la pulsación: una fallada pasa al final de la cola y el resto sale. */
    fun submitResult(card: Flashcard, quality: ReviewQuality): StudyQueue {
        val remainingCards = queue.drop(1)
        val newCounts = counts + (quality to (counts[quality] ?: 0) + 1)

        return if (quality == ReviewQuality.AGAIN) {
            copy(queue = remainingCards + card, counts = newCounts)
        } else {
            copy(queue = remainingCards, counts = newCounts)
        }
    }

    /** Salta la tarjeta actual SIN puntuarla (ya no existe): sale de la cola y el total baja en 1. */
    fun skipCurrent(): StudyQueue = copy(queue = queue.drop(1), total = (total - 1).coerceAtLeast(0))
}
