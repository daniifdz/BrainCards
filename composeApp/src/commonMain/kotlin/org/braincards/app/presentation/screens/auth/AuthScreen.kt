package org.braincards.app.presentation.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.*
import kotlinx.coroutines.flow.collectLatest
import org.braincards.app.domain.models.AuthState
import org.braincards.app.presentation.components.form.FormBanner
import org.braincards.app.presentation.components.form.FormScaffold
import org.braincards.app.presentation.components.form.FormTextField
import org.braincards.app.presentation.components.form.SubmitButton
import org.braincards.app.presentation.components.form.formField
import org.braincards.app.presentation.components.form.rememberFormFocus
import org.braincards.app.presentation.screens.home.LogoMark
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

/** Orden de foco de los campos de texto de Bienvenida (v2 §4): correo (0) -> contraseña (1). */
private val FIELD_ORDER = listOf(AuthField.EMAIL, AuthField.PASSWORD)

/**
 * Bienvenida: inicio de sesion y registro en una sola pantalla. [asEntry] = es la ENTRADA de la app (sin flecha de
 * vuelta); si no, esta apilada desde Home o Ajustes (con flecha). Estado: correo y modo con rememberSaveable (sobreviven a rotar); la CONTRASENA solo con remember (no
 * se guarda en el estado de la instancia). Errores: de campo (cliente: correo / contraseña; servidor: contraseña
 * debil) bajo el input, y banner para el resto.
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun AuthScreen(
    asEntry: Boolean = false,
    onBackClick: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    onAuthenticated: () -> Unit,
    onForgotPasswordClick: (email: String) -> Unit,
    onSignUpConfirmationRequired: (email: String) -> Unit,
    onEmailNotConfirmed: (email: String) -> Unit
) {
    val viewModel = koinViewModel<AuthViewModel>()
    val welcomeViewModel = koinViewModel<WelcomeViewModel>()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val uiError by viewModel.uiError.collectAsState()
    val hasLocalData by welcomeViewModel.hasLocalData.collectAsState()

    var isRegisterMode by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // NO rememberSaveable
    var clientErrors by remember { mutableStateOf(FieldErrors.None) }
    val focus = rememberFormFocus(FIELD_ORDER.size)

    fun submit() {
        if (isSubmitting) return
        val errors = if (isRegisterMode) validateRegisterForm(email, password) else validateLoginForm(email, password)
        clientErrors = errors
        if (!errors.isEmpty) {
            errors.firstIn(FIELD_ORDER)?.let { focus.requestFocus(FIELD_ORDER.indexOf(it)) }
            return
        }
        viewModel.clearError()
        val cleanEmail = email.trim()
        if (isRegisterMode) viewModel.signUp(cleanEmail, password) else viewModel.signIn(cleanEmail, password)
    }

    LaunchedEffect(Unit) {
        viewModel.signUpConfirmationRequiredEvent.collectLatest { onSignUpConfirmationRequired(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.emailNotConfirmedEvent.collectLatest { onEmailNotConfirmed(it) }
    }

    // Esta pantalla solo se ve sin sesion: cualquier Authenticated (iniciar sesion, registro con sesion inmediata, o
    // una sesion ya abierta al volver de un flujo abandonado, p. ej. tras verificar el codigo de restablecer) la cierra.
    // Sin saltarse la primera emision: la StateFlow del ViewModel puede conservar ya el Authenticated.
    LaunchedEffect(Unit) {
        var exited = false
        viewModel.authState.collect { state ->
            if (state is AuthState.Authenticated && !exited) {
                exited = true
                onAuthenticated()
            }
        }
    }

    val bannerError = uiError as? AuthFormTarget.Banner
    val serverFieldError = uiError as? AuthFormTarget.Field
    val emailError = clientErrors[AuthField.EMAIL]?.text()
    val passwordError = (clientErrors[AuthField.PASSWORD]
        ?: serverFieldError?.takeIf { it.field == AuthField.PASSWORD }?.message)?.text()

    FormScaffold(
        topBar = { if (!asEntry) AuthTopBar(onBack = onBackClick, enabled = !isSubmitting) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(Res.string.app_name),
                style = BrainCardsTheme.wordmark.copy(fontSize = 28.sp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(if (isRegisterMode) Res.string.auth_register_title else Res.string.auth_login_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp)
            )
            Text(
                text = stringResource(
                    if (isRegisterMode) Res.string.auth_register_subtitle else Res.string.auth_login_subtitle
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Solo si ya hay mazos locales (WelcomeViewModel): se anadiran a la cuenta al iniciar sesion.
        if (hasLocalData == true) {
            MergeNote(stringResource(Res.string.auth_merge_note))
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
                clientErrors = clientErrors.without(AuthField.EMAIL)
                viewModel.clearError()
            },
            label = stringResource(Res.string.auth_email_label),
            placeholder = stringResource(Res.string.auth_email_placeholder),
            error = emailError,
            enabled = !isSubmitting,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth().formField(focus, 0) { submit() }
        )

        FormTextField(
            value = password,
            onValueChange = {
                password = it
                clientErrors = clientErrors.without(AuthField.PASSWORD)
                viewModel.clearError()
            },
            label = stringResource(Res.string.auth_password_label),
            error = passwordError,
            // El minimo solo se exige (y se anuncia) al registrarse: el login acepta cuentas antiguas.
            hint = if (isRegisterMode) stringResource(Res.string.auth_password_hint) else null,
            enabled = !isSubmitting,
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            modifier = Modifier.fillMaxWidth().formField(focus, 1) { submit() }
        )

        SubmitButton(
            text = stringResource(if (isRegisterMode) Res.string.auth_register_action else Res.string.auth_login_action),
            loading = isSubmitting,
            onClick = { submit() }
        )

        if (!isRegisterMode) {
            TextButton(onClick = { onForgotPasswordClick(email.trim()) }, enabled = !isSubmitting) {
                Text(stringResource(Res.string.auth_forgot_password))
            }
        }

        TextButton(
            onClick = {
                isRegisterMode = !isRegisterMode
                clientErrors = FieldErrors.None
                viewModel.clearError()
            },
            enabled = !isSubmitting
        ) {
            Text(
                stringResource(
                    if (isRegisterMode) Res.string.auth_switch_to_login else Res.string.auth_switch_to_register
                )
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(Res.string.auth_or),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }

        // Aun no hay cuenta: se vuelve a donde se estaba (App.kt), sin tocar nada. Sin callejon sin salida.
        OutlinedButton(
            onClick = onContinueWithoutAccount,
            enabled = !isSubmitting,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(Res.string.auth_continue_without_account),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun MergeNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrainCardsTheme.extraColors.brandChip, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
