package org.braincards.app.presentation.screens.study

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Topic
import org.braincards.app.presentation.components.EMPTY_FREE_REVIEW_BUTTON
import org.braincards.app.presentation.components.GhostButtonMetrics
import org.braincards.app.presentation.components.HOME_FREE_REVIEW_BUTTON
import org.braincards.app.presentation.screens.flashcards.DeckContentUiState
import org.braincards.app.presentation.screens.flashcards.showFreeReviewButton
import org.braincards.app.presentation.screens.flashcards.showStudyButton
import org.braincards.app.presentation.screens.home.HomeUiState
import org.braincards.app.presentation.screens.home.homeShowsFreeReview
import org.braincards.app.domain.usecase.Topics.CardHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Que entrada al repaso libre se muestra en cada pantalla (handoff v4.25): decisiones puras. */
class FreeReviewEntriesTest {

    private val t0 = Instant.fromEpochMilliseconds(1_000)
    private val topic = Topic("t1", "d1", "Verbs", t0, t0)
    private fun root(hasCards: Boolean) =
        DeckContentUiState.Root("Ingles", topics = emptyList(), looseCards = emptyList(), hasCards = hasCards)

    // ---- Contenido de un mazo: la misma condicion que «Estudiar» ----

    @Test
    fun el_boton_del_mazo_sale_con_tarjetas_en_el_nivel_del_mazo() {
        assertTrue(showFreeReviewButton(root(hasCards = true)))
    }

    @Test
    fun el_boton_del_mazo_se_oculta_con_0_tarjetas() {
        assertFalse(showFreeReviewButton(root(hasCards = false)))
        assertFalse(showFreeReviewButton(DeckContentUiState.Empty("Ingles")), "mazo vacio: se ocultan los dos botones a la vez")
    }

    @Test
    fun el_boton_del_mazo_se_oculta_en_busqueda_y_en_el_subnivel() {
        assertFalse(showFreeReviewButton(DeckContentUiState.Search("Ingles", emptyList(), emptyList<CardHit>())))
        assertFalse(showFreeReviewButton(DeckContentUiState.NoResults("Ingles", "zzz")))
        assertFalse(showFreeReviewButton(DeckContentUiState.TopicLevel("Ingles", topic, emptyList())))
    }

    @Test
    fun el_boton_del_mazo_se_oculta_cargando_con_error_o_sin_mazo() {
        assertFalse(showFreeReviewButton(DeckContentUiState.Loading))
        assertFalse(showFreeReviewButton(DeckContentUiState.Error))
        assertFalse(showFreeReviewButton(DeckContentUiState.DeckGone))
    }

    @Test
    fun repaso_libre_y_estudiar_se_muestran_o_se_ocultan_siempre_a_la_vez() {
        val states = listOf(
            root(true), root(false), DeckContentUiState.Empty("x"), DeckContentUiState.Loading, DeckContentUiState.Error,
            DeckContentUiState.DeckGone, DeckContentUiState.NoResults("x", "q"), DeckContentUiState.TopicLevel("x", topic, emptyList()),
            DeckContentUiState.Search("x", emptyList(), emptyList<CardHit>())
        )
        for (s in states) assertEquals(showStudyButton(s), showFreeReviewButton(s), "$s")
    }

    // ---- Home: solo «Todo al dia» ----

    @Test
    fun home_ofrece_el_repaso_libre_solo_en_Todo_al_dia() {
        assertTrue(homeShowsFreeReview(HomeUiState.Empty))
    }

    @Test
    fun home_no_lo_ofrece_con_pendientes_ni_en_primer_uso_ni_sin_tarjetas_ni_cargando_ni_con_error() {
        assertFalse(homeShowsFreeReview(HomeUiState.Content(dueCount = 7)), "con pendientes: «Estudiar Todo» ya es la accion")
        assertFalse(homeShowsFreeReview(HomeUiState.Content(dueCount = 1)))
        assertFalse(homeShowsFreeReview(HomeUiState.NoDecks), "0 mazos: nada que repasar")
        assertFalse(homeShowsFreeReview(HomeUiState.NoCards), "mazos sin tarjetas: nada que repasar")
        assertFalse(homeShowsFreeReview(HomeUiState.Loading))
        assertFalse(homeShowsFreeReview(HomeUiState.Error))
    }

    // ---- vacio de Estudio: siempre, con cualquier origen ----

    @Test
    fun el_vacio_de_Estudio_siempre_ofrece_el_repaso_libre_con_cualquier_origen() {
        assertTrue(studyEmptyShowsFreeReview(StudyOrigin.Home))
        assertTrue(studyEmptyShowsFreeReview(StudyOrigin.Deck))
    }

    // ---- medidas de los mockups ----

    @Test
    fun el_boton_ghost_de_Home_mide_lo_que_dice_home_states() {
        assertEquals(GhostButtonMetrics(iconDp = 18, fontSp = 14.5f, gapDp = 8, horizontalDp = 4, verticalDp = 10), HOME_FREE_REVIEW_BUTTON)
    }

    @Test
    fun el_boton_ghost_del_vacio_de_Estudio_mide_lo_que_dice_estudio_vacio() {
        assertEquals(GhostButtonMetrics(iconDp = 15, fontSp = 13.5f, gapDp = 7, horizontalDp = 6, verticalDp = 6), EMPTY_FREE_REVIEW_BUTTON)
    }
}
