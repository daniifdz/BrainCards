package org.braincards.app.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.splash_logo
import org.jetbrains.compose.resources.painterResource

/** Morado de marca del splash: el `brand` del tema CLARO, fijo en claro y oscuro (handoff 5.8). Igual que `splash_background` en Android. */
val SPLASH_BACKGROUND = Color(0xFF5B3FBF)

/**
 * Cerebro blanco del splash y de EntryPending (vector de composeResources, copia del de androidMain/res; ver
 * SplashLogoConsistencyTest). Decorativo. [modifier] queda como hueco para la animacion futura.
 */
@Composable
fun SplashLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.splash_logo),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
