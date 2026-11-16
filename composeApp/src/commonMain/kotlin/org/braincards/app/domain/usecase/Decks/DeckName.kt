package org.braincards.app.domain.usecase.Decks

import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.DeckSummary

enum class DeckNameError { Blank, Duplicate }

/**
 * Nombre de mazo no valido. Hereda de IllegalArgumentException para no romper a quien ya capturaba el `require`
 * del caso de uso; la UI mira [reason] (nunca el mensaje).
 */
class DeckNameException(val reason: DeckNameError) : IllegalArgumentException(reason.name)

private val WHITESPACE = Regex("\\s+")

/**
 * Clave de comparacion para DUPLICADOS: trim, espacios internos colapsados a uno y minusculas. Los acentos SI
 * distinguen ("Fisica" != "Fisica con tilde").
 */
fun normalizeDeckName(name: String): String = name.trim().replace(WHITESPACE, " ").lowercase()

private val ACCENT_FOLD = mapOf(
    'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a',
    'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
    'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i',
    'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o',
    'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u',
    'ñ' to 'n', 'ç' to 'c'
)

/** Clave de ORDEN y BUSQUEDA: la de duplicados mas el plegado de acentos (aqui "Inglés" y "ingles" coinciden). */
fun deckSortKey(name: String): String =
    normalizeDeckName(name).map { ACCENT_FOLD[it] ?: it }.joinToString("")

/** ¿El nombre coincide con la busqueda? Sin distinguir mayusculas ni acentos; una busqueda en blanco coincide con todo. */
fun deckMatchesQuery(name: String, query: String): Boolean {
    val q = deckSortKey(query)
    return q.isEmpty() || deckSortKey(name).contains(q)
}

/** Orden fijo de la lista: nombre (clave de orden), luego antiguedad y luego id, para que sea estable con duplicados. */
val deckSummaryComparator: Comparator<DeckSummary> =
    compareBy<DeckSummary> { deckSortKey(it.deck.name) }
        .thenBy { it.deck.createdAt }
        .thenBy { it.deck.id }

/**
 * Valida un nombre contra los mazos vivos locales ([existing]; los borrados no cuentan y sus nombres quedan libres).
 * [ownId] excluye al propio mazo al renombrar. Solo bloquea CREAR un duplicado nuevo: no toca los que ya existan.
 */
fun checkDeckName(name: String, existing: List<Deck>, ownId: String? = null) {
    val key = normalizeDeckName(name)
    if (key.isEmpty()) throw DeckNameException(DeckNameError.Blank)
    val taken = existing.any { it.deletedAt == null && it.id != ownId && normalizeDeckName(it.name) == key }
    if (taken) throw DeckNameException(DeckNameError.Duplicate)
}
