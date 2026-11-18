package org.braincards.app.presentation.screens.navigation

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Coreografia de EntryPending y contrato de salida D8: funciones puras, sin reloj ni temporizadores. */
class SplashChoreographyTest {

    private fun assertNear(expected: Float, actual: Float, eps: Float = 1e-4f, msg: String = "") =
        assertTrue(abs(expected - actual) <= eps, "$msg esperado=$expected real=$actual")

    // ---- constantes del handoff ----

    @Test
    fun duraciones_del_handoff() {
        assertEquals(450, SPLASH_CONVERGE_MS)
        assertEquals(150, SPLASH_PULSE_MS)
        assertEquals(600, SPLASH_TOTAL_MS)
        assertEquals(150, SPLASH_ALPHA_RAMP_MS) // PROVISIONAL (13b2), se valora a ojo
        assertTrue(SPLASH_ALPHA_RAMP_MS in 1..SPLASH_CONVERGE_MS, "la rampa cabe en la convergencia")
        assertEquals(250, SPLASH_FADE_MS)
        assertEquals(450, SPLASH_MIN_HOLD_MS)
    }

    @Test
    fun medidas_del_handoff() {
        assertEquals(58f, SPLASH_OFFSET_UNITS)
        assertEquals(230f, SPLASH_VECTOR_UNITS)
        assertEquals(1.06f, SPLASH_PULSE_PEAK)
    }

    @Test
    fun easeOutBack_pasa_de_largo_y_acaba_en_1() {
        assertEquals(0f, SplashEaseOutBack.transform(0f))
        assertEquals(1f, SplashEaseOutBack.transform(1f))
        val peak = (1..99).maxOf { SplashEaseOutBack.transform(it / 100f) }
        assertTrue(peak > 1.05f, "pico $peak: tiene que pasar de largo (rebote leve)")
    }

    // ---- pose ----

    @Test
    fun pose_inicial_hemisferios_fuera_e_invisibles() {
        assertEquals(SplashPose(leftUnits = -58f, rightUnits = 58f, scale = 1f, alpha = 0f), splashPose(0f))
    }

    @Test
    fun al_acabar_la_convergencia_estan_en_su_sitio_sin_pulso_aun() {
        assertEquals(SplashPose.Rest, splashPose(450f))
    }

    @Test
    fun al_acabar_la_coreografia_es_el_reposo_y_fuera_de_rango_se_acota() {
        assertEquals(SplashPose.Rest, splashPose(600f))
        assertEquals(SplashPose.Rest, splashPose(10_000f))
        assertEquals(splashPose(0f), splashPose(-50f))
    }

    @Test
    fun los_hemisferios_son_simetricos_y_cruzan_el_centro_antes_de_asentarse() {
        for (t in 0..600 step 5) {
            val p = splashPose(t.toFloat())
            assertNear(0f, p.leftUnits + p.rightUnits, msg = "simetria en $t")
        }
        val crossed = (1 until 450).any { splashPose(it.toFloat()).leftUnits > 0.5f }
        assertTrue(crossed, "el izquierdo debe pasar al lado derecho (overshoot) antes de asentarse")
    }

    @Test
    fun entran_desde_fuera_sin_pasar_del_desplazamiento_inicial() {
        for (t in 0..450 step 5) {
            val p = splashPose(t.toFloat())
            assertTrue(p.leftUnits <= 58f * 0.15f && p.leftUnits >= -58f, "left=${p.leftUnits} en $t")
        }
        assertTrue(splashPose(100f).leftUnits < -10f, "a los 100 ms aun esta lejos del centro")
    }

    @Test
    fun la_pose_en_los_limites_449_450_599_600() {
        val p449 = splashPose(449f)
        assertEquals(1f, p449.scale, "449: aun sin pulso")
        assertEquals(1f, p449.alpha, "449: opaco")
        assertTrue(abs(p449.leftUnits) < 0.1f && abs(p449.rightUnits) < 0.1f, "449: hemisferios casi asentados (${p449.leftUnits})")
        assertEquals(SplashPose.Rest, splashPose(450f), "450: asentado, el pulso aun no ha empezado")
        val p599 = splashPose(599f)
        assertTrue(p599.scale >= 1f && p599.scale < 1.002f, "599: final del pulso (${p599.scale})")
        assertEquals(1f, p599.alpha, "599: opaco")
        assertEquals(0f, p599.leftUnits, "599: hemisferios en su sitio")
        assertEquals(SplashPose.Rest, splashPose(600f), "600: reposo")
    }

