package org.braincards.app.presentation.sync

/**
 * Maquina de estados PURA de la "red por defecto" de Android (sin tipos de Android: la red es un id Long, el glue
 * usa Network.networkHandle). Resuelve el problema de los cambios de red (Wi-Fi <-> datos): puede llegar onAvailable
 * de la red nueva y onLost de la ANTIGUA despues, y ese onLost tardio no debe poner la app offline.
 *
 * Reglas:
 *  - [onAvailable]: la red indicada pasa a ser la actual (provisionalmente con INTERNET: una red por defecto lo
 *    tiene por definicion; las capacidades reales llegan enseguida en onCapabilitiesChanged).
 *  - [onCapabilitiesChanged]: solo cuenta si es la red actual (o si no hay ninguna, en cuyo caso se adopta); la
 *    perdida de INTERNET en la red actual la deja offline y recuperarla la vuelve a poner online.
 *  - [onLost]: solo cuenta si es la red actual; el de una red que ya no lo es se ignora.
 * No se exige NET_CAPABILITY_VALIDATED. No es hilo-seguro: el glue serializa las llamadas.
 */
class DefaultNetworkTracker(activeNetwork: Long? = null, activeHasInternet: Boolean = false) {

    private var currentId: Long? = activeNetwork
    private var hasInternet: Boolean = activeNetwork != null && activeHasInternet

    /** Hay una red por defecto y tiene capacidad de INTERNET. */
    val isOnline: Boolean get() = currentId != null && hasInternet

    fun onAvailable(id: Long) {
        currentId = id
        hasInternet = true
    }

    fun onCapabilitiesChanged(id: Long, internet: Boolean) {
        if (currentId == null) currentId = id
        if (id != currentId) return // capacidades de una red que ya no es la por defecto
        hasInternet = internet
    }

    fun onLost(id: Long) {
        if (id != currentId) return // onLost tardio de la red antigua tras cambiar a la nueva
        currentId = null
        hasInternet = false
    }
}
