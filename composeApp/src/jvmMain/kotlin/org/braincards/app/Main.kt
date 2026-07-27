package org.braincards.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.res.painterResource
import org.braincards.app.data.local.DatabaseFactory
import org.braincards.app.di.initKoin

fun main() = application {
    initKoin(databaseFactory = DatabaseFactory())
    Window(
        onCloseRequest = ::exitApplication,
        title = "BrainCards",
        icon = painterResource("playstore-icon.png")
    ) {
        App()
    }
}