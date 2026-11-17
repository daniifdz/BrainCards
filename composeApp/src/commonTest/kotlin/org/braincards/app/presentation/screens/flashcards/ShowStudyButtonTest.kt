package org.braincards.app.presentation.screens.flashcards

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.usecase.Topics.CardHit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regla del boton "Estudiar": solo en el nivel del mazo, con >= 1 tarjeta y sin busqueda activa. */
class ShowStudyButtonTest {

    private val t0 = Instant.fromEpochMilliseconds(1_000)
    private val topic = Topic("t1", "d1", "Verbs", t0, t0)

    private fun root(hasCards: Boolean) =
        DeckContentUiState.Root("Ingles", topics = emptyList(), looseCards = emptyList(), hasCards = hasCards)

    // Mutacion S2: si ignorara hasCards, el siguiente test (Root sin tarjetas) fallaria.
    @Test
    fun en_el_nivel_del_mazo_con_tarjetas_se_muestra() {
        assertTrue(showStudyButton(root(hasCards = true)))
    }

    @Test
    fun en_el_nivel_del_mazo_sin_tarjetas_no_se_muestra() {
        assertFalse(showStudyButton(root(hasCards = false)))
    }

    // Mutacion S1: si Search devolviera true, este test fallaria.
    @Test
    fun con_busqueda_activa_no_se_muestra_ni_con_resultados_ni_sin_ellos() {
        assertFalse(showStudyButton(DeckContentUiState.Search("Ingles", emptyList(), emptyList<CardHit>())))
        assertFalse(showStudyButton(DeckContentUiState.NoResults("Ingles", "zzz")))
    }

    @Test
    fun en_el_subnivel_de_un_tema_y_en_el_mazo_vacio_no_se_muestra() {
        assertFalse(showStudyButton(DeckContentUiState.TopicLevel("Ingles", topic, emptyList())))
        assertFalse(showStudyButton(DeckContentUiState.Empty("Ingles")))
    }

    @Test
    fun cargando_con_error_o_sin_mazo_no_se_muestra() {
        assertFalse(showStudyButton(DeckContentUiState.Loading))
        assertFalse(showStudyButton(DeckContentUiState.Error))
        assertFalse(showStudyButton(DeckContentUiState.DeckGone))
    }
}
