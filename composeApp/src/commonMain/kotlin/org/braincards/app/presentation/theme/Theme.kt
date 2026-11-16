package org.braincards.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import org.braincards.app.platform.rememberReducedMotion
import org.braincards.app.presentation.screens.settings.AppTheme

internal val LocalBrainCardsWordmark = staticCompositionLocalOf { TextStyle.Default }

/**
 * Tema de BrainCards: paleta clara u oscura del handoff, Plus Jakarta Sans, formas 12/12/16/18/20 y sin tinte tonal.
 * [darkTheme] ya viene resuelto (ver [resolveDarkTheme]); el tema no conoce el enum AppTheme.
 * Al cambiar de claro a oscuro (o al reves) los colores transicionan 250 ms (handoff v4.8) salvo con [animate] = false
 * o movimiento reducido del sistema: entonces el cambio es inmediato.
 */
@Composable
fun BrainCardsTheme(darkTheme: Boolean, animate: Boolean = true, content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    val palette = animatedPalette(if (darkTheme) DarkPalette else LightPalette, animate && !reduced)
    val extraColors = remember(palette) { palette.toExtraColors() }
    val inversePrimary = (if (darkTheme) LightPalette else DarkPalette).brand
    val colorScheme = remember(palette, inversePrimary) { brainCardsColorScheme(palette, inversePrimary) }
    val wordmark = rememberBrainCardsWordmarkStyle()
    CompositionLocalProvider(
        LocalBrainCardsExtraColors provides extraColors,
        LocalBrainCardsWordmark provides wordmark
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
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

internal const val THEME_TRANSITION_MS = 250

internal fun lerpPalette(a: BrainCardsPalette, b: BrainCardsPalette, f: Float) = BrainCardsPalette(
    ground = lerp(a.ground, b.ground, f),
    surface = lerp(a.surface, b.surface, f),
    border = lerp(a.border, b.border, f),
    text = lerp(a.text, b.text, f),
    textSecondary = lerp(a.textSecondary, b.textSecondary, f),
    brand = lerp(a.brand, b.brand, f),
    brandText = lerp(a.brandText, b.brandText, f),
    brandChip = lerp(a.brandChip, b.brandChip, f),
    cta = lerp(a.cta, b.cta, f),
    ctaText = lerp(a.ctaText, b.ctaText, f),
    chipTeal = lerp(a.chipTeal, b.chipTeal, f),
    chipTealBg = lerp(a.chipTealBg, b.chipTealBg, f),
    chipGray = lerp(a.chipGray, b.chipGray, f),
    chipGrayBg = lerp(a.chipGrayBg, b.chipGrayBg, f),
    errorColor = lerp(a.errorColor, b.errorColor, f),
    errorBg = lerp(a.errorBg, b.errorBg, f),
    outline = lerp(a.outline, b.outline, f)
)

/**
 * La paleta objetivo, con transicion de [THEME_TRANSITION_MS] desde la paleta visible en el momento del cambio (si se
 * cambia a mitad, parte del color intermedio). Sin [animate] el cambio es inmediato. La primera composicion no anima.
 */
@Composable
private fun animatedPalette(target: BrainCardsPalette, animate: Boolean): BrainCardsPalette {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target, animate) {
        if (target == to) return@LaunchedEffect
        if (!animate) {
            from = target; to = target
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        from = lerpPalette(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(THEME_TRANSITION_MS))
    }
    return if (progress.value >= 1f) to else lerpPalette(from, to, progress.value)
}
