package org.braincards.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.splash_logo
import org.jetbrains.compose.resources.painterResource

/** Esquinas del badge: 22 % del lado (el icono de la app usa rx 112 / 512 = 21,9 %). */
const val BADGE_CORNER_PERCENT = 22

/**
 * Lado del vector del cerebro respecto al lado del badge: `translate(50 50) scale(0.287)` sobre un viewBox de 100, aplicado a
 * los trazados del SVG de marca (lienzo de 230 unidades): 0,287 * 230 / 100 = 0,66. El cerebro dibujado (x 35..221 de 256)
 * ocupa el 53,4 % del lado del badge y queda centrado.
 */
const val BADGE_BRAIN_FRACTION = 0.287f * 230f / 100f

/**
 * Logo de marca fuera del splash: cuadrado redondeado morado de marca ([SPLASH_BACKGROUND], fijo en claro y oscuro, igual que
 * el splash) con el cerebro blanco. Reutiliza el vector `splash_logo` del splash como única fuente: no hay copia del
 * trazado. Decorativo (el nombre de la app va en el texto que lo acompaña). [cornerPercent]: la tarjeta de Resultados usa
 * esquinas más redondas (~31 %).
 */
@Composable
fun BrandBadge(size: Dp, modifier: Modifier = Modifier, cornerPercent: Int = BADGE_CORNER_PERCENT) {
    BrandBadgeContent(size, painterResource(Res.drawable.splash_logo), modifier, cornerPercent)
}

/** Cuerpo del badge con el painter inyectado (los tests de render pasan el vector ya decodificado, sin cargar recursos). */
@Composable
internal fun BrandBadgeContent(size: Dp, brain: Painter, modifier: Modifier = Modifier, cornerPercent: Int = BADGE_CORNER_PERCENT) {
    Box(
        modifier = modifier
            .size(size)
            .background(SPLASH_BACKGROUND, RoundedCornerShape(percent = cornerPercent)),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = brain, contentDescription = null, modifier = Modifier.size(size * BADGE_BRAIN_FRACTION))
    }
}
