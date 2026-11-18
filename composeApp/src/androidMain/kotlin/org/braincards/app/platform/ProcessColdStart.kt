package org.braincards.app.platform

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bandera de PROCESO: `true` solo la primera vez que se consulta tras arrancar el proceso. Una rotacion, una restauracion tras
 * muerte de proceso (otro proceso, pero con estado guardado: lo descarta `shouldPlayColdStartSplash`) o un arranque en
 * caliente (proceso vivo) ya la encuentran consumida.
 */
object ProcessColdStart {
    private val fresh = AtomicBoolean(true)

    /** Consume la bandera: llamar UNA vez por onCreate de la actividad. */
    fun consume(): Boolean = fresh.getAndSet(false)
}
