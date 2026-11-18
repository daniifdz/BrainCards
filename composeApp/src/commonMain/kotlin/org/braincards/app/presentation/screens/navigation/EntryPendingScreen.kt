package org.braincards.app.presentation.screens.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.braincards.app.platform.splashLogoSize
import org.braincards.app.presentation.components.SPLASH_BACKGROUND
import org.braincards.app.presentation.components.SplashLogo

/** Fundido de salida de EntryPending hacia la pantalla real (handoff 5.8). Con "reducir animaciones" no se usa: el cambio es instantaneo. */
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
 * Mientras no se sabe a donde arrancar (auth sin resolver) o el tema no se ha leido: IDENTICA al splash del sistema (handoff
 * 5.8). Fondo morado FIJO ([SPLASH_BACKGROUND], igual en claro y oscuro: no usa el tema) y el cerebro blanco centrado, sin
 * texto ni spinner. En Android cubre el hueco si el splash del sistema agota su espera maxima. El tamano del logo lo da la
 * plataforma ([splashLogoSize]: 120 dp en Android, 160 en Desktop). Aqui ira la animacion cuando llegue su motion spec.
 */
@Composable
fun EntryPendingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(SPLASH_BACKGROUND),
        contentAlignment = Alignment.Center
    ) {
        SplashLogo(size = splashLogoSize())
    }
}
