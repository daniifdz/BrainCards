package org.braincards.app.domain.models

import kotlinx.serialization.Serializable

@Serializable
sealed class StudyScope {
    @Serializable object All : StudyScope() //dataobject sin datos
    @Serializable class Deck(val deckId: String) : StudyScope()
}