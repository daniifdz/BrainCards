package org.braincards.app.presentation.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Radios del tema (decision D2: 12 / 12 / 16 / 18 / 20 en los cinco huecos de M3). Los radios por componente del
 * handoff (tarjetas 18 en Desktop y 16 en movil, CTA 20 / 18, chips e inputs 12, pildora capsula) se aplican al
 * migrar cada pantalla; aqui solo van los huecos del tema.
 */
internal object BrainCardsRadii {
    val extraSmall: Dp = 12.dp
    val small: Dp = 12.dp
    val medium: Dp = 16.dp
    val large: Dp = 18.dp
    val extraLarge: Dp = 20.dp
}

internal val BrainCardsShapes = Shapes(
    extraSmall = RoundedCornerShape(BrainCardsRadii.extraSmall),
    small = RoundedCornerShape(BrainCardsRadii.small),
    medium = RoundedCornerShape(BrainCardsRadii.medium),
    large = RoundedCornerShape(BrainCardsRadii.large),
    extraLarge = RoundedCornerShape(BrainCardsRadii.extraLarge)
)

/**
 * Regla de "sin sombras": superficies planas con borde de 1 dp en vez de elevacion. El tema solo puede quitar el
 * tinte tonal (surfaceTint transparente); las elevaciones explicitas de cada pantalla se sustituyen al migrarla
 * usando estos valores.
 */
object BrainCardsDefaults {
    val borderWidth: Dp = 1.dp
    val elevation: Dp = 0.dp
    val tonalElevation: Dp = 0.dp

    /** Borde decorativo de 1 dp (outlineVariant). */
    @Composable
    fun borderStroke(): BorderStroke = BorderStroke(borderWidth, MaterialTheme.colorScheme.outlineVariant)
}
