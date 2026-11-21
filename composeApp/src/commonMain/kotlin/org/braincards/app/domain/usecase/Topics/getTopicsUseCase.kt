package org.braincards.app.domain.usecase.Topics

import kotlinx.coroutines.flow.Flow
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.TopicsRepository

class GetTopicsUseCase(private val repository: TopicsRepository) {
    operator fun invoke(deckId: String): Flow<List<Topic>> {
        return repository.getTopicsByDeckId(deckId)

    }

}