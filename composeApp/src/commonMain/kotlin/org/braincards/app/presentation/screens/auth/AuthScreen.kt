package org.braincards.app.presentation.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.braincards.app.domain.models.AuthState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(ExperimentalMaterial3Api::class, KoinExperimentalAPI::class)
@Composable
fun AuthScreen(
    onBackClick: () -> Unit,
    onAuthenticated: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    val viewModel = koinViewModel<AuthViewModel>()
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collectLatest { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    // No usamos authState como key (eso compara valores, no detecta transiciones):
    // si la pantalla se compusiera ya autenticada (o el registro diera sesion
    // inmediata sin confirmar email), navegaria sola en el primer render. En vez de
    // eso, hacemos collect directo y solo reaccionamos a un cambio real de
    // no-autenticado a autenticado ocurrido DESPUES de entrar a esta pantalla.
    LaunchedEffect(Unit) {
        var isFirstEmission = true
        viewModel.authState.collect { state ->
            if (isFirstEmission) {
                isFirstEmission = false
            } else if (state is AuthState.Authenticated) {
                onAuthenticated()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isRegisterMode) "Crear cuenta" else "Iniciar sesión") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isRegisterMode) {
                        viewModel.signUp(email, password)
                    } else {
                        viewModel.signIn(email, password)
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRegisterMode) "Crear cuenta" else "Iniciar sesión")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { isRegisterMode = !isRegisterMode }) {
                Text(
                    if (isRegisterMode) "¿Ya tienes cuenta? Inicia sesión"
                    else "¿No tienes cuenta? Regístrate"
                )
            }

            if (!isRegisterMode) {
                TextButton(onClick = onForgotPasswordClick) {
                    Text("¿Olvidaste tu contraseña?")
                }
            }
        }
    }
}
