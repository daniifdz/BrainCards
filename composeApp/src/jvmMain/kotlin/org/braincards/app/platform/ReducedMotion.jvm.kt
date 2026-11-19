package org.braincards.app.platform

import androidx.compose.runtime.Composable

// Desktop no pide movimiento reducido y la JVM no expone la preferencia del sistema.
@Composable
actual fun rememberReducedMotion(): Boolean = false
