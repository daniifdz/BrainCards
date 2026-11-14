package org.braincards.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import org.braincards.app.presentation.screens.settings.AppTheme

internal val LocalBrainCardsWordmark = staticCompositionLocalOf { TextStyle.Default }

/**
 * Tema de BrainCards: paleta clara u oscura del handoff, Plus Jakarta Sans, formas 12/12/16/18/20 y sin tinte tonal.
 * [darkTheme] ya viene resuelto (ver [resolveDarkTheme]); el tema no conoce el enum AppTheme.
 */
@Composable
fun BrainCardsTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val extraColors = remember(darkTheme) { palette.toExtraColors() }
    val wordmark = rememberBrainCardsWordmarkStyle()
    CompositionLocalProvider(
        LocalBrainCardsExtraColors provides extraColors,
        LocalBrainCardsWordmark provides wordmark
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = rememberBrainCardsTypography(),
            shapes = BrainCardsShapes,
            content = content
        )
    }
}

/** Accesos a lo que el tema aporta ademas de MaterialTheme. */
object BrainCardsTheme {
    val extraColors: BrainCardsExtraColors
        @Composable @ReadOnlyComposable get() = LocalBrainCardsExtraColors.current

    /** Fredoka para el wordmark; el tamano y el color se fijan donde se usa. */
    val wordmark: TextStyle
        @Composable @ReadOnlyComposable get() = LocalBrainCardsWordmark.current
}

/** SYSTEM sigue al sistema; LIGHT y DARK lo fuerzan. Sin valor todavia (DataStore sin leer) = SYSTEM. */
fun resolveDarkTheme(appTheme: AppTheme?, systemDark: Boolean): Boolean = when (appTheme ?: AppTheme.SYSTEM) {
    AppTheme.SYSTEM -> systemDark
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
}