    @Test
    fun la_opacidad_sube_lineal_durante_la_rampa_y_despues_es_1() {
        assertEquals(0f, splashPose(0f).alpha)
        assertNear(0.5f, splashPose(SPLASH_ALPHA_RAMP_MS / 2f).alpha)
        assertEquals(1f, splashPose(SPLASH_ALPHA_RAMP_MS.toFloat()).alpha)
        assertEquals(1f, splashPose(449f).alpha)
        var prev = 0f
        for (t in 0..600 step 10) {
            val a = splashPose(t.toFloat()).alpha
            assertTrue(a >= prev, "monotona en $t")
            prev = a
        }
    }

    @Test
    fun el_pulso_solo_ocurre_entre_450_y_600_con_pico_1_06_a_los_525() {
        for (t in 0..450) assertEquals(1f, splashPose(t.toFloat()).scale, "sin pulso antes de 450 (t=$t)")
        assertNear(1.06f, splashPose(525f).scale, msg = "pico")
        assertEquals(1f, splashPose(600f).scale)
        var max = 0f
        for (t in 450..600) {
            val s = splashPose(t.toFloat()).scale
            assertTrue(s >= 1f && s <= 1.06f + 1e-4f, "escala $s en $t")
            if (s > max) max = s
        }
        assertNear(1.06f, max, 1e-3f)
        assertTrue(splashPose(490f).scale > 1.01f && splashPose(560f).scale > 1.01f, "sube y baja, no es un pico aislado")
    }

    @Test
    fun conversion_de_unidades_a_pixeles() {
        assertEquals(58f, splashUnitsToPx(58f, 230f))
        assertEquals(29f, splashUnitsToPx(58f, 115f))
        assertEquals(0f, splashUnitsToPx(0f, 480f))
    }

    // ---- contrato D8 ----

    private fun plan(r: Long) = planSplashExit(r, reducedMotion = false)

    @Test
    fun d8_listo_antes_de_450_espera_hasta_450_sin_pulso() {
        for (r in listOf(0L, 1L, 200L, 449L)) {
            assertEquals(SplashExitPlan(fadeStartMs = 450, frozenElapsedMs = 450, fadeMs = 250), plan(r), "r=$r")
        }
        // congelado en 450 = hemisferios asentados y escala 1: el pulso NO se lanza
        assertEquals(1f, splashPose(plan(449).frozenElapsedMs.toFloat()).scale)
    }

    @Test
    fun d8_entre_450_y_599_se_corta_en_el_fotograma_actual() {
        assertEquals(SplashExitPlan(450, 450, 250), plan(450))
        assertEquals(SplashExitPlan(451, 451, 250), plan(451))
        assertEquals(SplashExitPlan(525, 525, 250), plan(525))
        assertEquals(SplashExitPlan(599, 599, 250), plan(599))
    }

    @Test
    fun d8_a_partir_de_600_fundido_en_cuanto_esta_lista_con_el_cerebro_en_reposo() {
        assertEquals(SplashExitPlan(600, 600, 250), plan(600))
        assertEquals(SplashExitPlan(601, 600, 250), plan(601))
        assertEquals(SplashExitPlan(5_000, 600, 250), plan(5_000))
        assertEquals(SplashPose.Rest, splashPose(plan(5_000).frozenElapsedMs.toFloat()))
    }

    @Test
    fun d8_reducir_animaciones_cerebro_completo_sin_fundido() {
        for (r in listOf(0L, 449L, 450L, 599L, 600L, 5_000L)) {
            val p = planSplashExit(r, reducedMotion = true)
            assertEquals(0, p.fadeMs, "r=$r sin fundido")
            assertEquals(600L, p.frozenElapsedMs, "r=$r cerebro completo")
            assertEquals(r, p.fadeStartMs, "r=$r sin espera")
        }
    }

    @Test
    fun d8_un_tiempo_negativo_se_trata_como_cero() {
        assertEquals(plan(0), plan(-30))
    }
}
