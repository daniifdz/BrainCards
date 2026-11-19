package org.braincards.app.presentation.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.*
import kotlinx.coroutines.flow.collectLatest
import org.braincards.app.presentation.components.form.CodeInput
import org.braincards.app.presentation.components.form.FormBanner
import org.braincards.app.presentation.components.form.FormCaption
import org.braincards.app.presentation.components.form.FormScaffold
import org.braincards.app.presentation.components.form.SubmitButton
import org.braincards.app.presentation.components.form.formField
import org.braincards.app.presentation.components.form.rememberFormFocus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

/**
 * Pantalla del código de confirmación de registro: 6 casillas, texto neutro (nunca confirma que la cuenta exista: «Si X es una
 * cuenta válida…», igual vengas de registrarte o de un login sin confirmar), cooldown de reenvío «(0:38)», error de campo
 * «Escribe los 6 dígitos» y del servidor «El código no es correcto o ha caducado.» con «Pedir código nuevo». Abrir la pantalla
 * nunca envía un código: solo lo hace una acción del usuario. El código se guarda con rememberSaveable; no hay contraseñas
 * aquí.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun ConfirmEmailScreen(
    email: String,
    codeJustSent: Boolean,
    onConfirmed: () -> Unit,
    onBackToLogin: () -> Unit
) {
    val viewModel = koinViewModel<AuthViewModel>()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val resendCooldownSeconds by viewModel.resendCooldownSeconds.collectAsState()
    val uiError by viewModel.uiError.collectAsState()

    var code by rememberSaveable { mutableStateOf("") }
    var clientError by remember { mutableStateOf<AuthMessage?>(null) }
    var lastActionWasResend by remember { mutableStateOf(false) }
    val focus = rememberFormFocus(1)
    val snackbarHostState = remember { SnackbarHostState() }
    val resentText = AuthNotice.CodeResent.text()

    fun submit() {
        if (isSubmitting) return
        lastActionWasResend = false
        clientError = validateCode(code)
        if (clientError != null) return
        viewModel.clearError()
        viewModel.confirmSignUp(email, code)
    }

    fun resend() {
        lastActionWasResend = true
        clientError = null
        viewModel.resendSignUpCode(email)
    }

    // Solo ARRANCA el cooldown del boton (el correo acaba de enviarse al registrarse); no envia nada.
    LaunchedEffect(Unit) {
        if (codeJustSent) viewModel.startInitialResendCooldown()
    }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus(0) }
    }
    LaunchedEffect(Unit) {
        viewModel.noticeEvent.collectLatest { snackbarHostState.showSnackbar(resentText) }
    }
    LaunchedEffect(Unit) {
        viewModel.signUpConfirmedEvent.collectLatest { onConfirmed() }
    }

    val bannerError = uiError as? AuthFormTarget.Banner
    val serverCodeError = (uiError as? AuthFormTarget.Field)?.takeIf { it.field == AuthField.CODE }?.message
    val codeError = (clientError ?: serverCodeError)?.text()

    val resendLabel = stringResource(
        if (serverCodeError != null) Res.string.auth_request_new_code else Res.string.auth_resend_code
    )

    FormScaffold(
        topBar = { AuthTopBar(onBack = onBackToLogin, enabled = !isSubmitting) },
        snackbarHostState = snackbarHostState
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.auth_confirm_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp)
            )
            Text(
                text = boldEmail(stringResource(Res.string.auth_confirm_neutral_text, email), email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (bannerError != null) {
            FormBanner(
                message = bannerError.message.text(),
                onRetry = if (bannerError.retryable) ({ if (lastActionWasResend) resend() else submit() }) else null
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CodeInput(
                value = code,
                onValueChange = {
                    code = it
                    clientError = null
                    viewModel.clearError()
                },
                isError = codeError != null,
                enabled = !isSubmitting,
                imeAction = ImeAction.Done,
                onSubmit = { submit() },
                modifier = Modifier.fillMaxWidth().formField(focus, 0) { submit() }
            )
            FormCaption(error = codeError)
        }

        SubmitButton(
            text = stringResource(Res.string.auth_confirm_action),
            loading = isSubmitting,
            onClick = { submit() }
        )

        TextButton(
            onClick = { resend() },
            enabled = !isSubmitting && resendCooldownSeconds == 0
        ) {
            Text(
                if (resendCooldownSeconds > 0) {
                    stringResource(Res.string.auth_resend_with_cooldown, resendLabel, formatCooldown(resendCooldownSeconds))
                } else {
                    resendLabel
                }
            )
        }

        TextButton(onClick = onBackToLogin, enabled = !isSubmitting) {
            Text(stringResource(Res.string.auth_back_to_login))
        }
    }
}

/** El correo en negrita dentro del texto ya formateado (si no aparece tal cual, el texto sin estilo). */
private fun boldEmail(text: String, email: String): AnnotatedString = buildAnnotatedString {
    val start = text.indexOf(email)
    if (start < 0) {
        append(text)
    } else {
        append(text.substring(0, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(email) }
        append(text.substring(start + email.length))
    }
}
