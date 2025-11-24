package org.braincards.app.domain.models

enum class ReviewQuality(val value: Int) {
    AGAIN(0), //Ni idea de la pregutna
    HARD(3),  //Dificil
    GOOD(4),  //Bien
    EASY(5)   //Facil
}