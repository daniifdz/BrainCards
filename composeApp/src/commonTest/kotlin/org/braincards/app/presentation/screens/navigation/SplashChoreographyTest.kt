package org.braincards.app.presentation.screens.navigation

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coreografia de EntryPending (variante C de 13b3: convergencia de 600 ms, pulso hasta 750 ms) y contrato de salida D8 con los
 * umbrales nuevos (600 / 750 ms): funciones puras, sin reloj ni temporizadores.
 */
class SplashChoreographyTest {

    private fun assertNear(expected: Float, actual: Float, eps: Float = 1e-4f, msg: String = "") =
        assertTrue(abs(expected - actual) <= eps, "$msg esperado=$expected real=$actual")

    // ---- constantes ----

    @Test
    fun duraciones_de_la_variante_C() {
        assertEquals(600, SPLASH_CONVERGE_MS)
        assertEquals(150, SPLASH_PULSE_MS)
        assertEquals(750, SPLASH_TOTAL_MS)
        assertEquals(80, SPLASH_ALPHA_RAMP_MS) // 13b3: visibles desde el principio del movimiento
        assertTrue(SPLASH_ALPHA_RAMP_MS in 1..SPLASH_CONVERGE_MS, "la rampa cabe en la convergencia")
        assertEquals(250, SPLASH_FADE_MS)
        assertEquals(600, SPLASH_MIN_HOLD_MS)
    }

    @Test
    fun medidas() {
        assertEquals(84f, SPLASH_OFFSET_UNITS)
        assertEquals(230f, SPLASH_VECTOR_UNITS)
        assertEquals(1.06f, SPLASH_PULSE_PEAK)
    }

    // ---- curva de la convergencia ----

    private fun progressAt(ms: Float) = SplashConvergeEasing.transform((ms / SPLASH_CONVERGE_MS).coerceIn(0f, 1f))

    @Test
    fun la_curva_esta_parametrizada_como_cubic_bezier_0_55_0_0_55_1_3() {
        assertEquals(0.55f, SPLASH_CURVE_X1)
        assertEquals(0f, SPLASH_CURVE_Y1)
        assertEquals(0.55f, SPLASH_CURVE_X2)
        assertEquals(1.3f, SPLASH_CURVE_Y2)
    }

    @Test
    fun la_curva_empieza_en_0_y_acaba_en_1() {
        assertEquals(0f, SplashConvergeEasing.transform(0f))
        assertEquals(1f, SplashConvergeEasing.transform(1f))
    }

    @Test
    fun la_convergencia_esta_repartida_no_concentrada_al_principio() {
        // Objetivo del ajuste: no mas de ~40 % del recorrido a los 100 ms (la curva anterior llevaba el 76 %).
        assertTrue(progressAt(100f) <= 0.40f, "a los 100 ms = ${progressAt(100f)}")
        assertTrue(progressAt(100f) in 0.03f..0.065f, "a los 100 ms = ${progressAt(100f)} (esperado ~4,6 %)")
        assertTrue(progressAt(200f) in 0.19f..0.23f, "a los 200 ms = ${progressAt(200f)} (esperado ~21 %)")
        assertTrue(progressAt(300f) in 0.50f..0.56f, "a los 300 ms = ${progressAt(300f)} (esperado ~52,7 %)")
        assertTrue(progressAt(450f) in 0.97f..1.0f, "a los 450 ms = ${progressAt(450f)} (esperado ~99 %)")
        assertEquals(1f, progressAt(600f))
    }

    @Test
    fun la_curva_pasa_de_largo_un_poco_al_final_y_vuelve_a_1() {
        val samples = (0..SPLASH_CONVERGE_MS).map { it to progressAt(it.toFloat()) }
        val (peakMs, peak) = samples.maxByOrNull { it.second }!!
        assertTrue(peak in 1.03f..1.06f, "pico = $peak (esperado ~104,5 %)")
        assertTrue(peakMs in 450..580, "el pico llega hacia el final ($peakMs ms), no al principio")
        // hasta el pico sube de forma monotona (sin vaivenes)
        assertTrue(samples.take(peakMs + 1).zipWithNext().all { (a, b) -> b.second >= a.second }, "monotona hasta el pico")
        assertEquals(1f, samples.last().second)
    }

