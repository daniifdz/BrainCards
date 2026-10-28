package org.braincards.app.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.braincards.app.presentation.sync.ConnectivityMonitor
import org.braincards.app.presentation.sync.DefaultNetworkTracker
import org.braincards.app.presentation.sync.SettableForegroundMonitor

/**
 * Android: primer plano = el PROCESO esta al menos en STARTED (ProcessLifecycleOwner). Retrasa ~700 ms el paso a
 * segundo plano, asi que recrear la Activity (rotar la pantalla) no lo dispara. Debe crearse en el hilo principal
 * (Application.onCreate). El valor inicial es false hasta que arranca la primera Activity: ese paso a true cuenta
 * como "abrir la app". [close] da de baja el observador.
 */
class AndroidForegroundMonitor(
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle
) : SettableForegroundMonitor(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)),
    DefaultLifecycleObserver,
    AutoCloseable {

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) = setForeground(true)

    override fun onStop(owner: LifecycleOwner) = setForeground(false)

    override fun close() {
        lifecycle.removeObserver(this)
    }
}

/**
 * Android: red por defecto con INTERNET (sin exigir VALIDATED), vigilada con registerDefaultNetworkCallback
 * (API 24 = minSdk). La logica de cambio de red (onLost tardio de la red antigua, perdida de INTERNET) vive en
 * [DefaultNetworkTracker], pura y con tests; aqui solo se traducen los callbacks.
 *
 * Sin permiso ACCESS_NETWORK_STATE (SecurityException) o si no se puede registrar el callback: online = true fijo,
 * para NO bloquear el sync (el fallo del propio sync + el backoff cubren la falta de red). [close] da de baja el callback.
 */
class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor, AutoCloseable {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val lock = Any()
    private val tracker: DefaultNetworkTracker?
    private val state: MutableStateFlow<Boolean>
    private var registered = false

    override val isOnline: StateFlow<Boolean> get() = state.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update { onAvailable(network.networkHandle) }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update {
            onCapabilitiesChanged(network.networkHandle, capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        }

        override fun onLost(network: Network) = update { onLost(network.networkHandle) }
    }

    init {
        var initialTracker: DefaultNetworkTracker? = null
        try {
            val active = connectivityManager.activeNetwork
            val hasInternet = active?.let {
                connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true
            initialTracker = DefaultNetworkTracker(active?.networkHandle, hasInternet)
            connectivityManager.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (e: SecurityException) {
            initialTracker = null // falta ACCESS_NETWORK_STATE
        } catch (e: RuntimeException) {
            initialTracker = null // p. ej. TooManyRequestsException: no se puede vigilar la red
        }
        tracker = initialTracker
        state = MutableStateFlow(tracker?.isOnline ?: true)
    }

    private inline fun update(block: DefaultNetworkTracker.() -> Unit) {
        val t = tracker ?: return
        synchronized(lock) {
            t.block()
            state.value = t.isOnline
        }
    }

    override fun close() {
        if (registered) {
            registered = false
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }
}
