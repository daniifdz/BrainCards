package org.braincards.app.presentation.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * ¿La app está en primer plano? Android: ProcessLifecycleOwner. Desktop: ventana visible y no minimizada (no el foco). El
 * AutoSyncController solo conoce esta interfaz.
 */
interface ForegroundMonitor {
    val isForeground: StateFlow<Boolean>
}

/**
 * ¿Hay conexión de red? Android: ConnectivityManager (permiso ACCESS_NETWORK_STATE). Desktop: interfaz de red activa (el fallo
 * de red del propio sync más el backoff hace el resto).
 */
interface ConnectivityMonitor {
    val isOnline: StateFlow<Boolean>
}
