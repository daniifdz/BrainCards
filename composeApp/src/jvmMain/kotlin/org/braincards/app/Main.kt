package org.braincards.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.braincards.app.data.local.DatabaseFactory
import org.braincards.app.data.local.SettingsDataStoreFactory
import org.braincards.app.di.desktopSyncModule
import org.braincards.app.di.followWindow
import org.braincards.app.di.initKoin
import org.braincards.app.di.shutdownAutoSync
import org.braincards.app.platform.DesktopForegroundMonitor
import javax.imageio.ImageIO
import org.koin.core.context.stopKoin

/** Mismo morado que SPLASH_BACKGROUND (#5B3FBF), como color AWT para el fondo de la ventana. */
private val SPLASH_AWT_BACKGROUND = java.awt.Color(0x5B, 0x3F, 0xBF)

/** Icono de la ventana desde `resources/icon.png`; si no se puede leer, la ventana sale sin icono. */
private fun loadWindowIcon(): BitmapPainter? = runCatching {
    Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")!!.use { ImageIO.read(it).toComposeImageBitmap() }
}.getOrNull()?.let { BitmapPainter(it) }

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
        val icon = remember { loadWindowIcon() }
        LaunchedEffect(windowState) {
            foregroundMonitor.followWindow(snapshotFlow { windowState.isMinimized })
        }
        Window(
            onCloseRequest = {
                shutdownAutoSync()
                exitApplication()
            },
            state = windowState,
            title = "BrainCards",
            icon = icon
        ) {
            // Fondo inicial de la ventana = morado del splash: evita el flash blanco antes del primer fotograma de Compose.
            window.background = SPLASH_AWT_BACKGROUND
            window.contentPane.background = SPLASH_AWT_BACKGROUND
            App()
        }
    }

    // La aplicacion de Compose ya termino: ahora si se para Koin (ejecuta los onClose pendientes).
    stopKoin()
}