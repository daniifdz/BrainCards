package org.braincards.app.presentation.screens.study

import androidx.datastore.core.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeReviewLogRepository
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.models.ReviewQuality.AGAIN
import org.braincards.app.domain.models.ReviewQuality.EASY
import org.braincards.app.domain.models.ReviewQuality.GOOD
import org.braincards.app.domain.models.ReviewQuality.HARD
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.repository.TopicsRepository
import org.braincards.app.domain.usecase.Flashcards.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardByIdUseCase
import org.braincards.app.domain.usecase.Flashcards.ResolveStudyLabelUseCase
import org.braincards.app.domain.usecase.Flashcards.ReviewFlashcardUseCase
import org.braincards.app.presentation.sync.AutoSyncPauseGate
import org.braincards.app.presentation.sync.PauseReason
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** StudyViewModel: inicio idempotente, cola con re-encolado, tarjetas borradas, fallos al guardar y etiqueta. */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val t0 = Instant.fromEpochMilliseconds(1_000)

    private fun card(id: String, topicId: String? = null, deckId: String = "d1") = Flashcard(
        id = id, deckId = deckId, question = "Q$id", answer = "A$id", createdAt = t0, updatedAt = t0,
        cardType = CardType.FLIP, topicId = topicId, userId = "user-1"
    )

    /** Tarjetas pendientes controladas a mano; las de [deleted] ya no existen para getFlashcardById. */
    private class CardsRepo(var due: List<Flashcard>) : FlashcardRepository by FakeFlashcardRepository() {
        val deleted = mutableSetOf<String>()
        var dueCalls = 0
        var dueFailure: Exception? = null

        override suspend fun getAllDueFlashcards(now: Instant): List<Flashcard> {
            dueCalls++
            dueFailure?.let { throw it }
            return due
        }

        override suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant): List<Flashcard> = getAllDueFlashcards(now)

        override suspend fun getFlashcardById(id: String): Flashcard? =
            if (id in deleted) null else due.firstOrNull { it.id == id }
    }

    /** card_scheduling cuyo upsert puede fallar (el repaso no se guarda). */
    private class FlakyScheduling(val inner: FakeCardSchedulingRepository = FakeCardSchedulingRepository()) :
        CardSchedulingRepository by inner {
        var failure: Exception? = null
        override suspend fun upsert(scheduling: CardScheduling) {
            failure?.let { throw it }
            inner.upsert(scheduling)
        }
    }

    private class CountingDecks(val inner: FakeDecksRepository = FakeDecksRepository()) : DecksRepository by inner {
        var calls = 0
        override suspend fun getDeckById(id: String): Deck? {
            calls++
            return inner.getDeckById(id)
        }
    }

    private class CountingTopics(val inner: FakeTopicsRepository = FakeTopicsRepository()) : TopicsRepository by inner {
        var calls = 0
        override suspend fun getTopicById(id: String): Topic? {
            calls++
            return inner.getTopicById(id)
        }
    }

    private inner class Env(due: List<Flashcard>) {
        val repo = CardsRepo(due)
        val scheduling = FlakyScheduling()
        val decks = CountingDecks()
        val topics = CountingTopics()
        val gate = AutoSyncPauseGate()
        val vm = StudyViewModel(
            getDueFlashcardsUseCase = GetDueFlashcardsUseCase(repo),
            reviewFlashcardUseCase = ReviewFlashcardUseCase(FakeReviewLogRepository(), scheduling),
            getFlashcardByIdUseCase = GetFlashcardByIdUseCase(repo),
            resolveStudyLabelUseCase = ResolveStudyLabelUseCase(decks, topics),
            syncPauseGate = gate
        )
        val pauses get() = gate.activeCount(PauseReason.STUDY)
        val state get() = vm.state.value
        val reviewing get() = assertIs<StudyState.Reviewing>(vm.state.value)
        val finished get() = assertIs<StudyState.Finished>(vm.state.value)
    }

    private fun TestScope.collectMessages(vm: StudyViewModel): List<StudyMessage> {
        val seen = mutableListOf<StudyMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messageEvent.collect { seen += it } }
        return seen
    }

    // ---- inicio ----

    @Test
    fun iniciar_muestra_la_primera_tarjeta_con_N_igual_a_M_y_sin_respuesta() {
        val e = Env(listOf(card("a"), card("b"), card("c")))

        e.vm.initSession(StudyScope.All)

        val r = e.reviewing
        assertEquals("a", r.currentCard.id)
        assertEquals(3, r.remaining)
        assertEquals(3, r.total)
        assertEquals(false, r.isAnswerVisible)
        assertEquals(0f, r.progress)
    }

    @Test
    fun sin_tarjetas_pendientes_es_Empty_y_libera_la_pausa() {
        val e = Env(emptyList())

        e.vm.initSession(StudyScope.All)

        assertEquals(StudyState.Empty, e.state)
        assertEquals(0, e.pauses)
    }

    // ---- inicio idempotente (rotacion) ----

    // Mutacion V1: si initSession no tuviera el guard por alcance, este test fallaria (la cola se re-leeria y M cambiaria).
    @Test
    fun repetir_initSession_con_el_mismo_alcance_no_reinicia_ni_cambia_M() {
        val e = Env(listOf(card("a"), card("b"), card("c")))
        e.vm.initSession(StudyScope.All)
        e.vm.showAnswer()
        e.vm.rateCard(GOOD) // a sale; ya no es "pendiente" en la base, pero la cola en memoria sigue siendo la de la sesion
        e.repo.due = listOf(card("b"), card("c")) // lo que devolveria ahora la consulta (a ya esta puntuada)
        val before = e.reviewing

        e.vm.initSession(StudyScope.All) // la pantalla se recompone tras rotar

        assertEquals(1, e.repo.dueCalls)
        assertEquals(before, e.state)
        assertEquals(2, e.reviewing.remaining)
        assertEquals(3, e.reviewing.total) // M no cambia
        assertEquals(1, e.pauses) // y no acumula leases
    }

    @Test
    fun la_respuesta_visible_sobrevive_a_repetir_initSession() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)
        e.vm.showAnswer()

        e.vm.initSession(StudyScope.All)

        assertEquals(true, e.reviewing.isAnswerVisible)
    }

    @Test
    fun un_alcance_distinto_si_reinicia_la_sesion() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)

        e.vm.initSession(StudyScope.Deck("d1"))

        assertEquals(2, e.repo.dueCalls)
        assertEquals(1, e.pauses)
    }

    // ---- fallar y reaparecer ----

    @Test
    fun una_fallada_reaparece_al_final_sin_cambiar_M_ni_N() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)

        e.vm.rateCard(AGAIN)

        assertEquals("b", e.reviewing.currentCard.id)
        assertEquals(2, e.reviewing.remaining)
        assertEquals(2, e.reviewing.total)
        assertEquals(false, e.reviewing.isAnswerVisible)

        e.vm.rateCard(GOOD) // b

        assertEquals("a", e.reviewing.currentCard.id) // reaparece
        assertEquals(1, e.reviewing.remaining)
        assertEquals(2, e.reviewing.total)
    }

    @Test
    fun el_progreso_avanza_solo_cuando_una_tarjeta_sale() {
        val e = Env(listOf(card("a"), card("b"), card("c")))
        e.vm.initSession(StudyScope.All)

        e.vm.rateCard(AGAIN)
        assertEquals(0f, e.reviewing.progress)

        e.vm.rateCard(GOOD)
        assertEquals(1f / 3, e.reviewing.progress)
    }

    // ---- Finished con desglose ----

    // Mutacion Q2 (en la cola) tambien se ve aqui: el desglose y el total salen de las pulsaciones.
    @Test
    fun finished_lleva_el_desglose_y_el_total_son_pulsaciones_no_tarjetas() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)

        e.vm.rateCard(AGAIN) // a vuelve
        e.vm.rateCard(HARD)  // b
        e.vm.rateCard(EASY)  // a

        val results = e.finished.results
        assertEquals(1, results.countOf(AGAIN))
        assertEquals(1, results.countOf(HARD))
        assertEquals(0, results.countOf(GOOD))
        assertEquals(1, results.countOf(EASY))
        assertEquals(3, results.totalReviews) // 3 pulsaciones con solo 2 tarjetas
        assertEquals(0, e.pauses)
    }

    // ---- tarjetas borradas ----

    // Mutacion Q3 (en la cola): si M no bajara al saltar, este test fallaria.
    @Test
    fun una_tarjeta_borrada_se_salta_antes_de_mostrarla_y_M_baja_en_1() {
        val e = Env(listOf(card("a"), card("b"), card("c")))
        e.vm.initSession(StudyScope.All)
        e.repo.deleted += "b" // se borra (otro dispositivo) mientras se estudia "a"

        e.vm.rateCard(GOOD) // a sale; b esta borrada -> se salta; sale c

        val r = e.reviewing
        assertEquals("c", r.currentCard.id)
        assertEquals(1, r.remaining)
        assertEquals(2, r.total) // 3 - 1
    }

    @Test
    fun una_borrada_al_empezar_tambien_se_salta() {
        val e = Env(listOf(card("a"), card("b")))
        e.repo.deleted += "a"

        e.vm.initSession(StudyScope.All)

        assertEquals("b", e.reviewing.currentCard.id)
        assertEquals(1, e.reviewing.remaining)
        assertEquals(1, e.reviewing.total)
    }

    @Test
    fun si_todas_estan_borradas_es_Empty() {
        val e = Env(listOf(card("a"), card("b")))
        e.repo.deleted += setOf("a", "b")

        e.vm.initSession(StudyScope.All)

        assertEquals(StudyState.Empty, e.state)
        assertEquals(0, e.pauses)
    }

    @Test
    fun si_se_borra_la_que_quedaba_tras_puntuar_termina_con_lo_puntuado() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)
        e.repo.deleted += "b"

        e.vm.rateCard(GOOD)

        assertEquals(1, e.finished.results.totalReviews)
    }

    // ---- error al puntuar (v4.18): no avanza y NO cuenta ----

    // Mutacion V3: si el repaso se contara ANTES de guardarlo, este test fallaria.
    @Test
    fun si_falla_al_guardar_la_tarjeta_se_queda_la_cola_no_avanza_y_ese_repaso_no_cuenta() = runTest {
        val e = Env(listOf(card("a"), card("b")))
        val messages = collectMessages(e.vm)
        e.vm.initSession(StudyScope.All)
        e.vm.showAnswer()
        e.scheduling.failure = IOException("disco lleno")

        e.vm.rateCard(GOOD)

        assertEquals(listOf(StudyMessage.RateFailed), messages)
        val r = e.reviewing
        assertEquals("a", r.currentCard.id) // la misma tarjeta, sigue en pantalla
        assertEquals(2, r.remaining)        // la cola no avanza
        assertEquals(true, r.isAnswerVisible)

        // Se reintenta con exito: el repaso fallido NO cuenta, solo el guardado.
        e.scheduling.failure = null
        e.vm.rateCard(GOOD) // a
        assertEquals("b", e.reviewing.currentCard.id) // no se salto b
        e.vm.rateCard(GOOD) // b
        assertEquals(2, e.finished.results.totalReviews) // 2 guardados, no 3
        assertEquals(2, e.finished.results.countOf(GOOD))
    }

    // Mutacion V2: si no se liberara isSubmitting tras el fallo, el reintento seria ignorado y este test fallaria.
    @Test
    fun tras_un_fallo_se_libera_isSubmitting_y_se_puede_volver_a_puntuar() = runTest {
        val e = Env(listOf(card("a")))
        e.vm.initSession(StudyScope.All)
        e.scheduling.failure = IOException("x")
        e.vm.rateCard(GOOD)

        e.scheduling.failure = null
        e.vm.rateCard(GOOD)

        assertEquals(1, e.finished.results.totalReviews)
    }

    @Test
    fun una_cancelacion_no_es_un_fallo_ni_emite_mensaje_y_libera_isSubmitting() = runTest {
        val e = Env(listOf(card("a")))
        val messages = collectMessages(e.vm)
        e.vm.initSession(StudyScope.All)
        e.scheduling.failure = CancellationException("cancelada")
        e.vm.rateCard(GOOD)

        assertTrue(messages.isEmpty())
        e.scheduling.failure = null
        e.vm.rateCard(GOOD)

        assertEquals(1, e.finished.results.totalReviews)
    }

    // ---- error al cargar ----

    @Test
    fun un_fallo_al_cargar_es_Error_libera_la_pausa_y_Reintentar_vuelve_a_cargar() {
        val e = Env(listOf(card("a")))
        e.repo.dueFailure = IOException("boom")

        e.vm.initSession(StudyScope.All)

        assertEquals(StudyState.Error, e.state)
        assertEquals(0, e.pauses)

        e.repo.dueFailure = null
        e.vm.retry()

        assertEquals("a", e.reviewing.currentCard.id)
        assertEquals(1, e.pauses)
    }

    @Test
    fun repetir_initSession_estando_en_Error_no_reintenta_solo() {
        val e = Env(listOf(card("a")))
        e.repo.dueFailure = IOException("boom")
        e.vm.initSession(StudyScope.All)
        e.repo.dueFailure = null

        e.vm.initSession(StudyScope.All)

        assertEquals(StudyState.Error, e.state) // solo "Reintentar" reabre
        assertEquals(1, e.repo.dueCalls)
    }

    // ---- etiqueta Mazo · Tema ----

    @Test
    fun la_etiqueta_lleva_el_nombre_del_mazo_y_del_tema_y_se_cachea() = runTest {
        val e = Env(listOf(card("a", topicId = "t1"), card("b", topicId = "t1")))
        e.decks.inner.addDeck(Deck("d1", "Inglés B2", t0, t0, null))
        e.topics.inner.addTopic(Topic("t1", "d1", "Phrasal verbs", t0, t0))

        e.vm.initSession(StudyScope.All)
        val first = e.reviewing.label
        e.vm.rateCard(GOOD)
        val second = e.reviewing.label

        assertEquals("Inglés B2", first?.deckName)
        assertEquals("Phrasal verbs", first?.topicName)
        assertEquals(first, second)
        assertEquals(1, e.decks.calls) // una sola consulta por mazo
        assertEquals(1, e.topics.calls) // y por tema
    }

    @Test
    fun una_tarjeta_sin_tema_lleva_solo_el_mazo_y_sin_mazo_no_hay_etiqueta() = runTest {
        val e = Env(listOf(card("a"), card("b", deckId = "huerfano")))
        e.decks.inner.addDeck(Deck("d1", "Inglés B2", t0, t0, null))
        e.vm.initSession(StudyScope.All)

        assertEquals("Inglés B2", e.reviewing.label?.deckName)
        assertNull(e.reviewing.label?.topicName)

        e.vm.rateCard(GOOD)

        assertNull(e.reviewing.label) // el mazo de "b" no existe
    }

    // ---- varios ----

    @Test
    fun puntuar_sin_tarjeta_en_pantalla_no_hace_nada() {
        val e = Env(listOf(card("a")))

        e.vm.rateCard(GOOD) // aun Loading: sin sesion

        assertEquals(StudyState.Loading, e.state)
        assertEquals(0, e.scheduling.inner.upsertCalls.size)
    }

    @Test
    fun mostrar_la_respuesta_solo_afecta_a_la_tarjeta_actual_y_al_puntuar_se_oculta() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)

        e.vm.showAnswer()
        assertEquals(true, e.reviewing.isAnswerVisible)

        e.vm.rateCard(GOOD)
        assertEquals(false, e.reviewing.isAnswerVisible)
    }
}
