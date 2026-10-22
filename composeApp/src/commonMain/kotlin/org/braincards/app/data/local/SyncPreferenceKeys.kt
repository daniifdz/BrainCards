package org.braincards.app.data.local

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

// Claves del DataStore (braincards.preferences_pb) que son ESTADO DE SYNC. Sitio compartido: las usan
// SyncCoordinator (que las borra en el cierre de sesion y en el borrado de cuenta) y los almacenes que
// las escriben. El mismo fichero guarda tambien preferencias del dispositivo (app_theme), que NO son
// estado de sync y no se tocan.

/** Instante (ISO UTC) del ultimo sync completo y correcto; lo muestra Ajustes. */
val LAST_SUCCESSFUL_SYNC_KEY = stringPreferencesKey("last_successful_sync_at")

/**
 * Marca de agua del pull incremental: UN solo JSON con el usuario, el cursor de cada tabla y la fecha
 * del ultimo pull completo (ver DataStorePullWatermarkStore).
 */
val PULL_WATERMARK_KEY = stringPreferencesKey("pull_watermark")

/**
 * TODAS las claves del DataStore que son estado de sync. SyncCoordinator.resetSyncState() borra
 * exactamente estas, una a una con remove (NUNCA clear(): app_theme y demas preferencias se
 * conservan). Una clave de sync nueva se anade aqui y nada mas.
 */
val SYNC_PREFS_KEYS: List<Preferences.Key<*>> = listOf(LAST_SUCCESSFUL_SYNC_KEY, PULL_WATERMARK_KEY)
