package org.braincards.app.presentation.screens.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Interruptor de la animacion de EntryPending en cada ARRANQUE EN FRIO de Android (13b2). Con `false`: comportamiento identico
 * a 13b (la capa solo sale si la navegacion tarda, el reloj arranca al componerse, sin listener de salida del splash) y el
 * splash del sistema conserva su logo. OJO: el icono transparente de `values-v31/splash_system_icon.xml` es un recurso y no lo
 * gobierna este valor; `SplashSystemIconConsistencyTest` exige que ambos esten de acuerdo (para revertir: ponerlo a `false` y
 * borrar ese fichero).
 */
const val SPLASH_ANIMATE_ON_COLD_START = true

/** Android minimo con splash propio del sistema y listener de salida fiable (Android 12). Por debajo, todo queda como estaba. */
const val SPLASH_COLD_START_MIN_SDK = 31

/**
 * Plan B si el listener de salida del splash del sistema nunca llega: el reloj arranca esta cantidad de ms DESPUES del primer
 * dibujo de EntryPending. Mas alto = mas seguro frente a arrancar la coreografia con el splash aun encima (se vería oculta);
 * mas bajo = menos espera si el listener falla. VALOR MEDIDO en la tablet y el emulador, ver el informe de 13b2.
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
