package org.braincards.app.presentation.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageVector
import org.jetbrains.skia.Bitmap
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Render real del badge (ImageComposeScene, sin compose.uiTest) con el vector del splash decodificado con el parser de Compose
 * resources, 100 px de lado a densidad 1. Medidas esperadas: esquinas del 22 % y cerebro dibujado = 53,4 % del lado, 45,3 %
 * de alto, centrado.
 */
class BrandBadgeRenderTest {

    private val xml = File("src/commonMain/composeResources/drawable/splash_logo.xml")

    @OptIn(ExperimentalResourceApi::class)
    private val vector: ImageVector = xml.readText().toByteArray().decodeToImageVector(Density(1f))

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(side: Int = 100, cornerPercent: Int = BADGE_CORNER_PERCENT): Bitmap {
        val scene = ImageComposeScene(side, side, Density(1f)) {
            BrandBadgeContent(side.dp, rememberVectorPainter(vector), cornerPercent = cornerPercent)
        }
        try {
            return Bitmap.makeFromImage(scene.render())
        } finally {
            scene.close()
        }
    }

    private val purple = 0xFF5B3FBF.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun esquinas_redondeadas_fondo_morado_fijo_y_surco_central() {
        val bmp = render()
        assertEquals(0, bmp.getColor(0, 0) ushr 24, "esquina exterior transparente (redondeada)")
        assertEquals(0, bmp.getColor(99, 99) ushr 24, "esquina opuesta transparente")
        assertEquals(purple, bmp.getColor(50, 3), "borde superior, morado de marca opaco")
        assertEquals(purple, bmp.getColor(50, 50), "hueco entre hemisferios, morado")
    }

    @Test
    fun el_radio_es_configurable_para_la_tarjeta_de_resultados() {
        // Pixel (8,8) de un badge de 100 px: con esquinas del 22 % queda DENTRO (opaco); con el 31 % de Resultados, FUERA.
        assertEquals(purple, render(cornerPercent = BADGE_CORNER_PERCENT).getColor(8, 8), "22 %: dentro")
        assertEquals(0, render(cornerPercent = 31).getColor(8, 8) ushr 24, "31 %: fuera (mas redondo)")
    }

    @Test
    fun el_cerebro_es_blanco_y_conserva_los_surcos() {
        val bmp = render()
        assertEquals(white, bmp.getColor(34, 50), "cuerpo del hemisferio izquierdo")
        assertEquals(white, bmp.getColor(66, 50), "cuerpo del hemisferio derecho")
    }

    @Test
    fun proporcion_del_cerebro_dentro_del_badge() {
        val bmp = render()
        var minX = 100; var maxX = -1; var minY = 100; var maxY = -1
        for (y in 0 until 100) for (x in 0 until 100) {
            val c = bmp.getColor(x, y)
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            if (c ushr 24 == 255 && r > 200 && g > 200 && b > 200) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        val width = maxX - minX + 1
        val height = maxY - minY + 1
        assertTrue(abs(width - 53.4) <= 2.0, "ancho del cerebro $width px (esperado 53,4 % del lado)")
        assertTrue(abs(height - 45.3) <= 2.0, "alto del cerebro $height px (esperado 45,3 % del lado)")
        assertTrue(abs((minX + maxX) / 2.0 - 49.5) <= 1.5, "centrado en horizontal")
        assertTrue(abs((minY + maxY) / 2.0 - 49.5) <= 1.5, "centrado en vertical")
    }
}
