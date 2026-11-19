package org.braincards.app.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Las paletas de Color.kt son exactamente las de docs/design/mockups/assets/tokens.css (fuente de los tokens de color). Si
 * alguien cambia un hex en un sitio y no en el otro, falla aquí.
 */
class PaletteTokensSyncTest {

    private fun tokensCss(): String {
        val relative = "docs/design/mockups/assets/tokens.css"
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .take(6)
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
        assertNotNull(file, "no se encuentra $relative subiendo desde ${File("").absolutePath}")
        return file.readText()
    }

    private fun block(css: String, theme: String): Map<String, String> {
        val body = Regex("""\[data-theme="$theme"]\s*\{([^}]*)}""").find(css)?.groupValues?.get(1)
        assertNotNull(body, "bloque data-theme=$theme")
        return Regex("""--([a-z\-]+):\s*([^;]+);""").findAll(body).associate { it.groupValues[1] to it.groupValues[2].trim() }
    }

    private fun parse(value: String): Color {
        Regex("""#([0-9A-Fa-f]{6})""").matchEntire(value)?.let { return Color((0xFF000000L or it.groupValues[1].toLong(16))) }
        val rgba = Regex("""rgba\((\d+),\s*(\d+),\s*(\d+),\s*([0-9.]+)\)""").matchEntire(value)
        assertNotNull(rgba, "formato de color no soportado: $value")
        val (r, g, b) = rgba.groupValues.drop(1).take(3).map { it.toInt() }
        return Color(red = r, green = g, blue = b, alpha = (rgba.groupValues[4].toDouble() * 255).roundToInt())
    }

    private fun check(theme: String, p: BrainCardsPalette) {
        val css = block(tokensCss(), theme)
        val expected = mapOf(
            "ground" to p.ground, "surface" to p.surface, "border" to p.border, "text" to p.text,
            "text-secondary" to p.textSecondary, "brand" to p.brand, "brand-text" to p.brandText,
            "brand-chip" to p.brandChip, "cta" to p.cta, "cta-text" to p.ctaText, "chip-teal" to p.chipTeal,
            "chip-teal-bg" to p.chipTealBg, "chip-gray" to p.chipGray, "chip-gray-bg" to p.chipGrayBg,
            "error" to p.errorColor, "error-bg" to p.errorBg
        )
        assertEquals(expected.keys, css.keys, "$theme: mismos tokens en tokens.css y en la paleta (outline es derivado y no esta en el css)")
        expected.forEach { (name, color) ->
            val fromCss = parse(css.getValue(name))
            assertEquals(fromCss.toArgb(), color.toArgb(), "$theme: --$name")
        }
        assertTrue(expected.size == 16)
    }

    @Test
    fun la_paleta_clara_coincide_con_tokens_css() = check("light", LightPalette)

    @Test
    fun la_paleta_oscura_coincide_con_tokens_css() = check("dark", DarkPalette)
}
