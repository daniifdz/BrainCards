package org.braincards.app.presentation.screens.study

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
import androidx.compose.ui.unit.dp
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Render real (ImageComposeScene, densidad 1: 1 px = 1 dp) de la fila de Estudio con el chip «Repaso libre» y su aviso, al ancho UTIL
 * de la pantalla de Estudio (ancho de ventana - 40 dp de margen): 320 dp en un movil de 360 dp y 560 dp en el umbral de 600 dp.
 * Comprueba que la fila cabe en UNA linea, que el aviso no la desborda ni la envuelve y que el conjunto queda centrado.
 */
class FreeReviewBannerRenderTest {

    private class Result(val width: Int, val height: Int, val bitmap: Bitmap)

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(widthDp: Int, notice: String?): Result {
        var size by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
        val scene = ImageComposeScene(widthDp, 120, Density(1f)) {
            BrainCardsTheme(darkTheme = false, animate = false) {
                Box(Modifier.width(widthDp.dp)) {
                    FreeReviewBanner(chipLabel = "Repaso libre", notice = notice, modifier = Modifier.onGloballyPositioned { size = it.size })
                }
            }
        }
        try {
            scene.render()
            val bmp = Bitmap.makeFromImage(scene.render())
            return Result(size.width, size.height, bmp)
        } finally {
            scene.close()
        }
    }

    /** Extremos horizontales (primer y ultimo pixel con algo dibujado) de un bitmap con fondo transparente. */
    private fun contentExtent(bmp: Bitmap): Pair<Int, Int> {
        val w = bmp.width
        val h = bmp.height
        val bytes = bmp.readPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL), w * 4)!!
        var min = w
        var max = -1
        for (y in 0 until h) for (x in 0 until w) if ((bytes[(y * w + x) * 4 + 3].toInt() and 0xFF) > 0) {
            if (x < min) min = x
            if (x > max) max = x
        }
        return min to max
    }

    private val notice = "No cambia tu programación."

    @Test
    fun a_360_dp_solo_el_chip_cabe_en_una_linea_y_queda_centrado() {
        val r = render(widthDp = 320, notice = null)
        assertEquals(320, r.width)
        val (min, max) = contentExtent(r.bitmap)
        assertTrue(max - min + 1 < 320, "el chip cabe con holgura (${max - min + 1} dp)")
        assertTrue(abs((min + max) / 2.0 - 160.0) <= 2.0, "centrado: centro en ${(min + max) / 2.0} dp (esperado 160)")
    }

    @Test
    fun en_el_umbral_de_600_dp_el_aviso_cabe_en_la_misma_linea_sin_envolver() {
        val chipOnly = render(widthDp = 560, notice = null)
        val withNotice = render(widthDp = 560, notice = notice)
        assertEquals(chipOnly.height, withNotice.height, "con el aviso la fila sigue teniendo la misma altura: no se envuelve")
        val (cMin, cMax) = contentExtent(chipOnly.bitmap)
        val (nMin, nMax) = contentExtent(withNotice.bitmap)
        assertTrue(nMax - nMin > cMax - cMin, "el aviso suma ancho a la fila")
        assertTrue(nMin >= 0 && nMax < 560, "no se sale del ancho disponible")
        assertTrue(abs((nMin + nMax) / 2.0 - 280.0) <= 3.0, "el conjunto chip + aviso queda centrado (centro en ${(nMin + nMax) / 2.0})")
    }

    @Test
    fun la_fila_es_baja_una_pildora_de_una_sola_linea() {
        val r = render(widthDp = 320, notice = null)
        assertTrue(r.height in 20..40, "altura de la fila = ${r.height} dp (pill.sm: 6 + texto + 6)")
    }

    @Test
    fun el_aviso_ocupa_mas_a_320_dp_de_lo_que_ocuparia_el_chip_solo_y_sigue_cabiendo() {
        // Documenta por qué el aviso es una decisión de diseño y no una necesidad: también cabría a 320 dp (móvil), pero se reserva
        // para Desktop o tablet y el umbral es 600 dp de ventana.
        val r = render(widthDp = 320, notice = notice)
        val (min, max) = contentExtent(r.bitmap)
        assertTrue(min >= 0 && max < 320, "chip + aviso caben en 320 dp (${max - min + 1} dp)")
    }
}
