package org.braincards.app.platform

import androidx.compose.runtime.Composable

/**
 * Intercepta el "atras" del sistema mientras [enabled] es true (handoff 5.1e: dentro del subnivel de un tema, el atras
 * vuelve al mazo). Con [enabled] = false no hace nada y el atras sigue su camino normal.
 *
 * Android: androidx.activity.compose.BackHandler (gesto / boton de atras). Desktop no tiene atras del sistema: no hace
 * nada (cuenta la flecha de la barra). `androidx.compose.ui.backhandler.BackHandler` no resuelve en commonMain con la
 * version actual de Compose Multiplatform sin una dependencia extra, de ahi este expect/actual.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
