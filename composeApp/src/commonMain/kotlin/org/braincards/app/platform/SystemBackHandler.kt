package org.braincards.app.platform

import androidx.compose.runtime.Composable

/**
 * Intercepta el «atrás» del sistema mientras [enabled] es true (p. ej. dentro del subnivel de un tema, atrás vuelve al mazo).
 * Con [enabled] = false no hace nada y el atrás sigue su camino normal.
 *
 * Android: androidx.activity.compose.BackHandler (gesto o botón de atrás). Desktop no tiene atrás del sistema: no hace nada
 * (cuenta la flecha de la barra). `androidx.compose.ui.backhandler.BackHandler` no resuelve en commonMain con la versión
 * actual de Compose Multiplatform sin una dependencia extra, de ahí este expect/actual.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
