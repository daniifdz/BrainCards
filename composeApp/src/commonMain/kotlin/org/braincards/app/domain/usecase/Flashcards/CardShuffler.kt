package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.Flashcard
import kotlin.random.Random

/** Orden aleatorio INYECTABLE del repaso libre: los tests pasan uno determinista (inverso, identidad...). */
fun interface CardShuffler {
    fun shuffle(cards: List<Flashcard>, seed: Long): List<Flashcard>
}

/** Barajado de produccion: Fisher-Yates uniforme con `Random(seed)`: la misma semilla y la misma lista dan el mismo orden. */
object SeededCardShuffler : CardShuffler {
    override fun shuffle(cards: List<Flashcard>, seed: Long): List<Flashcard> = cards.shuffled(Random(seed))
}
