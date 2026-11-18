package org.braincards.app.platform

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** Lo implementa `MainActivity`: recalcula el estilo de las barras con [statusBarMode]. */
interface SystemBarsHost {
    fun setEntryPendingVisible(visible: Boolean)
}

private fun Context.findSystemBarsHost(): SystemBarsHost? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is SystemBarsHost) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext
    }
    return null
}

@Composable
actual fun EntryPendingSystemBars(visible: Boolean) {
    val host = LocalContext.current.findSystemBarsHost()
    DisposableEffect(host, visible) {
        host?.setEntryPendingVisible(visible)
        // Si la composicion sale con la capa aun visible, no deja los iconos claros fijados.
        onDispose { if (visible) host?.setEntryPendingVisible(false) }
    }
}
