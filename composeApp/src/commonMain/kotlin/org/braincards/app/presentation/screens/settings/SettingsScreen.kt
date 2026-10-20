package org.braincards.app.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import braincards.composeapp.generated.resources.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.braincards.app.domain.models.AuthState
import org.braincards.app.platform.openEmailClient
import org.braincards.app.presentation.screens.auth.AuthViewModel
import org.braincards.app.presentation.screens.auth.SignOutEvent
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

// dd/MM HH:mm en hora local -- sin libreria nueva, solo kotlinx-datetime (ya en uso).
private fun Instant.toShortLocalString(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    val day = local.dayOfMonth.toString().padStart(2, '0')
    val month = local.monthNumber.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$day/$month $hour:$minute"
}

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
    val isSyncing by authViewModel.isSyncing.collectAsState()
    val lastSuccessfulSyncAt by authViewModel.lastSuccessfulSyncAt.collectAsState()
    val pendingChangesCount by authViewModel.pendingChangesCount.collectAsState()
    val isDeletingAccount by authViewModel.isDeletingAccount.collectAsState()
    val signOutPrompt by authViewModel.signOutPrompt.collectAsState()
    val isSigningOut by authViewModel.isSigningOut.collectAsState()
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        authViewModel.messageEvent.collectLatest { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.signOutEvent.collectLatest { event ->
            val message = when (event) {
                SignOutEvent.SignedOut -> getString(Res.string.sign_out_signed_out)
                SignOutEvent.SessionCloseFailed -> getString(Res.string.sign_out_session_close_failed)
                SignOutEvent.WipeFailed -> getString(Res.string.sign_out_wipe_failed)
                SignOutEvent.SessionChanged -> getString(Res.string.sign_out_session_changed)
            }
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    // Desactivada a mitad del cierre de sesion: no se sale de Ajustes mientras se borra.
                    IconButton(onClick = onBackClick, enabled = !isSigningOut) {
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
                        supportingContent = {
                            Text(if (state.sessionConfirmed) "Sincronización activada" else "Sin conexión")
                        },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                    ListItem(
                        headlineContent = { Text("Sincronizar ahora") },
                        supportingContent = { Text(if (isSyncing) "Sincronizando…" else "Sube y baja tus mazos manualmente") },
                        leadingContent = {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable(enabled = !isSyncing) { authViewModel.syncNow() }
                    )
                    ListItem(
                        headlineContent = { Text("Estado de sincronización") },
                        supportingContent = {
                            val lastSyncText = lastSuccessfulSyncAt?.let { "Última sincronización correcta: ${it.toShortLocalString()}" }
                                ?: "Sin sincronizar todavía"
                            Text("$lastSyncText · $pendingChangesCount cambios pendientes")
                        },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                    ListItem(
                        headlineContent = { Text("Cerrar sesión") },
                        supportingContent = if (isSigningOut) {
                            { Text(stringResource(Res.string.sign_out_progress)) }
                        } else {
                            null
                        },
                        leadingContent = {
                            if (isSigningOut) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable(enabled = !isSigningOut) { authViewModel.requestSignOut() }
                    )
                    ListItem(
                        headlineContent = {
                            Text("Eliminar cuenta", color = MaterialTheme.colorScheme.error)
                        },
                        supportingContent = {
                            Text(
                                if (isDeletingAccount) "Eliminando…" else "Borra tu cuenta y tus datos en la nube"
                            )
                        },
                        leadingContent = {
                            if (isDeletingAccount) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier.clickable(enabled = !isDeletingAccount) {
                            showDeleteAccountDialog = true
                        }
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

    // Confirmacion de cierre de sesion: solo si hay cambios sin sincronizar que se perderian.
    // Con isSigningOut (borrado en curso) el dialogo no se puede cerrar ni volver a pulsar.
    signOutPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { if (!isSigningOut) authViewModel.dismissSignOut() },
            properties = DialogProperties(
                dismissOnBackPress = !isSigningOut,
                dismissOnClickOutside = !isSigningOut
            ),
            title = { Text(stringResource(Res.string.sign_out_dialog_title)) },
            text = {
                Column {
                    Text(
                        pluralStringResource(
                            Res.plurals.sign_out_dialog_text,
                            prompt.unsyncedChanges,
                            prompt.unsyncedChanges
                        )
                    )
                    if (isSigningOut) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(Res.string.sign_out_progress))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { authViewModel.confirmSignOut() }, enabled = !isSigningOut) {
                    Text(
                        stringResource(Res.string.sign_out_dialog_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { authViewModel.dismissSignOut() }, enabled = !isSigningOut) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    // Sin dialogo de confirmacion pero con cierre en curso (sync previo, o nada que perder):
    // progreso modal no descartable para que no se pueda lanzar nada a mitad.
    if (signOutPrompt == null && isSigningOut) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = MaterialTheme.shapes.medium) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(Res.string.sign_out_progress))
                }
            }
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Eliminar cuenta") },
            text = {
                Text(
                    "Esta acción es irreversible: se borrarán los datos de tu cuenta " +
                        "en la nube. Los datos locales de este dispositivo se conservarán."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccountDialog = false
                    authViewModel.deleteAccount()
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}