package org.braincards.app.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.braincards.app.presentation.sync.ConnectivityMonitor
import org.braincards.app.presentation.sync.NetworkInterfaceInfo
import org.braincards.app.presentation.sync.SettableForegroundMonitor
import org.braincards.app.presentation.sync.hasUsableNetwork
import java.net.NetworkInterface
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Cada cuanto Desktop mira si hay interfaz de red activa. */
val DESKTOP_CONNECTIVITY_POLL_INTERVAL: Duration = 10.seconds

/**
 * Desktop: "primer plano" = ventana visible y NO minimizada (no el foco). La ventana arranca visible, asi que el
 * valor inicial es true; Main.kt lo alimentara con el estado de la ventana (4.2).
 */
class DesktopForegroundMonitor(initiallyVisible: Boolean = true) : SettableForegroundMonitor(initiallyVisible) {
    fun onWindowMinimizedChanged(minimized: Boolean) = setForeground(!minimized)
}

/**
 * Desktop: hay conexion si [probe] dice que hay una interfaz de red usable. Valor inicial SINCRONO (el primer sync no
 * espera al primer sondeo); despues se vuelve a mirar cada [interval] en [scope] (en produccion Dispatchers.IO).
 * Si el sondeo falla se conserva el ultimo valor. [close] cancela el sondeo.
 */
class DesktopConnectivityMonitor(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val interval: Duration = DESKTOP_CONNECTIVITY_POLL_INTERVAL,
    private val probe: () -> Boolean = ::probeSystemNetworkInterfaces
) : ConnectivityMonitor, AutoCloseable {

    private val state = MutableStateFlow(runCatching(probe).getOrDefault(true))
    override val isOnline: StateFlow<Boolean> = state.asStateFlow()

    private val job: Job = scope.launch {
        while (isActive) {
            delay(interval)
            runCatching(probe).onSuccess { state.value = it }
        }
    }

    override fun close() {
        job.cancel()
    }
}

/** Sondeo real: las interfaces del sistema, filtradas por [hasUsableNetwork]. */
fun probeSystemNetworkInterfaces(): Boolean {
    val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { nic ->
        NetworkInterfaceInfo(
            isUp = nic.isUp,
            isLoopback = nic.isLoopback,
            isVirtual = nic.isVirtual,
            hasUsableAddress = nic.inetAddresses.toList().any { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        )
    }
    return hasUsableNetwork(interfaces)
}
