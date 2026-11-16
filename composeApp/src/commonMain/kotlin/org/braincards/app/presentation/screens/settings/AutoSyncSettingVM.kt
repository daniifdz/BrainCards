package org.braincards.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.braincards.app.domain.repository.AutoSyncPreference
import org.braincards.app.util.logError

/** Avisos del interruptor de sync automatico. Tipo y no texto: SettingsScreen resuelve el string. */
enum class AutoSyncSettingEvent { SaveFailed }

/**
 * VM del interruptor de sincronizacion automatica de Ajustes.
 *
 * El interruptor NO es optimista: dibuja siempre [enabled] (el valor persistido). Si la escritura falla, el valor no
 * cambia y el interruptor vuelve solo a su sitio; ademas se avisa con [AutoSyncSettingEvent.SaveFailed]. Asi la UI
 * nunca muestra un valor distinto del que usa el AutoSyncController (que lee la misma preferencia).
 */
class AutoSyncSettingViewModel(
    private val preference: AutoSyncPreference
) : ViewModel() {

    /** `null` = aun no leido: el interruptor se muestra deshabilitado. */
    val enabled: StateFlow<Boolean?> = preference.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _events = MutableSharedFlow<AutoSyncSettingEvent>()
    val events: SharedFlow<AutoSyncSettingEvent> = _events.asSharedFlow()

    // Solo se toca desde el hilo principal (viewModelScope = Main): un segundo toque con una escritura en curso se ignora.
    private var isWriting = false

    fun setEnabled(value: Boolean) {
        if (isWriting) return
        isWriting = true
        viewModelScope.launch {
            try {
                preference.setEnabled(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("No se pudo guardar el interruptor de sync automatico", e)
                _events.emit(AutoSyncSettingEvent.SaveFailed)
            } finally {
                isWriting = false
            }
        }
    }
}
