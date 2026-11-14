package org.braincards.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ratio de contraste WCAG 2.x entre dos colores OPACOS (luminancia relativa, umbral 0,03928). */
internal object Wcag {
    private fun channel(v: Int): Double {
        val s = v / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(c: Color): Double {
        val argb = c.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [fg] con opacidad [alpha] sobre [bg], redondeado por canal a 8 bits (resultado opaco). */
    fun blend(fg: Color, bg: Color, alpha: Double): Color {
        fun mix(shift: Int): Int {
            val f = (fg.toArgb() shr shift) and 0xFF
            val b = (bg.toArgb() shr shift) and 0xFF
            return (f * alpha + b * (1 - alpha)).roundToInt()
        }
        return Color(red = mix(16), green = mix(8), blue = mix(0))
    }
}

/**
 * Contrastes del tema con umbrales MINIMOS de WCAG propios (no los numeros del documento de diseno): texto >= 4,5:1;
 * iconos, elementos grandes y bordes de campo >= 3:1. Se comprueban los pares de roles M3 que de verdad se usan y los
 * pares de los tokens que quedan fuera de rol, en claro y en oscuro.
 */
class ThemeContrastTest {

    private class Mode(val name: String, val palette: BrainCardsPalette, val scheme: ColorScheme)

    private val modes = listOf(
        Mode("claro", LightPalette, LightColorScheme),
        Mode("oscuro", DarkPalette, DarkColorScheme)
    )

    private class Pair(val label: String, val fg: Color, val bg: Color)

    private fun round2(v: Double): Double = (v * 100).roundToInt() / 100.0

    private fun textPairs(m: Mode): List<Pair> {
        val s = m.scheme
        val p = m.palette
        return listOf(
            Pair("onPrimary/primary", s.onPrimary, s.primary),
            Pair("onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer),
            Pair("onSecondary/secondary", s.onSecondary, s.secondary),
            Pair("onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
            Pair("onTertiary/tertiary", s.onTertiary, s.tertiary),
            Pair("onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
            Pair("onError/error", s.onError, s.error),
            Pair("onErrorContainer/errorContainer", s.onErrorContainer, s.errorContainer),
            Pair("onBackground/background", s.onBackground, s.background),
            Pair("onSurface/surface", s.onSurface, s.surface),
            Pair("onSurface/surfaceVariant", s.onSurface, s.surfaceVariant),
            Pair("onSurfaceVariant/surface", s.onSurfaceVariant, s.surface),
            Pair("onSurfaceVariant/background", s.onSurfaceVariant, s.background),
            Pair("onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant),
            Pair("inverseOnSurface/inverseSurface", s.inverseOnSurface, s.inverseSurface),
            Pair("inversePrimary/inverseSurface", s.inversePrimary, s.inverseSurface),
            Pair("primary/background (enlaces)", s.primary, s.background),
            Pair("primary/surface (enlaces)", s.primary, s.surface),
            Pair("error/surface", s.error, s.surface),
            Pair("error/background", s.error, s.background),
            // fuera de rol
            Pair("ctaText/cta", p.ctaText, p.cta),
            Pair("chipTeal/chipTealBg", p.chipTeal, p.chipTealBg),
            Pair("chipGray/chipGrayBg", p.chipGray, p.chipGrayBg),
            Pair("brand/brandChip", p.brand, p.brandChip),
            Pair("chipTeal/surface", p.chipTeal, p.surface),
            Pair("text/chipTealBg", p.text, p.chipTealBg)
        )
    }

    private fun iconPairs(m: Mode): List<Pair> {
        val s = m.scheme
        val p = m.palette
        return listOf(
            Pair("outline/surface (borde de campos)", s.outline, s.surface),
            Pair("primary/background (iconos)", s.primary, s.background),
            Pair("primary/surface (iconos)", s.primary, s.surface),
            Pair("onSurfaceVariant/surface (iconos)", s.onSurfaceVariant, s.surface),
            Pair("error/surface (iconos)", s.error, s.surface),
            Pair("cta/ground (elemento grande)", p.cta, p.ground),
            Pair("chipTeal/surface (iconos)", p.chipTeal, p.surface)
        )
    }

    @Test
    fun la_funcion_de_ratio_da_los_valores_conocidos_de_wcag() {
        assertEquals(21.0, Wcag.ratio(Color.Black, Color.White), 0.001)
        assertEquals(1.0, Wcag.ratio(Color(0xFF123456), Color(0xFF123456)), 0.0001)
        // #777777 sobre blanco: 4,48:1 (el limite clasico: no llega a 4,5).
        assertEquals(4.478, Wcag.ratio(Color(0xFF777777), Color.White), 0.005)
        assertEquals(Wcag.ratio(Color.Black, Color.White), Wcag.ratio(Color.White, Color.Black), 0.0)
    }

    @Test
    fun todos_los_pares_de_texto_cumplen_4_5_a_1_en_claro_y_en_oscuro() {
        val failures = modes.flatMap { m ->
            textPairs(m).mapNotNull { p ->
                val r = Wcag.ratio(p.fg, p.bg)
                if (r < 4.5) "${m.name}: ${p.label} = ${round2(r)}" else null
            }
        }
        assertTrue(failures.isEmpty(), "pares de texto por debajo de 4,5:1 -> $failures")
    }

    @Test
    fun iconos_y_elementos_grandes_cumplen_3_a_1_en_claro_y_en_oscuro() {
        val failures = modes.flatMap { m ->
            iconPairs(m).mapNotNull { p ->
                val r = Wcag.ratio(p.fg, p.bg)
                if (r < 3.0) "${m.name}: ${p.label} = ${round2(r)}" else null
            }
        }
        assertTrue(failures.isEmpty(), "pares de iconos por debajo de 3:1 -> $failures")
    }

    @Test
    fun outline_es_la_mezcla_mas_suave_de_textSecondary_sobre_surface_que_alcanza_3_a_1() {
        for (m in modes) {
            val p = m.palette
            // Primera opacidad (pasos de 1 %) cuya mezcla sobre surface llega a 3:1.
            val alphaPercent = (1..100).first { a ->
                Wcag.ratio(Wcag.blend(p.textSecondary, p.surface, a / 100.0), p.surface) >= 3.0
            }
            val expected = Wcag.blend(p.textSecondary, p.surface, alphaPercent / 100.0)
            assertEquals(expected.toArgb(), p.outline.toArgb(), "${m.name}: outline debe ser textSecondary al $alphaPercent % sobre surface")
            assertTrue(Wcag.ratio(p.outline, p.surface) >= 3.0, "${m.name}: outline sobre surface >= 3:1")
            if (alphaPercent > 1) {
                val softer = Wcag.blend(p.textSecondary, p.surface, (alphaPercent - 1) / 100.0)
                assertTrue(Wcag.ratio(softer, p.surface) < 3.0, "${m.name}: con 1 % menos de opacidad ya no llega a 3:1")
            }
        }
    }
}
