package org.braincards.app.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens de color de la paleta de diseño (la fuente es tokens.css, en docs/design; PaletteTokensSyncTest comprueba que
 * coinciden). Dos instancias: [LightPalette] y [DarkPalette].
 *
 * [outline] no es un token de la paleta: es la mezcla más suave de textSecondary sobre surface que alcanza 3:1 contra surface
 * (WCAG 1.4.11, borde de campos). Se guarda como constante opaca; ContrastTest comprueba que es exactamente esa mezcla
 * (pasos de 1 % de opacidad) y que con 1 % menos no llega a 3:1.
 */
@Immutable
internal data class BrainCardsPalette(
    val ground: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val textSecondary: Color,
    val brand: Color,
    val brandText: Color,
    val brandChip: Color,
    val cta: Color,
    val ctaText: Color,
    val chipTeal: Color,
    val chipTealBg: Color,
    val chipGray: Color,
    val chipGrayBg: Color,
    val errorColor: Color,
    val errorBg: Color,
    val outline: Color
)

internal val LightPalette = BrainCardsPalette(
    ground = Color(0xFFFBF7F2),
    surface = Color(0xFFFFFFFF),
    border = Color(0x1F2B2420), // rgba(43,36,32,0.12)
    text = Color(0xFF2B2420),
    textSecondary = Color(0xFF6B6258),
    brand = Color(0xFF5B3FBF),
    brandText = Color(0xFFFFFFFF),
    brandChip = Color(0xFFEDE7FA),
    cta = Color(0xFFB8512E),
    ctaText = Color(0xFFFFFFFF),
    chipTeal = Color(0xFF3D7566),
    chipTealBg = Color(0xFFE1F0EB),
    chipGray = Color(0xFF5B5349),
    chipGrayBg = Color(0xFFEFEAE2),
    errorColor = Color(0xFFB3261E),
    errorBg = Color(0xFFFBEAE8),
    outline = Color(0xFF9A948D) // textSecondary al 68 % sobre surface
)

internal val DarkPalette = BrainCardsPalette(
    ground = Color(0xFF1C1712),
    surface = Color(0xFF271F19),
    border = Color(0x24F5EFE6), // rgba(245,239,230,0.14)
    text = Color(0xFFF5EFE6),
    textSecondary = Color(0xFFB8AC9C),
    brand = Color(0xFFA693F0),
    brandText = Color(0xFF1C1712),
    brandChip = Color(0xFF352B4D),
    cta = Color(0xFFE8774F),
    ctaText = Color(0xFF1C1712),
    chipTeal = Color(0xFF7FBFAE),
    chipTealBg = Color(0xFF1E342E),
    chipGray = Color(0xFFC2B8AB),
    chipGrayBg = Color(0xFF342B22),
    errorColor = Color(0xFFFFB4A8),
    errorBg = Color(0xFF3B1410),
    outline = Color(0xFF746A5E) // textSecondary al 53 % sobre surface
)

/**
 * Esquema M3 completo: se usa el constructor con los 36 roles (sin valores por defecto), así que no se puede olvidar ninguno y
 * ninguno se cuela en el morado base de Material.
 *
 * Mapeo: brand -> primary, brandText -> onPrimary (y on* de secondary, tertiary y error), brandChip -> primaryContainer;
 * chipTeal / chipTealBg -> secondary / secondaryContainer; textSecondary -> tertiary (provisional); errorColor / errorBg ->
 * error / errorContainer; ground -> background; surface -> surface; chipGrayBg / textSecondary -> surfaceVariant /
 * onSurfaceVariant; border -> outlineVariant (decorativo); outline = la mezcla descrita arriba. Todos los surfaceContainer*
 * son surface: la separación la da el borde, no el tono. surfaceTint transparente: sin tinte tonal de elevación.
 */
internal fun brainCardsColorScheme(p: BrainCardsPalette, inversePrimary: Color): ColorScheme = ColorScheme(
    primary = p.brand,
    onPrimary = p.brandText,
    primaryContainer = p.brandChip,
    onPrimaryContainer = p.brand,
    inversePrimary = inversePrimary,
    secondary = p.chipTeal,
    onSecondary = p.brandText,
    secondaryContainer = p.chipTealBg,
    onSecondaryContainer = p.chipTeal,
    tertiary = p.textSecondary,
    onTertiary = p.brandText,
    tertiaryContainer = p.chipGrayBg,
    onTertiaryContainer = p.chipGray,
    background = p.ground,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.chipGrayBg,
    onSurfaceVariant = p.textSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = p.text,
    inverseOnSurface = p.ground,
    error = p.errorColor,
    onError = p.brandText,
    errorContainer = p.errorBg,
    onErrorContainer = p.errorColor,
    outline = p.outline,
    outlineVariant = p.border,
    scrim = Color.Black,
    surfaceBright = p.surface,
    surfaceDim = p.ground,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.surface,
    surfaceContainerHighest = p.surface,
    surfaceContainerLow = p.surface,
    surfaceContainerLowest = p.surface
)

// inversePrimary es el "primary" del modo contrario (texto de accion sobre inverseSurface, p. ej. el Snackbar).
internal val LightColorScheme: ColorScheme = brainCardsColorScheme(LightPalette, inversePrimary = DarkPalette.brand)
internal val DarkColorScheme: ColorScheme = brainCardsColorScheme(DarkPalette, inversePrimary = LightPalette.brand)

/** Colores de la paleta que no son un rol M3 (cta, chips, brandChip). Se leen con `BrainCardsTheme.extraColors`. */
@Immutable
class BrainCardsExtraColors(
    val cta: Color,
    val ctaText: Color,
    val chipTeal: Color,
    val chipTealBg: Color,
    val chipGray: Color,
    val chipGrayBg: Color,
    val brandChip: Color
)

internal fun BrainCardsPalette.toExtraColors() = BrainCardsExtraColors(
    cta = cta,
    ctaText = ctaText,
    chipTeal = chipTeal,
    chipTealBg = chipTealBg,
    chipGray = chipGray,
    chipGrayBg = chipGrayBg,
    brandChip = brandChip
)

internal val LocalBrainCardsExtraColors = staticCompositionLocalOf { LightPalette.toExtraColors() }
