package org.braincards.app.platform

/**
 * ¿Se muestran las pistas de teclado (número bajo cada botón de puntuación, «o pulsa Espacio»)? En Desktop sí; en Android no.
 * Los atajos funcionan igual en los dos (un teclado físico en Android también los usa): esto solo decide si se dibujan las
 * pistas.
 */
expect val showKeyboardHints: Boolean
