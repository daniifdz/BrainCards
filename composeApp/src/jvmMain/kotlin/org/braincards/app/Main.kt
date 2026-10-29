package org.braincards.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.res.painterResource
import org.braincards.app.data.local.DatabaseFactory
import org.braincards.app.data.local.SettingsDataStoreFactory
import org.braincards.app.di.desktopSyncModule
import org.braincards.app.di.followWindow
import org.braincards.app.di.initKoin
import org.braincards.app.di.shutdownAutoSync
import org.braincards.app.platform.DesktopForegroundMonitor
import org.koin.core.context.stopKoin

fun main() {
    // Primer plano de la sync automatica = ventana visible y no minimizada (la alimenta el estado de la ventana).
    val foregroundMonitor = DesktopForegroundMonitor()
    // Koin se inicia UNA vez, fuera de la composicion (dentro de application { } se reejecutaria al recomponer).
    initKoin(
        databaseFactory = DatabaseFactory(),
        settingsDataStoreFactory = SettingsDataStoreFactory()
    ) {
        modules(desktopSyncModule(foregroundMonitor))
    }

    application {
        val windowState = rememberWindowState()
        LaunchedEffect(windowState) {
            foregroundMonitor.followWindow(snapshotFlow { windowState.isMinimized })
        }
        Window(
            onCloseRequest = {
                shutdownAutoSync() // para el controlador y cierra los monitores
                exitApplication()
            },
            state = windowState,
            title = "BrainCards",
            icon = painterResource("playstore-icon.png")
        ) {
            App()
        }
    }

    // La aplicacion de Compose ya termino: ahora si se para Koin (ejecuta los onClose pendientes).
    stopKoin()
}