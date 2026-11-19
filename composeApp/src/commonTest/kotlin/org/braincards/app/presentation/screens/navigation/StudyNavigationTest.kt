package org.braincards.app.presentation.screens.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Cada puerta de entrada al estudio: la normal sigue con freeReview=false; las de repaso libre llevan freeReview=true y LA semilla del punto de navegacion. */
class StudyNavigationTest {

    private val seed = 987_654_321_012L

    @Test
    fun estudiar_un_mazo_es_sesion_normal_sin_semilla_y_sin_sustituir_nada() {
        val nav = studyNavigation(StudyEntry.Study, deckId = "d1", seed = seed)
        assertEquals(Screen.StudySession(deckId = "d1"), nav.destination)
        assertFalse(nav.destination.freeReview, "la sesion normal NUNCA es repaso libre")
        assertEquals(0L, nav.destination.seed, "la sesion normal no lleva semilla aunque se le pase una")
        assertFalse(nav.replaceCurrentStudy)
    }

    @Test
    fun estudiar_todo_desde_Home_es_sesion_normal_global() {
        val nav = studyNavigation(StudyEntry.Study, deckId = null, seed = seed)
        assertEquals(Screen.StudySession(deckId = null), nav.destination)
        assertFalse(nav.destination.freeReview)
        assertFalse(nav.replaceCurrentStudy)
    }

    @Test
    fun repaso_libre_desde_un_mazo_abre_la_sesion_libre_de_ese_mazo_con_la_semilla() {
        val nav = studyNavigation(StudyEntry.FreeReview, deckId = "d1", seed = seed)
        assertEquals(Screen.StudySession(deckId = "d1", freeReview = true, seed = seed), nav.destination)
        assertTrue(nav.destination.freeReview)
        assertEquals(seed, nav.destination.seed)
        assertFalse(nav.replaceCurrentStudy, "se APILA sobre el mazo: ✕ vuelve al mazo")
    }

    @Test
    fun repaso_libre_desde_Home_abre_la_sesion_libre_global_con_la_semilla() {
        val nav = studyNavigation(StudyEntry.FreeReview, deckId = null, seed = seed)
        assertEquals(Screen.StudySession(deckId = null, freeReview = true, seed = seed), nav.destination)
        assertEquals(null, nav.destination.deckId)
        assertTrue(nav.destination.freeReview)
        assertEquals(seed, nav.destination.seed)
        assertFalse(nav.replaceCurrentStudy, "se APILA sobre Home: ✕ vuelve a Home")
    }

    @Test
    fun repaso_libre_desde_el_vacio_de_Estudio_SUSTITUYE_a_la_pantalla_actual() {
        val fromHome = studyNavigation(StudyEntry.FreeReviewFromEmpty, deckId = null, seed = seed)
        assertEquals(Screen.StudySession(deckId = null, freeReview = true, seed = seed), fromHome.destination)
        assertTrue(fromHome.replaceCurrentStudy, "popUpTo<StudySession> inclusive: la pila no crece")

        val fromDeck = studyNavigation(StudyEntry.FreeReviewFromEmpty, deckId = "d1", seed = seed)
        assertEquals(Screen.StudySession(deckId = "d1", freeReview = true, seed = seed), fromDeck.destination)
        assertTrue(fromDeck.replaceCurrentStudy)
    }

    @Test
    fun todas_las_entradas_de_repaso_libre_llevan_freeReview_y_la_semilla_dada() {
        for (entry in listOf(StudyEntry.FreeReview, StudyEntry.FreeReviewFromEmpty)) for (deck in listOf(null, "d9")) {
            val nav = studyNavigation(entry, deck, seed)
            assertTrue(nav.destination.freeReview, "$entry/$deck")
            assertEquals(seed, nav.destination.seed, "$entry/$deck")
            assertEquals(deck, nav.destination.deckId, "$entry/$deck conserva el origen")
        }
    }

    @Test
    fun solo_la_entrada_desde_el_vacio_sustituye_a_la_pantalla_actual() {
        assertEquals(
            setOf(StudyEntry.FreeReviewFromEmpty),
            StudyEntry.entries.filter { studyNavigation(it, "d1", seed).replaceCurrentStudy }.toSet()
        )
    }

    @Test
    fun semillas_distintas_dan_rutas_distintas_la_misma_semilla_la_misma_ruta() {
        val a = studyNavigation(StudyEntry.FreeReview, "d1", 1L).destination
        assertEquals(a, studyNavigation(StudyEntry.FreeReview, "d1", 1L).destination)
        assertTrue(a != studyNavigation(StudyEntry.FreeReview, "d1", 2L).destination)
    }
}
