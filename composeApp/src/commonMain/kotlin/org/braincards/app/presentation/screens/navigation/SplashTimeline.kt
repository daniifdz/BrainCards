package org.braincards.app.presentation.screens.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reloj de EntryPending: cuánto lleva la coreografía y si la capa sigue visible. Toda la decisión de tiempos es pura
 * ([planSplashExit], [splashPose]); aquí solo se ejecuta: un Animatable lineal de 0 a [SPLASH_TOTAL_MS] y la espera hasta el
 * instante que dicte el plan.
 *
 * El reloj arranca al componerse EntryPending, salvo en el arranque en frío de Android: ahí ([exitSignal] != null) arranca
 * cuando el splash del sistema sale (si no, correría oculto debajo de él: entre el primer dibujo y la salida pasan 150-600 ms),
 * con el plan B de [SPLASH_EXIT_SAFETY_MS] tras el primer dibujo si la salida nunca se avisa.
 */
@Stable
class SplashTimeline internal constructor(
    startVisible: Boolean,
    private val reducedMotion: Boolean,
    private val exitSignal: SplashExitSignal? = null
) {

    internal val clock = Animatable(0f)
    private var frozenMs: Float? by mutableStateOf(null)
    private var firstDrawn: Boolean by mutableStateOf(false)
    private var safetyElapsed: Boolean by mutableStateOf(false)

    /** `true` mientras la capa EntryPending debe estar en pantalla (al ponerse a `false` empieza el fundido de salida). */
    var overlayVisible: Boolean by mutableStateOf(startVisible)
        private set

    /** Ms de coreografia para dibujar. Con "reducir animaciones", siempre el reposo final. Se lee en la fase de dibujo. */
    fun elapsedMs(): Float = when {
        reducedMotion -> SPLASH_TOTAL_MS.toFloat()
        else -> frozenMs ?: clock.value
    }

    /** Primer dibujo real de la capa (se llama desde la fase de dibujo). Arma el temporizador del plan B. */
    fun markFirstDraw() {
        firstDrawn = true
    }

    /** Con listener de salida: espera a que el splash salga o a que venza el plan B. Sin listener, no espera. */
    private suspend fun awaitSplashExit() {
        val signal = exitSignal ?: return
        coroutineScope {
            val safety = launch {
                snapshotFlow { firstDrawn }.first { it }
                delay(SPLASH_EXIT_SAFETY_MS)
                safetyElapsed = true
            }
            snapshotFlow { signal.fired || safetyElapsed }.first { it }
            safety.cancel()
        }
    }

    internal suspend fun run() {
        if (!overlayVisible || reducedMotion) return
        awaitSplashExit()
        clock.animateTo(SPLASH_TOTAL_MS.toFloat(), tween(SPLASH_TOTAL_MS, easing = LinearEasing))
    }

    /** La navegacion esta lista: aplica [planSplashExit]. */
    internal suspend fun navigationReady() {
        if (!overlayVisible) return
        val plan = planSplashExit(readyAtMs = clock.value.toLong(), reducedMotion = reducedMotion)
        if (!reducedMotion && clock.value < plan.fadeStartMs) {
            snapshotFlow { clock.value }.first { it >= plan.fadeStartMs }
        }
        frozenMs = plan.frozenElapsedMs.toFloat()
        clock.stop()
        overlayVisible = false
    }

    /** La capa vuelve a hacer falta tras haberse ido (caso raro): cerebro completo y quieto, sin repetir la animacion. */
    internal fun navigationPendingAgain() {
        if (overlayVisible) return
        frozenMs = SPLASH_TOTAL_MS.toFloat()
        overlayVisible = true
    }
}

/**
 * [pending] = la navegacion aun no esta lista (`shouldShowEntryPending`). Si ya nace en `false` (rotacion con la ruta resuelta)
 * no hay capa ni animacion, salvo en el arranque en frio ([exitSignal] != null): ahi la capa nace visible aunque la navegacion
 * ya este lista, y la coreografia arranca cuando sale el splash del sistema.
 */
@Composable
fun rememberSplashTimeline(pending: Boolean, reducedMotion: Boolean, exitSignal: SplashExitSignal? = null): SplashTimeline {
    val timeline = remember(reducedMotion, exitSignal) {
        SplashTimeline(startVisible = pending || exitSignal != null, reducedMotion = reducedMotion, exitSignal = exitSignal)
    }
    LaunchedEffect(timeline) { timeline.run() }
    LaunchedEffect(timeline, pending) {
        if (pending) timeline.navigationPendingAgain() else timeline.navigationReady()
    }
    return timeline
}
