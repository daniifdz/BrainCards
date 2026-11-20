package org.braincards.app.domain.usecase.Decks

import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.SampleDeckStore
import org.braincards.app.domain.sample.SampleDeckContent
import org.braincards.app.domain.sample.SampleDeckNames
import org.braincards.app.domain.util.IdGenerator
import kotlin.time.Duration.Companion.milliseconds

/** [deckId] es el mazo de ejemplo; [created] es `false` si ya existía y no se escribió nada. */
data class SampleDeckResult(val deckId: String, val created: Boolean)

/**
 * Crea el mazo de ejemplo «Primeros pasos» (2 temas, 10 tarjetas) con las mismas reglas que el alta manual: ids nuevos, `userId`
 * y `lastSyncedAt` nulos (los adopta y sube el sync) y sin programación (cada tarjeta es «nueva» y sale pendiente).
 *
 * Si ya hay un mazo vivo con exactamente ese nombre (p. ej. llegó por sincronización desde otro dispositivo) no se duplica: se
 * devuelve el existente. Cada tarjeta se crea 1 ms después de la anterior para que se muestren en el orden del contenido (el
 * orden de las tarjetas es por antigüedad y luego por id, y el id es aleatorio).
 */
class CreateSampleDeckUseCase(
    private val decksRepository: DecksRepository,
    private val store: SampleDeckStore,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(names: SampleDeckNames): SampleDeckResult {
        require(names.topics.size == SampleDeckContent.topics.size) { "un nombre por cada tema del contenido" }

        val existing = decksRepository.getAllDecks().first().firstOrNull { it.deletedAt == null && it.name == names.deck }
        if (existing != null) return SampleDeckResult(existing.id, created = false)

        val now = clock.now()
        val deck = Deck(id = IdGenerator.newId(), name = names.deck, createdAt = now, updatedAt = now, description = null)
        val topics = names.topics.map { name ->
            Topic(id = IdGenerator.newId(), deckId = deck.id, name = name, createdAt = now, updatedAt = now)
        }
        var order = 0L
        val cards = SampleDeckContent.topics.flatMapIndexed { index, topicCards ->
            topicCards.map { card ->
                val at = now + (order++).milliseconds
                Flashcard(
                    id = IdGenerator.newId(),
                    deckId = deck.id,
                    question = card.question,
                    answer = card.answer,
                    createdAt = at,
                    updatedAt = at,
                    cardType = CardType.FLIP,
                    topicId = topics[index].id
                )
            }
        }

        store.insert(deck, topics, cards)
        return SampleDeckResult(deck.id, created = true)
    }
}
