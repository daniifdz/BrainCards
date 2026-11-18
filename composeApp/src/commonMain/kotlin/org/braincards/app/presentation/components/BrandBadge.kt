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

/**
 * Esquinas del badge: 22 % del lado (mockups: `rx="22"` sobre un viewBox de 100; el icono de app usa rx 112 / 512 = 21,9 %).
 */
const val BADGE_CORNER_PERCENT = 22

/**
 * Lado del vector del cerebro respecto al lado del badge. Medido en los mockups (bienvenida*.html, home*.html, ajustes*.html,
 * marca-logo.html): `translate(50 50) scale(0.287) translate(-128 -128)` sobre un viewBox de 100, aplicado a los trazados del
 * SVG de marca (lienzo de 230 unidades): 0,287 * 230 / 100 = 0,66. El cerebro DIBUJADO (x 35..221 de 256) ocupa 53,4 % del lado
 * del badge y queda centrado; el texto del handoff («~70 % del lienzo») no coincide con lo dibujado y manda el dibujo.
 */
const val BADGE_BRAIN_FRACTION = 0.287f * 230f / 100f

/**
 * Logo de marca fuera del splash: cuadrado redondeado morado de marca ([SPLASH_BACKGROUND], FIJO en claro y oscuro, igual que el
 * splash) con el cerebro blanco. Reutiliza el vector del splash ([SplashLogo]) como unica fuente: no hay copia del trazado.
 * Decorativo (el nombre de la app va en el texto que lo acompana).
 */
@Composable
fun BrandBadge(size: Dp, modifier: Modifier = Modifier) {
    BrandBadgeContent(size, painterResource(Res.drawable.splash_logo), modifier)
}

/** Cuerpo del badge con el painter inyectado (los tests de render pasan el vector ya decodificado, sin cargar recursos). */
@Composable
internal fun BrandBadgeContent(size: Dp, brain: Painter, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(SPLASH_BACKGROUND, RoundedCornerShape(percent = BADGE_CORNER_PERCENT)),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = brain, contentDescription = null, modifier = Modifier.size(size * BADGE_BRAIN_FRACTION))
    }
}
