package org.braincards.app.presentation.screens.study

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.braincards.app.domain.models.CardType
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.models.StudyQueue
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Feedback de interaccion de Estudio: barra con muelle, pulso de los botones y su disparo por teclado. Funciones puras. */
@OptIn(ExperimentalCoroutinesApi::class)
class StudyMotionTest {

    private fun assertNear(expected: Float, actual: Float, eps: Float = 1e-4f, msg: String = "") =
        assertTrue(abs(expected - actual) <= eps, "$msg esperado=$expected real=$actual")

    // ---- constantes ----

    @Test
    fun constantes_de_movimiento() {
        assertEquals(0.6f, STUDY_PROGRESS_DAMPING)
        assertEquals(380f, STUDY_PROGRESS_STIFFNESS)
        assertEquals(60, STUDY_PRESS_DOWN_MS)
        assertEquals(0.95f, STUDY_PRESS_SCALE)
        assertEquals(0.12f, STUDY_PRESS_BRIGHTNESS)
        assertEquals(0.5f, STUDY_PRESS_UP_DAMPING)
        assertEquals(600f, STUDY_PRESS_UP_STIFFNESS)
    }

    // ---- barra de progreso ----

    private fun barAt(ms: Int, from: Float, to: Float): Float =
        STUDY_PROGRESS_SPRING.vectorize(Float.VectorConverter)
            .getValueFromNanos(ms * 1_000_000L, AnimationVector1D(from), AnimationVector1D(to), AnimationVector1D(0f)).value

    @Test
    fun la_barra_pasa_de_largo_del_destino_con_el_muelle_real() {
        val from = 0.5f
        val to = 0.5f + 1f / 12f
        val step = to - from
        val peak = (0..1000).maxOf { barAt(it, from, to) }
        val overshoot = (peak - to) / step
        // spring(0.6): exp(-pi * 0.6 / sqrt(1 - 0.36)) = ~9.5 % del tramo recorrido.
        assertTrue(overshoot > 0.07f && overshoot < 0.12f, "rebote = ${overshoot * 100} % del tramo (esperado ~9,5 %)")
        assertEquals(from, barAt(0, from, to), "arranca en el valor anterior")
    }

    @Test
    fun la_barra_se_asienta_en_el_destino_antes_de_600_ms() {
        val from = 0.25f
        val to = 0.33f
        val lastOutside = (0..1500).last { abs(barAt(it, from, to) - to) >= 0.005f * (to - from) }
        assertTrue(lastOutside + 1 <= 600, "asentada (+-0,5 % del tramo) desde ${lastOutside + 1} ms (objetivo: ~380 ms)")
        assertTrue(abs(barAt(1500, from, to) - to) < 1e-4f)
    }

    @Test
    fun el_ultimo_repaso_rebasa_1_pero_lo_que_se_dibuja_no_pasa_de_1() {
        val animatedMax = (0..1000).maxOf { barAt(it, 11f / 12f, 1f) }
        assertTrue(animatedMax > 1f, "el valor animado SI pasa de 1.0 (overshoot): $animatedMax")
        for (ms in 0..1000) {
            val visible = studyBarVisibleFraction(barAt(ms, 11f / 12f, 1f))
            assertTrue(visible in 0f..1f, "visible = $visible en $ms ms")
        }
        assertEquals(1f, studyBarVisibleFraction(animatedMax))
    }

    @Test
    fun la_fraccion_visible_y_el_destino_se_acotan_a_0_1() {
        assertEquals(1f, studyBarVisibleFraction(1.05f))
        assertEquals(1f, studyBarVisibleFraction(Float.MAX_VALUE))
        assertEquals(0f, studyBarVisibleFraction(-0.03f))
        assertEquals(0f, studyBarVisibleFraction(-Float.MAX_VALUE))
        assertEquals(0.5f, studyBarVisibleFraction(0.5f))
        assertEquals(1f, studyBarTarget(1.7f))
        assertEquals(0f, studyBarTarget(-1f))
        assertEquals(0.25f, studyBarTarget(0.25f))
    }

    // ---- coherencia de la barra con la cola (falladas re-encoladas y tarjetas borradas) ----

    private fun card(id: String) = Flashcard(
        id = id, deckId = "d", question = "Q$id", answer = "A$id",
        createdAt = Instant.fromEpochMilliseconds(1), updatedAt = Instant.fromEpochMilliseconds(1), cardType = CardType.FLIP
    )

    private fun queue(n: Int) = StudyQueue(queue = (1..n).map { card("c$it") })

    private fun StudyQueue.rate(q: ReviewQuality) = submitResult(currentCard!!, q)

