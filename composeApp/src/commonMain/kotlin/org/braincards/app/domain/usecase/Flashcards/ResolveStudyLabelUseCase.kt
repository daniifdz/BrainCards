package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.StudyCardLabel
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.TopicsRepository

/**
 * Etiqueta "Mazo · Tema" de una tarjeta, con getDeckById / getTopicById (sin tocar las interfaces de repositorio).
 * CACHEA los nombres: una instancia por sesion de estudio (el VM recibe una instancia propia de Koin `factory`), asi que
 * un mazo o tema repetido se consulta una sola vez. Devuelve null si el mazo ya no existe.
 */
class ResolveStudyLabelUseCase(
    private val decksRepository: DecksRepository,
    private val topicsRepository: TopicsRepository
) {
    private val deckNames = mutableMapOf<String, String?>()
    private val topicNames = mutableMapOf<String, String?>()

    suspend operator fun invoke(card: Flashcard): StudyCardLabel? {
        val deckName = deckName(card.deckId) ?: return null
        val topicId = card.topicId
        val topicName = if (topicId == null) null else topicName(topicId)
        return StudyCardLabel(deckName, topicName)
    }

    /** Nombre del mazo (con la misma cache); null si ya no existe. La usa el VM para la etiqueta de Resultados. */
    suspend fun deckName(deckId: String): String? {
        if (!deckNames.containsKey(deckId)) deckNames[deckId] = decksRepository.getDeckById(deckId)?.name
        return deckNames[deckId]
    }

    private suspend fun topicName(topicId: String): String? {
        if (!topicNames.containsKey(topicId)) topicNames[topicId] = topicsRepository.getTopicById(topicId)?.name
        return topicNames[topicId]
    }
}
