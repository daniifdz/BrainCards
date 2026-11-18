package org.braincards.app.presentation.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageVector
import org.jetbrains.skia.Bitmap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guarda del vector del splash en Compose resources (CMP 1.9.0), decodificado con el mismo parser que `vectorResource` y
 * pintado a un bitmap con ImageComposeScene (sin compose.uiTest).
 *
 * Que protege cada test, de forma honesta:
 *  - [el_parser_conserva_evenOdd_en_las_dos_formas]: es el UNICO que protege `fillType="evenOdd"` (inspecciona el
 *    `pathFillType` decodificado). El render NO lo distingue: los subtrazados interiores del cerebro giran en sentido
 *    contrario al contorno, asi que con nonZero los surcos tambien salen vacios (se comprobo con un control).
 *  - [renderiza_el_cerebro_sin_excepcion_y_con_el_cuerpo_relleno]: el vector se decodifica y se pinta sin excepcion, el
 *    cuerpo queda relleno (opaco), el exterior transparente y el surco vacio (comprobacion de cordura, no de evenOdd).
 */
class SplashLogoRenderTest {

    private val xml = File("src/commonMain/composeResources/drawable/splash_logo.xml")

    @OptIn(ExperimentalResourceApi::class)
    private fun decode(text: String): ImageVector = text.toByteArray().decodeToImageVector(Density(1f))

    private fun paths(node: VectorNode): List<VectorPath> = when (node) {
        is VectorPath -> listOf(node)
        is VectorGroup -> node.flatMap { paths(it) }
        else -> emptyList()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(vector: ImageVector): Bitmap {
        val scene = ImageComposeScene(230, 230, Density(1f)) {
            Image(rememberVectorPainter(vector), null, Modifier.size(230.dp))
        }
        try {
            return Bitmap.makeFromImage(scene.render())
        } finally {
            scene.close()
        }
    }

    private fun alpha(bmp: Bitmap, x: Int, y: Int) = bmp.getColor(x, y) ushr 24

    @Test
    fun el_parser_conserva_evenOdd_en_las_dos_formas() {
        val vector = decode(xml.readText())
        val found = paths(vector.root)
        assertEquals(2, found.size)
        found.forEach { assertEquals(PathFillType.EvenOdd, it.pathFillType) }
    }

    @Test
    fun renderiza_el_cerebro_sin_excepcion_y_con_el_cuerpo_relleno() {
        val bmp = render(decode(xml.readText()))
        assertEquals(0, alpha(bmp, 5, 5), "fuera del dibujo")
        assertEquals(255, alpha(bmp, 60, 115), "cuerpo del cerebro")
        assertEquals(0, alpha(bmp, 60, 95), "surco interior (cordura; no distingue evenOdd de nonZero)")
    }
}
