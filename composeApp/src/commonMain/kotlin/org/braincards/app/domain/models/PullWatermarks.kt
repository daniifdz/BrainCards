package org.braincards.app.domain.models

import kotlinx.datetime.Instant

/** Las tablas que se sincronizan; [storageKey] es el nombre estable con el que se guarda su cursor. */
enum class SyncTable(val storageKey: String) {
    DECKS("decks"),
    TOPICS("topics"),
    FLASHCARDS("flashcards"),
    CARD_SCHEDULING("card_scheduling")
}

/**
 * Marca de agua del pull incremental de UN usuario: el cursor de cada tabla que ya tiene uno y la
 * fecha del ultimo pull COMPLETO. Una tabla sin entrada en [cursors] = sin marca = pull completo de
 * esa tabla.
 */
data class PullWatermarks(
    val cursors: Map<SyncTable, PullCursor> = emptyMap(),
    val lastFullPullAt: Instant? = null
)
