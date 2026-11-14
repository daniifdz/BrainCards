package org.braincards.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.fredoka_bold
import braincards.composeapp.generated.resources.fredoka_semibold
import braincards.composeapp.generated.resources.plusjakartasans_bold
import braincards.composeapp.generated.resources.plusjakartasans_medium
import braincards.composeapp.generated.resources.plusjakartasans_regular
import braincards.composeapp.generated.resources.plusjakartasans_semibold
import org.jetbrains.compose.resources.Font

// Las fuentes se cargan con Font(Res.font.x, weight) de Compose Multiplatform: es @Composable en Android y en Desktop.
// En Android la carga es sincrona (assets); en Desktop es asincrona y los primeros fotogramas usan una fuente vacia de
// marcador hasta que llegan los bytes. Por eso la familia y la Typography se construyen DENTRO de la composicion.

/** Plus Jakarta Sans 400 / 500 / 600 / 700 (todo el texto salvo el wordmark). */
@Composable
internal fun rememberBrainCardsFontFamily(): FontFamily {
    val regular = Font(Res.font.plusjakartasans_regular, FontWeight.Normal)
    val medium = Font(Res.font.plusjakartasans_medium, FontWeight.Medium)
    val semiBold = Font(Res.font.plusjakartasans_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.plusjakartasans_bold, FontWeight.Bold)
    return remember(regular, medium, semiBold, bold) { FontFamily(regular, medium, semiBold, bold) }
}

/**
 * Estilo del wordmark "BrainCards": Fredoka 600 (700 disponible). Solo fija familia y peso; el tamano y el color los
 * pone quien lo usa. Ver `BrainCardsTheme.wordmark`.
 */
@Composable
internal fun rememberBrainCardsWordmarkStyle(): TextStyle {
    val semiBold = Font(Res.font.fredoka_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.fredoka_bold, FontWeight.Bold)
    return remember(semiBold, bold) {
        TextStyle(fontFamily = FontFamily(semiBold, bold), fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Typography con [family] en los 15 estilos de M3; tamanos, pesos e interlineados siguen siendo los de M3 hasta que
 * cada pantalla fije los suyos al migrarla.
 */
internal fun Typography.withFontFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family)
)

@Composable
internal fun rememberBrainCardsTypography(): Typography {
    val family = rememberBrainCardsFontFamily()
    return remember(family) { Typography().withFontFamily(family) }
}
