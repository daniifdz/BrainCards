package org.braincards.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.braincards.app.domain.repository.AutoSyncPreference

/**
 * LECTURA UNICA del interruptor (la usan la preferencia y el SyncCoordinator): si DataStore falla al leer (IOException,
 * incluida CorruptionException = preferencias corruptas) se lee como si estuvieran vacias, es decir, el valor por
 * defecto: ACTIVADO. Cualquier otra excepcion se propaga.
 */
internal fun DataStore<Preferences>.autoSyncEnabledFlow(): Flow<Boolean> = data
    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
    .map { prefs -> prefs[AUTO_SYNC_ENABLED_KEY] ?: true }
    .distinctUntilChanged()

/** [AutoSyncPreference] sobre el DataStore de preferencias. Solo toca SU clave ([AUTO_SYNC_ENABLED_KEY]): nunca `clear()`. */
class DataStoreAutoSyncPreference(private val dataStore: DataStore<Preferences>) : AutoSyncPreference {

    override val enabled: Flow<Boolean> = dataStore.autoSyncEnabledFlow()

    override suspend fun setEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[AUTO_SYNC_ENABLED_KEY] = value }
    }
}
