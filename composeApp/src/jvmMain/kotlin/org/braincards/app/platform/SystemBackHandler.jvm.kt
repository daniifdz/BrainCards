package org.braincards.app.platform

import androidx.compose.runtime.Composable

// Desktop no tiene "atras" del sistema: el subnivel de un tema se cierra con la flecha de la barra.
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
