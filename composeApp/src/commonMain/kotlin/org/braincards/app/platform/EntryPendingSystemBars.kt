package org.braincards.app.platform

import androidx.compose.runtime.Composable

/**
 * Avisa a la plataforma de que EntryPending esta ([visible]) o ha dejado de estar (empieza su fundido) para que la barra de
 * estado use iconos claros sobre el morado ([statusBarMode]). Android: lo aplica `MainActivity` (la unica que llama a
 * `enableEdgeToEdge`, asi nada pisa el estilo). Desktop: no hace nada.
 */
@Composable
expect fun EntryPendingSystemBars(visible: Boolean)