    @Test
    fun una_fallada_re_encolada_no_mueve_la_barra_ni_la_hace_retroceder() {
        // OJO con la premisa: la fallada pasa al FINAL (no se duplica), asi que N y M no cambian y la fraccion tampoco.
        var q = queue(10).rate(ReviewQuality.GOOD).rate(ReviewQuality.GOOD)
        val before = q.progress
        q = q.rate(ReviewQuality.AGAIN)
        assertEquals(before, q.progress, "una fallada no avanza ni retrocede")
        assertEquals(10, q.total)
        assertEquals(8, q.remaining)
    }

    @Test
    fun la_barra_nunca_retrocede_en_toda_la_sesion_y_acaba_en_100() {
        val script = listOf(
            ReviewQuality.AGAIN, ReviewQuality.GOOD, ReviewQuality.AGAIN, ReviewQuality.AGAIN, ReviewQuality.HARD,
            ReviewQuality.EASY, ReviewQuality.AGAIN, ReviewQuality.GOOD, ReviewQuality.GOOD, ReviewQuality.GOOD
        )
        var q = queue(5)
        var prev = q.progress
        var i = 0
        while (!q.isFinished) {
            q = q.rate(script[i % script.size].takeIf { i < script.size } ?: ReviewQuality.GOOD)
            assertTrue(q.progress >= prev, "retrocede en el paso $i: ${q.progress} < $prev")
            assertTrue(q.progress in 0f..1f)
            prev = q.progress
            i++
        }
        assertEquals(1f, q.progress, "ultimo repaso = 100 %")
    }

    @Test
    fun cuando_baja_M_por_una_tarjeta_borrada_la_barra_solo_avanza() {
        var q = queue(10).rate(ReviewQuality.GOOD).rate(ReviewQuality.GOOD).rate(ReviewQuality.GOOD)
        val before = q.progress
        q = q.skipCurrent() // se salta una borrada: M = 9, N = 6
        assertEquals(9, q.total)
        assertTrue(q.progress >= before, "no retrocede: ${q.progress} >= $before")
        assertEquals(6, q.remaining)
        assertNear(3f / 9f, q.progress) // (M - N) / M = (9 - 6) / 9: sube de 0.30 a 0.33, nunca baja
        // tambien con una sola tarjeta restante
        val last = queue(1).skipCurrent()
        assertEquals(0, last.total)
        assertEquals(0f, last.progress, "sin tarjetas no hay barra que mostrar (la sesion pasa a Empty)")
    }

    // ---- pulso de los botones ----

    @Test
    fun escala_y_brillo_del_pulso() {
        assertEquals(1f, studyPressScale(0f), "reposo")
        assertNear(0.95f, studyPressScale(1f), msg = "pulsado a fondo")
        assertTrue(studyPressScale(-0.1f) > 1f, "el rebote de vuelta pasa un poco de 1.0")
        assertEquals(0f, studyPressOverlayAlpha(0f), "sin brillo en reposo")
        assertNear(0.12f, studyPressOverlayAlpha(1f), msg = "brillo +12 %")
        assertEquals(0f, studyPressOverlayAlpha(-0.3f), "nunca negativo aunque el muelle pase de 0")
        assertNear(0.12f, studyPressOverlayAlpha(2f), msg = "ni mayor del 12 %")
    }

    private fun upAt(ms: Int): Float =
        STUDY_PRESS_UP_SPRING.vectorize(Float.VectorConverter)
            .getValueFromNanos(ms * 1_000_000L, AnimationVector1D(1f), AnimationVector1D(0f), AnimationVector1D(0f)).value

    @Test
    fun la_vuelta_del_pulso_es_un_pequeno_rebote_y_se_apaga() {
        assertEquals(1f, upAt(0))
        val minimum = (0..1000).minOf { upAt(it) }
        assertTrue(minimum < 0f && minimum > -0.25f, "rebote de vuelta = $minimum (escala ${studyPressScale(minimum)})")
        val lastOutside = (0..1500).last { abs(upAt(it)) >= 0.005f }
        assertTrue(lastOutside + 1 <= 500, "apagado desde ${lastOutside + 1} ms (se esperan ~180 ms; mandan los parametros del muelle)")
    }

    @Test
    fun el_pulso_solo_se_reproduce_si_es_nuevo_y_sin_reducir_animaciones() {
        assertTrue(shouldPlayStudyPulse(reducedMotion = false, token = 1, baseToken = 0))
        assertFalse(shouldPlayStudyPulse(reducedMotion = true, token = 1, baseToken = 0), "reducir animaciones")
        assertFalse(shouldPlayStudyPulse(false, token = 3, baseToken = 3), "pantalla recien compuesta: no repite un pulso viejo")
    }

