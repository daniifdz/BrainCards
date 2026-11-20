package org.braincards.app.platform

private const val HEX_DIGITS = "0123456789ABCDEF"

/** Enlace `mailto:` con asunto y cuerpo codificados en porcentaje (UTF-8; el espacio es `%20`, nunca `+`). */
fun buildMailtoUri(recipient: String, subject: String, body: String): String =
    "mailto:$recipient?subject=${percentEncode(subject)}&body=${percentEncode(body)}"

private fun percentEncode(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '-' || char == '_' || char == '.' || char == '~') {
            append(char)
        } else {
            append('%')
            append(HEX_DIGITS[value shr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }
}
