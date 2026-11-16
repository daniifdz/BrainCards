package org.braincards.app.domain.models

/** Un mazo vivo con el numero de tarjetas vivas que contiene (para la Lista de mazos). */
data class DeckSummary(
    val deck: Deck,
    val cardCount: Int
)
