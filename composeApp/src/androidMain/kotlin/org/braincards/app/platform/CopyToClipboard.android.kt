package org.braincards.app.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.braincards.app.util.logError
import org.koin.core.context.GlobalContext

actual fun copyToClipboard(text: String): Boolean = try {
    val context = GlobalContext.get().get<Context>()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("BrainCards", text))
    true
} catch (e: Exception) {
    logError("Error al copiar al portapapeles", e)
    false
}
