package org.braincards.app.presentation.screens.study

import org.braincards.app.domain.models.StudyScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Origen (boton "Volver al inicio" / "Volver al mazo") y etiqueta de Resultados por alcance de la sesion. */
class StudyResultsTest {

    // Mutacion O1: si se intercambiaran los alcances (All -> Deck, Deck -> Home), estos tests fallarian.
    @Test
    fun la_sesion_global_viene_de_Home() {
        assertEquals(StudyOrigin.Home, studyOrigin(StudyScope.All))
    }

    @Test
    fun la_sesion_de_un_mazo_viene_de_ese_mazo() {
        assertEquals(StudyOrigin.Deck, studyOrigin(StudyScope.Deck("d1")))
    }

    // Mutacion L1: si la etiqueta de la sesion global dependiera del nombre o se intercambiara, estos tests fallarian.
    @Test
    fun la_sesion_global_se_etiqueta_Estudiar_Todo_aunque_haya_un_nombre() {
        assertEquals(StudyResultsLabel.AllDecks, resultsLabel(StudyScope.All, deckName = null))
        assertEquals(StudyResultsLabel.AllDecks, resultsLabel(StudyScope.All, deckName = "Inglés B2"))
    }

    @Test
    fun la_sesion_de_un_mazo_lleva_solo_el_nombre_del_mazo() {
        assertEquals(StudyResultsLabel.DeckName("Inglés B2"), resultsLabel(StudyScope.Deck("d1"), "Inglés B2"))
    }

    @Test
    fun si_el_nombre_del_mazo_no_se_pudo_resolver_no_hay_etiqueta() {
        assertNull(resultsLabel(StudyScope.Deck("d1"), deckName = null))
    }
}
