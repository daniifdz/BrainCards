package org.braincards.app.platform

/**
 * Copia [text] al portapapeles del sistema. Devuelve `false` si no se pudo. Es un expect/actual porque la API de portapapeles de
 * Compose Multiplatform 1.9 no permite crear la entrada de texto desde `commonMain`.
 */
expect fun copyToClipboard(text: String): Boolean