    @Test
    fun el_recorrido_de_la_pose_sigue_la_curva_y_el_desplazamiento_de_84() {
        for (ms in listOf(0f, 100f, 200f, 300f, 400f, 500f, 600f)) {
            val p = splashPose(ms)
            assertNear(1f - abs(p.leftUnits) * (if (p.leftUnits <= 0f) 1f else -1f) / SPLASH_OFFSET_UNITS, progressAt(ms), 1e-3f, "recorrido en $ms ms")
        }
        assertNear(-84f, splashPose(0f).leftUnits)
        assertNear(84f, splashPose(0f).rightUnits)
    }

    // ---- pose ----

    @Test
    fun pose_inicial_hemisferios_fuera_e_invisibles() {
        assertEquals(SplashPose(leftUnits = -84f, rightUnits = 84f, scale = 1f, alpha = 0f), splashPose(0f))
    }

    @Test
    fun al_acabar_la_convergencia_estan_en_su_sitio_sin_pulso_aun() {
        assertEquals(SplashPose.Rest, splashPose(600f))
    }

    @Test
    fun al_acabar_la_coreografia_es_el_reposo_y_fuera_de_rango_se_acota() {
        assertEquals(SplashPose.Rest, splashPose(750f))
        assertEquals(SplashPose.Rest, splashPose(10_000f))
        assertEquals(splashPose(0f), splashPose(-50f))
    }

    @Test
    fun los_hemisferios_son_simetricos_y_cruzan_el_centro_antes_de_asentarse() {
        for (t in 0..750 step 5) {
            val p = splashPose(t.toFloat())
            assertNear(0f, p.leftUnits + p.rightUnits, msg = "simetria en $t")
        }
        val crossed = (1 until SPLASH_CONVERGE_MS).any { splashPose(it.toFloat()).leftUnits > 0.5f }
        assertTrue(crossed, "el izquierdo debe pasar al lado derecho (overshoot) antes de asentarse")
    }

    @Test
    fun entran_desde_fuera_sin_pasar_del_desplazamiento_inicial() {
        for (t in 0..SPLASH_CONVERGE_MS step 5) {
            val p = splashPose(t.toFloat())
            assertTrue(p.leftUnits <= SPLASH_OFFSET_UNITS * 0.15f && p.leftUnits >= -SPLASH_OFFSET_UNITS, "left=${p.leftUnits} en $t")
        }
        assertTrue(splashPose(100f).leftUnits < -70f, "a los 100 ms aun esta casi a todo el desplazamiento (${splashPose(100f).leftUnits})")
    }

    @Test
    fun la_pose_en_los_limites_599_600_749_750() {
        val p599 = splashPose(599f)
        assertEquals(1f, p599.scale, "599: aun sin pulso")
        assertEquals(1f, p599.alpha, "599: opaco")
        assertTrue(abs(p599.leftUnits) < 0.25f && abs(p599.rightUnits) < 0.25f, "599: hemisferios casi asentados (${p599.leftUnits})")
        assertEquals(SplashPose.Rest, splashPose(600f), "600: asentado, el pulso aun no ha empezado")
        val p749 = splashPose(749f)
        assertTrue(p749.scale >= 1f && p749.scale < 1.002f, "749: final del pulso (${p749.scale})")
        assertEquals(1f, p749.alpha, "749: opaco")
        assertEquals(0f, p749.leftUnits, "749: hemisferios en su sitio")
        assertEquals(SplashPose.Rest, splashPose(750f), "750: reposo")
    }

