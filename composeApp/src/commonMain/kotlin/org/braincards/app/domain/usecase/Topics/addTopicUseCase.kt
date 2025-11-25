package org.braincards.app.domain.usecase.Topics

import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.TopicsRepository

class AddTopicUseCase(private val repository: TopicsRepository) {
    suspend operator fun invoke(topic: Topic) {
        repository.addTopic(topic)

    }

}