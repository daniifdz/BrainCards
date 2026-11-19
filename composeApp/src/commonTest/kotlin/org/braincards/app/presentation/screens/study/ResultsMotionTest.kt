package org.braincards.app.presentation.screens.study

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Celebracion de Resultados: conteo, pulso del badge y medidas. Funciones puras: sin reloj ni temporizadores. */
class ResultsMotionTest {

    @Test
    fun constantes_del_handoff() {
        assertEquals(500, RESULTS_COUNT_MS)
        assertEquals(600, RESULTS_CELEBRATION_MAX_MS)
        assertEquals(0.45f, RESULTS_PULSE_DAMPING)
        assertEquals(500f, RESULTS_PULSE_STIFFNESS)
        assertEquals(1.08f, RESULTS_PULSE_PEAK)
        assertTrue(RESULTS_COUNT_MS <= RESULTS_CELEBRATION_MAX_MS)
    }

    private val targets = listOf(1, 2, 3, 5, 7, 12, 27, 100, 10_000)

    @Test
    fun el_conteo_empieza_en_0_y_acaba_en_el_objetivo() {
        for (t in targets) {
            assertEquals(0, countUpValue(t, 0f), "inicio de $t")
            assertEquals(t, countUpValue(t, 1f), "final de $t")
        }
    }

    @Test
    fun un_objetivo_cero_o_negativo_nunca_cuenta() {
        for (f in listOf(0f, 0.3f, 0.5f, 1f, 2f, -1f)) {
            assertEquals(0, countUpValue(0, f), "objetivo 0, fraccion $f")
            assertEquals(0, countUpValue(-4, f), "objetivo negativo, fraccion $f")
        }
    }

    @Test
    fun las_fracciones_fuera_de_rango_se_acotan() {
        assertEquals(12, countUpValue(12, 1.5f))
        assertEquals(12, countUpValue(12, 1000f))
        assertEquals(12, countUpValue(12, Float.MAX_VALUE))
        assertEquals(0, countUpValue(12, -0.5f))
        assertEquals(0, countUpValue(12, -1000f))
        assertEquals(0, countUpValue(12, -Float.MAX_VALUE))
        assertEquals(10_000, countUpValue(10_000, 7f))
    }

    @Test
    fun es_monotono_entero_y_no_sale_de_0_a_objetivo() {
        for (t in targets) {
            var prev = 0
            for (i in 0..200) {
                val v = countUpValue(t, i / 200f)
                assertTrue(v in 0..t, "objetivo $t en $i/200 = $v")
                assertTrue(v >= prev, "monotono: objetivo $t en $i/200 ($v < $prev)")
                prev = v
            }
        }
    }

    @Test
    fun usa_fastOutSlowIn_no_lineal() {
        // FastOutSlowIn arranca despacio, acelera y frena al final: a mitad de tiempo ya se lleva ~80 %, y a 3/4 casi todo.
        val half = countUpValue(100, 0.5f)
        assertTrue(half in 70..90, "a mitad de tiempo = $half (lineal daria 50)")
        assertTrue(countUpValue(100, 0.75f) > 90, "a tres cuartos ya casi esta (lineal daria 75)")
        assertTrue(countUpValue(100, 0.25f) < 25, "arranque lento (lineal daria 25)")
    }

    @Test
    fun redondea_al_mas_cercano_no_trunca() {
        var distintos = 0
        for (t in listOf(3, 7, 12, 27)) {
            for (i in 0..100) {
                val exact = t * FastOutSlowInEasing.transform(i / 100f)
                val expected = (exact + 0.5f).toInt()
                assertEquals(expected, countUpValue(t, i / 100f), "objetivo $t en $i/100")
                if (expected != exact.toInt()) distintos++
            }
        }
        assertTrue(distintos > 0, "el barrido debe contener casos donde redondear y truncar difieren")
    }

    private fun springAt(ms: Int): Float {
        val spec = RESULTS_PULSE_SPRING.vectorize(Float.VectorConverter)
        return spec.getValueFromNanos(
            ms * 1_000_000L,
            AnimationVector1D(1f),
            AnimationVector1D(1f),
            AnimationVector1D(RESULTS_PULSE_KICK_VELOCITY)
        ).value
    }

    @Test
    fun el_pulso_parte_de_1_y_pasa_por_1_08_con_el_muelle_real() {
        assertEquals(1f, springAt(0), "reposo inicial")
        val peak = (0..1000).maxOf { springAt(it) }
        assertTrue(abs(peak - 1.08f) <= 0.002f, "pico real = $peak (esperado 1.08)")
    }

    @Test
    fun el_pulso_es_un_solo_rebote_y_se_asienta_antes_de_600_ms() {
        // Subamortiguado (0.45): rebota alrededor de 1.0 y se apaga en ~400 ms; el muelle real lo cumple.
        val lastOutside = (0..1000).last { abs(springAt(it) - 1f) >= 0.005f }
        assertTrue(lastOutside <= 450, "asentado (+-0,5 %) desde ${lastOutside + 1} ms")
        assertTrue(lastOutside + 1 <= RESULTS_CELEBRATION_MAX_MS)
        assertTrue(abs(springAt(1000) - 1f) < 1e-4f, "reposo final")
        val minimum = (0..1000).minOf { springAt(it) }
        assertTrue(minimum > 0.95f, "el rebote de vuelta es pequeno ($minimum)")
    }

    @Test
    fun la_velocidad_inicial_sale_del_pico_pedido() {
        assertEquals(0f, pulseKickVelocity(0.45f, 500f, 1f), "pico 1.0 = sin pulso")
        assertTrue(RESULTS_PULSE_KICK_VELOCITY > 3.0f && RESULTS_PULSE_KICK_VELOCITY < 3.3f, "v = $RESULTS_PULSE_KICK_VELOCITY")
        assertTrue(pulseKickVelocity(0.45f, 500f, 1.16f) > 1.9f * RESULTS_PULSE_KICK_VELOCITY, "el doble de pico pide ~el doble de velocidad")
    }

    @Test
    fun la_celebracion_solo_se_juega_una_vez_y_nunca_con_reducir_animaciones() {
        assertTrue(shouldPlayResultsCelebration(alreadySeen = false, reducedMotion = false))
        assertFalse(shouldPlayResultsCelebration(alreadySeen = true, reducedMotion = false), "tras rotar")
        assertFalse(shouldPlayResultsCelebration(alreadySeen = false, reducedMotion = true), "reducir animaciones")
        assertFalse(shouldPlayResultsCelebration(alreadySeen = true, reducedMotion = true))
    }

    @Test
    fun medidas_del_badge_segun_el_mockup() {
        assertEquals(ResultsBadgeSpec(sizeDp = 64, cornerPercent = 31), resultsBadgeSpec(480f), "tarjeta normal: 64 px / 20 px")
        assertEquals(ResultsBadgeSpec(sizeDp = 56, cornerPercent = 32), resultsBadgeSpec(360f), "movil: 56 px / 18 px")
        assertEquals(ResultsBadgeSpec(64, 31), resultsBadgeSpec(1200f))
    }

    @Test
    fun el_umbral_compacto_esta_entre_las_dos_tarjetas_del_mockup() {
        assertEquals(420f, RESULTS_COMPACT_WIDTH_DP)
        assertEquals(56, resultsBadgeSpec(419.9f).sizeDp)
        assertEquals(64, resultsBadgeSpec(420f).sizeDp)
    }
}
