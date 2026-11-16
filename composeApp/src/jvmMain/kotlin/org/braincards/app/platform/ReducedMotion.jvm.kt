package org.braincards.app.platform

import androidx.compose.runtime.Composable

// El handoff no pide movimiento reducido en Desktop y la JVM no expone la preferencia del sistema.
@Composable
actual fun rememberReducedMotion(): Boolean = false
