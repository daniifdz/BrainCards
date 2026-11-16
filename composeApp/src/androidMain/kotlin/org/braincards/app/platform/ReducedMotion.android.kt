package org.braincards.app.platform

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        isReducedMotion(
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, DEFAULT_ANIMATOR_DURATION_SCALE)
        )
    }
}
