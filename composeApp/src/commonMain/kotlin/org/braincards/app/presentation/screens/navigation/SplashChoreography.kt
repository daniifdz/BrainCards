package org.braincards.app.presentation.screens.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.abs

// Coreografía de EntryPending como funciones puras del tiempo transcurrido: ninguna lee un reloj ni lanza una animación, así que
// se prueban sin temporizadores. El reloj real (un Animatable lineal de 0 a SPLASH_TOTAL_MS) vive en `SplashTimeline`; el
// dibujo, en `SplashBrain`.
//
// Pulso final: keyframes deterministas de 150 ms (sube a 1.06 en 75 ms y baja en otros 75, con FastOutSlowInEasing en cada
// mitad) en lugar de un muelle, que tardaría ~350 ms en asentarse y no cabría en 150 ms; así el total es exacto. En Resultados
// y Estudio mandan los parámetros del muelle, no la duración.
//
// Convergencia: dura 600 ms y se reparte con ease-in-out y un rebote pequeño al final (`SplashConvergeEasing`: 4,6 % / 21 % /
// 52,7 % / 99 % a los 100 / 200 / 300 / 450 ms; pico 104,5 % hacia los 525 ms), con desplazamiento inicial grande y una rampa de
// opacidad corta para que los hemisferios se vean desde el principio del movimiento. Una curva easeOutBack recorre el 76 % en
// los primeros 100 ms y deja el resto en pausa, por eso no se usa.
//
// El contrato de salida se mantiene con los umbrales derivados de [SPLASH_CONVERGE_MS] (600 / 750 ms). Con
// `SPLASH_CONVERGE_MS = 450` (misma curva, desplazamiento y rampa) la espera mínima y la duración total se derivan solas (450 y
// 600 ms), a cambio de 150 ms menos en cada arranque en frío.

/** Convergencia de los hemisferios (curva [SplashConvergeEasing]): 600 ms. */
const val SPLASH_CONVERGE_MS = 600

/** Pulso final de escala, tras la convergencia. */
const val SPLASH_PULSE_MS = 150

/** Duración total de la coreografía (convergencia + pulso): 750 ms. */
const val SPLASH_TOTAL_MS = SPLASH_CONVERGE_MS + SPLASH_PULSE_MS

/**
 * Rampa de opacidad de los hemisferios (0 -> 1, lineal, desde el inicio): 80 ms, para que se vean desde el principio del
 * movimiento. Debe ser <= [SPLASH_CONVERGE_MS].
 */
const val SPLASH_ALPHA_RAMP_MS = 80

/**
 * Desplazamiento inicial de cada hemisferio, en unidades del lienzo del vector (230): ±84. Mayor que el cerebro entero: necesita
 * un área de dibujo más grande que el cerebro (`SPLASH_DRAW_AREA_FACTOR` en SplashBrain).
 */
const val SPLASH_OFFSET_UNITS = 84f

/** Lado del lienzo del vector del cerebro, en unidades (viewBox del SVG de marca). */
const val SPLASH_VECTOR_UNITS = 230f

/** Pico de escala del pulso final. */
const val SPLASH_PULSE_PEAK = 1.06f

/**
 * Tiempo mínimo que EntryPending permanece antes de empezar el fundido de salida si la navegación está lista antes. Igual que la
 * convergencia: no se corta con los hemisferios a medio camino ni se lanza el pulso.
 */
const val SPLASH_MIN_HOLD_MS = SPLASH_CONVERGE_MS

/** Curva de la convergencia: `cubic-bezier(0.55, 0, 0.55, 1.3)` (ease-in-out con rebote pequeno al final), parametrizada. */
const val SPLASH_CURVE_X1 = 0.55f
const val SPLASH_CURVE_Y1 = 0f
const val SPLASH_CURVE_X2 = 0.55f
const val SPLASH_CURVE_Y2 = 1.3f

val SplashConvergeEasing = CubicBezierEasing(SPLASH_CURVE_X1, SPLASH_CURVE_Y1, SPLASH_CURVE_X2, SPLASH_CURVE_Y2)

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

/** Pose a los [elapsedMs] desde que EntryPending entra en composicion. Fuera de 0..[SPLASH_TOTAL_MS] se acota. */
fun splashPose(elapsedMs: Float): SplashPose {
    val t = elapsedMs.coerceIn(0f, SPLASH_TOTAL_MS.toFloat())
    val eased = SplashConvergeEasing.transform((t / SPLASH_CONVERGE_MS).coerceIn(0f, 1f))
    val offset = SPLASH_OFFSET_UNITS * (1f - eased)
    return SplashPose(
        leftUnits = 0f - offset, // no `-offset`: en reposo daria -0.0 y SplashPose.Rest (0.0) no seria igual
        rightUnits = offset,
        scale = if (t <= SPLASH_CONVERGE_MS) 1f else splashPulseScale(t - SPLASH_CONVERGE_MS),
        alpha = (t / SPLASH_ALPHA_RAMP_MS).coerceIn(0f, 1f)
    )
}

/** Unidades del vector -> pixeles para un lado de [sizePx]. */
fun splashUnitsToPx(units: Float, sizePx: Float): Float = units * sizePx / SPLASH_VECTOR_UNITS

/**
 * Salida de EntryPending. [fadeStartMs] = instante (desde que entró en composición) en que empieza el fundido; [frozenElapsedMs] =
 * pose con la que se congela el cerebro durante el fundido; [fadeMs] = 0 si no hay fundido.
 */
data class SplashExitPlan(val fadeStartMs: Long, val frozenElapsedMs: Long, val fadeMs: Int)

/**
 * [readyAtMs] = ms transcurridos cuando la navegacion esta lista:
 *  - r < [SPLASH_MIN_HOLD_MS] (600): se espera hasta ese instante exacto SIN lanzar el pulso (congelado en 600) y se hace el fundido.
 *  - 600 <= r < [SPLASH_TOTAL_MS] (750): se corta en el fotograma actual (congelado en r) y fundido inmediato.
 *  - r >= [SPLASH_TOTAL_MS]: fundido en cuanto esta lista (congelado en el reposo final, 750).
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
