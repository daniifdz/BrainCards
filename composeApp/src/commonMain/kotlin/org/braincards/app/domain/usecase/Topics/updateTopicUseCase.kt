package org.braincards.app.domain.usecase.Topics

import kotlinx.coroutines.flow.first
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.TopicsRepository


class UpdateTopicUseCase(private val repository: TopicsRepository) {

    /**
     * Guarda el tema. Lanza [TopicNameException] si el nombre esta en blanco o, al CAMBIARLO, coincide con otro tema
     * vivo del mismo mazo (el propio se excluye por id). Si el nombre normalizado no cambia no se valida duplicado:
     * asi se puede guardar un tema que ya tuviera un duplicado llegado por sincronizacion.
     */
    suspend operator fun invoke(topic: Topic) {
        val current = repository.getTopicById(topic.id)
        val unchanged = current != null && normalizeTopicName(current.name) == normalizeTopicName(topic.name)
        if (unchanged) {
            checkTopicName(topic.name, topic.deckId, existing = emptyList(), ownId = topic.id)
        } else {
            checkTopicName(
                topic.name, topic.deckId,
                existing = repository.getTopicsByDeckId(topic.deckId).first(), ownId = topic.id
            )
        }
        repository.updateTopic(topic.copy(name = topic.name.trim()))
    }
}
