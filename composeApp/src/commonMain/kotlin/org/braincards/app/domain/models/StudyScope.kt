package org.braincards.app.domain.models

sealed class StudyScope {
    data object All : StudyScope() //dataobject sin datos
    data class Deck(val deckId: String) : StudyScope()
}