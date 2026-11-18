package org.braincards.app.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.braincards.app.presentation.screens.navigation.SplashPose
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageVector
import org.jetbrains.skia.Bitmap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Render real (ImageComposeScene, 230 px = 230 unidades del vector, densidad 1) del cerebro dibujado en DOS mitades con el
 * mismo painter. Protege que partir en dos no altera el dibujo y que el recorte esta en el eje central: un recorte desplazado
 * dejaria trozos de un hemisferio dentro del hueco al separarlos.
 */
class SplashBrainRenderTest {

    private val xml = File("src/commonMain/composeResources/drawable/splash_logo.xml")

    @OptIn(ExperimentalResourceApi::class)
    private val vector: ImageVector = xml.readText().toByteArray().decodeToImageVector(Density(1f))

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(content: @androidx.compose.runtime.Composable (Painter) -> Unit): Bitmap {
        val scene = ImageComposeScene(230, 230, Density(1f)) { content(rememberVectorPainter(vector)) }
        try {
            return Bitmap.makeFromImage(scene.render())
        } finally {
            scene.close()
        }
    }

    private fun split(pose: SplashPose) = render { SplashBrainContent(230.dp, it, { pose }) }

    private fun plain() = render { Image(it, null, Modifier.size(230.dp)) }

    private fun alpha(b: Bitmap, x: Int, y: Int) = b.getColor(x, y) ushr 24

    @Test
    fun en_reposo_las_dos_mitades_reproducen_el_vector_pixel_a_pixel() {
        val a = split(SplashPose.Rest)
        val b = plain()
        for (y in 0 until 230) for (x in 0 until 230) {
            assertEquals(b.getColor(x, y), a.getColor(x, y), "pixel ($x,$y)")
        }
    }

    @Test
    fun separados_el_hueco_central_queda_vacio_y_cada_mitad_se_mueve_su_desplazamiento() {
        val bmp = split(SplashPose(leftUnits = -30f, rightUnits = 30f, scale = 1f, alpha = 1f))
        // hemisferio izquierdo: x 22..111 -> -8..81 ; derecho: 119..208 -> 149..238. Hueco: 83..147.
        for (y in 0 until 230) for (x in 83..147) assertEquals(0, alpha(bmp, x, y), "hueco ($x,$y)")
        // cuerpo de cada mitad desplazado (en reposo (60,115) es opaco y su simetrico (170,115) tambien)
        assertEquals(255, alpha(bmp, 30, 115), "cuerpo izquierdo desplazado -30")
        assertEquals(255, alpha(bmp, 200, 115), "cuerpo derecho desplazado +30")
    }

    @Test
    fun con_opacidad_cero_no_se_dibuja_nada() {
        val bmp = split(SplashPose(leftUnits = 0f, rightUnits = 0f, scale = 1f, alpha = 0f))
        for (y in 0 until 230 step 3) for (x in 0 until 230 step 3) assertEquals(0, alpha(bmp, x, y), "($x,$y)")
    }

    @Test
    fun la_escala_del_pulso_agranda_el_cerebro_desde_su_centro() {
        val rest = split(SplashPose.Rest)
        val big = split(SplashPose(0f, 0f, 1.06f, 1f))
        // El extremo izquierdo del cerebro (x=22 en y=119) queda fuera del dibujo en reposo y dentro al escalar 1.06.
        assertEquals(0, alpha(rest, 19, 119), "reposo")
        assertTrue(alpha(big, 19, 119) > 0, "escalado")
    }
}
