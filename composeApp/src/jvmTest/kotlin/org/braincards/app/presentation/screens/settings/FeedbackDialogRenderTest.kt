package org.braincards.app.presentation.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Render real (ImageComposeScene, densidad 1) del contenido del aviso de «Enviar comentarios»: con el ancho mínimo de un diálogo
 * y con fuente ampliada el texto y la dirección se dibujan dentro del ancho disponible (envuelven, no se salen) y no quedan vacíos.
 * El diálogo en sí (AlertDialog) abre una ventana propia que esta escena no pinta: se prueba su contenido.
 */
class FeedbackDialogRenderTest {

    private class Result(val size: IntSize, val painted: Int, val maxX: Int)

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(widthDp: Int, fontScale: Float): Result {
        var size by mutableStateOf(IntSize.Zero)
        val scene = ImageComposeScene(widthDp, 1200, Density(1f, fontScale)) {
            BrainCardsTheme(darkTheme = false, animate = false) {
                Box(Modifier.width(widthDp.dp)) {
                    FeedbackUnavailableContent(FEEDBACK_EMAIL, Modifier.onGloballyPositioned { size = it.size })
                }
            }
        }
        try {
            repeat(20) { scene.render(it * 50_000_000L) }
            val bmp = Bitmap.makeFromImage(scene.render(1_000_000_000L))
            var painted = 0
            var maxX = -1
            for (y in 0 until bmp.height) for (x in 0 until bmp.width) if ((bmp.getColor(x, y) ushr 24) > 0) {
                painted++
                if (x > maxX) maxX = x
            }
            return Result(size, painted, maxX)
        } finally {
            scene.close()
        }
    }

    @Test
    fun con_el_ancho_minimo_de_un_dialogo_el_contenido_se_dibuja_dentro_del_ancho() {
        val r = render(widthDp = 232, fontScale = 1f)
        assertTrue(r.painted > 0, "el contenido no se dibujó")
        assertTrue(r.maxX < 232, "el contenido llega a x=${r.maxX} dp (ancho 232)")
        assertTrue(r.size.width in 1..232, "ancho medido = ${r.size.width}")
    }

    @Test
    fun con_fuente_ampliada_el_contenido_crece_hacia_abajo_sin_salirse_del_ancho() {
        val normal = render(widthDp = 232, fontScale = 1f)
        val big = render(widthDp = 232, fontScale = 2f)
        assertTrue(big.maxX < 232, "con fuente x2 llega a x=${big.maxX} dp (ancho 232)")
        assertTrue(big.size.height > normal.size.height, "alto x2 = ${big.size.height}, normal = ${normal.size.height}")
    }
}
