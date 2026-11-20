package org.braincards.app.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Render real (ImageComposeScene, densidad 1) del botón «Crear mazo de ejemplo»: alto mínimo de 48 dp también con fuente ampliada,
 * dentro del ancho, pulsación REAL (puntero) que solo responde en reposo, y aspecto distinto mientras crea.
 */
class SampleDeckButtonRenderTest {

    @OptIn(ExperimentalComposeUiApi::class)
    private fun measure(widthDp: Int, fontScale: Float, creating: Boolean): IntSize {
        var size by mutableStateOf(IntSize.Zero)
        val scene = ImageComposeScene(widthDp, 300, Density(1f, fontScale)) {
            BrainCardsTheme(darkTheme = false, animate = false) {
                Box(Modifier, contentAlignment = Alignment.TopStart) {
                    SampleDeckButton(creating = creating, onClick = {}, modifier = Modifier.onGloballyPositioned { size = it.size })
                }
            }
        }
        try {
            repeat(20) { scene.render(it * 50_000_000L) }
        } finally {
            scene.close()
        }
        return size
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun clickCount(creating: Boolean): Int {
        var clicks = 0
        val scene = ImageComposeScene(320, 100, Density(1f)) {
            BrainCardsTheme(darkTheme = false, animate = false) {
                Box(Modifier, contentAlignment = Alignment.TopStart) { SampleDeckButton(creating = creating, onClick = { clicks++ }) }
            }
        }
        try {
            repeat(5) { scene.render(it * 50_000_000L) }
            val p = Offset(60f, 24f)
            scene.sendPointerEvent(PointerEventType.Press, p)
            scene.render(300_000_000L)
            scene.sendPointerEvent(PointerEventType.Release, p)
            scene.render(350_000_000L)
        } finally {
            scene.close()
        }
        return clicks
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun pixels(creating: Boolean): ByteArray {
        val scene = ImageComposeScene(320, 100, Density(1f)) {
            BrainCardsTheme(darkTheme = false, animate = false) {
                Box(Modifier, contentAlignment = Alignment.TopStart) { SampleDeckButton(creating = creating, onClick = {}) }
            }
        }
        try {
            repeat(5) { scene.render(it * 50_000_000L) }
            val bmp = Bitmap.makeFromImage(scene.render(250_000_000L))
            return bmp.readPixels(ImageInfo(bmp.width, bmp.height, ColorType.BGRA_8888, ColorAlphaType.PREMUL), bmp.width * 4)!!
        } finally {
            scene.close()
        }
    }

    @Test
    fun en_reposo_mide_al_menos_48_dp_y_cabe_en_360_dp() {
        val size = measure(widthDp = 360, fontScale = 1f, creating = false)
        assertTrue(size.height >= 48, "alto = ${size.height} dp")
        assertTrue(size.width in 1..360, "ancho = ${size.width} dp")
    }

    @Test
    fun con_fuente_ampliada_mantiene_los_48_dp() {
        listOf(1.3f, 2f).forEach { scale ->
            assertTrue(measure(360, scale, creating = false).height >= 48, "reposo con fuente x$scale")
            assertTrue(measure(360, scale, creating = true).height >= 48, "creando con fuente x$scale")
        }
    }

    @Test
    fun una_pulsacion_en_reposo_llama_a_onClick() {
        assertEquals(1, clickCount(creating = false))
    }

    @Test
    fun mientras_crea_la_pulsacion_no_hace_nada() {
        assertEquals(0, clickCount(creating = true), "deshabilitado: un doble toque no puede duplicar")
    }

    @Test
    fun creando_se_ve_distinto_del_reposo() {
        assertFalse(pixels(creating = false).contentEquals(pixels(creating = true)), "el estado «Creando…» debe notarse")
    }
}
