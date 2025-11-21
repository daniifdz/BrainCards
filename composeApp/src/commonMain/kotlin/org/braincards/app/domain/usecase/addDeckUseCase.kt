package org.braincards.app.domain.usecase

import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository
import kotlinx.datetime.Clock

class AddDeckUseCase(private val repository: DecksRepository){

    //uso de clase como funcion
    suspend operator fun invoke(name: String, description: String?) {

        val now = Clock.System.now()

        val newDeck = Deck(
            id = 0,
            name = name,
            description = description,
            createdAt = now,
            updatedAt = now
        )

        repository.addDeck(newDeck)
    }
}