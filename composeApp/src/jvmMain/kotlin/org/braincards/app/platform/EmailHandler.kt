package org.braincards.app.platform

import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

actual fun openEmailClient(
    recipient: String,
    subject: String,
    body: String
) {
    val desktop = Desktop.getDesktop()
    try {
        val encodedSubject = URLEncoder.encode(subject, StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8.toString()).replace("+", "%20")
        
        val uri = URI("mailto:$recipient?subject=$encodedSubject&body=$encodedBody")
        desktop.mail(uri)
    } catch (e: Exception) {
        println("Error al intentar abrir el cliente de email: ${e.message}")
    }
}