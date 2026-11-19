package org.braincards.app.presentation.screens.study

import androidx.datastore.core.IOException
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
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.models.ReviewQuality.AGAIN
import org.braincards.app.domain.models.ReviewQuality.EASY
import org.braincards.app.domain.models.ReviewQuality.GOOD
import org.braincards.app.domain.models.ReviewQuality.HARD
import org.braincards.app.domain.models.StudyMode
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.domain.repository.CardSchedulingRepository
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.usecase.Flashcards.CardShuffler
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * StudyViewModel en repaso libre: usa todas las tarjetas barajadas, nunca guarda nada (ni programación ni historial), re-encola
 * las falladas, no se reordena al rotar, no pausa la sync y, sin tarjetas, vuelve al origen. Cada test que mira «nada
 * guardado» tiene un control en la sesión normal que sí guarda (para que el espía no pueda estar ciego).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelFreeReviewTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val t0 = Instant.fromEpochMilliseconds(1_000)

    private fun card(id: String, deckId: String = "d1") = Flashcard(
        id = id, deckId = deckId, question = "Q$id", answer = "A$id", createdAt = t0, updatedAt = t0, cardType = CardType.FLIP
    )

    /** Lee las tarjetas por id (las de [deleted] ya no existen) y expone [due] como las "pendientes" de la sesion normal. */
    private class LiveRepo(var cards: List<Flashcard>, var due: List<Flashcard> = emptyList()) :
        FlashcardRepository by FakeFlashcardRepository() {
        val deleted = mutableSetOf<String>()
        override suspend fun getAllDueFlashcards(now: Instant): List<Flashcard> = due
        override suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant): List<Flashcard> = due
        override suspend fun getFlashcardById(id: String): Flashcard? = if (id in deleted) null else cards.firstOrNull { it.id == id }
    }

    /** card_scheduling cuyo upsert FALLA: en la sesion normal darias RateFailed; en libre no debe importar. */
    private class FailingScheduling(val inner: FakeCardSchedulingRepository = FakeCardSchedulingRepository()) :
        CardSchedulingRepository by inner {
        override suspend fun upsert(scheduling: CardScheduling) {
            inner.upsertCalls += scheduling
            throw IOException("disco lleno")
        }
    }

    private inner class Env(
        val cards: List<Flashcard>,
        due: List<Flashcard> = emptyList(),
        shuffler: CardShuffler = CardShuffler { c, _ -> c },
        failingScheduling: Boolean = false
    ) {
        val repo = LiveRepo(cards, due)
        val source = FakeFreeReviewCardsSource(cards)
        val seeds = mutableListOf<Long>()
        val reviewLog = FakeReviewLogRepository()
        private val failing = FailingScheduling()
        val scheduling = FakeCardSchedulingRepository()
        val gate = AutoSyncPauseGate()
        val vm = StudyViewModel(
            getDueFlashcardsUseCase = GetDueFlashcardsUseCase(repo),
            getFreeReviewCardsUseCase = GetFreeReviewCardsUseCase(source, CardShuffler { c, seed ->
                seeds += seed
                shuffler.shuffle(c, seed)
            }),
            scheduledRecorder = ScheduledRecorder(
                ReviewFlashcardUseCase(reviewLog, if (failingScheduling) failing else scheduling)
            ),
            getFlashcardByIdUseCase = GetFlashcardByIdUseCase(repo),
            resolveStudyLabelUseCase = ResolveStudyLabelUseCase(FakeDecksRepository(), FakeTopicsRepository()),
            syncPauseGate = gate
        )
        val state get() = vm.state.value
        val reviewing get() = assertIs<StudyState.Reviewing>(vm.state.value)
        val finished get() = assertIs<StudyState.Finished>(vm.state.value)
        val writes get() = reviewLog.addedLogs.size + scheduling.upsertCalls.size + failing.inner.upsertCalls.size

        fun rate(q: ReviewQuality) {
            vm.showAnswer()
            vm.rateCard(q)
        }
    }

    private fun TestScope.collectExits(vm: StudyViewModel): MutableList<Unit> {
        val seen = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.exitEvent.collect { seen += it } }
        return seen
    }

    /** Historial de TODOS los estados por los que pasa el VM (incluido el inicial). */
    private fun TestScope.collectStates(vm: StudyViewModel): MutableList<StudyState> {
        val seen = mutableListOf<StudyState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
        return seen
    }

    private fun assertNeverShowedASessionScreen(states: List<StudyState>) {
        assertTrue(states.none { it is StudyState.Finished }, "no se pasa por Resultados: $states")
        assertTrue(states.none { it is StudyState.Empty }, "ni por la pantalla de «al dia»: $states")
        assertTrue(states.none { it is StudyState.Reviewing }, "ni se muestra ninguna tarjeta: $states")
    }

    private fun TestScope.collectMessages(vm: StudyViewModel): MutableList<StudyMessage> {
        val seen = mutableListOf<StudyMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messageEvent.collect { seen += it } }
        return seen
    }

    private val free = StudyMode.Free(seed = 7L)

    // ---- la invariante: nunca se guarda nada ----

    @Test
    fun una_sesion_libre_completa_nunca_guarda_programacion_ni_historial() {
        val e = Env(listOf(card("a"), card("b"), card("c")))
        e.vm.initSession(StudyScope.All, free)

        e.rate(AGAIN) // a pasa al final
        e.rate(GOOD)
        e.rate(EASY)
        e.rate(HARD)  // a otra vez

        assertEquals(0, e.writes, "ni una escritura en toda la sesion")
        assertTrue(e.reviewLog.addedLogs.isEmpty())
        assertTrue(e.scheduling.upsertCalls.isEmpty())
        val done = e.finished
        assertEquals(4, done.results.totalReviews, "pero las pulsaciones SI se cuentan en memoria para Resultados")
        assertEquals(1, done.results.countOf(AGAIN))
        assertEquals(true, done.freeReview)
    }

    @Test
    fun CONTROL_la_misma_sesion_en_modo_normal_SI_guarda() {
        val cards = listOf(card("a"), card("b"), card("c"))
        val e = Env(cards, due = cards)
        e.vm.initSession(StudyScope.All, StudyMode.Scheduled)

        e.rate(GOOD)
        e.rate(GOOD)
        e.rate(GOOD)

        assertEquals(3, e.reviewLog.addedLogs.size)
        assertEquals(3, e.scheduling.upsertCalls.size)
        assertEquals(false, e.finished.freeReview)
    }

    @Test
    fun en_libre_un_guardado_que_fallaria_no_importa_no_hay_RateFailed_y_la_tarjeta_avanza() = runTest {
        val e = Env(listOf(card("a"), card("b")), failingScheduling = true)
        val messages = collectMessages(e.vm)
        e.vm.initSession(StudyScope.All, free)

        e.rate(GOOD)

        assertEquals("b", e.reviewing.currentCard.id, "avanza siempre, en el momento")
        assertTrue(messages.isEmpty(), "el error de guardado no aplica al repaso libre")
        assertEquals(0, e.writes)
    }

    // ---- que tarjetas y en que orden ----

    @Test
    fun usa_todas_las_tarjetas_aunque_no_haya_ninguna_pendiente() {
        val e = Env(listOf(card("a"), card("b"), card("c")), due = emptyList())
        e.vm.initSession(StudyScope.All, free)

        val r = e.reviewing
        assertEquals(3, r.remaining)
        assertEquals(3, r.total)
        assertEquals(true, r.freeReview)
    }

    @Test
    fun el_orden_lo_da_el_barajador_inyectado_con_la_semilla_de_la_ruta() {
        val e = Env(listOf(card("a"), card("b"), card("c")), shuffler = { cards, _ -> cards.reversed() })
        e.vm.initSession(StudyScope.All, free)

        assertEquals("c", e.reviewing.currentCard.id)
        assertEquals(listOf(7L), e.seeds)
    }

    @Test
    fun el_ambito_de_un_mazo_pide_solo_las_tarjetas_de_ese_mazo() {
        val e = Env(listOf(card("a", "d1"), card("b", "d2"), card("c", "d1")))
        e.vm.initSession(StudyScope.Deck("d1"), free)

        assertEquals(2, e.reviewing.total)
        assertEquals(listOf("d1"), e.source.getByDeckCalls)
        assertEquals(0, e.source.getAllCalls)
    }

    // ---- reencolado de falladas ----

    @Test
    fun las_falladas_se_reencolan_al_final_sin_cambiar_N_ni_M_ni_la_barra() {
        val e = Env(listOf(card("a"), card("b"), card("c")))
        e.vm.initSession(StudyScope.All, free)

        e.rate(AGAIN)

        val r = e.reviewing
        assertEquals("b", r.currentCard.id)
        assertEquals(3, r.remaining, "no se duplica: N no cambia")
        assertEquals(3, r.total)
        assertEquals(0f, r.progress, "una fallada no hace avanzar la barra")

        e.rate(GOOD)
        e.rate(GOOD)
        assertEquals("a", e.reviewing.currentCard.id, "la fallada vuelve al final de la cola")
        assertEquals(1, e.reviewing.remaining)
        e.rate(GOOD)
        assertEquals(4, e.finished.results.totalReviews, "total = pulsaciones, no tarjetas")
    }

    // ---- rotar ----

    @Test
    fun rotar_no_reinicia_ni_reordena_la_sesion() {
        val e = Env(listOf(card("a"), card("b"), card("c")), shuffler = { cards, _ -> cards.reversed() })
        e.vm.initSession(StudyScope.All, free)
        e.rate(GOOD)
        assertEquals("b", e.reviewing.currentCard.id)

        e.vm.initSession(StudyScope.All, free) // la pantalla se recompone tras rotar

        assertEquals("b", e.reviewing.currentCard.id, "sigue donde estaba")
        assertEquals(1, e.seeds.size, "no vuelve a barajar")
        assertEquals(1, e.source.getAllCalls, "ni a leer las tarjetas")
    }

    @Test
    fun otra_semilla_es_otra_sesion_se_vuelve_a_barajar_desde_el_principio() {
        val e = Env(listOf(card("a"), card("b"), card("c")), shuffler = { cards, seed -> if (seed == 7L) cards else cards.reversed() })
        e.vm.initSession(StudyScope.All, StudyMode.Free(7L))
        e.rate(GOOD)

        e.vm.initSession(StudyScope.All, StudyMode.Free(8L))

        assertEquals(listOf(7L, 8L), e.seeds)
        assertEquals("c", e.reviewing.currentCard.id)
        assertEquals(3, e.reviewing.remaining, "sesion nueva desde el principio")
    }

    @Test
    fun el_mismo_alcance_en_modo_normal_y_en_libre_son_sesiones_distintas() {
        val cards = listOf(card("a"), card("b"))
        val e = Env(cards, due = cards)
        e.vm.initSession(StudyScope.All, StudyMode.Scheduled)
        assertEquals(false, e.reviewing.freeReview)

        e.vm.initSession(StudyScope.All, free)

        assertEquals(true, e.reviewing.freeReview)
    }

    @Test
    fun reintentar_tras_un_error_de_carga_usa_la_misma_semilla() {
        val e = Env(listOf(card("a"), card("b")))
        e.source.failure = IOException("sin disco")
        e.vm.initSession(StudyScope.All, free)
        assertEquals(StudyState.Error, e.state)

        e.source.failure = null
        e.vm.retry()

        assertEquals(listOf(7L), e.seeds, "el fallo ocurrio al LEER, antes de barajar; el reintento baraja con la semilla de la ruta")
        assertEquals("a", e.reviewing.currentCard.id)
    }

    // ---- sync ----

    @Test
    fun el_repaso_libre_no_pausa_la_sync_automatica_y_la_sesion_normal_si() {
        val cards = listOf(card("a"), card("b"))
        val libre = Env(cards)
        libre.vm.initSession(StudyScope.All, free)
        assertEquals(0, libre.gate.activeCount(PauseReason.STUDY), "libre: la sync sigue corriendo")

        val normal = Env(cards, due = cards)
        normal.vm.initSession(StudyScope.All, StudyMode.Scheduled)
        assertEquals(1, normal.gate.activeCount(PauseReason.STUDY), "normal: pausada mientras dura la sesion")
    }

    // ---- sin tarjetas: vuelve al origen ----

    @Test
    fun sin_ninguna_tarjeta_al_cargar_vuelve_al_origen_sin_pantalla_de_Empty() = runTest {
        val e = Env(emptyList())
        val exits = collectExits(e.vm)
        val states = collectStates(e.vm)

        e.vm.initSession(StudyScope.All, free)

        assertEquals(1, exits.size, "pide volver al origen")
        assertNotEquals(StudyState.Empty, e.state, "el repaso libre no tiene pantalla de «al dia»")
        assertNeverShowedASessionScreen(states)
    }

    @Test
    fun la_sesion_normal_sin_pendientes_sigue_mostrando_Empty_y_no_pide_salir() = runTest {
        val e = Env(emptyList(), due = emptyList())
        val exits = collectExits(e.vm)

        e.vm.initSession(StudyScope.All, StudyMode.Scheduled)

        assertEquals(StudyState.Empty, e.state)
        assertTrue(exits.isEmpty())
    }

    @Test
    fun si_todas_las_tarjetas_se_borran_antes_de_puntuar_ninguna_vuelve_al_origen() = runTest {
        val e = Env(listOf(card("a"), card("b")))
        e.repo.deleted += setOf("a", "b") // la fuente aun las listaba, pero ya no existen al mostrarlas
        val exits = collectExits(e.vm)
        val states = collectStates(e.vm)

        e.vm.initSession(StudyScope.All, free)

        assertEquals(1, exits.size)
        assertNotEquals(StudyState.Empty, e.state)
        assertNeverShowedASessionScreen(states)
    }

    @Test
    fun si_las_restantes_se_borran_tras_puntuar_alguna_termina_en_Resultados() = runTest {
        val e = Env(listOf(card("a"), card("b"), card("c")))
        val exits = collectExits(e.vm)
        e.vm.initSession(StudyScope.All, free)

        e.repo.deleted += setOf("b", "c")
        e.rate(GOOD)

        assertEquals(1, e.finished.results.totalReviews)
        assertTrue(exits.isEmpty())
    }

    @Test
    fun una_tarjeta_borrada_durante_la_sesion_se_salta_y_M_baja_en_1() {
        val e = Env(listOf(card("a"), card("b"), card("c")))
        e.vm.initSession(StudyScope.All, free)

        e.repo.deleted += "b"
        e.rate(GOOD)

        val r = e.reviewing
        assertEquals("c", r.currentCard.id, "b se salta sin puntuarla")
        assertEquals(2, r.total)
        assertEquals(1, r.remaining)
    }

    // ---- casos limite ----

    @Test
    fun con_una_sola_tarjeta_Bien_termina_y_Otra_vez_la_mantiene() {
        val e = Env(listOf(card("solo")))
        e.vm.initSession(StudyScope.All, free)
        assertEquals(1, e.reviewing.total)

        e.rate(AGAIN)
        assertEquals("solo", e.reviewing.currentCard.id)
        assertEquals(1, e.reviewing.remaining)

        e.rate(GOOD)
        assertEquals(2, e.finished.results.totalReviews)
        assertEquals(0, e.writes)
    }
}
