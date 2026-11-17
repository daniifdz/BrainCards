package org.braincards.app.platform

/**
 * ¿Se muestran las pistas de teclado (numero bajo cada boton de puntuacion, "o pulsa Espacio")? Desktop si; Android no
 * (mockup `estudio-mobile.html`). Los ATAJOS funcionan igual en los dos (un teclado fisico en Android tambien los usa):
 * esto solo decide si se DIBUJAN las pistas.
 */
expect val showKeyboardHints: Boolean
