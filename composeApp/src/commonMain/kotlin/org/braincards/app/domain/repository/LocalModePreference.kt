package org.braincards.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * «Modo local elegido»: el usuario pulso "Continuar sin cuenta" en la Bienvenida de entrada. Sin sesion y sin este
 * flag la app arranca en Bienvenida; con el, en Home. Interfaz aparte, aditiva (no toca los repositorios de dominio).
 * Persistida (sobrevive a reiniciar la app).
 */
interface LocalModePreference {
    /** Valor actual al suscribirse y cada cambio. `false` si nunca se eligio. */
    val chosen: Flow<Boolean>

    /** `true` al elegir el modo local; `false` al perder la sesion (se borra la clave: NUNCA clear()). */
    suspend fun setChosen(value: Boolean)
}
