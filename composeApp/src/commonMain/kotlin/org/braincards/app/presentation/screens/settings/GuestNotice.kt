package org.braincards.app.presentation.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Ajustes en modo invitado (sin sesion): en lugar de Cuenta / Sincronizacion, un aviso con boton hacia Bienvenida. */
@Composable
internal fun GuestNotice(onSignInClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.guest_notice_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.guest_notice_text),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onSignInClick) {
                Text(stringResource(Res.string.guest_notice_cta))
            }
        }
    }
}
