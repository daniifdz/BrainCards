package org.braincards.app.presentation.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * ¿La app esta en primer plano? Android: ProcessLifecycleOwner. Desktop: ventana visible y no minimizada (no el
 * foco). El AutoSyncController solo conoce esta interfaz; la implementacion real se enchufa en el sub-paso 4.
 */
interface ForegroundMonitor {
    val isForeground: StateFlow<Boolean>
}

/**
 * ¿Hay conexion de red? Android: ConnectivityManager (permiso ACCESS_NETWORK_STATE). Desktop: interfaz de red
 * activa (el fallo de red del propio sync + backoff hace el resto). Solo la interfaz en el sub-paso 3.
 */
interface ConnectivityMonitor {
    val isOnline: StateFlow<Boolean>
}
