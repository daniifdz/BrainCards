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
import kotlinx.coroutines.flow.first

/**
 * Reloj de EntryPending: cuanto lleva la coreografia y si la capa sigue visible. Toda la DECISION de tiempos es pura
 * ([planSplashExit], [splashPose]); aqui solo se ejecuta: un Animatable lineal de 0 a [SPLASH_TOTAL_MS] y la espera hasta el
 * instante que dicte el plan. El reloj arranca cuando EntryPending entra en composicion.
 */
@Stable
class SplashTimeline internal constructor(startVisible: Boolean, private val reducedMotion: Boolean) {

    internal val clock = Animatable(0f)
    private var frozenMs: Float? by mutableStateOf(null)

    /** `true` mientras la capa EntryPending debe estar en pantalla (al ponerse a `false` empieza el fundido de salida). */
    var overlayVisible: Boolean by mutableStateOf(startVisible)
        private set

    /** Ms de coreografia para dibujar. Con "reducir animaciones", siempre el reposo final. Se lee en la fase de dibujo. */
    fun elapsedMs(): Float = when {
        reducedMotion -> SPLASH_TOTAL_MS.toFloat()
        else -> frozenMs ?: clock.value
    }

    internal suspend fun run() {
        if (overlayVisible && !reducedMotion) clock.animateTo(SPLASH_TOTAL_MS.toFloat(), tween(SPLASH_TOTAL_MS, easing = LinearEasing))
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
 * no hay capa ni animacion.
 */
@Composable
fun rememberSplashTimeline(pending: Boolean, reducedMotion: Boolean): SplashTimeline {
    val timeline = remember(reducedMotion) { SplashTimeline(startVisible = pending, reducedMotion = reducedMotion) }
    LaunchedEffect(timeline) { timeline.run() }
    LaunchedEffect(timeline, pending) {
        if (pending) timeline.navigationPendingAgain() else timeline.navigationReady()
    }
    return timeline
}
