package org.braincards.app.di

import kotlinx.coroutines.flow.Flow
import org.braincards.app.platform.DesktopConnectivityMonitor
import org.braincards.app.platform.DesktopForegroundMonitor
import org.braincards.app.presentation.sync.AutoSyncController
import org.braincards.app.presentation.sync.ConnectivityMonitor
import org.braincards.app.presentation.sync.ForegroundMonitor
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * Monitores de la sync automatica en Desktop. [foreground] lo crea Main.kt (lo alimenta el estado de la ventana);
 * [connectivityFactory] permite a los tests usar otro sondeo. La conectividad se cierra (deja de sondear) al cerrar Koin.
 */
fun desktopSyncModule(
    foreground: DesktopForegroundMonitor,
    connectivityFactory: () -> DesktopConnectivityMonitor = { DesktopConnectivityMonitor() }
) = module {
    single<ForegroundMonitor> { foreground }
    single<ConnectivityMonitor> { connectivityFactory() } onClose { (it as? AutoCloseable)?.close() }
}

/** Alimenta el monitor con el estado de la ventana: primer plano = NO minimizada. Main.kt le pasa snapshotFlow { isMinimized }. */
suspend fun DesktopForegroundMonitor.followWindow(minimized: Flow<Boolean>) {
    minimized.collect { onWindowMinimizedChanged(it) }
}

/**
 * Cierre ordenado de la sync automatica al cerrar la ventana: para el controlador y cierra los monitores. Idempotente.
 * stopKoin() lo llama Main.kt DESPUES de que termine la aplicacion de Compose (parar Koin mientras aun se compone
 * podria romper un koinViewModel en la ultima recomposicion).
 */
fun shutdownAutoSync() {
    val koin = GlobalContext.getOrNull() ?: return
    runCatching { koin.get<AutoSyncController>().stop() }
    runCatching { (koin.get<ConnectivityMonitor>() as? AutoCloseable)?.close() }
    runCatching { (koin.get<ForegroundMonitor>() as? AutoCloseable)?.close() }
}
