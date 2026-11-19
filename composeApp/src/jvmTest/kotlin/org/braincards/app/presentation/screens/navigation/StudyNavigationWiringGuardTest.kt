package org.braincards.app.presentation.screens.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guarda del CABLEADO de `App.kt` (no hay `compose.uiTest` ni NavController de prueba): las puertas al estudio salen TODAS de
 * `studyNavigation(...)` (la funcion pura que prueba StudyNavigationTest) y cada entrada de repaso libre genera SU semilla en el punto de
 * navegacion con `Random.nextLong()`. Lee el codigo fuente e ignora los comentarios.
 */
class StudyNavigationWiringGuardTest {

    private fun appSource(): String {
        val relative = "composeApp/src/commonMain/kotlin/org/braincards/app/App.kt"
        val file = generateSequence(File("").absoluteFile) { it.parentFile }.take(6).map { File(it, relative) }.firstOrNull { it.isFile }
        assertNotNull(file, "no se encuentra $relative subiendo desde ${File("").absolutePath}")
        return file.readText().replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(?m)//.*$"""), "")
    }

    private fun count(source: String, needle: String) = Regex(Regex.escape(needle)).findAll(source).count()

    @Test
    fun las_5_puertas_salen_de_studyNavigation_con_su_entrada() {
        val src = appSource()
        assertEquals(2, count(src, "studyNavigation(StudyEntry.Study,"), "Estudiar Todo (Home) y Estudiar (mazo)")
        assertEquals(2, count(src, "studyNavigation(StudyEntry.FreeReview,"), "Repaso libre desde Home y desde el mazo")
        assertEquals(1, count(src, "studyNavigation(StudyEntry.FreeReviewFromEmpty,"), "Repaso libre desde el vacio de Estudio")
    }

    @Test
    fun cada_entrada_de_repaso_libre_genera_su_semilla_en_el_punto_de_navegacion() {
        val src = appSource()
        assertEquals(3, count(src, "Random.nextLong()"), "una semilla por cada una de las 3 entradas de repaso libre")
        assertEquals(2, count(src, "seed = 0L"), "las dos entradas normales no llevan semilla")
        // la semilla va justo en la llamada de cada entrada libre
        val libres = Regex("""studyNavigation\(StudyEntry\.FreeReview(FromEmpty)?,[^)]*\)""").findAll(src).map { it.value }.toList()
        assertEquals(3, libres.size)
        for (call in libres) assertTrue("Random.nextLong()" in call, "la entrada libre no genera semilla: $call")
    }

    @Test
    fun la_ruta_de_estudio_solo_se_construye_en_studyNavigation_y_la_sustitucion_usa_popUpTo_inclusive() {
        val src = appSource()
        assertEquals(0, count(src, "Screen.StudySession("), "App.kt no construye StudySession a mano")
        assertEquals(1, count(src, "popUpTo<Screen.StudySession> { inclusive = true }"))
        assertTrue("if (nav.replaceCurrentStudy)" in src, "el popUpTo solo se aplica cuando la navegacion lo pide")
    }

    @Test
    fun las_tres_pantallas_reciben_su_callback_de_repaso_libre() {
        val src = appSource()
        assertEquals(1, count(src, "onNavigateToFreeReview ="), "HomeScreen")
        assertEquals(1, count(src, "onFreeReviewClick ="), "DeckContentScreen")
        assertEquals(1, count(src, "onFreeReview ="), "StudyScreen (estado vacio)")
    }
}
