package org.braincards.app.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import org.braincards.app.presentation.theme.BrainCardsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pulsacion REAL sobre el boton ghost «Repaso libre» (Home «Todo al dia» y estado vacio de Estudio): dispara su accion una sola vez. */
class FreeReviewGhostButtonClickTest {

    @OptIn(ExperimentalComposeUiApi::class)
    private fun clickOn(metrics: GhostButtonMetrics): Int {
        var clicks = 0
        val scene = ImageComposeScene(300, 120, Density(1f)) {
            BrainCardsTheme(darkTheme = false, animate = false) {
                Box(Modifier, contentAlignment = Alignment.TopStart) {
                    FreeReviewGhostButton(label = "Repaso libre", onClick = { clicks++ }, metrics = metrics)
                }
            }
        }
        try {
            scene.render()
            val p = Offset(40f, 24f) // dentro del boton (nace arriba a la izquierda; minimo tactil de 48 dp)
            scene.sendPointerEvent(PointerEventType.Press, p)
            scene.render()
            scene.sendPointerEvent(PointerEventType.Release, p)
            scene.render()
        } finally {
            scene.close()
        }
        return clicks
    }

    @Test
    fun el_boton_de_Home_responde_a_la_pulsacion() = assertEquals(1, clickOn(HOME_FREE_REVIEW_BUTTON))

    @Test
    fun el_boton_del_vacio_de_Estudio_responde_a_la_pulsacion() = assertEquals(1, clickOn(EMPTY_FREE_REVIEW_BUTTON))
}