    @Test
    fun cada_boton_solo_ve_el_progreso_del_pulso_si_es_el_suyo() {
        assertEquals(0.7f, studyPressProgress(ReviewQuality.GOOD, ReviewQuality.GOOD, 0.7f))
        assertEquals(0f, studyPressProgress(ReviewQuality.AGAIN, ReviewQuality.GOOD, 0.7f), "pulso de otro boton")
        assertEquals(0f, studyPressProgress(null, ReviewQuality.GOOD, 0.7f), "sin pulso")
    }

    // ---- estado compartido y teclado ----

    @Test
    fun el_estado_del_pulso_cuenta_cada_disparo_sin_acumular_nada_mas() {
        val pulse = RatePulseState()
        assertEquals(0, pulse.token)
        assertEquals(null, pulse.quality)
        repeat(5) { pulse.fire(ReviewQuality.HARD) }
        assertEquals(5, pulse.token)
        assertEquals(ReviewQuality.HARD, pulse.quality)
        pulse.fire(ReviewQuality.EASY)
        assertEquals(6, pulse.token)
        assertEquals(ReviewQuality.EASY, pulse.quality, "solo cuenta el ultimo pulso")
    }

    @Test
    fun el_manejador_unico_marca_el_pulso_y_puntua() {
        val pulse = RatePulseState()
        val rated = mutableListOf<ReviewQuality>()
        val handler = ratingHandler(pulse) { rated += it }
        handler(ReviewQuality.GOOD)
        assertEquals(listOf(ReviewQuality.GOOD), rated, "puntua")
        assertEquals(1, pulse.token, "y marca el pulso")
        assertEquals(ReviewQuality.GOOD, pulse.quality)
    }

    private fun keyRate(key: Key, answerVisible: Boolean, pulse: RatePulseState, rated: MutableList<ReviewQuality>) {
        val handler = ratingHandler(pulse) { rated += it }
        val action = gateStudyKeyAction(studyKeyAction(key, ctrl = false, alt = false, meta = false, answerVisible = answerVisible), cardShown = true)
        dispatchStudyKeyAction(action, onReveal = {}, onRate = handler, onExit = {})
    }

    @Test
    fun las_teclas_1_a_4_con_la_respuesta_visible_disparan_el_pulso_del_boton_correspondiente() {
        val expected = listOf(
            Key.One to ReviewQuality.AGAIN, Key.Two to ReviewQuality.HARD, Key.Three to ReviewQuality.GOOD, Key.Four to ReviewQuality.EASY,
            Key.NumPad1 to ReviewQuality.AGAIN, Key.NumPad4 to ReviewQuality.EASY
        )
        for ((key, quality) in expected) {
            val pulse = RatePulseState()
            val rated = mutableListOf<ReviewQuality>()
            keyRate(key, answerVisible = true, pulse = pulse, rated = rated)
            assertEquals(listOf(quality), rated, "$key puntua")
            assertEquals(quality, pulse.quality, "$key dispara el pulso del boton $quality")
            assertEquals(1, pulse.token, "$key")
        }
    }

    @Test
    fun espacio_enter_y_teclas_con_la_respuesta_oculta_no_puntuan_ni_pulsan() {
        for ((key, visible) in listOf(Key.Spacebar to true, Key.Enter to true, Key.NumPadEnter to true, Key.Three to false, Key.One to false)) {
            val pulse = RatePulseState()
            val rated = mutableListOf<ReviewQuality>()
            keyRate(key, visible, pulse, rated)
            assertTrue(rated.isEmpty(), "$key (respuesta visible=$visible) no puntua")
            assertEquals(0, pulse.token, "$key no dispara pulso")
        }
    }

    @Test
    fun el_pulso_no_depende_de_la_respuesta_a_la_puntuacion() {
        // El manejador marca el pulso y llama a puntuar sin esperar nada: aunque puntuar tarde o falle, el pulso ya esta marcado.
        val pulse = RatePulseState()
        val handler = ratingHandler(pulse) { }
        handler(ReviewQuality.EASY)
        assertNotEquals(0, pulse.token)
    }

    // ---- fantasma ----

    @Test
    fun el_interruptor_del_fantasma_y_su_ventana() {
        assertEquals(240, STUDY_PULSE_GHOST_MS)
        assertTrue(studyGhostEnabled(STUDY_PULSE_GHOST_MS, reducedMotion = false))
        assertFalse(studyGhostEnabled(0, reducedMotion = false), "con 0 queda deshabilitado (reversion a 13d)")
        assertFalse(studyGhostEnabled(STUDY_PULSE_GHOST_MS, reducedMotion = true), "reducir animaciones: sin fantasma")
        assertFalse(studyGhostEnabled(-5, reducedMotion = false))
    }