    @Test
    fun la_opacidad_sube_lineal_durante_la_rampa_y_despues_es_1() {
        assertEquals(0f, splashPose(0f).alpha)
        assertNear(0.5f, splashPose(SPLASH_ALPHA_RAMP_MS / 2f).alpha)
        assertEquals(1f, splashPose(SPLASH_ALPHA_RAMP_MS.toFloat()).alpha)
        assertEquals(1f, splashPose(599f).alpha)
        var prev = 0f
        for (t in 0..750 step 10) {
            val a = splashPose(t.toFloat()).alpha
            assertTrue(a >= prev, "monotona en $t")
            prev = a
        }
    }

    @Test
    fun el_pulso_solo_ocurre_entre_600_y_750_con_pico_1_06_a_los_675() {
        for (t in 0..600) assertEquals(1f, splashPose(t.toFloat()).scale, "sin pulso antes de 600 (t=$t)")
        assertNear(1.06f, splashPose(675f).scale, msg = "pico")
        assertEquals(1f, splashPose(750f).scale)
        var max = 0f
        for (t in 600..750) {
            val s = splashPose(t.toFloat()).scale
            assertTrue(s >= 1f && s <= 1.06f + 1e-4f, "escala $s en $t")
            if (s > max) max = s
        }
        assertNear(1.06f, max, 1e-3f)
        assertTrue(splashPose(640f).scale > 1.01f && splashPose(710f).scale > 1.01f, "sube y baja, no es un pico aislado")
    }

    @Test
    fun conversion_de_unidades_a_pixeles() {
        assertEquals(58f, splashUnitsToPx(58f, 230f))
        assertEquals(29f, splashUnitsToPx(58f, 115f))
        assertEquals(0f, splashUnitsToPx(0f, 480f))
    }

    // ---- contrato D8 con los umbrales de la variante C ----

    private fun plan(r: Long) = planSplashExit(r, reducedMotion = false)

    @Test
    fun d8_listo_antes_de_600_espera_hasta_600_sin_pulso() {
        for (r in listOf(0L, 1L, 200L, 450L, 599L)) {
            assertEquals(SplashExitPlan(fadeStartMs = 600, frozenElapsedMs = 600, fadeMs = 250), plan(r), "r=$r")
        }
        // congelado en 600 = hemisferios asentados y escala 1: el pulso NO se lanza
        assertEquals(1f, splashPose(plan(599).frozenElapsedMs.toFloat()).scale)
    }

    @Test
    fun d8_entre_600_y_749_se_corta_en_el_fotograma_actual() {
        assertEquals(SplashExitPlan(600, 600, 250), plan(600))
        assertEquals(SplashExitPlan(601, 601, 250), plan(601))
        assertEquals(SplashExitPlan(675, 675, 250), plan(675))
        assertEquals(SplashExitPlan(749, 749, 250), plan(749))
    }

    @Test
    fun d8_a_partir_de_750_fundido_en_cuanto_esta_lista_con_el_cerebro_en_reposo() {
        assertEquals(SplashExitPlan(750, 750, 250), plan(750))
        assertEquals(SplashExitPlan(751, 750, 250), plan(751))
        assertEquals(SplashExitPlan(5_000, 750, 250), plan(5_000))
        assertEquals(SplashPose.Rest, splashPose(plan(5_000).frozenElapsedMs.toFloat()))
    }

    @Test
    fun d8_reducir_animaciones_cerebro_completo_sin_fundido() {
        for (r in listOf(0L, 599L, 600L, 749L, 750L, 5_000L)) {
            val p = planSplashExit(r, reducedMotion = true)
            assertEquals(0, p.fadeMs, "r=$r sin fundido")
            assertEquals(750L, p.frozenElapsedMs, "r=$r cerebro completo")
            assertEquals(r, p.fadeStartMs, "r=$r sin espera")
        }
    }

    @Test
    fun d8_un_tiempo_negativo_se_trata_como_cero() {
        assertEquals(plan(0), plan(-30))
    }
}
