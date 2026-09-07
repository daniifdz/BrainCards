package org.braincards.app.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.braincards.app.domain.models.AuthState
import org.braincards.app.platform.openEmailClient
import org.braincards.app.presentation.screens.auth.AuthViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

// Enum para representar las opciones de tema de forma clara y segura.
enum class AppTheme(val displayName: String) {
    SYSTEM("Por defecto del sistema"),
    LIGHT("Claro"),
    DARK("Oscuro")
}

@OptIn(ExperimentalMaterial3Api::class, KoinExperimentalAPI::class)
@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToAuth: () -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val authViewModel = koinViewModel<AuthViewModel>()
    val authState by authViewModel.authState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            Text(
                text = "Apariencia",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded,
                onExpandedChange = { isDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = currentTheme.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tema de la aplicación") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false }
                ) {
                    AppTheme.entries.forEach { themeOption ->
                        DropdownMenuItem(
                            text = { Text(themeOption.displayName) },
                            onClick = {
                                onThemeChange(themeOption)
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Cuenta",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when (val state = authState) {
                is AuthState.Authenticated -> {
                    ListItem(
                        headlineContent = { Text(state.email ?: "Sesión iniciada") },
                        supportingContent = { Text("Sincronización activada") },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                    ListItem(
                        headlineContent = { Text("Cerrar sesión") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        modifier = Modifier.clickable { authViewModel.signOut() }
                    )
                }
                else -> {
                    ListItem(
                        headlineContent = { Text("Sincronizar entre dispositivos") },
                        supportingContent = { Text("Inicia sesión para guardar tus mazos en la nube") },
                        leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) },
                        modifier = Modifier.clickable { onNavigateToAuth() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Acerca de",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ListItem(
                headlineContent = { Text("Versión de BrainCards") },
                supportingContent = { Text("v1.0.0") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )

            ListItem(
                headlineContent = { Text("Desarrollado por") },
                supportingContent = { Text("Daniel Fernández-Muñiz Arribas") },
                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
            )

            ListItem(
                headlineContent = { Text("Enviar comentarios") },
                supportingContent = { Text("Ayúdanos a mejorar") },
                leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.clickable {
                    openEmailClient(
                        recipient = "braincards.soporte@proton.me",
                        subject = "Feedback para BrainCards v1.0.0",
                        body = "Hola, tengo una sugerencia/problema:\n\n"
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Copyright") },
                supportingContent = { Text("© 2024 BrainCards. Todos los derechos reservados.") },
                leadingContent = { Icon(Icons.Default.Code, contentDescription = null) }
            )
        }
    }
}