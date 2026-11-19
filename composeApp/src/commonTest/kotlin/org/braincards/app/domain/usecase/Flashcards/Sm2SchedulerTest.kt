package org.braincards.app.domain.usecase.Flashcards

import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.models.ReviewQuality.AGAIN
import org.braincards.app.domain.models.ReviewQuality.EASY
import org.braincards.app.domain.models.ReviewQuality.GOOD
import org.braincards.app.domain.models.ReviewQuality.HARD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Sm2SchedulerTest {

    private fun next(ef: Double, interval: Int, repetitions: Int, q: ReviewQuality) =
        calculateNextSchedule(Sm2Schedule(ef, interval, repetitions), q)

    /** Repite la MISMA calidad [steps] veces desde una tarjeta nueva y devuelve el estado tras cada paso. */
    private fun repeat(q: ReviewQuality, steps: Int): List<Sm2Schedule> {
        val out = mutableListOf<Sm2Schedule>()
        var s = Sm2Schedule.NEW
        repeat(steps) {
            s = calculateNextSchedule(s, q)
            out += s
        }
        return out
    }

    @Test
    fun primer_acierto_dificil_y_bien_dan_1_dia_y_facil_4() {
        assertEquals(1, next(2.5, 0, 0, HARD).interval)
        assertEquals(1, next(2.5, 0, 0, GOOD).interval)
        assertEquals(4, next(2.5, 0, 0, EASY).interval)
        assertEquals(1, next(2.5, 0, 0, GOOD).repetitions)
    }

    @Test
    fun segundo_acierto_dificil_2_bien_6_facil_8() {
        assertEquals(2, next(2.5, 1, 1, HARD).interval)
        assertEquals(6, next(2.5, 1, 1, GOOD).interval)
        assertEquals(8, next(2.5, 1, 1, EASY).interval)
    }

    @Test
    fun tercer_acierto_dificil_7_bien_15_facil_20() {
        assertEquals(7, next(2.5, 6, 2, HARD).interval)
        assertEquals(15, next(2.5, 6, 2, GOOD).interval) // round(6 * 2.5): Bien no cambia
        assertEquals(20, next(2.5, 6, 2, EASY).interval) // round(15 * 1.3)
    }

    @Test
    fun dificil_usa_el_multiplicador_1_2_con_intervalos_largos() {
        assertEquals(24, next(2.5, 20, 3, HARD).interval) // round(20 * 1.2) = 24 > 20 + 1
    }

    @Test
    fun facil_usa_el_bonus_1_3_sobre_el_intervalo_de_bien_con_intervalos_largos() {
        assertEquals(50, next(2.5, 20, 3, GOOD).interval)
        assertEquals(65, next(2.5, 20, 3, EASY).interval) // round(50 * 1.3)
    }

    @Test
    fun bien_nunca_se_queda_igual_aunque_el_factor_sea_el_minimo() {
        assertEquals(2, next(1.3, 1, 3, GOOD).interval) // round(1 * 1.3) = 1 -> al menos intervalo + 1
    }

    @Test
    fun otra_vez_resetea_repeticiones_e_intervalo_y_baja_el_factor_0_20() {
        val s = next(2.5, 20, 5, AGAIN)
        assertEquals(0, s.repetitions)
        assertEquals(0, s.interval)
        assertEquals(2.3, s.easeFactor)
    }

    @Test
    fun tras_un_fallo_el_siguiente_acierto_vuelve_al_primer_paso() {
        val failed = next(2.5, 20, 5, AGAIN)
        val recovered = calculateNextSchedule(failed, GOOD)
        assertEquals(1, recovered.repetitions)
        assertEquals(1, recovered.interval)
    }

    @Test
    fun el_factor_varia_por_calidad_otra_vez_menos_0_20_dificil_menos_0_15_bien_0_facil_mas_0_15() {
        assertEquals(2.3, next(2.5, 6, 2, AGAIN).easeFactor)
        assertEquals(2.35, next(2.5, 6, 2, HARD).easeFactor)
        assertEquals(2.5, next(2.5, 6, 2, GOOD).easeFactor)
        assertEquals(2.65, next(2.5, 6, 2, EASY).easeFactor)
    }

    @Test
    fun el_factor_no_baja_de_1_3() {
        assertEquals(1.3, next(1.35, 6, 2, AGAIN).easeFactor)  // 1.15 sin suelo
        assertEquals(1.3, next(1.4, 6, 2, HARD).easeFactor)    // 1.25 sin suelo
        assertEquals(1.3, next(1.3, 6, 2, AGAIN).easeFactor)
    }

    @Test
    fun el_factor_se_redondea_a_2_decimales_sin_arrastre() {
        val ef = repeat(HARD, 4).last().easeFactor
        assertEquals(1.9, ef) // 2.5 - 4 * 0.15, exacto
    }

    @Test
    fun repetir_bien_da_1_6_15_38_95() {
        val steps = repeat(GOOD, 5)
        assertEquals(listOf(1, 6, 15, 38, 95), steps.map { it.interval })
        assertEquals(2.5, steps.last().easeFactor)
    }

    @Test
    fun repetir_dificil_da_1_2_3_4_5() {
        val steps = repeat(HARD, 5)
        assertEquals(listOf(1, 2, 3, 4, 5), steps.map { it.interval })
        assertEquals(1.75, steps.last().easeFactor)
    }

    @Test
    fun repetir_facil_da_4_8_29_112_451() {
        val steps = repeat(EASY, 5)
        assertEquals(listOf(4, 8, 29, 112, 451), steps.map { it.interval })
        assertEquals(3.25, steps.last().easeFactor)
    }

    @Test
    fun desde_el_segundo_acierto_dificil_menor_que_bien_menor_que_facil_estrictamente() {
        var checked = 0
        var ef = 1.3
        while (ef <= 3.5) {
            for (interval in 1..300) {
                for (reps in 1..6) {
                    val hard = next(ef, interval, reps, HARD).interval
                    val good = next(ef, interval, reps, GOOD).interval
                    val easy = next(ef, interval, reps, EASY).interval
                    assertTrue(hard < good && good < easy, "ef=$ef interval=$interval reps=$reps -> $hard/$good/$easy")
                    assertTrue(hard >= 1, "Dificil nunca da 0 (ef=$ef interval=$interval reps=$reps)")
                    checked++
                }
            }
            ef += 0.05
        }
        assertTrue(checked > 10_000)
    }

    @Test
    fun en_el_primer_acierto_facil_supera_a_bien_y_dificil_empata_con_bien() {
        // Con intervalos en dias enteros no hay valor entre 0 (ahora) y 1: Dificil y Bien no se pueden separar.
        for (ef in listOf(1.3, 2.0, 2.5, 3.0)) {
            val hard = next(ef, 0, 0, HARD).interval
            val good = next(ef, 0, 0, GOOD).interval
            val easy = next(ef, 0, 0, EASY).interval
            assertEquals(good, hard)
            assertTrue(easy > good)
        }
    }

    @Test
    fun otra_vez_siempre_deja_repetitions_e_interval_a_0() {
        for (interval in listOf(0, 1, 6, 40, 400)) for (reps in 0..5) {
            val s = next(2.5, interval, reps, AGAIN)
            assertEquals(0, s.repetitions)
            assertEquals(0, s.interval)
        }
    }
}
