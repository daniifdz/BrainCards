package org.braincards.app.presentation.screens.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import org.braincards.app.presentation.screens.home.HomeContent
import org.braincards.app.presentation.screens.home.HomeUiState
import org.braincards.app.presentation.screens.study.StudyEmpty
import org.braincards.app.presentation.screens.study.StudyOrigin
import org.braincards.app.presentation.sync.SyncIndicator
import org.braincards.app.presentation.theme.BrainCardsTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Render REAL de las pantallas que ofrecen «Repaso libre» (Home «Todo al dia» y vacio de Estudio): se barre la ventana con pulsaciones y se
 * cuenta cuantas llegan al callback de repaso libre. >0 = el boton se dibuja y es pulsable; 0 = no hay boton. Complementa a las funciones
 * puras (que deciden) comprobando que el dibujo las obedece.
 */
class FreeReviewEntriesRenderTest {

    @OptIn(ExperimentalComposeUiApi::class)
    private fun hitsOnFreeReview(widthDp: Int, content: @Composable (onFree: () -> Unit) -> Unit): Int {
        var hits = 0
        val scene = ImageComposeScene(widthDp, 900, Density(1f)) {
            BrainCardsTheme(darkTheme = false, animate = false) { content { hits++ } }
        }
        try {
            scene.render()
            scene.render()
            var y = 4f
            while (y < 900f) {
                var x = 4f
                while (x < widthDp) {
                    val p = Offset(x, y)
                    scene.sendPointerEvent(PointerEventType.Press, p)
                    scene.sendPointerEvent(PointerEventType.Release, p)
                    x += 12f
                }
                y += 12f
            }
            scene.render()
        } finally {
            scene.close()
        }
        return hits
    }

    private fun home(state: HomeUiState, width: Int = 900) = hitsOnFreeReview(width) { onFree ->
        HomeContent(
            state = state, indicator = SyncIndicator.Offline, onRetry = {}, onNavigateToDecks = {}, onNavigateToStudy = {},
            onNavigateToFreeReview = onFree, onNavigateToStats = {}, onPillClick = {}, onNavigateToSettings = {}
        )
    }

    @Test
    fun home_Todo_al_dia_dibuja_el_boton_y_es_pulsable_en_movil_y_escritorio() {
        assertTrue(home(HomeUiState.Empty, 900) > 0, "escritorio")
        assertTrue(home(HomeUiState.Empty, 360) > 0, "movil")
    }

    @Test
    fun home_no_dibuja_el_boton_en_los_demas_estados() {
        assertEquals(0, home(HomeUiState.Content(dueCount = 7)), "con pendientes")
        assertEquals(0, home(HomeUiState.NoCards), "mazos sin tarjetas")
        assertEquals(0, home(HomeUiState.NoDecks), "primer uso")
        assertEquals(0, home(HomeUiState.Loading), "cargando")
        assertEquals(0, home(HomeUiState.Error), "error")
    }

    @Test
    fun el_vacio_de_Estudio_dibuja_el_boton_con_los_dos_origenes() {
        for (origin in StudyOrigin.entries) {
            val hits = hitsOnFreeReview(600) { onFree -> StudyEmpty(origin = origin, onBack = {}, onFreeReview = onFree) }
            assertTrue(hits > 0, "origen $origin")
        }
    }
}
