package org.braincards.app.presentation.screens.flashcards

import androidx.datastore.core.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Decks.GetDeckUseCase
import org.braincards.app.domain.usecase.Flashcards.AddFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.DeleteFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.UpdateFlashcardUseCase
import org.braincards.app.domain.usecase.Topics.AddTopicUseCase
import org.braincards.app.domain.usecase.Topics.DeleteTopicUseCase
import org.braincards.app.domain.usecase.Topics.GetTopicsUseCase
import org.braincards.app.domain.usecase.Topics.TopicNameError
import org.braincards.app.domain.usecase.Topics.UpdateTopicUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeckContentViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val t0 = Instant.fromEpochMilliseconds(1_000)

    private fun deck(id: String = "d1", name: String = "Inglés") = Deck(id, name, t0, t0, null)
    private fun topic(id: String, name: String, created: Long = 1_000, deckId: String = "d1") =
        Topic(id, deckId, name, Instant.fromEpochMilliseconds(created), Instant.fromEpochMilliseconds(created))

    private fun card(id: String, topicId: String? = null, created: Long = 1_000, q: String = "Q$id", a: String = "A$id") =
        Flashcard(
            id = id, deckId = "d1", question = q, answer = a, createdAt = Instant.fromEpochMilliseconds(created),
            updatedAt = Instant.fromEpochMilliseconds(created), cardType = CardType.FLIP, topicId = topicId
        )

    /** Fuente controlada a mano: null = aun sin emitir; Result.failure = la lectura falla. */
    private class Manual<T> {
        var subscriptions = 0
        val source = MutableStateFlow<Result<List<T>>?>(null)
        fun flow(): Flow<List<T>> = flow {
            subscriptions++
            source.filterNotNull().collect { emit(it.getOrThrow()) }
        }
    }

    private class ControlledTopics(
        val inner: FakeTopicsRepository = FakeTopicsRepository(),
        val feed: Manual<Topic>
    ) : TopicsRepository by inner {
        var failWrites: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var addCalls = 0

        override fun getTopicsByDeckId(deckId: String): Flow<List<Topic>> = feed.flow()

        override suspend fun addTopic(topic: Topic) {
            addCalls++
            gate?.await()
            failWrites?.let { throw it }
            inner.addTopic(topic)
        }

        override suspend fun deleteTopic(topic: Topic) {
            failWrites?.let { throw it }
            inner.deleteTopic(topic)
        }
    }

    private class ControlledCards(
        val inner: FakeFlashcardRepository = FakeFlashcardRepository(),
        val feed: Manual<Flashcard>
    ) : FlashcardRepository by inner {
        var failWrites: Exception? = null
        val added = mutableListOf<Flashcard>()
        val deleted = mutableListOf<Flashcard>()

        override fun getFlashcardsByDeckId(deckId: String): Flow<List<Flashcard>> = feed.flow()

        override suspend fun addFlashcard(flashcard: Flashcard) {
            failWrites?.let { throw it }
            added += flashcard
        }

        override suspend fun deleteFlashcard(flashcard: Flashcard) {
            failWrites?.let { throw it }
            deleted += flashcard
        }
    }

    private class FixedClock(private val now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class Env(
        val decks: Manual<Deck>,
        val topics: Manual<Topic>,
        val cards: Manual<Flashcard>,
        val topicRepo: ControlledTopics,
        val cardRepo: ControlledCards,
        val vm: DeckContentViewModel
    ) {
        /** Emite mazo + temas + tarjetas y el estado deja de ser Loading. */
        fun load(
            deck: Deck? = Deck("d1", "Inglés", Instant.fromEpochMilliseconds(1_000), Instant.fromEpochMilliseconds(1_000), null),
            topics: List<Topic> = emptyList(),
            cards: List<Flashcard> = emptyList()
        ) {
            this.decks.source.value = Result.success(listOfNotNull(deck))
            this.topics.source.value = Result.success(topics)
            this.cards.source.value = Result.success(cards)
        }
    }

    private fun env(): Env {
        val decks = Manual<Deck>()
        val topics = Manual<Topic>()
        val cards = Manual<Flashcard>()
        val topicRepo = ControlledTopics(feed = topics)
        val cardRepo = ControlledCards(feed = cards)
        val decksRepo = object : DecksRepository by FakeDecksRepository() {
            override fun getAllDecks(): Flow<List<Deck>> = decks.flow()
        }
        val vm = DeckContentViewModel(
            deckId = "d1",
            getDecks = GetDeckUseCase(decksRepo),
            getTopics = GetTopicsUseCase(topicRepo),
            getFlashcards = GetFlashcardUseCase(cardRepo),
            addTopicUseCase = AddTopicUseCase(topicRepo),
            updateTopicUseCase = UpdateTopicUseCase(topicRepo),
            deleteTopicUseCase = DeleteTopicUseCase(topicRepo),
            addFlashcardUseCase = AddFlashcardUseCase(cardRepo),
            updateFlashcardUseCase = UpdateFlashcardUseCase(cardRepo),
            deleteFlashcardUseCase = DeleteFlashcardUseCase(cardRepo),
            clock = FixedClock(Instant.fromEpochMilliseconds(9_000))
        )
        return Env(decks, topics, cards, topicRepo, cardRepo, vm)
    }

    private fun TestScope.collectStates(vm: DeckContentViewModel): List<DeckContentUiState> {
        val seen = mutableListOf<DeckContentUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
        return seen
    }

    private fun TestScope.collectMessages(vm: DeckContentViewModel): List<ContentMessage> {
        val seen = mutableListOf<ContentMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messageEvent.collect { seen += it } }
        return seen
    }

    // ---- estados ----

    @Test
    fun antes_de_la_primera_lectura_es_Loading_y_hasta_que_llegan_las_tres_fuentes() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        assertEquals(listOf<DeckContentUiState>(DeckContentUiState.Loading), seen)

        e.decks.source.value = Result.success(listOf(deck()))
        e.topics.source.value = Result.success(emptyList())
        assertEquals(DeckContentUiState.Loading, seen.last()) // faltan las tarjetas
    }

    @Test
    fun un_mazo_sin_temas_ni_tarjetas_es_Empty_con_el_nombre() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        e.load()
        assertEquals(DeckContentUiState.Empty("Inglés"), seen.last())
    }

    @Test
    fun con_contenido_es_Root_con_temas_ordenados_contador_y_tarjetas_sin_tema() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        e.load(
            topics = listOf(topic("t2", "zeta"), topic("t1", "Árbol")),
            cards = listOf(card("1", "t1"), card("2", "t1"), card("3"))
        )
        val root = assertIs<DeckContentUiState.Root>(seen.last())
        assertEquals(listOf("Árbol", "zeta"), root.topics.map { it.topic.name })
        assertEquals(listOf(2, 0), root.topics.map { it.cardCount })
        assertEquals(listOf("3"), root.looseCards.map { it.id })
        assertTrue(root.hasCards)
    }

    @Test
    fun si_el_mazo_ya_no_existe_es_DeckGone() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        e.load(deck = null, topics = listOf(topic("t1", "A")))
        assertEquals(DeckContentUiState.DeckGone, seen.last())
    }

    @Test
    fun un_fallo_de_lectura_es_Error_y_Reintentar_vuelve_a_suscribir() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        e.decks.source.value = Result.success(listOf(deck()))
        e.topics.source.value = Result.failure(IllegalStateException("boom"))
        e.cards.source.value = Result.success(emptyList())
        assertEquals(DeckContentUiState.Error, seen.last())

        e.topics.source.value = Result.success(emptyList())
        e.vm.retry()

        assertEquals(DeckContentUiState.Empty("Inglés"), seen.last())
        assertTrue(e.topics.subscriptions >= 2)
    }

    // ---- buscador ----

    @Test
    fun el_buscador_da_Search_NoResults_y_vuelve_a_Root() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        e.load(
            topics = listOf(topic("t1", "Verbs")),
            cards = listOf(card("1", "t1", q = "to get along", a = "llevarse bien"), card("2", q = "huge"))
        )

        e.vm.onSearchQueryChange("LLEVARSE")
        val search = assertIs<DeckContentUiState.Search>(seen.last())
        assertEquals(listOf("1"), search.cards.map { it.card.id })
        assertEquals("Verbs", search.cards.single().topicName)

        e.vm.onSearchQueryChange("  zzz ")
        assertEquals(DeckContentUiState.NoResults("Inglés", "zzz"), seen.last())

        e.vm.onSearchQueryChange("")
        assertIs<DeckContentUiState.Root>(seen.last())
    }

    // ---- subnivel ----

    @Test
    fun openTopic_entra_al_subnivel_limpia_la_busqueda_y_closeTopic_vuelve_al_mazo() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        e.load(topics = listOf(topic("t1", "Verbs")), cards = listOf(card("1", "t1"), card("2")))
        e.vm.onSearchQueryChange("verbs")

        e.vm.openTopic("t1")

        val level = assertIs<DeckContentUiState.TopicLevel>(seen.last())
        assertEquals("t1", level.topic.id)
        assertEquals(listOf("1"), level.cards.map { it.id })
        assertEquals("", e.vm.searchQuery.value)

        e.vm.closeTopic()
        assertIs<DeckContentUiState.Root>(seen.last())
    }

    @Test
    fun un_tema_sin_tarjetas_es_TopicLevel_con_lista_vacia() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        e.load(topics = listOf(topic("t1", "Idioms")))

        e.vm.openTopic("t1")

        assertTrue(assertIs<DeckContentUiState.TopicLevel>(seen.last()).cards.isEmpty())
    }

    @Test
    fun si_el_tema_abierto_desaparece_se_vuelve_al_nivel_del_mazo() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        e.load(topics = listOf(topic("t1", "Verbs"), topic("t2", "Nouns")), cards = listOf(card("1", "t1")))
        e.vm.openTopic("t1")
        assertIs<DeckContentUiState.TopicLevel>(seen.last())

        e.topics.source.value = Result.success(listOf(topic("t2", "Nouns")))

        assertIs<DeckContentUiState.Root>(seen.last())
    }

    // ---- escrituras ----

    @Test
    fun crear_un_tema_valido_lo_guarda_recortado_y_llama_a_onSuccess() = runTest {
        val e = env()
        collectStates(e.vm)
        e.load()
        var done = false

        e.vm.createTopic("  Verbs ") { done = true }

        assertTrue(done)
        val saved = e.topicRepo.inner.addedTopics.single()
        assertEquals("Verbs", saved.name)
        assertEquals("d1", saved.deckId)
        assertNull(saved.userId)
        assertFalse(e.vm.saving.value)
        assertNull(e.vm.topicNameError.value)
    }

    @Test
    fun un_nombre_repetido_deja_el_error_de_campo_no_cierra_y_no_guarda() = runTest {
        val e = env()
        collectStates(e.vm)
        e.load(topics = listOf(topic("t1", "Verbs")))
        var done = false

        e.vm.createTopic("verbs") { done = true }

        assertFalse(done)
        assertEquals(TopicNameError.Duplicate, e.vm.topicNameError.value)
        assertTrue(e.topicRepo.inner.addedTopics.isEmpty())
        assertFalse(e.vm.saving.value)

        e.vm.clearErrors()
        assertNull(e.vm.topicNameError.value)
    }

    @Test
    fun un_fallo_de_disco_emite_el_mensaje_y_no_tumba_ni_cierra() = runTest {
        val e = env()
        val messages = collectMessages(e.vm)
        collectStates(e.vm)
        e.load()
        e.topicRepo.failWrites = IOException("disco lleno")
        var done = false

        e.vm.createTopic("Verbs") { done = true }

        assertFalse(done)
        assertEquals(listOf(ContentMessage.SaveTopicFailed), messages)
        assertFalse(e.vm.saving.value)
    }

    @Test
    fun un_toque_repetido_mientras_se_guarda_no_escribe_dos_veces() = runTest {
        val e = env()
        collectStates(e.vm)
        e.load()
        val gate = CompletableDeferred<Unit>()
        e.topicRepo.gate = gate

        e.vm.createTopic("Verbs") {}
        assertTrue(e.vm.saving.value)
        e.vm.createTopic("Nouns") {}
        gate.complete(Unit)

        assertEquals(1, e.topicRepo.addCalls)
        assertFalse(e.vm.saving.value)
    }

    @Test
    fun una_cancelacion_no_es_un_fallo_y_libera_saving() = runTest {
        val e = env()
        val messages = collectMessages(e.vm)
        collectStates(e.vm)
        e.load()
        e.topicRepo.failWrites = CancellationException("cancelada")

        e.vm.createTopic("Verbs") {}

        assertTrue(messages.isEmpty())
        assertFalse(e.vm.saving.value)
    }

    @Test
    fun renombrar_un_tema_conserva_el_resto_y_cambia_updatedAt() = runTest {
        val e = env()
        collectStates(e.vm)
        val original = topic("t1", "phrasal")
        e.load(topics = listOf(original))
        e.topicRepo.inner.addTopic(original)
        var done = false

        e.vm.updateTopic(original, "Phrasal") { done = true }

        assertTrue(done)
        val saved = e.topicRepo.inner.getTopicById("t1")!!
        assertEquals("Phrasal", saved.name)
        assertEquals(original.createdAt, saved.createdAt)
        assertEquals(Instant.fromEpochMilliseconds(9_000), saved.updatedAt)
    }

    @Test
    fun eliminar_un_tema_llama_a_onSuccess_y_un_fallo_emite_DeleteFailed() = runTest {
        val e = env()
        val messages = collectMessages(e.vm)
        collectStates(e.vm)
        val t = topic("t1", "Verbs")
        e.load(topics = listOf(t))
        var done = 0

        e.vm.deleteTopic(t) { done++ }
        assertEquals(1, done)

        e.topicRepo.failWrites = IOException("x")
        e.vm.deleteTopic(t) { done++ }
        assertEquals(1, done)
        assertEquals(listOf(ContentMessage.DeleteFailed), messages)
    }

    @Test
    fun crear_una_tarjeta_flip_dentro_de_un_tema() = runTest {
        val e = env()
        collectStates(e.vm)
        e.load(topics = listOf(topic("t1", "Verbs")))
        var done = false

        e.vm.createCard("to get along", "llevarse bien", "t1") { done = true }

        assertTrue(done)
        val saved = e.cardRepo.added.single()
        assertEquals("t1", saved.topicId)
        assertEquals("d1", saved.deckId)
        assertEquals(CardType.FLIP, saved.cardType)
    }

    @Test
    fun editar_una_tarjeta_puede_cambiarla_de_tema_y_conserva_el_resto() = runTest {
        val e = env()
        collectStates(e.vm)
        val original = card("1", "t1", q = "Q", a = "A")
        e.load(topics = listOf(topic("t1", "A"), topic("t2", "B")), cards = listOf(original))

        e.vm.updateCard(original, "Q2", "A2", "t2") {}

        val saved = e.cardRepo.inner.updateFlashcardCalls.single()
        assertEquals("t2", saved.topicId)
        assertEquals("Q2", saved.question)
        assertEquals("A2", saved.answer)
        assertEquals(original.createdAt, saved.createdAt)
        assertEquals(Instant.fromEpochMilliseconds(9_000), saved.updatedAt)
    }

    @Test
    fun eliminar_una_tarjeta_y_fallo_de_disco() = runTest {
        val e = env()
        val messages = collectMessages(e.vm)
        collectStates(e.vm)
        val c = card("1")
        e.load(cards = listOf(c))
        var done = 0

        e.vm.deleteCard(c) { done++ }
        assertEquals(listOf("1"), e.cardRepo.deleted.map { it.id })

        e.cardRepo.failWrites = IOException("x")
        e.vm.deleteCard(c) { done++ }
        assertEquals(1, done)
        assertEquals(listOf(ContentMessage.DeleteFailed), messages)
    }

    // ---- funcion pura ----

    @Test
    fun buildDeckContentState_un_mazo_borrado_es_DeckGone() {
        val gone = deck().copy(deletedAt = Instant.fromEpochMilliseconds(5))
        assertEquals(DeckContentUiState.DeckGone, buildDeckContentState(gone, emptyList(), emptyList(), null, ""))
        assertEquals(DeckContentUiState.DeckGone, buildDeckContentState(null, emptyList(), emptyList(), null, ""))
    }

    @Test
    fun buildDeckContentState_vacio_gana_a_la_busqueda() {
        val s = buildDeckContentState(deck(), emptyList(), emptyList(), null, "algo")
        assertEquals(DeckContentUiState.Empty("Inglés"), s)
    }
}
