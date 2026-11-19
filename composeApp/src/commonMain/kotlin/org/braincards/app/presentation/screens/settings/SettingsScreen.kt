package org.braincards.app.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import braincards.composeapp.generated.resources.*
import org.braincards.app.domain.models.AuthState
import org.braincards.app.platform.openEmailClient
import org.braincards.app.APP_VERSION
import org.braincards.app.presentation.components.BrandBadge
import org.braincards.app.presentation.screens.auth.AuthViewModel
import org.braincards.app.presentation.screens.auth.DeleteAccountEvent
import org.braincards.app.presentation.screens.auth.SignOutEvent
import org.braincards.app.presentation.screens.auth.SignOutPhase
import org.braincards.app.presentation.screens.auth.SyncUserMessage
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.braincards.app.presentation.sync.ConnectivityMonitor
import org.braincards.app.presentation.sync.SyncStatusViewModel
import org.braincards.app.presentation.sync.syncDetail
import org.koin.core.annotation.KoinExperimentalAPI

// Enum para representar las opciones de tema de forma clara y segura.
enum class AppTheme(val displayName: String) {
    SYSTEM("Por defecto del sistema"),
    LIGHT("Claro"),
    DARK("Oscuro")
}

/** Medidas de los badges del logo en Ajustes (mockups `ajustes*.html`): junto al titulo, su hueco y el bloque «Acerca de». */
const val SETTINGS_TITLE_BADGE_DP = 30
const val SETTINGS_TITLE_BADGE_GAP_DP = 12
const val SETTINGS_ABOUT_BADGE_DP = 40

/** Ancho maximo del contenido en pantallas anchas (mockup `ajustes.html`: 640 px). */
private val SETTINGS_MAX_WIDTH = 640.dp

