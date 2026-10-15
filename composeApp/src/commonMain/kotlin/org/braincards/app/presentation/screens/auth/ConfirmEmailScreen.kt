package org.braincards.app.presentation.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

/**
 * Pantalla del codigo de confirmacion de registro. Mismo patron que
 * ResetPasswordScreen (eventos del ViewModel, snackbar, filtro de digitos), sin el
 * campo de contraseña: aqui solo hay un paso.
 */
@OptIn(ExperimentalMaterial3Api::class, KoinExperimentalAPI::class)
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

    var code by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (codeJustSent) viewModel.startInitialResendCooldown()
    }

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collectLatest { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.signUpConfirmedEvent.collectLatest { onConfirmed() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Confirma tu cuenta") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Mismo texto exista o no la cuenta: no se revela si el email ya estaba
            // registrado (el servidor tampoco lo hace).
            Text(
                text = if (codeJustSent) {
                    "Si el email $email es válido, te hemos enviado un código. Escríbelo aquí para confirmar tu cuenta."
                } else {
                    "Tu cuenta aún no está confirmada. Escribe el código que te enviamos a $email, o pide uno nuevo."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = code,
                onValueChange = { input -> code = input.filter { it.isDigit() } },
                label = { Text("Código") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.confirmSignUp(email, code) },
                enabled = !isSubmitting && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirmar")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { viewModel.resendSignUpCode(email) },
                enabled = !isSubmitting && resendCooldownSeconds == 0
            ) {
                Text(
                    if (resendCooldownSeconds > 0) "Reenviar código (${resendCooldownSeconds} s)"
                    else "Reenviar código"
                )
            }

            if (codeJustSent) {
                Text(
                    text = "¿Ya tienes cuenta?",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            TextButton(onClick = onBackToLogin) {
                Text("Volver a iniciar sesión")
            }
        }
    }
}
