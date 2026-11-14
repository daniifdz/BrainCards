package org.braincards.app.presentation.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.*
import kotlinx.coroutines.flow.collectLatest
import org.braincards.app.presentation.components.form.FormBanner
import org.braincards.app.presentation.components.form.FormScaffold
import org.braincards.app.presentation.components.form.FormTextField
import org.braincards.app.presentation.components.form.SubmitButton
import org.braincards.app.presentation.components.form.formField
import org.braincards.app.presentation.components.form.rememberFormFocus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

/**
 * Recuperar contraseña (v4.4): correo (precargado con el escrito en Bienvenida), error de campo «Escribe tu correo
 * electronico», banner de red unificado y «Volver a iniciar sesion». El correo se guarda con rememberSaveable.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun ForgotPasswordScreen(
    initialEmail: String = "",
    onBackClick: () -> Unit,
    onCodeSent: (email: String) -> Unit
) {
    val viewModel = koinViewModel<AuthViewModel>()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val uiError by viewModel.uiError.collectAsState()

    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var clientError by remember { mutableStateOf<AuthMessage?>(null) }
    val focus = rememberFormFocus(1)

    fun submit() {
        if (isSubmitting) return
        clientError = validateEmail(email)
        if (clientError != null) {
            focus.requestFocus(0)
            return
        }
        viewModel.clearError()
        viewModel.sendPasswordReset(email.trim())
    }

    LaunchedEffect(Unit) {
        viewModel.passwordResetCodeSentEvent.collectLatest { sentEmail -> onCodeSent(sentEmail) }
    }

    val bannerError = uiError as? AuthFormTarget.Banner
    val serverEmailError = (uiError as? AuthFormTarget.Field)?.takeIf { it.field == AuthField.EMAIL }?.message
    val emailError = (clientError ?: serverEmailError)?.text()

    FormScaffold(topBar = { AuthTopBar(onBack = onBackClick, enabled = !isSubmitting) }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.auth_forgot_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp)
            )
            Text(
                text = stringResource(Res.string.auth_forgot_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (bannerError != null) {
            FormBanner(
                message = bannerError.message.text(),
                onRetry = if (bannerError.retryable) ({ submit() }) else null
            )
        }

        FormTextField(
            value = email,
            onValueChange = {
                email = it
                clientError = null
                viewModel.clearError()
            },
            label = stringResource(Res.string.auth_email_label),
            placeholder = stringResource(Res.string.auth_email_placeholder),
            error = emailError,
            enabled = !isSubmitting,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            modifier = Modifier.fillMaxWidth().formField(focus, 0) { submit() }
        )

        SubmitButton(
            text = stringResource(Res.string.auth_send_code),
            loading = isSubmitting,
            onClick = { submit() }
        )

        TextButton(onClick = onBackClick, enabled = !isSubmitting) {
            Text(stringResource(Res.string.auth_back_to_login))
        }
    }
}
