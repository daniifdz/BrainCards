package org.braincards.app.domain.usecase.Topics

import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.usecase.Decks.deckMatchesQuery
import org.braincards.app.domain.usecase.Decks.deckSortKey
import org.braincards.app.domain.usecase.Decks.normalizeDeckName

enum class TopicNameError { Blank, Duplicate }

/**
 * Nombre de tema no valido. Hereda de IllegalArgumentException para no romper a quien ya capturaba el `require` del
 * caso de uso; la UI mira [reason] (nunca el mensaje).
 */
class TopicNameException(val reason: TopicNameError) : IllegalArgumentException(reason.name)

// Mismas reglas de comparación que las de los mazos: se delega para que no diverjan.

/** Clave de DUPLICADOS: trim, espacios internos colapsados y minusculas. Los acentos SI distinguen. */
fun normalizeTopicName(name: String): String = normalizeDeckName(name)

/** Clave de ORDEN y BUSQUEDA: la de duplicados mas el plegado de acentos. */
fun topicSortKey(name: String): String = deckSortKey(name)

/** ¿El nombre coincide con la busqueda? Sin distinguir mayusculas ni acentos; una busqueda en blanco coincide con todo. */
fun topicMatchesQuery(name: String, query: String): Boolean = deckMatchesQuery(name, query)

/** Orden fijo de los temas: nombre (clave de orden), luego antiguedad y luego id, estable con duplicados. */
val topicComparator: Comparator<Topic> =
    compareBy<Topic> { topicSortKey(it.name) }
        .thenBy { it.createdAt }
        .thenBy { it.id }

/**
 * Valida un nombre de tema dentro del mazo [deckId]. [existing] puede traer temas de cualquier mazo y borrados: solo
 * cuentan los vivos de ESTE mazo. [ownId] excluye al propio tema al renombrar. Solo bloquea CREAR un duplicado nuevo:
 * no toca los que ya existan (pueden llegar por sincronizacion).
 */
fun checkTopicName(name: String, deckId: String, existing: List<Topic>, ownId: String? = null) {
    val key = normalizeTopicName(name)
    if (key.isEmpty()) throw TopicNameException(TopicNameError.Blank)
    val taken = existing.any {
        it.deletedAt == null && it.deckId == deckId && it.id != ownId && normalizeTopicName(it.name) == key
    }
    if (taken) throw TopicNameException(TopicNameError.Duplicate)
}
