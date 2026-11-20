package org.braincards.app.domain.usecase.Decks

/** Atajos de los tests: ¿se creó el mazo? y el id del mazo listo (falla si el resultado es [SampleDeckResult.AlreadyHasDecks]). */
val SampleDeckResult.created: Boolean get() = this is SampleDeckResult.Created

val SampleDeckResult.deckId: String get() = (this as SampleDeckResult.Ready).deckId
