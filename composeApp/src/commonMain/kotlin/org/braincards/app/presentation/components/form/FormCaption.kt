package org.braincards.app.presentation.components.form

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/**
 * Texto bajo un control que no es un campo de texto (p. ej. [CodeInput]): el [error] manda sobre el [hint] y se
 * pinta con el color de error, como el `supportingText` de FormTextField. Sin texto no ocupa sitio.
 */
@Composable
fun FormCaption(error: String?, hint: String? = null, modifier: Modifier = Modifier) {
    val text = error ?: hint ?: return
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
