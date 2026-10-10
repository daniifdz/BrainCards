package org.braincards.app.data.fakes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake en memoria de DataStore<Preferences>, sin tocar disco. Interfaz real
 * verificada en el codigo fuente de datastore-core 1.2.1: solo `data: Flow<T>` y
 * `suspend fun updateData(transform): T` -- `.edit { }` (usado por SyncCoordinator)
 * es una extension que llama a updateData por debajo, asi que funciona tal cual
 * sobre este fake sin necesidad de reimplementarla aparte.
 */
class FakeSettingsDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
