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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.*
import kotlinx.coroutines.flow.collectLatest
import org.braincards.app.presentation.components.form.CodeInput
import org.braincards.app.presentation.components.form.FormBanner
import org.braincards.app.presentation.components.form.FormCaption
import org.braincards.app.presentation.components.form.FormScaffold
import org.braincards.app.presentation.components.form.FormTextField
import org.braincards.app.presentation.components.form.SubmitButton
import org.braincards.app.presentation.components.form.formField
import org.braincards.app.presentation.components.form.rememberFormFocus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

/** Orden de foco de Restablecer (v4.4): codigo (0) -> contraseña nueva (1) -> confirmacion (2). */
private val FIELD_ORDER = listOf(AuthField.CODE, AuthField.PASSWORD, AuthField.CONFIRM_PASSWORD)

/**
 * Restablecer contraseña (v4.4): codigo (6 casillas) + contraseña nueva + confirmacion, en una sola pantalla.
 * El codigo se guarda con rememberSaveable; las DOS contraseñas solo con remember (no se conservan al rotar).
 * Errores de campo (codigo incompleto, contraseña corta, no coinciden) y del servidor (codigo incorrecto o
 * caducado bajo el codigo; contraseña debil bajo la contraseña).
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun ResetPasswordScreen(
    email: String,
    onBackClick: () -> Unit,
    onBackToLogin: () -> Unit,
    onPasswordResetCompleted: () -> Unit
) {
    val viewModel = koinViewModel<AuthViewModel>()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val codeVerified by viewModel.codeVerified.collectAsState()
    val uiError by viewModel.uiError.collectAsState()

    var code by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }      // NO rememberSaveable
    var confirmation by remember { mutableStateOf("") }  // NO rememberSaveable
    var clientErrors by remember { mutableStateOf(FieldErrors.None) }
    val focus = rememberFormFocus(FIELD_ORDER.size)

    fun submit() {
        if (isSubmitting) return
        val errors = validateResetForm(code, password, confirmation)
        clientErrors = errors
        if (!errors.isEmpty) {
            errors.firstIn(FIELD_ORDER)?.let { focus.requestFocus(FIELD_ORDER.indexOf(it)) }
            return
        }
        viewModel.clearError()
        viewModel.confirmPasswordReset(email, code, password)
    }

    LaunchedEffect(Unit) {
        viewModel.passwordResetCompletedEvent.collectLatest { onPasswordResetCompleted() }
    }

    val bannerError = uiError as? AuthFormTarget.Banner
    val serverField = uiError as? AuthFormTarget.Field
    fun serverError(field: AuthField) = serverField?.takeIf { it.field == field }?.message
    val codeError = (clientErrors[AuthField.CODE] ?: serverError(AuthField.CODE))?.text()
    val passwordError = (clientErrors[AuthField.PASSWORD] ?: serverError(AuthField.PASSWORD))?.text()
    val confirmError = clientErrors[AuthField.CONFIRM_PASSWORD]?.text()

    FormScaffold(topBar = { AuthTopBar(onBack = onBackClick, enabled = !isSubmitting) }) {
        Text(
            text = stringResource(Res.string.auth_reset_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp)
        )

        if (bannerError != null) {
            FormBanner(
                message = bannerError.message.text(),
                onRetry = if (bannerError.retryable) ({ submit() }) else null
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(Res.string.auth_reset_code_label),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            )
            CodeInput(
                value = code,
                onValueChange = {
                    code = it
                    clientErrors = clientErrors.without(AuthField.CODE)
                    viewModel.clearError()
                },
                isError = codeError != null,
                // Con el codigo ya verificado (y la contraseña rechazada) no hace falta reescribirlo.
                enabled = !isSubmitting && !codeVerified,
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth().formField(focus, 0) { submit() }
            )
            FormCaption(
                error = codeError,
                hint = stringResource(
                    if (codeVerified) Res.string.auth_reset_code_verified else Res.string.auth_reset_code_hint
                )
            )
        }

        FormTextField(
            value = password,
            onValueChange = {
                password = it
                clientErrors = clientErrors.without(AuthField.PASSWORD).without(AuthField.CONFIRM_PASSWORD)
                viewModel.clearError()
            },
            label = stringResource(Res.string.auth_reset_password_label),
            error = passwordError,
            hint = stringResource(Res.string.auth_password_hint),
            enabled = !isSubmitting,
            isPassword = true,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth().formField(focus, 1) { submit() }
        )

        FormTextField(
            value = confirmation,
            onValueChange = {
                confirmation = it
                clientErrors = clientErrors.without(AuthField.CONFIRM_PASSWORD)
            },
            label = stringResource(Res.string.auth_reset_confirm_label),
            error = confirmError,
            enabled = !isSubmitting,
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            modifier = Modifier.fillMaxWidth().formField(focus, 2) { submit() }
        )

        SubmitButton(
            text = stringResource(Res.string.auth_reset_action),
            loading = isSubmitting,
            onClick = { submit() }
        )

        TextButton(onClick = onBackToLogin, enabled = !isSubmitting) {
            Text(stringResource(Res.string.auth_back_to_login))
        }
    }
}
