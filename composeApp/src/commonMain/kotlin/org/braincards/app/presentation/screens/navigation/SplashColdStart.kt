package org.braincards.app.presentation.screens.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Interruptor de la animación de EntryPending en cada arranque en frío de Android. Con `false`: la capa solo sale si la
 * navegación tarda, el reloj arranca al componerse, no hay listener de salida del splash y el splash del sistema conserva su
 * logo. Ojo: el icono transparente de `values-v31/splash_system_icon.xml` es un recurso y no lo gobierna este valor;
 * `SplashSystemIconConsistencyTest` exige que ambos estén de acuerdo (para revertir: ponerlo a `false` y borrar ese fichero).
 */
const val SPLASH_ANIMATE_ON_COLD_START = true

/** Android minimo con splash propio del sistema y listener de salida fiable (Android 12). Por debajo, todo queda como estaba. */
const val SPLASH_COLD_START_MIN_SDK = 31

/**
 * Plan B si el listener de salida del splash del sistema nunca llega: el reloj arranca esta cantidad de ms después del primer
 * dibujo de EntryPending. Más alto = más seguro frente a arrancar la coreografía con el splash aún encima (se vería oculta); más
 * bajo = menos espera si el listener falla. Valor medido en una tablet y en el emulador.
 */
const val SPLASH_EXIT_SAFETY_MS = 1_000L

/**
 * Arranque en frio = proceso nuevo: [freshProcess] (bandera de proceso consumida una sola vez), sin estado guardado (ni rotacion
 * ni restauracion tras muerte de proceso: [hasSavedState]) y Android >= 12.
 */
fun shouldPlayColdStartSplash(enabled: Boolean, sdkInt: Int, hasSavedState: Boolean, freshProcess: Boolean): Boolean =
    enabled && sdkInt >= SPLASH_COLD_START_MIN_SDK && !hasSavedState && freshProcess

/** Aviso de que el splash del sistema ya se ha retirado (lo dispara la plataforma); el reloj de la coreografia arranca entonces. */
@Stable
class SplashExitSignal {
    var fired: Boolean by mutableStateOf(false)
        private set

    fun fire() {
        fired = true
    }
}
