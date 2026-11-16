package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Interruptor de sincronizacion automatica de Ajustes. Interfaz aparte, aditiva (no toca los repositorios de dominio).
 * Persistida. Solo gobierna el sync AUTOMATICO: "Sincronizar ahora" y la comprobacion previa al cierre de sesion no
 * dependen de el.
 */
interface AutoSyncPreference {
    /** Valor actual al suscribirse y cada cambio. `true` (activado) si nunca se toco. */
    val enabled: Flow<Boolean>

    suspend fun setEnabled(value: Boolean)
}
