package org.braincards.app.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import org.braincards.app.presentation.screens.home.SyncPill
import org.braincards.app.presentation.sync.SyncIndicator
import org.braincards.app.presentation.theme.BrainCardsTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pulsacion REAL (puntero) sobre el Pill compartido: con `onClick` es un boton (la pildora de sincronizacion de Home lleva a Ajustes /
 * Bienvenida) y sin el es informativo (el chip «Repaso libre»). Protege que extraer el Pill no deje la pildora de Home sin accion.
 */
class PillClickRenderTest {

    @OptIn(ExperimentalComposeUiApi::class)
    private fun clickCenter(content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(300, 80, Density(1f)) {
            BrainCardsTheme(darkTheme = false, animate = false) {
                Box(Modifier, contentAlignment = Alignment.TopStart) { content() }
            }
        }
        try {
            scene.render()
            // La pastilla nace arriba a la izquierda: se pulsa dentro de su zona (a unos 40 px del borde, 15 px de alto).
            val p = Offset(40f, 15f)
            scene.sendPointerEvent(PointerEventType.Press, p)
            scene.render()
            scene.sendPointerEvent(PointerEventType.Release, p)
            scene.render()
        } finally {
            scene.close()
        }
    }

    @Test
    fun el_Pill_con_onClick_responde_a_la_pulsacion() {
        var clicks = 0
        clickCenter {
            Pill(label = "Repaso libre", icon = Icons.Default.Shuffle, container = Color.Gray, content = Color.White, compact = false,
                onClick = { clicks++ })
        }
        assertEquals(1, clicks)
    }

    @Test
    fun la_pildora_de_sync_de_Home_sigue_siendo_clicable() {
        var clicks = 0
        clickCenter { SyncPill(SyncIndicator.Offline, compact = false, onClick = { clicks++ }) }
        assertEquals(1, clicks, "la pildora de sync de Home debe seguir llevando a su destino")
    }

    @Test
    fun el_chip_informativo_sin_onClick_no_dispara_nada_ni_falla() {
        // Sin onClick no hay callback que llamar: pulsar encima no debe lanzar excepciones.
        clickCenter {
            Pill(label = "Repaso libre", icon = Icons.Default.Shuffle, container = Color.Gray, content = Color.White, compact = true)
        }
    }
}
