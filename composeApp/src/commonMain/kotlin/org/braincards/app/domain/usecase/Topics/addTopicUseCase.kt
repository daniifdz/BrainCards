package org.braincards.app.domain.usecase.Topics

import kotlinx.coroutines.flow.first
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.TopicsRepository

class AddTopicUseCase(private val repository: TopicsRepository) {

    /**
     * Crea un tema. Lanza [TopicNameException] si el nombre esta en blanco o ya existe un tema vivo con ese nombre en
     * el MISMO mazo (ver [checkTopicName]). El nombre se guarda recortado. El `userId` lo trae el [topic] (null, como
     * al crear un mazo: lo adopta despues el backfill / el sync).
     */
    suspend operator fun invoke(topic: Topic) {
        checkTopicName(topic.name, topic.deckId, existing = repository.getTopicsByDeckId(topic.deckId).first())

        repository.addTopic(topic.copy(name = topic.name.trim()))
    }

}
