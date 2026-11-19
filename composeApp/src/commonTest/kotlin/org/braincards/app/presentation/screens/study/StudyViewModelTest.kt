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
import org.braincards.app.domain.fakes.FakeFreeReviewCardsSource
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
import org.braincards.app.domain.usecase.Flashcards.GetFreeReviewCardsUseCase
import org.braincards.app.domain.usecase.Flashcards.ResolveStudyLabelUseCase
import org.braincards.app.domain.usecase.Flashcards.ReviewFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.ScheduledRecorder
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

        var lookupFailure: Exception? = null

        override suspend fun getFlashcardById(id: String): Flashcard? {
            lookupFailure?.let { throw it }
            return if (id in deleted) null else due.firstOrNull { it.id == id }
        }
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
        var failure: Exception? = null
        override suspend fun getDeckById(id: String): Deck? {
            calls++
            failure?.let { throw it }
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
            getFreeReviewCardsUseCase = GetFreeReviewCardsUseCase(FakeFreeReviewCardsSource()),
            scheduledRecorder = ScheduledRecorder(ReviewFlashcardUseCase(FakeReviewLogRepository(), scheduling)),
            getFlashcardByIdUseCase = GetFlashcardByIdUseCase(repo),
            resolveStudyLabelUseCase = ResolveStudyLabelUseCase(decks, topics),
            syncPauseGate = gate
        )
        val pauses get() = gate.activeCount(PauseReason.STUDY)
        val state get() = vm.state.value
        val reviewing get() = assertIs<StudyState.Reviewing>(vm.state.value)
        val finished get() = assertIs<StudyState.Finished>(vm.state.value)

        /** Revela la respuesta y puntua (el VM no puntua con la respuesta oculta). */
        fun rate(quality: ReviewQuality) {
            vm.showAnswer()
            vm.rateCard(quality)
        }
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

    @Test
    fun repetir_initSession_con_el_mismo_alcance_no_reinicia_ni_cambia_M() {
        val e = Env(listOf(card("a"), card("b"), card("c")))
        e.vm.initSession(StudyScope.All)
        e.vm.showAnswer()
        e.rate(GOOD) // a sale; ya no es "pendiente" en la base, pero la cola en memoria sigue siendo la de la sesion
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

        e.rate(AGAIN)

        assertEquals("b", e.reviewing.currentCard.id)
        assertEquals(2, e.reviewing.remaining)
        assertEquals(2, e.reviewing.total)
        assertEquals(false, e.reviewing.isAnswerVisible)

        e.rate(GOOD)

        assertEquals("a", e.reviewing.currentCard.id)
        assertEquals(1, e.reviewing.remaining)
        assertEquals(2, e.reviewing.total)
    }

    @Test
    fun el_progreso_avanza_solo_cuando_una_tarjeta_sale() {
        val e = Env(listOf(card("a"), card("b"), card("c")))
        e.vm.initSession(StudyScope.All)

        e.rate(AGAIN)
        assertEquals(0f, e.reviewing.progress)

        e.rate(GOOD)
        assertEquals(1f / 3, e.reviewing.progress)
    }

    // ---- Finished con desglose ----

    @Test
    fun finished_lleva_el_desglose_y_el_total_son_pulsaciones_no_tarjetas() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)

        e.rate(AGAIN)
        e.rate(HARD)
        e.rate(EASY)

        val results = e.finished.results
        assertEquals(1, results.countOf(AGAIN))
        assertEquals(1, results.countOf(HARD))
        assertEquals(0, results.countOf(GOOD))
        assertEquals(1, results.countOf(EASY))
        assertEquals(3, results.totalReviews) // 3 pulsaciones con solo 2 tarjetas
        assertEquals(0, e.pauses)
    }

    // ---- tarjetas borradas ----

    @Test
    fun una_tarjeta_borrada_se_salta_antes_de_mostrarla_y_M_baja_en_1() {
        val e = Env(listOf(card("a"), card("b"), card("c")))
        e.vm.initSession(StudyScope.All)
        e.repo.deleted += "b" // se borra (otro dispositivo) mientras se estudia "a"

        e.rate(GOOD) // a sale; b esta borrada -> se salta; sale c

        val r = e.reviewing
        assertEquals("c", r.currentCard.id)
        assertEquals(1, r.remaining)
        assertEquals(2, r.total)
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

        e.rate(GOOD)

        assertEquals(1, e.finished.results.totalReviews)
    }

    // ---- error al puntuar: no avanza y no cuenta ----

    @Test
    fun si_falla_al_guardar_la_tarjeta_se_queda_la_cola_no_avanza_y_ese_repaso_no_cuenta() = runTest {
        val e = Env(listOf(card("a"), card("b")))
        val messages = collectMessages(e.vm)
        e.vm.initSession(StudyScope.All)
        e.vm.showAnswer()
        e.scheduling.failure = IOException("disco lleno")

        e.rate(GOOD)

        assertEquals(listOf(StudyMessage.RateFailed), messages)
        val r = e.reviewing
        assertEquals("a", r.currentCard.id) // la misma tarjeta, sigue en pantalla
        assertEquals(2, r.remaining)        // la cola no avanza
        assertEquals(true, r.isAnswerVisible)

        // Se reintenta con exito: el repaso fallido NO cuenta, solo el guardado.
        e.scheduling.failure = null
        e.rate(GOOD)
        assertEquals("b", e.reviewing.currentCard.id) // no se salto b
        e.rate(GOOD)
        assertEquals(2, e.finished.results.totalReviews) // 2 guardados, no 3
        assertEquals(2, e.finished.results.countOf(GOOD))
    }

    @Test
    fun tras_un_fallo_se_libera_isSubmitting_y_se_puede_volver_a_puntuar() = runTest {
        val e = Env(listOf(card("a")))
        e.vm.initSession(StudyScope.All)
        e.scheduling.failure = IOException("x")
        e.rate(GOOD)

        e.scheduling.failure = null
        e.rate(GOOD)

        assertEquals(1, e.finished.results.totalReviews)
    }

    @Test
    fun una_cancelacion_no_es_un_fallo_ni_emite_mensaje_y_libera_isSubmitting() = runTest {
        val e = Env(listOf(card("a")))
        val messages = collectMessages(e.vm)
        e.vm.initSession(StudyScope.All)
        e.scheduling.failure = CancellationException("cancelada")
        e.rate(GOOD)

        assertTrue(messages.isEmpty())
        e.scheduling.failure = null
        e.rate(GOOD)

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
        e.rate(GOOD)
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

        e.rate(GOOD)

        assertNull(e.reviewing.label) // el mazo de "b" no existe
    }

    // ---- varios ----

    @Test
    fun puntuar_sin_tarjeta_en_pantalla_no_hace_nada() {
        val e = Env(listOf(card("a")))

        e.rate(GOOD) // aun Loading: sin sesion

        assertEquals(StudyState.Loading, e.state)
        assertEquals(0, e.scheduling.inner.upsertCalls.size)
    }

    @Test
    fun mostrar_la_respuesta_solo_afecta_a_la_tarjeta_actual_y_al_puntuar_se_oculta() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)

        e.vm.showAnswer()
        assertEquals(true, e.reviewing.isAnswerVisible)

        e.rate(GOOD)
        assertEquals(false, e.reviewing.isAnswerVisible)
    }

    // ---- segunda barrera: no se puntua con la respuesta oculta ----

    @Test
    fun puntuar_con_la_respuesta_oculta_no_hace_nada() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)
        val before = e.reviewing

        e.vm.rateCard(GOOD)

        assertEquals(before, e.state)
        assertEquals(0, e.scheduling.inner.upsertCalls.size)
        assertEquals(2, e.reviewing.remaining)
    }

    @Test
    fun una_vez_revelada_la_respuesta_si_se_puede_puntuar() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)

        e.vm.showAnswer()
        e.vm.rateCard(GOOD)

        assertEquals("b", e.reviewing.currentCard.id)
        assertEquals(1, e.scheduling.inner.upsertCalls.size)
    }
    // ---- etiqueta de Resultados (7.3) ----

    @Test
    fun finished_de_una_sesion_de_un_mazo_lleva_solo_el_nombre_del_mazo() = runTest {
        val e = Env(listOf(card("a", topicId = "t1")))
        e.decks.inner.addDeck(Deck("d1", "Inglés B2", t0, t0, null))
        e.topics.inner.addTopic(Topic("t1", "d1", "Phrasal verbs", t0, t0))
        e.vm.initSession(StudyScope.Deck("d1"))

        e.rate(GOOD)

        assertEquals(StudyResultsLabel.DeckName("Inglés B2"), e.finished.label) // sin el tema
    }

    @Test
    fun finished_de_la_sesion_global_es_Estudiar_Todo() = runTest {
        val e = Env(listOf(card("a")))
        e.decks.inner.addDeck(Deck("d1", "Inglés B2", t0, t0, null))
        e.vm.initSession(StudyScope.All)

        e.rate(GOOD)

        assertEquals(StudyResultsLabel.AllDecks, e.finished.label)
    }

    @Test
    fun finished_de_un_mazo_que_ya_no_existe_no_lleva_etiqueta_pero_sigue_siendo_valido() = runTest {
        val e = Env(listOf(card("a")))
        e.vm.initSession(StudyScope.Deck("d1")) // el mazo no esta en el repositorio

        e.rate(GOOD)

        assertNull(e.finished.label)
        assertEquals(1, e.finished.results.totalReviews)
    }

    @Test
    fun un_fallo_al_leer_el_nombre_del_mazo_no_impide_mostrar_Resultados() = runTest {
        val e = Env(listOf(card("a")))
        e.decks.inner.addDeck(Deck("zz", "Otro", t0, t0, null))
        // Alcance "zz": su nombre no esta en cache (las tarjetas son de "d1"), asi que se lee al terminar.
        e.vm.initSession(StudyScope.Deck("zz"))
        e.decks.failure = IOException("boom")

        e.rate(GOOD)

        assertNull(e.finished.label)
        assertEquals(1, e.finished.results.totalReviews)
        assertEquals(0, e.pauses)
    }
    // ---- deuda (e): el mazo de la tarjeta se borra (por sync) mientras se estudia ----
    // ReviewFlashcardUseCase NO consulta getFlashcardById: recibe la tarjeta que se muestra. Solo la sesion la consulta,
    // ANTES de mostrar cada tarjeta; si devuelve null (borrada o de un mazo borrado) se salta, y un fallo de lectura acaba
    // en el estado Error ya existente. Nunca en una excepcion sin capturar.

    @Test
    fun si_el_mazo_de_la_tarjeta_en_pantalla_se_borra_justo_antes_de_puntuar_el_repaso_se_guarda_y_la_sesion_sigue() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)
        e.repo.deleted += "a" // su mazo se borra mientras "a" esta en pantalla: getFlashcardById("a") ya da null

        e.rate(GOOD)

        assertEquals(1, e.scheduling.inner.upsertCalls.size) // el repaso de "a" se guardo (no depende de getFlashcardById)
        assertEquals("b", e.reviewing.currentCard.id)
        assertEquals(2, e.reviewing.total)
    }

    @Test
    fun si_se_borra_el_mazo_de_la_ultima_tarjeta_en_pantalla_antes_de_puntuar_termina_con_su_repaso_contado() {
        val e = Env(listOf(card("a")))
        e.vm.initSession(StudyScope.All)
        e.repo.deleted += "a"

        e.rate(GOOD)

        assertEquals(1, e.finished.results.totalReviews)
        assertEquals(0, e.pauses)
    }

    @Test
    fun si_la_comprobacion_de_la_siguiente_tarjeta_falla_la_sesion_acaba_en_Error_y_no_revienta() {
        val e = Env(listOf(card("a"), card("b")))
        e.vm.initSession(StudyScope.All)
        e.repo.lookupFailure = IOException("disco")

        e.rate(GOOD)

        assertEquals(StudyState.Error, e.state)
        assertEquals(1, e.scheduling.inner.upsertCalls.size) // el repaso ya estaba guardado
        assertEquals(0, e.pauses)

        e.repo.lookupFailure = null
        e.vm.retry()
        assertIs<StudyState.Reviewing>(e.state)
    }
}
