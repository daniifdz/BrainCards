package org.braincards.app.platform

/** Abre el cliente de correo con el mensaje preparado. Devuelve `false` si no se pudo abrir (no hay cliente o falla el sistema). */
expect fun openEmailClient(
    recipient: String,
    subject: String,
    body: String
): Boolean