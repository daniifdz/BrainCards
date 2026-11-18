package org.braincards.app.presentation.splash

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El cerebro del splash existe en TRES sitios: el SVG de marca, el vector de composeResources (EntryPending) y el de
 * androidMain/res (splash del sistema). Android no puede leer composeResources, asi que hay dos copias; este test impide
 * que diverjan del SVG (mismos dos trazados, mismo viewBox, relleno blanco y evenOdd).
 */
class SplashLogoConsistencyTest {

    private val svg = File("../docs/design/brand/logo/braincards-logo-white.svg").readText()
    private val compose = File("src/commonMain/composeResources/drawable/splash_logo.xml").readText()
    private val android = File("src/androidMain/res/drawable/splash_logo_brain.xml").readText()

    private fun svgPaths() = Regex("""<path d="([^"]+)"""").findAll(svg).map { it.groupValues[1] }.toList()
    private fun vectorPaths(xml: String) = Regex("""android:pathData="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()

    @Test
    fun el_svg_de_marca_tiene_dos_trazados() = assertEquals(2, svgPaths().size)

    @Test
    fun los_dos_vectores_usan_los_trazados_del_svg() {
        assertEquals(svgPaths(), vectorPaths(compose), "composeResources")
        assertEquals(svgPaths(), vectorPaths(android), "androidMain/res")
    }

    @Test
    fun viewBox_relleno_y_fillType_coinciden() {
        // SVG: viewBox "13 13 230 230" -> viewport 230 y traslacion -13.
        assertEquals(true, svg.contains("""viewBox="13 13 230 230""""))
        for ((name, xml) in listOf("composeResources" to compose, "androidMain/res" to android)) {
            assertEquals(true, xml.contains("""android:viewportWidth="230""""), "$name viewportWidth")
            assertEquals(true, xml.contains("""android:viewportHeight="230""""), "$name viewportHeight")
            assertEquals(true, xml.contains("""android:translateX="-13""""), "$name translateX")
            assertEquals(true, xml.contains("""android:translateY="-13""""), "$name translateY")
            assertEquals(2, Regex("""android:fillType="evenOdd"""").findAll(xml).count(), "$name evenOdd")
            assertEquals(2, Regex("""android:fillColor="#FFFFFF"""").findAll(xml).count(), "$name blanco")
        }
    }
}
