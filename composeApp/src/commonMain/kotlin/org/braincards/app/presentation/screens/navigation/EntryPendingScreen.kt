package org.braincards.app.presentation.screens.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.braincards.app.platform.splashLogoSize
import org.braincards.app.presentation.components.SPLASH_BACKGROUND
import org.braincards.app.presentation.components.SplashBrain

/** Fundido de salida de EntryPending hacia la pantalla real. Con «reducir animaciones» no se usa: el cambio es instantáneo. */
const val SPLASH_FADE_MS = 250

/** Tope de espera del tema guardado: pasado este tiempo se arranca con el tema del sistema en vez de seguir esperando. */
const val THEME_LOAD_TIMEOUT_MS = 1_000L

/**
 * Pantalla de entrada mientras auth sin resolver O tema sin leer (para que no haya parpadeo de tema), este ultimo
 * con tope de [THEME_LOAD_TIMEOUT_MS] ([themeTimedOut]).
 */
fun shouldShowEntryPending(startRoute: EntryRoute, themeLoaded: Boolean, themeTimedOut: Boolean): Boolean =
    startRoute == EntryRoute.Pending || (!themeLoaded && !themeTimedOut)

/**
 * Mientras no se sabe a dónde arrancar (auth sin resolver) o el tema no se ha leído: idéntica al splash del sistema. Fondo morado
 * fijo ([SPLASH_BACKGROUND], igual en claro y oscuro: no usa el tema) y el cerebro blanco centrado, sin texto ni spinner. En
 * Android cubre el hueco si el splash del sistema agota su espera máxima. El tamaño del logo lo da la plataforma
 * ([splashLogoSize]: 120 dp en Android, 160 en Desktop). Los hemisferios entran animados ([splashPose] a los [elapsedMs] de la
 * coreografía; por defecto el reposo final = el cerebro completo, igual que el splash del sistema).
 */
@Composable
fun EntryPendingScreen(elapsedMs: () -> Float = { SPLASH_TOTAL_MS.toFloat() }, onFirstDraw: (() -> Unit)? = null) {
    Box(
        modifier = Modifier.fillMaxSize().background(SPLASH_BACKGROUND),
        contentAlignment = Alignment.Center
    ) {
        SplashBrain(size = splashLogoSize(), pose = { splashPose(elapsedMs()) }, onFirstDraw = onFirstDraw)
    }
}
