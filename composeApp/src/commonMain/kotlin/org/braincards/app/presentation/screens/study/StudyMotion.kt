package org.braincards.app.presentation.screens.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

/**
 * Botones "fantasma" (13d2): tras puntuar, los botones de dificultad siguen DIBUJADOS este tiempo (ms) encima de la zona de
 * "Mostrar respuesta" para que el pulso se vea entero, aunque la tarjeta ya haya avanzado (el guardado es rapido y los botones
 * reales salen de composicion antes de que acabe el pulso). El fantasma es solo dibujo: no recibe toques ni foco ni semantica, y no
 * retrasa nada. INTERRUPTOR DE REVERSION: con 0 no hay fantasma y todo se comporta como en 13d.
 */
const val STUDY_PULSE_GHOST_MS = 240

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

/** El pulso se reproduce si NO hay "reducir animaciones" y es NUEVO desde que la pantalla se compuso (no repite uno viejo tras recomponer). */
fun shouldPlayStudyPulse(reducedMotion: Boolean, token: Int, baseToken: Int): Boolean = !reducedMotion && token != baseToken

/** Progreso de pulsacion (0..1) que ve un boton: el del pulso en marcha solo si es SU calidad; los demas, en reposo. */
fun studyPressProgress(pulsed: ReviewQuality?, own: ReviewQuality, press: Float): Float = if (pulsed == own) press else 0f

/** Fantasma habilitado: interruptor distinto de 0 y sin "reducir animaciones". */
fun studyGhostEnabled(ghostMs: Int, reducedMotion: Boolean): Boolean = ghostMs > 0 && !reducedMotion

/**
 * El fantasma se dibuja SOLO mientras dura su ventana ([fading]) Y los botones reales ya no estan (la respuesta dejo de verse): si
 * la puntuacion falla, los botones reales siguen en pantalla y acaban su pulso ellos mismos.
 */
fun studyShowGhost(enabled: Boolean, fading: Boolean, answerVisible: Boolean): Boolean = enabled && fading && !answerVisible

/** Opacidad del fantasma para un avance [fade] de 0 (recien puntuado) a 1 (ventana terminada): se desvanece lineal. */
fun studyGhostAlpha(fade: Float): Float = (1f - fade).coerceIn(0f, 1f)

/**
 * Estado COMPARTIDO del pulso de puntuacion (D7): lo disparan igual el clic/toque en un boton y las teclas 1-4 de Desktop, porque
 * ambos pasan por `fire(...)` antes de llamar al ViewModel (el pulso no depende de `interactionSource`). No retrasa nada: solo marca.
 * Pulsar muchas veces seguido solo incrementa [token]: el boton reinicia su animacion, no la acumula.
 */
@Stable
class RatePulseState {
    var token: Int by mutableIntStateOf(0)
        private set
    var quality: ReviewQuality? by mutableStateOf(null)
        private set

    /** 0 = reposo, 1 = pulsado a fondo (el muelle de vuelta puede pasar de 0). Se lee en la fase de dibujo. */
    val press = Animatable(0f)

    /** 0 = recien puntuado (fantasma opaco) .. 1 = ventana del fantasma terminada. Un solo Animatable: no hay fantasmas apilados. */
    val ghostFade = Animatable(1f)

    fun fire(quality: ReviewQuality) {
        this.quality = quality
        token += 1
    }

    /**
     * Reproduce el pulso y la ventana del fantasma. Vive aqui (no en cada boton) para que el fantasma CONTINUE el pulso cuando los
     * botones reales salen de composicion. Un pulso nuevo cancela el anterior (la corrutina de la pantalla se reinicia) y el fundido
     * vuelve a 0: el fantasma anterior se SUSTITUYE, nunca se acumulan.
     */
    suspend fun play(ghostMs: Int) = coroutineScope {
        if (ghostMs > 0) {
            ghostFade.snapTo(0f)
            launch { ghostFade.animateTo(1f, tween(ghostMs, easing = LinearEasing)) }
        }
        press.animateTo(1f, STUDY_PRESS_DOWN_SPEC)
        press.animateTo(0f, STUDY_PRESS_UP_SPRING)
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
