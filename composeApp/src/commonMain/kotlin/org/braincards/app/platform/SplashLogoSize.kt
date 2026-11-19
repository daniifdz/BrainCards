package org.braincards.app.platform

import androidx.compose.ui.unit.Dp

/** Lado del cuadro del logo del splash y de EntryPending: ~120 dp en Android (zona segura de 192 dp), 160 en Desktop. */
expect fun splashLogoSize(): Dp
