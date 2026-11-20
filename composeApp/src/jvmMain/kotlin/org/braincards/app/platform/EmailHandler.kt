package org.braincards.app.platform

import java.awt.Desktop
import java.net.URI
import org.braincards.app.util.logError

actual fun openEmailClient(
    recipient: String,
    subject: String,
    body: String
): Boolean = try {
    if (!Desktop.isDesktopSupported()) {
        false
    } else {
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.MAIL)) {
            false
        } else {
            desktop.mail(URI(buildMailtoUri(recipient, subject, body)))
            true
        }
    }
} catch (e: Exception) {
    logError("Error al intentar abrir el cliente de email", e)
    false
}
