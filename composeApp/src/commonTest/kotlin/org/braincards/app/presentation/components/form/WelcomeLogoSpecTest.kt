package org.braincards.app.presentation.components.form

import kotlin.test.Test
import kotlin.test.assertEquals

/** Tamanos del simbolo de la Bienvenida (handoff v4.10): bordes 360 dp de ancho y 600 dp de alto, ambos tipos de dispositivo. */
class WelcomeLogoSpecTest {

    private val desktopNormal = WelcomeLogoSpec(iconDp = 88f, wordmarkSp = 54f, gapDp = 14f)
    private val mobileNormal = WelcomeLogoSpec(iconDp = 80f, wordmarkSp = 50f, gapDp = 12f)
    private val desktopCompact = WelcomeLogoSpec(iconDp = 44f, wordmarkSp = 27f, gapDp = 10f)
    private val mobileCompact = WelcomeLogoSpec(iconDp = 40f, wordmarkSp = 25f, gapDp = 10f)

    private data class Case(val name: String, val w: Float, val h: Float, val mobile: Boolean, val expected: WelcomeLogoSpec)

    @Test
    fun tabla_de_tamanos() {
        listOf(
            Case("desktop normal", 960f, 700f, false, desktopNormal),
            Case("movil normal", 390f, 844f, true, mobileNormal),
            Case("desktop ancho 359 -> compacto", 359f, 700f, false, desktopCompact),
            Case("desktop ancho 360 -> normal", 360f, 700f, false, desktopNormal),
            Case("movil ancho 359 -> compacto", 359f, 844f, true, mobileCompact),
            Case("movil ancho 360 -> normal", 360f, 844f, true, mobileNormal),
            Case("desktop alto 599 -> compacto", 960f, 599f, false, desktopCompact),
            Case("desktop alto 600 -> normal", 960f, 600f, false, desktopNormal),
            Case("movil alto 599 -> compacto", 390f, 599f, true, mobileCompact),
            Case("movil alto 600 -> normal", 390f, 600f, true, mobileNormal),
            Case("movil 320x568 compacto", 320f, 568f, true, mobileCompact),
            Case("movil horizontal 800x390 compacto", 800f, 390f, true, mobileCompact),
            Case("desktop 300x300 compacto", 300f, 300f, false, desktopCompact)
        ).forEach { c ->
            assertEquals(c.expected, welcomeLogoSpec(c.w, c.h, c.mobile), c.name)
        }
    }
}
