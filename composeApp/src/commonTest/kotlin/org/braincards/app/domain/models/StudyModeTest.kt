package org.braincards.app.domain.models

import kotlinx.serialization.json.Json
import org.braincards.app.presentation.screens.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Modo de estudio a partir de la ruta: por DEFECTO siempre es la sesion normal (el repaso libre solo si se pide). */
class StudyModeTest {

    @Test
    fun sin_freeReview_es_la_sesion_normal_sea_cual_sea_la_semilla() {
        assertEquals(StudyMode.Scheduled, studyModeFor(freeReview = false, seed = 0L))
        assertEquals(StudyMode.Scheduled, studyModeFor(freeReview = false, seed = 99L))
    }

    @Test
    fun con_freeReview_es_repaso_libre_con_la_semilla_de_la_ruta() {
        assertEquals(StudyMode.Free(99L), studyModeFor(freeReview = true, seed = 99L))
    }

    @Test
    fun la_ruta_de_estudio_arranca_por_defecto_en_sesion_normal() {
        val route = Screen.StudySession()
        assertEquals(null, route.deckId)
        assertFalse(route.freeReview, "un StudySession sin argumentos NUNCA es repaso libre")
        assertEquals(0L, route.seed)
        assertEquals(StudyMode.Scheduled, studyModeFor(route.freeReview, route.seed))
        assertFalse(Screen.StudySession(deckId = "d1").freeReview)
    }

    @Test
    fun la_ruta_se_serializa_de_ida_y_vuelta_con_la_semilla() {
        val route = Screen.StudySession(deckId = "d1", freeReview = true, seed = -123456789012L)
        val json = Json.encodeToString(Screen.StudySession.serializer(), route)
        assertEquals(route, Json.decodeFromString(Screen.StudySession.serializer(), json))
    }

    @Test
    fun una_ruta_guardada_antes_del_repaso_libre_se_lee_como_sesion_normal() {
        val old = Json.decodeFromString(Screen.StudySession.serializer(), """{"deckId":"d1"}""")
        assertEquals(Screen.StudySession(deckId = "d1"), old)
        assertFalse(old.freeReview)
    }
}
