package org.braincards.app.presentation.screens.study

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeReviewLogRepository
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.domain.repository.FlashcardRepository
import org.braincards.app.domain.usecase.Flashcards.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardUseCase
import org.braincards.app.domain.usecase.Flashcards.ReviewFlashcardUseCase
import org.braincards.app.presentation.sync.AutoSyncPauseGate
import org.braincards.app.presentation.sync.PauseReason
import org.braincards.app.presentation.sync.STUDY_PAUSE_INACTIVITY_TTL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** StudyViewModel toma la puerta de pausa de la sync automatica al iniciar la sesion y la libera en cada salida. */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelPauseGateTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    private fun card(id: String) = Flashcard(
        id = id, deckId = "deck-1", question = "Q$id", answer = "A$id",
        createdAt = ms(1), updatedAt = ms(1), cardType = CardType.FLIP, userId = "user-1"
    )

    /** FlashcardRepository cuyas tarjetas "debidas" y las del mazo son las que se le dan (el fake estandar no modela "due"). */
    private class CardsRepo(private val cards: List<Flashcard>) : FlashcardRepository by FakeFlashcardRepository() {
        override suspend fun getDueFlashcardsByDeck(deckId: String, now: Instant) = cards
        override suspend fun getAllDueFlashcards(now: Instant) = cards
        override fun getFlashcardsByDeckId(deckId: String): Flow<List<Flashcard>> = flowOf(cards)
    }

    private inner class Env(cards: List<Flashcard>) {
        val time = TestTimeSource()
        val gate = AutoSyncPauseGate(timeSource = time)
        private val repo = CardsRepo(cards)
        private val store = ViewModelStore()
        val vm: StudyViewModel = ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    StudyViewModel(
                        getDueFlashcardsUseCase = GetDueFlashcardsUseCase(repo),
                        reviewFlashcardUseCase = ReviewFlashcardUseCase(FakeReviewLogRepository(), FakeCardSchedulingRepository()),
                        getFlashcardUseCase = GetFlashcardUseCase(repo),
                        syncPauseGate = gate
                    )
                }
            }
        )[StudyViewModel::class]

        /** Destruye el ViewModel (lo que hace la navegacion al salir de la pantalla): invoca onCleared. */
        fun clear() = store.clear()

        val studyPauses get() = gate.activeCount(PauseReason.STUDY)
    }

    // --- toma ---

    @Test
    fun iniciar_una_sesion_toma_la_puerta_mientras_se_estudia() {
        val env = Env(listOf(card("a"), card("b")))

        env.vm.initSession(StudyScope.All)

        assertIs<StudyState.Reviewing>(env.vm.state.value)
        assertEquals(PauseReason.STUDY, env.gate.activeReason())
        assertEquals(1, env.studyPauses)
    }

    @Test
    fun repasar_todo_el_mazo_tambien_toma_la_puerta() {
        val env = Env(listOf(card("a")))

        env.vm.reviewAllCards("deck-1")

        assertIs<StudyState.Reviewing>(env.vm.state.value)
        assertEquals(1, env.studyPauses)
    }

    // --- liberacion en cada camino ---

    @Test
    fun sin_tarjetas_que_estudiar_libera_la_puerta() {
        val env = Env(emptyList())

        env.vm.initSession(StudyScope.Deck("deck-1"))

        assertEquals(StudyState.Empty, env.vm.state.value)
        assertEquals(0, env.studyPauses)
    }

    @Test
    fun repasar_todo_un_mazo_vacio_libera_la_puerta() {
        val env = Env(emptyList())

        env.vm.reviewAllCards("deck-1")

        assertEquals(StudyState.Empty, env.vm.state.value)
        assertEquals(0, env.studyPauses)
    }

    @Test
    fun al_terminar_la_sesion_libera_la_puerta() {
        val env = Env(listOf(card("a"), card("b")))
        env.vm.initSession(StudyScope.All)

        env.vm.rateCard(ReviewQuality.GOOD)
        assertEquals(1, env.studyPauses, "a media sesion sigue en pausa")
        env.vm.rateCard(ReviewQuality.EASY)

        assertIs<StudyState.Finished>(env.vm.state.value)
        assertEquals(0, env.studyPauses)
    }

    @Test
    fun al_destruir_el_viewmodel_a_media_sesion_libera_la_puerta() {
        val env = Env(listOf(card("a"), card("b")))
        env.vm.initSession(StudyScope.All)
        assertEquals(1, env.studyPauses)

        env.clear()

        assertEquals(0, env.studyPauses)
        assertEquals(null, env.gate.activeReason())
    }

    @Test
    fun destruir_un_viewmodel_que_nunca_inicio_sesion_no_falla() {
        val env = Env(listOf(card("a")))

        env.clear()

        assertEquals(0, env.studyPauses)
    }

    @Test
    fun reiniciar_la_sesion_no_acumula_leases() {
        val env = Env(listOf(card("a"), card("b")))

        env.vm.initSession(StudyScope.All)
        env.vm.initSession(StudyScope.All)
        env.vm.reviewAllCards("deck-1")

        assertEquals(1, env.studyPauses)
        env.clear()
        assertEquals(0, env.studyPauses)
    }

    // --- caducidad por inactividad y renovacion con cada tarjeta ---

    @Test
    fun cada_tarjeta_puntuada_renueva_la_pausa_no_es_un_plazo_fijo() {
        val env = Env(listOf(card("a"), card("b"), card("c")))
        env.vm.initSession(StudyScope.All)

        // Cada 20 min se puntua una: 20, 40 min... con un plazo fijo de 30 min la puerta se habria abierto.
        env.time += 20.minutes
        env.vm.rateCard(ReviewQuality.GOOD)
        env.time += 20.minutes

        assertEquals(PauseReason.STUDY, env.gate.activeReason(), "40 min desde el inicio, 20 desde la ultima tarjeta")
        env.vm.rateCard(ReviewQuality.GOOD)
        env.time += 20.minutes
        assertEquals(PauseReason.STUDY, env.gate.activeReason(), "60 min desde el inicio, 20 desde la ultima tarjeta")
    }

    @Test
    fun una_sesion_abandonada_sin_puntuar_deja_de_pausar_tras_la_inactividad() {
        val env = Env(listOf(card("a"), card("b")))
        env.vm.initSession(StudyScope.All)

        env.time += STUDY_PAUSE_INACTIVITY_TTL - 1.seconds
        assertEquals(PauseReason.STUDY, env.gate.activeReason())
        env.time += 2.seconds

        assertEquals(null, env.gate.activeReason())
    }

    @Test
    fun puntuar_tras_caducar_la_pausa_la_reactiva_porque_el_estudio_sigue() {
        val env = Env(listOf(card("a"), card("b")))
        env.vm.initSession(StudyScope.All)
        env.time += STUDY_PAUSE_INACTIVITY_TTL + 5.minutes
        assertEquals(null, env.gate.activeReason())

        env.vm.rateCard(ReviewQuality.GOOD)

        assertEquals(PauseReason.STUDY, env.gate.activeReason())
    }
}
