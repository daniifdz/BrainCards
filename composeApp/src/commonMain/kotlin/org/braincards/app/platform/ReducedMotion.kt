package org.braincards.app.platform

import androidx.compose.runtime.Composable

/** Escala de animaciones del sistema (Android: ANIMATOR_DURATION_SCALE). 0 = el usuario las desactivo. */
internal fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** Valor por defecto si el ajuste no existe en el sistema: animaciones normales. */
internal const val DEFAULT_ANIMATOR_DURATION_SCALE = 1f

/** true = no animar. Android lo lee del ajuste del sistema; Desktop no tiene preferencia equivalente: siempre false. */
@Composable
expect fun rememberReducedMotion(): Boolean
