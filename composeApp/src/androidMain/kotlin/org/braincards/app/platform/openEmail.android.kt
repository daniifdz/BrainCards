package org.braincards.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.koin.core.context.GlobalContext

actual fun openEmailClient(
    recipient: String,
    subject: String,
    body: String
) {
    // Obtenemos el contexto a través de Koin
    val context = try {
        GlobalContext.get().get<Context>()
    } catch (e: Exception) {
        null
    }

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
        println("Error: Contexto de Android no encontrado en Koin.")
    }
}