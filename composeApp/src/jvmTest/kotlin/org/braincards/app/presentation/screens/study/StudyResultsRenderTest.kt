package org.braincards.app.presentation.screens.study

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.models.StudyResults
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Render real (ImageComposeScene, densidad 1: 1 px = 1 dp) de la tarjeta de Resultados. Comprueba que el botón de volver
 * conserva al menos 48 dp de alto con el relleno de cada ancho y con la fuente ampliada, y que mide lo mismo en compacto que
 * en normal (el relleno de la tarjeta no le quita altura).
 */
class StudyResultsRenderTest {

    private val purple = 0xFF5B3FBF.toInt()

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(widthDp: Int, fontScale: Float): Bitmap {
        val scene = ImageComposeScene(widthDp, 1400, Density(1f, fontScale)) {
            BrainCardsTheme(darkTheme = false, animate = false) {
                StudyFinished(
                    results = StudyResults(mapOf(ReviewQuality.GOOD to 3, ReviewQuality.EASY to 2)),
                    label = StudyResultsLabel.AllDecks,
                    origin = StudyOrigin.Home,
                    onBack = {}
                )
            }
        }
        try {
            // Un fotograma deja pasar la carga de recursos y la celebracion de entrada (que empieza en valores a 0).
            repeat(40) { scene.render(it * 50_000_000L) }
            return Bitmap.makeFromImage(scene.render(3_000_000_000L))
        } finally {
            scene.close()
        }
    }

    /** Alto (px = dp) de la banda morada continua más alta de la columna [x]: el botón relleno de la tarjeta. */
    private fun tallestPurpleRun(bmp: Bitmap, x: Int): Int {
        var best = 0
        var run = 0
        for (y in 0 until bmp.height) {
            if (bmp.getColor(x, y) == purple) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }

    /** Primera columna cuya banda morada continua llega a 30 dp: el borde izquierdo del botón (la esquina redondeada lo recorta). */
    private fun buttonLeftEdge(bmp: Bitmap): Int =
        (0 until bmp.width).first { x -> tallestPurpleRun(bmp, x) >= 30 }

    private fun buttonHeight(widthDp: Int, fontScale: Float): Int {
        val pad = resultsCardPadding(widthDp.toFloat()).horizontalDp
        // 20 dp dentro del borde izquierdo del botón: fuera de la esquina redondeada y sin texto encima.
        return tallestPurpleRun(render(widthDp, fontScale), x = pad + 20)
    }

    @Test
    fun el_boton_mide_al_menos_48_dp_en_compacto() {
        val h = buttonHeight(widthDp = 360, fontScale = 1f)
        assertTrue(h >= 48, "botón de $h dp a 360 dp")
    }

    @Test
    fun el_boton_mide_al_menos_48_dp_en_normal() {
        val h = buttonHeight(widthDp = 480, fontScale = 1f)
        assertTrue(h >= 48, "botón de $h dp a 480 dp")
    }

    @Test
    fun el_boton_mantiene_48_dp_con_la_fuente_ampliada_en_compacto() {
        listOf(1.3f, 2f).forEach { scale ->
            val h = buttonHeight(widthDp = 360, fontScale = scale)
            assertTrue(h >= 48, "botón de $h dp a 360 dp con fuente x$scale")
        }
    }

    @Test
    fun el_relleno_de_la_tarjeta_no_cambia_la_altura_del_boton() {
        assertEquals(buttonHeight(widthDp = 360, fontScale = 1f), buttonHeight(widthDp = 480, fontScale = 1f))
    }

    @Test
    fun el_boton_empieza_donde_acaba_el_relleno_de_la_tarjeta() {
        listOf(360, 480).forEach { w ->
            val expected = resultsCardPadding(w.toFloat()).horizontalDp
            val edge = buttonLeftEdge(render(widthDp = w, fontScale = 1f))
            assertTrue(kotlin.math.abs(edge - expected) <= 1, "a $w dp el botón empieza en $edge dp (relleno esperado $expected)")
        }
    }
}
