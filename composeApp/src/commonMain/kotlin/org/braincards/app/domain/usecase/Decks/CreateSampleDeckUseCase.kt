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

/** Resultado de crear el mazo de ejemplo. Solo [Created] escribe en la base de datos. */
sealed interface SampleDeckResult {
    /** Hay un mazo de ejemplo listo para abrir (recién creado o ya existente). */
    sealed interface Ready : SampleDeckResult {
        val deckId: String
    }

    /** Se creó el mazo de ejemplo. */
    data class Created(override val deckId: String) : Ready

    /** Ya había un mazo vivo llamado igual que el de ejemplo: no se escribió nada. */
    data class AlreadyExists(override val deckId: String) : Ready

    /** Ya hay mazos vivos (y ninguno es el de ejemplo): no se escribió nada; el ejemplo es solo para el primer uso. */
    data object AlreadyHasDecks : SampleDeckResult
}

/**
 * Crea el mazo de ejemplo «Primeros pasos» (2 temas, 10 tarjetas) con las mismas reglas que el alta manual: ids nuevos, `userId`
 * y `lastSyncedAt` nulos (los adopta y sube el sync) y sin programación (cada tarjeta es «nueva» y sale pendiente).
 *
 * Solo se crea con 0 mazos vivos (los borrados con tombstone no cuentan): si ya hay un mazo vivo con exactamente ese nombre (p. ej.
 * llegó por sincronización desde otro dispositivo) se devuelve el existente ([SampleDeckResult.AlreadyExists]) y, si hay cualquier
 * otro mazo vivo, no se escribe nada ([SampleDeckResult.AlreadyHasDecks]). Así un toque en el botón mientras Home aún no conocía
 * los mazos reales no puede añadir un mazo de ejemplo junto a los del usuario. Cada tarjeta se crea 1 ms después de la anterior para que se muestren en el orden del contenido (el
 * orden de las tarjetas es por antigüedad y luego por id, y el id es aleatorio).
 */
class CreateSampleDeckUseCase(
    private val decksRepository: DecksRepository,
    private val store: SampleDeckStore,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(names: SampleDeckNames): SampleDeckResult {
        require(names.topics.size == SampleDeckContent.topics.size) { "un nombre por cada tema del contenido" }

        val live = decksRepository.getAllDecks().first().filter { it.deletedAt == null }
        live.firstOrNull { it.name == names.deck }?.let { return SampleDeckResult.AlreadyExists(it.id) }
        if (live.isNotEmpty()) return SampleDeckResult.AlreadyHasDecks

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
        return SampleDeckResult.Created(deck.id)
    }
}