    @Test
    fun el_fantasma_solo_se_dibuja_en_su_ventana_y_cuando_los_botones_reales_ya_no_estan() {
        assertTrue(studyShowGhost(enabled = true, fading = true, answerVisible = false))
        assertFalse(studyShowGhost(true, fading = true, answerVisible = true), "los botones reales siguen: no hay fantasma (p. ej. fallo al guardar)")
        assertFalse(studyShowGhost(true, fading = false, answerVisible = false), "ventana terminada")
        assertFalse(studyShowGhost(enabled = false, fading = true, answerVisible = false), "deshabilitado")
    }

    @Test
    fun el_fantasma_se_desvanece_lineal_de_1_a_0() {
        assertEquals(1f, studyGhostAlpha(0f))
        assertNear(0.5f, studyGhostAlpha(0.5f))
        assertEquals(0f, studyGhostAlpha(1f))
        assertEquals(0f, studyGhostAlpha(1.4f), "no es negativo")
        assertEquals(1f, studyGhostAlpha(-0.2f), "no pasa de 1")
    }

    /** Avanza el reloj de fotogramas manual (sin tiempo real) hasta [ms]. */
    private fun BroadcastFrameClock.at(ms: Long) = sendFrame(ms * 1_000_000L)

    @Test
    fun play_reproduce_el_pulso_y_la_ventana_del_fantasma_y_vuelve_al_reposo() = runTest {
        val clock = BroadcastFrameClock()
        val pulse = RatePulseState()
        pulse.fire(ReviewQuality.GOOD)
        val job = launch(clock) { pulse.play(STUDY_PULSE_GHOST_MS) }
        runCurrent()
        assertEquals(0f, pulse.ghostFade.value, "ventana recien abierta")
        clock.at(0); runCurrent()
        clock.at(60); runCurrent()
        assertNear(1f, pulse.press.value, 0.02f, "a los 60 ms el boton esta pulsado a fondo")
        assertTrue(pulse.ghostFade.value in 0.2f..0.3f, "a los 60 ms la ventana va por ~25 %: ${pulse.ghostFade.value}")
        clock.at(120); runCurrent()
        assertTrue(pulse.ghostFade.value in 0.45f..0.55f, "a los 120 ms, a la mitad: ${pulse.ghostFade.value}")
        clock.at(240); runCurrent()
        assertEquals(1f, pulse.ghostFade.value, "la ventana acaba a los 240 ms")
        for (t in 300..1500 step 60) { clock.at(t.toLong()); runCurrent() }
        assertTrue(abs(pulse.press.value) < 0.01f, "el pulso vuelve al reposo: ${pulse.press.value}")
        job.cancel()
    }

    @Test
    fun un_pulso_nuevo_sustituye_al_fantasma_anterior_no_lo_acumula() = runTest {
        val clock = BroadcastFrameClock()
        val pulse = RatePulseState()
        pulse.fire(ReviewQuality.HARD)
        val first = launch(clock) { pulse.play(STUDY_PULSE_GHOST_MS) }
        runCurrent(); clock.at(0); runCurrent(); clock.at(120); runCurrent()
        assertTrue(pulse.ghostFade.value in 0.45f..0.55f, "primer fantasma a mitad: ${pulse.ghostFade.value}")
        // Segunda pulsacion rapida: la pantalla cancela la corrutina anterior (LaunchedEffect(token)) y lanza otra.
        first.cancel()
        pulse.fire(ReviewQuality.EASY)
        val second = launch(clock) { pulse.play(STUDY_PULSE_GHOST_MS) }
        runCurrent()
        assertEquals(0f, pulse.ghostFade.value, "la ventana REINICIA (un solo fantasma): no sigue por la mitad")
        assertEquals(ReviewQuality.EASY, pulse.quality, "el fantasma pasa a mostrar la ultima calidad")
        clock.at(180); runCurrent(); clock.at(300); runCurrent()
        assertTrue(pulse.ghostFade.value in 0.45f..0.55f, "la nueva ventana tambien dura 240 ms: ${pulse.ghostFade.value}")
        clock.at(420); runCurrent()
        assertEquals(1f, pulse.ghostFade.value)
        second.cancel()
    }

    @Test
    fun con_el_interruptor_a_0_la_ventana_del_fantasma_nunca_se_abre() = runTest {
        val clock = BroadcastFrameClock()
        val pulse = RatePulseState()
        pulse.fire(ReviewQuality.GOOD)
        val job = launch(clock) { pulse.play(0) }
        runCurrent()
        assertEquals(1f, pulse.ghostFade.value, "ni siquiera al lanzar (antes de cualquier fotograma) se abre la ventana")
        for (t in 0..600 step 30) {
            clock.at(t.toLong()); runCurrent()
            assertEquals(1f, pulse.ghostFade.value, "sin fantasma en $t ms")
            assertFalse(studyShowGhost(studyGhostEnabled(0, false), pulse.ghostFade.value < 1f, answerVisible = false))
        }
        job.cancel()
    }
}
