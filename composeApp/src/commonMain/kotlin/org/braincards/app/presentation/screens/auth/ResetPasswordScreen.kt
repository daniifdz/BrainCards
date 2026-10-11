package org.braincards.app.presentation.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(ExperimentalMaterial3Api::class, KoinExperimentalAPI::class)
@Composable
fun ResetPasswordScreen(email: String, onPasswordResetCompleted: () -> Unit) {
    val viewModel = koinViewModel<AuthViewModel>()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val codeVerified by viewModel.codeVerified.collectAsState()

    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collectLatest { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.passwordResetCompletedEvent.collectLatest { onPasswordResetCompleted() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nueva contraseña") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Hemos enviado un código a $email. Escríbelo aquí junto con tu contraseña nueva.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (codeVerified) {
                Text(
                    text = "Código verificado. Elige una contraseña nueva.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            OutlinedTextField(
                value = code,
                onValueChange = { input -> code = input.filter { it.isDigit() } },
                label = { Text("Código") },
                singleLine = true,
                enabled = !codeVerified,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Contraseña nueva") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.confirmPasswordReset(email, code, newPassword) },
                enabled = !isSubmitting && code.isNotBlank() && newPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirmar")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { viewModel.sendPasswordReset(email) },
                enabled = !isSubmitting
            ) {
                Text("Reenviar código")
            }
        }
    }
}
