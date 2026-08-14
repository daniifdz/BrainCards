package org.braincards.app.data.local

import java.io.File

internal fun appDataDir(): File {
    val userHome = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()

    val base = when {
        os.contains("win") ->
            System.getenv("APPDATA")?.let(::File) ?: File(userHome, "AppData/Roaming")
        os.contains("mac") ->
            File(userHome, "Library/Application Support")
        else ->
            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(userHome, ".local/share")
    }

    return File(base, "BrainCards").apply { mkdirs() }
}
