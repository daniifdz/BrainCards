package org.braincards.app.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import org.braincards.app.util.logError

actual fun copyToClipboard(text: String): Boolean = try {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    true
} catch (e: Exception) {
    logError("Error al copiar al portapapeles", e)
    false
}
