package org.braincards.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.lang.ref.WeakReference

object AndroidContext {
    private var contextRef: WeakReference<Context>? = null

    fun setContext(context: Context) {
        contextRef = WeakReference(context)
    }

    fun getContext(): Context? = contextRef?.get()
}

actual fun openEmailClient(
    recipient: String,
    subject: String,
    body: String
) {
    val context = AndroidContext.getContext()
    if (context != null) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    } else {
        println("Error: Contexto de Android no inicializado.")
    }
}