package org.braincards.app.presentation.screens.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.braincards.app.presentation.screens.home.LogoMark

/**
 * Mientras no se sabe a donde arrancar (auth sin resolver): solo fondo y logo, como el splash. Sin texto, sin
 * Bienvenida ni Home. En Android cubre el hueco si el splash del sistema agota su espera maxima.
 */
@Composable
fun EntryPendingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        LogoMark(size = 64.dp)
    }
}
