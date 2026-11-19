package org.braincards.app.presentation.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.braincards.app.presentation.screens.navigation.SPLASH_CONVERGE_MS
import org.braincards.app.presentation.screens.navigation.SPLASH_OFFSET_UNITS
import org.braincards.app.presentation.screens.navigation.SplashPose
import org.braincards.app.presentation.screens.navigation.splashPose
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageVector
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guarda del recorte: al separarse, cada hemisferio sale del cuadro del cerebro; si el área de dibujo fuera el cuadro (o menor
 * que `splashMinDrawAreaFactor`), la capa offscreen recortaría lo que cae fuera y la entrada se vería como astillas.
 *
 * Se renderiza el SplashBrainContent real (cerebro de 160 dp en una escena de 360 px, densidad 1) en cada fotograma a 60 fps de
 * la convergencia y se cuenta el área pintada (píxeles con alfa > 50 %, con la opacidad forzada a 1 para medir solo la
 * geometría). Debe ser 100 ± 2 % del área del cerebro en reposo en todos los fotogramas, tanto con la configuración actual
 * (±84, curva actual) como con la anterior (±58, easeOutBack), que también recortaba un poco.
 */
class SplashBrainAreaTest {

    private val xml = File("src/commonMain/composeResources/drawable/splash_logo.xml")

    @OptIn(ExperimentalResourceApi::class)
    private val vector: ImageVector = xml.readText().toByteArray().decodeToImageVector(Density(1f))

    private val side = 360

    @OptIn(ExperimentalComposeUiApi::class)
    private fun paintedArea(pose: SplashPose): Int {
        val scene = ImageComposeScene(side, side, Density(1f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SplashBrainContent(160.dp, rememberVectorPainter(vector), { pose })
            }
        }
        try {
            val bmp: Bitmap = Bitmap.makeFromImage(scene.render())
            val bytes = bmp.readPixels(ImageInfo(side, side, ColorType.BGRA_8888, ColorAlphaType.PREMUL), side * 4)!!
            var n = 0
            for (i in 0 until side * side) if ((bytes[i * 4 + 3].toInt() and 0xFF) > 127) n++
            return n
        } finally {
            scene.close()
        }
    }

    private val restArea by lazy { paintedArea(SplashPose.Rest) }

    /** Centroide (x, y) en pixeles del area pintada. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun paintedCentroid(pose: SplashPose): Pair<Float, Float> {
        val scene = ImageComposeScene(side, side, Density(1f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SplashBrainContent(160.dp, rememberVectorPainter(vector), { pose })
            }
        }
        try {
            val bmp: Bitmap = Bitmap.makeFromImage(scene.render())
            val bytes = bmp.readPixels(ImageInfo(side, side, ColorType.BGRA_8888, ColorAlphaType.PREMUL), side * 4)!!
            var n = 0; var sx = 0.0; var sy = 0.0
            for (i in 0 until side * side) if ((bytes[i * 4 + 3].toInt() and 0xFF) > 127) { n++; sx += i % side; sy += i / side }
            return (sx / n).toFloat() to (sy / n).toFloat()
        } finally {
            scene.close()
        }
    }

    private fun frames60(): List<Float> = (1..(SPLASH_CONVERGE_MS * 60 / 1000)).map { it * 1000f / 60f } // t > 0, hasta la convergencia

    private fun assertNoClipping(label: String, pose: (Float) -> SplashPose) {
        assertTrue(restArea > 5_000, "el cerebro en reposo ocupa un area razonable ($restArea px)")
        for (ms in frames60()) {
            val ratio = paintedArea(pose(ms)).toFloat() / restArea
            assertTrue(ratio in 0.98f..1.02f, "$label a ${"%.0f".format(ms)} ms: area = ${"%.1f".format(ratio * 100)} % del cerebro (esperado 100 +- 2 %)")
        }
    }

    @Test
    fun la_configuracion_actual_no_se_recorta_en_ningun_fotograma() {
        assertNoClipping("actual (+-${SPLASH_OFFSET_UNITS.toInt()})") { splashPose(it).copy(alpha = 1f) }
    }

    @Test
    fun la_configuracion_anterior_de_58_unidades_tampoco_se_recorta() {
        val oldCurve = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
        assertNoClipping("anterior (+-58)") { ms ->
            val offset = 58f * (1f - oldCurve.transform((ms / SPLASH_CONVERGE_MS).coerceIn(0f, 1f)))
            SplashPose(leftUnits = 0f - offset, rightUnits = offset, scale = 1f, alpha = 1f)
        }
    }

    @Test
    fun el_area_de_dibujo_cubre_el_desplazamiento_con_margen() {
        assertTrue(SPLASH_DRAW_AREA_FACTOR >= splashMinDrawAreaFactor(SPLASH_OFFSET_UNITS), "factor $SPLASH_DRAW_AREA_FACTOR < minimo")
        assertTrue(splashMinDrawAreaFactor(SPLASH_OFFSET_UNITS) > 1.7f, "con +-84 el minimo es ~1,73 (un lienzo del tamano del cerebro recorta)")
        assertEquals(2f, SPLASH_DRAW_AREA_FACTOR)
    }

    @Test
    fun el_layout_no_cambia_solo_el_area_de_dibujo() {
        // En reposo, el area pintada es la misma que antes de ampliar el lienzo: el cerebro sigue siendo del tamano pedido.
        val ratioToBox = restArea.toFloat() / (160 * 160)
        assertTrue(ratioToBox in 0.30f..0.55f, "el cerebro llena ~${"%.0f".format(ratioToBox * 100)} % de su cuadro de 160 dp")
        // Y sigue CENTRADO en su cuadro (el area de dibujo ampliada se centra, no se desplaza el cerebro).
        val (cx, cy) = paintedCentroid(SplashPose.Rest)
        assertTrue(kotlin.math.abs(cx - side / 2f) <= 1.5f, "centroide x = $cx (esperado ${side / 2f})")
        assertTrue(kotlin.math.abs(cy - side / 2f) <= 8f, "centroide y = $cy (esperado ~${side / 2f})")
    }
}
