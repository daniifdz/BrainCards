package org.braincards.app.presentation.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import org.braincards.app.domain.models.AuthState
import org.braincards.app.presentation.components.form.FormBanner
import org.braincards.app.presentation.components.form.FormNote
import org.braincards.app.presentation.components.form.FormTextField
import org.braincards.app.presentation.components.form.SubmitButton
import org.braincards.app.presentation.components.form.WelcomeScaffold
import org.braincards.app.presentation.components.form.formField
import org.braincards.app.presentation.components.form.rememberFormFocus
import org.braincards.app.presentation.screens.navigation.WelcomeNotice
import org.braincards.app.presentation.components.BrandBadge
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

/** Orden de foco de los campos de texto de Bienvenida (v2 §4): correo (0) -> contraseña (1). */
private val FIELD_ORDER = listOf(AuthField.EMAIL, AuthField.PASSWORD)

/**
 * Bienvenida: inicio de sesión y registro en una sola pantalla. [asEntry] = es la entrada de la app (sin flecha de vuelta); si
 * no, está apilada desde Home o Ajustes (con flecha).
 *
 * Estado: correo y modo con rememberSaveable (sobreviven a rotar); la contraseña solo con remember (no se guarda en el estado
 * de la instancia). [notice] = aviso de salida de sesión (ver [WelcomeNotice]): Snackbar de un solo uso (SignedOut /
 * AccountDeleted, se pide con [onNoticeConsumed]) o nota persistente (SessionExpired) que se descarta con [onNoticeDismissed]
 * en cuanto el usuario interactúa con el formulario.
 *
 * Errores: de campo (cliente: correo y contraseña; servidor: contraseña débil) bajo el input, y banner para el resto.
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
    onEmailNotConfirmed: (email: String) -> Unit,
    notice: WelcomeNotice? = null,
    onNoticeConsumed: (WelcomeNotice) -> Boolean = { false },
    onNoticeDismissed: () -> Unit = {}
) {
    val viewModel = koinViewModel<AuthViewModel>()
    val welcomeViewModel = koinViewModel<WelcomeViewModel>()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val uiError by viewModel.uiError.collectAsState()
    val hasLocalData by welcomeViewModel.hasLocalData.collectAsState()

    var isRegisterMode by rememberSaveable { mutableStateOf(false) }
    // Sesion caducada: el correo llega precargado (solo el correo; la contrasena nunca).
    var email by rememberSaveable { mutableStateOf((notice as? WelcomeNotice.SessionExpired)?.email.orEmpty()) }
    // Sin rememberSaveable a propósito.
    var password by remember { mutableStateOf("") }
    var clientErrors by remember { mutableStateOf(FieldErrors.None) }
    val focus = rememberFormFocus(FIELD_ORDER.size)

    // Unico punto por el que se descarta el aviso persistente: solo lo hacen los eventos de isDismissingInteraction.
    fun interact(event: WelcomeInteraction) {
        if (isDismissingInteraction(event)) onNoticeDismissed()
    }

    fun submit(via: WelcomeInteraction = WelcomeInteraction.SubmitByButton) {
        if (isSubmitting) return
        interact(via)
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

    // Snackbar de un solo uso: se SACA del slot antes de mostrarlo (consumo atomico) y se lanza en un scope propio, no
    // en el efecto, que se cancela al cambiar `notice` a null. Los textos se resuelven ya (sin suspender entre medias).
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val signedOutText = stringResource(Res.string.welcome_notice_signed_out)
    val accountDeletedText = stringResource(Res.string.welcome_notice_account_deleted)
    LaunchedEffect(notice) {
        val current = notice ?: return@LaunchedEffect
        val text = when (current) {
            WelcomeNotice.SignedOut -> signedOutText
            WelcomeNotice.AccountDeleted -> accountDeletedText
            is WelcomeNotice.SessionExpired -> return@LaunchedEffect
        }
        if (onNoticeConsumed(current)) {
            snackbarScope.launch { snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short) }
        }
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
                interact(WelcomeInteraction.Authenticated)
                onAuthenticated()
            }
        }
    }

    val bannerError = uiError as? AuthFormTarget.Banner
    val serverFieldError = uiError as? AuthFormTarget.Field
    val emailError = clientErrors[AuthField.EMAIL]?.text()
    val passwordError = (clientErrors[AuthField.PASSWORD]
        ?: serverFieldError?.takeIf { it.field == AuthField.PASSWORD }?.message)?.text()

    WelcomeScaffold(
        topBar = { if (!asEntry) AuthTopBar(onBack = onBackClick, enabled = !isSubmitting) },
        snackbarHostState = snackbarHostState,
        // Tamaños según welcomeLogoSpec.
        hero = { logo ->
            BrandBadge(size = logo.iconDp.dp)
            Text(
                text = stringResource(Res.string.app_name),
                style = BrainCardsTheme.wordmark.copy(fontSize = logo.wordmarkSp.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false
            )
        }
    ) {
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

        // Sesion perdida sola: aviso persistente (no es un error ni un Snackbar), hasta que el usuario interactue.
        if (notice is WelcomeNotice.SessionExpired) {
            FormNote(stringResource(Res.string.welcome_notice_session_expired), announce = true)
        }

        // Solo si ya hay mazos locales (WelcomeViewModel): se anadiran a la cuenta al iniciar sesion.
        if (hasLocalData == true) {
            FormNote(stringResource(Res.string.auth_merge_note))
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
                interact(WelcomeInteraction.EmailEdited)
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
            modifier = Modifier.fillMaxWidth().formField(focus, 0) { submit(WelcomeInteraction.SubmitByIme) }
        )

        FormTextField(
            value = password,
            onValueChange = {
                interact(WelcomeInteraction.PasswordEdited)
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
            onImeAction = { submit(WelcomeInteraction.SubmitByIme) },
            modifier = Modifier.fillMaxWidth().formField(focus, 1) { submit(WelcomeInteraction.SubmitByIme) }
        )

        SubmitButton(
            text = stringResource(if (isRegisterMode) Res.string.auth_register_action else Res.string.auth_login_action),
            loading = isSubmitting,
            onClick = { submit() }
        )

        if (!isRegisterMode) {
            TextButton(
                onClick = {
                    interact(WelcomeInteraction.ForgotPassword)
                    onForgotPasswordClick(email.trim())
                },
                enabled = !isSubmitting
            ) {
                Text(stringResource(Res.string.auth_forgot_password))
            }
        }

        TextButton(
            onClick = {
                interact(WelcomeInteraction.ToggleMode)
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
            onClick = {
                interact(WelcomeInteraction.ContinueWithoutAccount)
                onContinueWithoutAccount()
            },
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
