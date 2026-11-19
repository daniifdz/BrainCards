package org.braincards.app.domain.fakes

import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.RemoteDataSource

/**
 * Fake en memoria de RemoteDataSource, sin tocar Supabase de verdad.
 *
 * - pull{Decks,Topics,Flashcards} devuelven lo precargado en *ToPull, o lanzan la
 *   excepcion de pull*Failure si no es null (para simular que el pull de esa tabla
 *   falla entera).
 * - push{Decks,Topics,Flashcards} lanzan pushFailureFactory() para cada fila cuyo id
 *   este en push*IdsThatFail (para simular fallos parciales fila a fila); el resto se
 *   registra en pushed*Ids.
 * - calls registra, en orden, cada metodo invocado -- util para comprobar el orden
 *   pull-antes-que-push entre tablas.
 */
class FakeRemoteDataSource : RemoteDataSource {

    var decksToPull: List<Deck> = emptyList()
    var topicsToPull: List<Topic> = emptyList()
    var flashcardsToPull: List<Flashcard> = emptyList()

    var pullDecksFailure: Throwable? = null
    var pullTopicsFailure: Throwable? = null
    var pullFlashcardsFailure: Throwable? = null

    var cardSchedulingToPull: List<CardScheduling> = emptyList()
    var pullCardSchedulingFailure: Throwable? = null
    var pushCardSchedulingIdsThatFail: Set<String> = emptySet()
    val pushedCardSchedulingIds = mutableListOf<String>()

    /** Cada llamada a pushCardScheduling (con exito o no), con los cardId que llevaba. */
    val pushCardSchedulingAttempts = mutableListOf<List<String>>()
    var pushCardSchedulingCallFailures: Map<Int, Throwable> = emptyMap()

    var pushDeckIdsThatFail: Set<String> = emptySet()
    var pushTopicIdsThatFail: Set<String> = emptySet()
    var pushFlashcardIdsThatFail: Set<String> = emptySet()
    var pushFailureFactory: () -> Throwable = { IllegalStateException("push simulado fallido") }

    val calls = mutableListOf<String>()
    val pushedDeckIds = mutableListOf<String>()
    val pushedTopicIds = mutableListOf<String>()
    val pushedFlashcardIds = mutableListOf<String>()

    // Lo usa SyncCoordinatorTest para demostrar con un delay real que dos syncNow() concurrentes no se solapan (si el Mutex del
    // coordinador fallara, maxConcurrentPullDecksCalls subiría a más de 1).
    var pullDecksDelayMillis: Long = 0
    private var activePullDecksCalls = 0
    var maxConcurrentPullDecksCalls = 0
        private set

    override suspend fun pullDecks(userId: String, since: Instant): List<Deck> {
        calls.add("pullDecks")
        activePullDecksCalls++
        maxConcurrentPullDecksCalls = maxOf(maxConcurrentPullDecksCalls, activePullDecksCalls)
        try {
            if (pullDecksDelayMillis > 0) delay(pullDecksDelayMillis)
            pullDecksFailure?.let { throw it }
            return decksToPull
        } finally {
            activePullDecksCalls--
        }
    }

    override suspend fun pushDecks(decks: List<Deck>) {
        calls.add("pushDecks")
        decks.forEach { deck ->
            if (deck.id in pushDeckIdsThatFail) throw pushFailureFactory()
            pushedDeckIds.add(deck.id)
        }
    }

    override suspend fun pullTopics(userId: String, since: Instant): List<Topic> {
        calls.add("pullTopics")
        pullTopicsFailure?.let { throw it }
        return topicsToPull
    }

    override suspend fun pushTopics(topics: List<Topic>) {
        calls.add("pushTopics")
        topics.forEach { topic ->
            if (topic.id in pushTopicIdsThatFail) throw pushFailureFactory()
            pushedTopicIds.add(topic.id)
        }
    }

    override suspend fun pullFlashcards(userId: String, since: Instant): List<Flashcard> {
        calls.add("pullFlashcards")
        pullFlashcardsFailure?.let { throw it }
        return flashcardsToPull
    }

    override suspend fun pushFlashcards(flashcards: List<Flashcard>) {
        calls.add("pushFlashcards")
        flashcards.forEach { flashcard ->
            if (flashcard.id in pushFlashcardIdsThatFail) throw pushFailureFactory()
            pushedFlashcardIds.add(flashcard.id)
        }
    }

    // card_scheduling (mismo patrón). pushCardScheduling recibe la lista entera (el lote y el reintento fila a fila son cosa del
    // SyncEngine): si algún cardId está en pushCardSchedulingIdsThatFail lanza antes de registrar nada, como un upsert atómico.
    override suspend fun pullCardScheduling(userId: String, since: Instant): List<CardScheduling> {
        calls.add("pullCardScheduling")
        pullCardSchedulingFailure?.let { throw it }
        return cardSchedulingToPull
    }

    override suspend fun pushCardScheduling(schedulings: List<CardScheduling>) {
        calls.add("pushCardScheduling")
        pushCardSchedulingAttempts.add(schedulings.map { it.cardId })
        // Fallo forzado por numero de llamada (1 = la primera): permite simular, p. ej., un
        // error de red solo en el 2.o lote.
        pushCardSchedulingCallFailures[pushCardSchedulingAttempts.size]?.let { throw it }
        if (schedulings.any { it.cardId in pushCardSchedulingIdsThatFail }) throw pushFailureFactory()
        schedulings.forEach { pushedCardSchedulingIds.add(it.cardId) }
    }
}
