package org.braincards.app.presentation.screens.study

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.braincards.app.domain.models.ReviewQuality

/*
 * Feedback de interaccion de la sesion de estudio (handoff v4.22, 5.5b; mockup `estudio.html`), como FUNCIONES PURAS y
 * constantes: nada aqui lee un reloj. El dibujo y los Animatable viven en `StudyContent.kt`.
 *
 * D2: mandan los PARAMETROS DEL MUELLE. Barra: spring(0.6, 380) (el handoff dice ~380 ms; el muelle real se asienta en ese orden).
 * Boton: bajada tween(60, FastOutLinearIn) y subida spring(0.5, 600); el handoff cita 180 ms para la subida, pero ese muelle tarda
 * ~300 ms en apagarse del todo: mandan los parametros.
 *
 * NOTA sobre el mockup: el fotograma de la barra dibuja un pico exagerado (25 % -> 36 % -> 33 %). Un spring(0.6) real solo pasa de
 * largo ~9,5 % del tramo recorrido (ver el test), asi que con un tramo de 1/12 el rebote real es de ~0,8 puntos: es sutil por diseno.
 */

/** Barra de progreso: muelle al avanzar tras cada repaso. */
const val STUDY_PROGRESS_DAMPING = 0.6f
const val STUDY_PROGRESS_STIFFNESS = 380f
val STUDY_PROGRESS_SPRING = spring<Float>(dampingRatio = STUDY_PROGRESS_DAMPING, stiffness = STUDY_PROGRESS_STIFFNESS)

/** Boton de dificultad al puntuar: bajada corta a 0.95 con brillo y vuelta con un pequeño rebote. */
const val STUDY_PRESS_DOWN_MS = 60
const val STUDY_PRESS_SCALE = 0.95f

/** Brillo +12 %: opacidad de una superposicion BLANCA (no un ColorFilter) sobre el boton. */
const val STUDY_PRESS_BRIGHTNESS = 0.12f
const val STUDY_PRESS_UP_DAMPING = 0.5f
const val STUDY_PRESS_UP_STIFFNESS = 600f

val STUDY_PRESS_DOWN_SPEC = tween<Float>(durationMillis = STUDY_PRESS_DOWN_MS, easing = FastOutLinearInEasing)
val STUDY_PRESS_UP_SPRING = spring<Float>(dampingRatio = STUDY_PRESS_UP_DAMPING, stiffness = STUDY_PRESS_UP_STIFFNESS)

/** Destino de la barra: la fraccion (M - N) / M acotada a 0..1. */
fun studyBarTarget(progress: Float): Float = progress.coerceIn(0f, 1f)

/**
 * Fraccion que SE DIBUJA. El muelle pasa de largo del destino (overshoot): con el ultimo repaso (destino 1.0) el valor
 * animado supera 1.0 y la barra no puede salirse de su pista ni ser negativa (al retroceder de un muelle).
 */
fun studyBarVisibleFraction(animated: Float): Float = animated.coerceIn(0f, 1f)

/** Escala del boton para un progreso de pulsacion [p] (0 = reposo, 1 = pulsado a fondo; el muelle de vuelta puede pasar de 0). */
fun studyPressScale(p: Float): Float = 1f - (1f - STUDY_PRESS_SCALE) * p

/** Opacidad de la superposicion blanca (brillo) para [p]; nunca negativa aunque el muelle de vuelta pase de 0. */
fun studyPressOverlayAlpha(p: Float): Float = (STUDY_PRESS_BRIGHTNESS * p).coerceIn(0f, STUDY_PRESS_BRIGHTNESS)

/**
 * Un boton debe pulsarse visualmente si NO hay "reducir animaciones", el pulso es NUEVO desde que el boton entro en
 * composicion ([token] != [baseToken]: un boton recien compuesto no repite un pulso viejo) y es de su calidad.
 */
fun shouldRunStudyPress(
    reducedMotion: Boolean,
    token: Int,
    baseToken: Int,
    pulsed: ReviewQuality?,
    own: ReviewQuality
): Boolean = !reducedMotion && token != baseToken && pulsed == own

/**
 * Estado COMPARTIDO del pulso de puntuacion (D7): lo disparan igual el clic/toque en un boton y las teclas 1-4 de Desktop, porque
 * ambos pasan por `fire(...)` antes de llamar al ViewModel (el pulso no depende de `interactionSource`). No retrasa nada: solo marca.
 * Pulsar muchas veces seguido solo incrementa [token]: el boton reinicia su animacion, no la acumula.
 */
@Stable
class RatePulseState {
    var token: Int by mutableIntStateOf(0)
        private set
    var quality: ReviewQuality? = null
        private set

    fun fire(quality: ReviewQuality) {
        this.quality = quality
        token += 1
    }
}

/**
 * El MANEJADOR UNICO de puntuacion de la pantalla: primero marca el pulso (estado compartido) y despues puntua. Lo usan los botones
 * (clic/toque) y las teclas 1-4 de Desktop, asi el pulso no depende del `interactionSource` del boton y nunca retrasa [rate].
 */
fun ratingHandler(pulse: RatePulseState, rate: (ReviewQuality) -> Unit): (ReviewQuality) -> Unit = { quality ->
    pulse.fire(quality)
    rate(quality)
}
