package org.braincards.app.presentation.screens.study

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Celebración de Resultados como funciones puras: ninguna lee un reloj ni lanza una animación, así que se prueban sin
// temporizadores. El dibujo y los Animatable viven en `StudyResults.kt`.
//
// Mandan los parámetros del muelle (dampingRatio / stiffness) y no las duraciones: el muelle spring(0.45, 500) se asienta
// (|x - 1| < 0,5 %) en ~400 ms. Si algún día se ajustan los parámetros, las duraciones se mueven con ellos.

/** Duracion del conteo de los 5 numeros (total + 4 casillas), en paralelo. */
const val RESULTS_COUNT_MS = 500

/** La celebración completa (conteo + pulso, solapados) no pasa de esto. */
const val RESULTS_CELEBRATION_MAX_MS = 600

/** Pulso del badge: muelle y pico de escala (1.0 -> pico -> 1.0). */
const val RESULTS_PULSE_DAMPING = 0.45f
const val RESULTS_PULSE_STIFFNESS = 500f
const val RESULTS_PULSE_PEAK = 1.08f

val RESULTS_PULSE_SPRING = spring<Float>(dampingRatio = RESULTS_PULSE_DAMPING, stiffness = RESULTS_PULSE_STIFFNESS)

/**
 * Un muelle que parte del reposo en 1.0 con destino 1.0 no se mueve. Para que pase por 1.08 hace falta una velocidad inicial:
 * para un muelle subamortiguado (masa 1, como en Compose) el pico desde x = 0 con velocidad v es
 * `v / wd * exp(-z * wn * tp) * sqrt(1 - z^2)`, con `wn = sqrt(k)`, `wd = wn * sqrt(1 - z^2)` y
 * `tp = atan(sqrt(1 - z^2) / z) / wd`. Despejando v para que el pico sea [peak] - 1.
 */
fun pulseKickVelocity(damping: Float, stiffness: Float, peak: Float): Float {
    val z = damping.toDouble()
    val wn = sqrt(stiffness.toDouble())
    val wd = wn * sqrt(1.0 - z * z)
    val tp = atan(sqrt(1.0 - z * z) / z) / wd
    val peakPerVelocity = exp(-z * wn * tp) / wd * sqrt(1.0 - z * z) // sin(wd * tp) = sqrt(1 - z^2)
    return ((peak - 1f) / peakPerVelocity).toFloat()
}

/** Velocidad inicial (por segundo) con la que arranca el pulso del badge. */
val RESULTS_PULSE_KICK_VELOCITY: Float = pulseKickVelocity(RESULTS_PULSE_DAMPING, RESULTS_PULSE_STIFFNESS, RESULTS_PULSE_PEAK)

/**
 * Valor mostrado de un numero que cuenta desde 0 hasta [target]: `round(target * FastOutSlowIn(fraction))`. [fraction] se acota a
 * 0..1 (un fotograma de mas no pasa del objetivo ni baja de 0). Entero siempre; un objetivo <= 0 nunca cuenta (0).
 */
fun countUpValue(target: Int, fraction: Float): Int {
    if (target <= 0) return 0
    val f = fraction.coerceIn(0f, 1f)
    return (target * FastOutSlowInEasing.transform(f)).roundToInt()
}

/**
 * La celebracion solo se juega UNA vez por pantalla: tras rotar (`alreadySeen`) o con "reducir animaciones" los numeros salen ya
 * en su valor final y el badge sin pulso.
 */
fun shouldPlayResultsCelebration(alreadySeen: Boolean, reducedMotion: Boolean): Boolean = !alreadySeen && !reducedMotion

/** Medidas del badge de la tarjeta de Resultados (64 dp normal y 56 dp compacto; esquinas en porcentaje del lado). */
data class ResultsBadgeSpec(val sizeDp: Int, val cornerPercent: Int)

/** Ancho disponible por debajo del cual se usa el badge pequeño (punto medio entre tarjetas de 360 y 480 dp). */
const val RESULTS_COMPACT_WIDTH_DP = 420f

fun resultsBadgeSpec(availableWidthDp: Float): ResultsBadgeSpec =
    if (availableWidthDp < RESULTS_COMPACT_WIDTH_DP) ResultsBadgeSpec(sizeDp = 56, cornerPercent = 32)
    else ResultsBadgeSpec(sizeDp = 64, cornerPercent = 31)
