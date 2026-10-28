package org.braincards.app.presentation.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ForegroundMonitor cuyo valor lo fija quien conoce el estado de la plataforma (observador de ciclo de vida, ventana). */
open class SettableForegroundMonitor(initial: Boolean) : ForegroundMonitor {
    private val state = MutableStateFlow(initial)
    override val isForeground: StateFlow<Boolean> = state.asStateFlow()

    protected fun setForeground(value: Boolean) {
        state.value = value
    }
}
