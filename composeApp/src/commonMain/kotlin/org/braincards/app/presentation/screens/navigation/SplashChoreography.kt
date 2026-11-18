package org.braincards.app.presentation.screens.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.abs

/*
 * Coreografia de EntryPending (handoff v4.22 / 5.8 y contrato v4.23), como FUNCIONES PURAS del tiempo transcurrido: ninguna
 * lee un reloj ni lanza una animacion, asi que se prueban sin temporizadores. El reloj real (un Animatable lineal de 0 a
 * SPLASH_TOTAL_MS) vive en `SplashTimeline`; el dibujo, en `SplashBrain`.
 *
 * DISCREPANCIA CONSCIENTE CON EL HANDOFF: la tabla de `splash-animacion.html` da al pulso final `spring(dampingRatio = 0.5,
 * stiffness = 700)` en "150 ms", pero ese muelle tarda ~350 ms en asentarse y no cabe en 150. Se decidio (D2) respetar los
 * 600 ms exactos del total con KEYFRAMES deterministas de 150 ms (sube a 1.06 en 75 ms y baja en otros 75, con
 * FastOutSlowInEasing en cada mitad). En Resultados y Estudio mandan los parametros del muelle, no la duracion.
 */

/** Convergencia de los hemisferios (con easeOutBack). */
const val SPLASH_CONVERGE_MS = 450

/** Pulso final de escala, tras la convergencia. */
const val SPLASH_PULSE_MS = 150

/** Duracion total de la coreografia (convergencia + pulso). */
const val SPLASH_TOTAL_MS = SPLASH_CONVERGE_MS + SPLASH_PULSE_MS

/** Los dos hemisferios pasan de opacidad 0 a 1 en los primeros 300 ms (lineal). */
const val SPLASH_FADE_IN_MS = 300

/** Desplazamiento inicial de cada hemisferio, en unidades del lienzo del vector (230). */
const val SPLASH_OFFSET_UNITS = 58f

/** Lado del lienzo del vector del cerebro, en unidades (viewBox del SVG de marca). */
const val SPLASH_VECTOR_UNITS = 230f

/** Pico de escala del pulso final. */
const val SPLASH_PULSE_PEAK = 1.06f

/**
 * Contrato D8: tiempo MINIMO que EntryPending permanece antes de empezar el fundido de salida si la navegacion esta lista
 * antes. Igual que la convergencia: no se corta con los hemisferios a medio camino ni se lanza el pulso.
 */
const val SPLASH_MIN_HOLD_MS = SPLASH_CONVERGE_MS

/** `cubic-bezier(.34, 1.56, .64, 1)` del handoff: pasa de largo (cruce) y vuelve. */
val SplashEaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Estado de dibujo en un instante. Los desplazamientos son en unidades del vector (negativo = izquierda). */
data class SplashPose(val leftUnits: Float, val rightUnits: Float, val scale: Float, val alpha: Float) {
    companion object {
        /** Cerebro completo, quieto y opaco: estado final y el de "reducir animaciones". */
        val Rest = SplashPose(leftUnits = 0f, rightUnits = 0f, scale = 1f, alpha = 1f)
    }
}

/** Escala del pulso a los [t] ms de su inicio (0..150): 1.0 -> 1.06 a los 75 ms -> 1.0. */
internal fun splashPulseScale(t: Float): Float {
    val x = 1f - abs(t.coerceIn(0f, SPLASH_PULSE_MS.toFloat()) - SPLASH_PULSE_MS / 2f) / (SPLASH_PULSE_MS / 2f)
    return 1f + (SPLASH_PULSE_PEAK - 1f) * FastOutSlowInEasing.transform(x)
}

/** Pose a los [elapsedMs] desde que EntryPending entra en composicion. Fuera de 0..600 se acota. */
fun splashPose(elapsedMs: Float): SplashPose {
    val t = elapsedMs.coerceIn(0f, SPLASH_TOTAL_MS.toFloat())
    val eased = SplashEaseOutBack.transform((t / SPLASH_CONVERGE_MS).coerceIn(0f, 1f))
    val offset = SPLASH_OFFSET_UNITS * (1f - eased)
    return SplashPose(
        leftUnits = 0f - offset, // no `-offset`: en reposo daria -0.0 y SplashPose.Rest (0.0) no seria igual
        rightUnits = offset,
        scale = if (t <= SPLASH_CONVERGE_MS) 1f else splashPulseScale(t - SPLASH_CONVERGE_MS),
        alpha = (t / SPLASH_FADE_IN_MS).coerceIn(0f, 1f)
    )
}

/** Unidades del vector -> pixeles para un lado de [sizePx]. */
fun splashUnitsToPx(units: Float, sizePx: Float): Float = units * sizePx / SPLASH_VECTOR_UNITS

/**
 * Salida de EntryPending (contrato D8). [fadeStartMs] = instante (desde que entro en composicion) en que empieza el fundido;
 * [frozenElapsedMs] = pose con la que se congela el cerebro durante el fundido; [fadeMs] = 0 si no hay fundido.
 */
data class SplashExitPlan(val fadeStartMs: Long, val frozenElapsedMs: Long, val fadeMs: Int)

/**
 * [readyAtMs] = ms transcurridos cuando la navegacion esta lista:
 *  - r < 450: se espera hasta 450 ms exactos SIN lanzar el pulso (congelado en 450) y se hace el fundido.
 *  - 450 <= r < 600: se corta en el fotograma actual (congelado en r) y fundido inmediato.
 *  - r >= 600: fundido en cuanto esta lista (congelado en el reposo final, 600).
 *  - "Reducir animaciones": cerebro completo, 0 ms, sin fundido.
 */
fun planSplashExit(readyAtMs: Long, reducedMotion: Boolean): SplashExitPlan {
    val r = readyAtMs.coerceAtLeast(0L)
    if (reducedMotion) return SplashExitPlan(fadeStartMs = r, frozenElapsedMs = SPLASH_TOTAL_MS.toLong(), fadeMs = 0)
    val fadeStart = if (r < SPLASH_MIN_HOLD_MS) SPLASH_MIN_HOLD_MS.toLong() else r
    return SplashExitPlan(
        fadeStartMs = fadeStart,
        frozenElapsedMs = minOf(fadeStart, SPLASH_TOTAL_MS.toLong()),
        fadeMs = SPLASH_FADE_MS
    )
}