@OptIn(ExperimentalMaterial3Api::class, KoinExperimentalAPI::class)
@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val authViewModel = koinViewModel<AuthViewModel>()
    val syncStatusViewModel = koinViewModel<SyncStatusViewModel>()
    val indicator by syncStatusViewModel.indicator.collectAsState()
    val isOnline by koinInject<ConnectivityMonitor>().isOnline.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val isSyncing by authViewModel.isSyncing.collectAsState()
    val pendingChangesCount by authViewModel.pendingChangesCount.collectAsState()
    val isDeletingAccount by authViewModel.isDeletingAccount.collectAsState()
    val signOutPrompt by authViewModel.signOutPrompt.collectAsState()
    val signOutPhase by authViewModel.signOutPhase.collectAsState()
    val isSigningOut = signOutPhase != SignOutPhase.Idle
    val autoSyncViewModel = koinViewModel<AutoSyncSettingViewModel>()
    val autoSyncEnabled by autoSyncViewModel.enabled.collectAsState()
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        authViewModel.syncMessageEvent.collectLatest { message ->
            val text = when (message) {
                SyncUserMessage.NotAuthenticated -> getString(Res.string.sync_not_authenticated)
                SyncUserMessage.Synced -> getString(Res.string.sync_done)
                SyncUserMessage.ServerOutdated -> getString(Res.string.sync_server_outdated)
                SyncUserMessage.SyncFailed -> getString(Res.string.sync_failed)
                SyncUserMessage.PullNetwork -> getString(Res.string.sync_pull_network)
                SyncUserMessage.PullRejected -> getString(Res.string.sync_pull_rejected)
                SyncUserMessage.PullLocal -> getString(Res.string.sync_pull_local)
                is SyncUserMessage.PushPartial ->
                    getPluralString(Res.plurals.sync_push_partial, message.pendingRows, message.pendingRows)
            }
            coroutineScope.launch { snackbarHostState.showSnackbar(text) }
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.deleteAccountEvent.collectLatest { event ->
            val text = when (event) {
                DeleteAccountEvent.Failed -> getString(Res.string.delete_account_failed)
                DeleteAccountEvent.SessionNotClosed -> getString(Res.string.delete_account_session_not_closed)
            }
            coroutineScope.launch { snackbarHostState.showSnackbar(text) }
        }
    }

    LaunchedEffect(Unit) {
        autoSyncViewModel.events.collectLatest { event ->
            val text = when (event) {
                AutoSyncSettingEvent.SaveFailed -> getString(Res.string.settings_auto_sync_save_failed)
            }
            coroutineScope.launch { snackbarHostState.showSnackbar(text) }
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.signOutEvent.collectLatest { event ->
            val message = when (event) {
                // El aviso "Sesion cerrada." lo muestra la Bienvenida (la navegacion ocurria casi a la vez y este
                // Snackbar no llegaba a verse). Aqui solo quedan los casos en los que SE SIGUE en Ajustes.
                SignOutEvent.SignedOut -> return@collectLatest
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
                title = {
                    // Badge del logo junto al titulo (mockups ajustes*.html: 30 px, hueco de 12 px; igual en normal, movil e invitado).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandBadge(size = SETTINGS_TITLE_BADGE_DP.dp)
                        Spacer(Modifier.width(SETTINGS_TITLE_BADGE_GAP_DP.dp))
                        Text("Ajustes")
                    }
                },
                navigationIcon = {
                    // Desactivada a mitad del cierre de sesion o del borrado de cuenta: no se sale de Ajustes.
                    IconButton(onClick = onBackClick, enabled = !isSigningOut && !isDeletingAccount) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // Columna centrada de ancho maximo ~640 dp en pantallas anchas (mockup ajustes.html). El scroll es de la zona
        // completa (la rueda del raton funciona tambien fuera de la columna); en Compact (< 640 dp) no cambia nada.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = SETTINGS_MAX_WIDTH).fillMaxWidth().padding(16.dp)) {

                Text(
                    text = stringResource(Res.string.theme_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = stringResource(Res.string.theme_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                ThemeCards(current = currentTheme, onThemeChange = onThemeChange)


                when (val state = authState) {
                    is AuthState.Authenticated -> {
                        SectionDivider()
                        Text(
                            text = "Cuenta",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        ListItem(
                            headlineContent = { Text(state.email ?: "Sesión iniciada") },
                            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        ListItem(
                            headlineContent = { Text("Cerrar sesión") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                            modifier = Modifier.clickable(enabled = !isSigningOut && !isDeletingAccount) {
                                authViewModel.requestSignOut()
                            }
                        )
                        ListItem(
                            headlineContent = {
                                Text("Eliminar cuenta", color = MaterialTheme.colorScheme.error)
                            },
                            supportingContent = { Text(stringResource(Res.string.settings_delete_account_subtitle)) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            modifier = Modifier.clickable(enabled = !isSigningOut && !isDeletingAccount) {
                                showDeleteAccountDialog = true
                            }
                        )

                        val syncDetail = syncDetail(indicator, pendingChangesCount)
                        if (syncDetail != null) {
                            SectionDivider()
                            SyncSection(
                                detail = syncDetail,
                                canSyncNow = isOnline && !isSyncing,
                                onSyncNow = { authViewModel.syncNow() },
                                autoSyncEnabled = autoSyncEnabled,
                                onAutoSyncChange = { autoSyncViewModel.setEnabled(it) }
                            )
                        }
                    }
                    // Invitado: sin Cuenta ni Sincronizacion, solo el aviso con el boton hacia Bienvenida.
                    AuthState.NotAuthenticated -> {
                        SectionDivider()
                        GuestNotice(onSignInClick = onNavigateToAuth)
                    }
                    // Aun sin estado de sesion: no se dibuja nada (evita parpadeos entre Cuenta y aviso).
                    AuthState.Loading -> Unit
                }

                SectionDivider()

                Text(
                    text = "Acerca de",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Bloque de identidad (mockup ajustes.html): badge 40 + nombre + lema, encima de la version.
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.app_name)) },
                    supportingContent = { Text(stringResource(Res.string.home_tagline)) },
                    leadingContent = { BrandBadge(size = SETTINGS_ABOUT_BADGE_DP.dp) }
                )

                ListItem(
                    headlineContent = { Text("Versión de BrainCards") },
                    supportingContent = { Text(APP_VERSION) },
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
                            subject = "Feedback para BrainCards v$APP_VERSION",
                            body = "Hola, tengo una sugerencia/problema:\n\n"
                        )
                    }
                )

                ListItem(
                    headlineContent = { Text("Copyright") },
                    supportingContent = { Text("© 2026 BrainCards. Todos los derechos reservados.") },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) }
                )
            }
        }
    }

    // Cierre de sesion: la comprobacion previa y el cierre propiamente dicho son modales NO descartables; el dialogo de
    // perdida de datos solo se ve mientras se espera la decision (fase Idle con prompt).
    when (signOutPhase) {
        SignOutPhase.Checking -> CheckingPendingDialog()
        SignOutPhase.SigningOut -> SigningOutDialog()
        SignOutPhase.Idle -> signOutPrompt?.let { prompt ->
            SignOutLossDialog(
                unsyncedChanges = prompt.unsyncedChanges,
                onConfirm = { authViewModel.confirmSignOut() },
                onDismiss = { authViewModel.dismissSignOut() }
            )
        }
    }

    if (isDeletingAccount) {
        DeletingAccountDialog()
    }

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            onConfirm = {
                showDeleteAccountDialog = false
                authViewModel.deleteAccount()
            },
            onDismiss = { showDeleteAccountDialog = false }
        )
    }
}

@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
}
