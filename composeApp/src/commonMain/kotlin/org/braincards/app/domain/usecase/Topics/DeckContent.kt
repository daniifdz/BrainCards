package org.braincards.app.domain.usecase.Topics

import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic

/** Un tema con el numero de tarjetas vivas que tiene (calculado en memoria con las tarjetas del mazo). */
data class TopicWithCount(val topic: Topic, val cardCount: Int)

/** Una tarjeta encontrada por la busqueda, con el nombre de su tema (null = sin tema) como segunda linea. */
data class CardHit(val card: Flashcard, val topicName: String?)

data class DeckSearchResult(val topics: List<TopicWithCount>, val cards: List<CardHit>) {
    val isEmpty: Boolean get() = topics.isEmpty() && cards.isEmpty()
}

/**
 * Contenido vivo de un mazo, ya ordenado: temas por nombre ([topicComparator]) y tarjetas por antigüedad (luego id). Solo
 * cuentan temas y tarjetas con `deletedAt == null`. El recuento por tema sale de las mismas tarjetas que se muestran (una
 * sola pasada, sin consultas extra), así que no puede divergir de la lista ni del aviso de eliminar.
 *
 * Una tarjeta cuyo `topicId` no corresponde a ningún tema vivo (p. ej. su tema llegó borrado por sincronización) cuenta
 * como sin tema: se ve en «Tarjetas sin tema» y el selector del diálogo de edición mostrará «Sin tema».
 */
class DeckContent private constructor(
    val topics: List<TopicWithCount>,
    private val cards: List<Flashcard>
) {
    private val topicById: Map<String, Topic> = topics.associate { it.topic.id to it.topic }

    /** Tarjetas sin tema (o con tema colgando), por antiguedad. */
    val looseCards: List<Flashcard> = cards.filter { it.topicId == null || it.topicId !in topicById }

    val hasCards: Boolean get() = cards.isNotEmpty()

    /** Ni temas ni tarjetas. */
    val isEmpty: Boolean get() = topics.isEmpty() && cards.isEmpty()

    fun topic(id: String): Topic? = topicById[id]

    fun cardsOf(topicId: String): List<Flashcard> = cards.filter { it.topicId == topicId }

    /**
     * Busca en el nombre de los temas y en el anverso y reverso de TODAS las tarjetas del mazo (tambien las que estan
     * dentro de un tema), sin distinguir mayusculas ni acentos. Una busqueda en blanco no filtra nada.
     */
    fun search(query: String): DeckSearchResult = DeckSearchResult(
        topics = topics.filter { topicMatchesQuery(it.topic.name, query) },
        cards = cards
            .filter { topicMatchesQuery(it.question, query) || topicMatchesQuery(it.answer, query) }
            .map { CardHit(it, it.topicId?.let { id -> topicById[id]?.name }) }
    )

    companion object {
        fun of(topics: List<Topic>, cards: List<Flashcard>): DeckContent {
            val liveTopics = topics.filter { it.deletedAt == null }.sortedWith(topicComparator)
            val liveCards = cards.filter { it.deletedAt == null }.sortedWith(compareBy({ it.createdAt }, { it.id }))
            val counts = liveCards.mapNotNull { it.topicId }.groupingBy { it }.eachCount()
            return DeckContent(
                topics = liveTopics.map { TopicWithCount(it, counts[it.id] ?: 0) },
                cards = liveCards
            )
        }
    }
}
