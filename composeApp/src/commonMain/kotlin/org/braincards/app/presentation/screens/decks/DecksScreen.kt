package org.braincards.app.presentation.screens.decks

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.decks_back
import braincards.composeapp.generated.resources.decks_delete_failed
import braincards.composeapp.generated.resources.decks_empty_cta
import braincards.composeapp.generated.resources.decks_empty_text
import braincards.composeapp.generated.resources.decks_empty_title
import braincards.composeapp.generated.resources.decks_load_error
import braincards.composeapp.generated.resources.decks_loading
import braincards.composeapp.generated.resources.decks_new
import braincards.composeapp.generated.resources.decks_no_results
import braincards.composeapp.generated.resources.decks_no_results_hint
import braincards.composeapp.generated.resources.decks_save_failed
import braincards.composeapp.generated.resources.decks_search_clear
import braincards.composeapp.generated.resources.decks_search_placeholder
import braincards.composeapp.generated.resources.decks_title
import org.braincards.app.platform.rememberReducedMotion
import org.braincards.app.presentation.components.form.FormBanner
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

/** Ancho maximo del contenido en pantallas anchas (Desktop/tablet), como el mockup (780 dp). */
private val DECKS_MAX_WIDTH = 780.dp

@OptIn(KoinExperimentalAPI::class)
@Composable
fun DecksScreen(onDeckClick: (String) -> Unit, onBackClick: () -> Unit) {
    val viewModel = koinViewModel<DecksViewModel>()
    val state by viewModel.state.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val nameError by viewModel.nameError.collectAsState()
    val saving by viewModel.saving.collectAsState()

    // Dialogos: sobreviven a la rotacion (solo guardan el id del mazo, nunca el objeto).
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(Res.string.decks_save_failed)
    val deleteFailed = stringResource(Res.string.decks_delete_failed)
    LaunchedEffect(viewModel, saveFailed, deleteFailed) {
        viewModel.messageEvent.collect { message ->
            snackbarHostState.showSnackbar(
                when (message) {
                    DeckMessage.SaveFailed -> saveFailed
                    DeckMessage.DeleteFailed -> deleteFailed
                }
            )
        }
    }

    fun openCreate() {
        viewModel.clearNameError()
        showCreate = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = DECKS_MAX_WIDTH).fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                DecksHeader(onBackClick = onBackClick, onNewClick = ::openCreate)

                val rows = (state as? DecksUiState.Content)?.rows.orEmpty()
                // El buscador solo tiene sentido si hay mazos (con 0 mazos, cargando o con error no se dibuja).
                if (state is DecksUiState.Content || state is DecksUiState.NoResults) {
                    DecksSearchField(query = query, onQueryChange = viewModel::onSearchQueryChange)
                }

                when (val current = state) {
                    DecksUiState.Loading -> DecksSkeleton()
                    DecksUiState.Error -> FormBanner(
                        message = stringResource(Res.string.decks_load_error),
                        onRetry = viewModel::retry
                    )
                    DecksUiState.Empty -> DecksEmpty(onCreateClick = ::openCreate)
                    is DecksUiState.NoResults -> DecksNoResults(query = current.query)
                    is DecksUiState.Content -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(rows, key = { it.deck.id }) { summary ->
                            DeckRow(
                                summary = summary,
                                onClick = { onDeckClick(summary.deck.id) },
                                onEdit = {
                                    viewModel.clearNameError()
                                    editId = summary.deck.id
                                },
                                onDelete = { deleteId = summary.deck.id }
                            )
                        }
                    }
                }
            }
        }

        // Los dialogos se resuelven contra la lista actual: si el mazo desaparece (p. ej. por sincronizacion), se cierran.
        val all = (state as? DecksUiState.Content)?.rows.orEmpty()

        if (showCreate) {
            CreateDeckDialog(
                error = nameError,
                saving = saving,
                onNameChange = viewModel::clearNameError,
                onConfirm = { name -> viewModel.createDeck(name) { showCreate = false } },
                onDismiss = {
                    viewModel.clearNameError()
                    showCreate = false
                }
            )
        }

        all.firstOrNull { it.deck.id == editId }?.let { summary ->
            EditDeckDialog(
                currentName = summary.deck.name,
                error = nameError,
                saving = saving,
                onNameChange = viewModel::clearNameError,
                onConfirm = { name -> viewModel.updateDeck(summary.deck, name) { editId = null } },
                onDismiss = {
                    viewModel.clearNameError()
                    editId = null
                }
            )
        }

        all.firstOrNull { it.deck.id == deleteId }?.let { summary ->
            DeleteDeckDialog(
                deckName = summary.deck.name,
                cardCount = summary.cardCount,
                saving = saving,
                onConfirm = { viewModel.deleteDeck(summary.deck) { deleteId = null } },
                onDismiss = { deleteId = null }
            )
        }
    }
}

@Composable
private fun DecksHeader(onBackClick: () -> Unit, onNewClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.decks_back))
        }
        Text(
            text = stringResource(Res.string.decks_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Button(onClick = onNewClick) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(Res.string.decks_new))
        }
    }
}

@Composable
private fun DecksSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.decks_search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.decks_search_clear))
                }
            }
        } else null
    )
}

/** Tres esqueletos de 64 dp (radio 16). La pulsacion se queda quieta si el sistema pide reducir el movimiento. */
@Composable
private fun DecksSkeleton() {
    val reduced = rememberReducedMotion()
    val alpha = if (reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition()
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
        ).value
    }
    val loading = stringResource(Res.string.decks_loading)
    Column(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = loading
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
private fun DecksEmpty(onCreateClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.decks_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.decks_empty_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onCreateClick) { Text(stringResource(Res.string.decks_empty_cta)) }
        }
    }
}

@Composable
private fun DecksNoResults(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            text = stringResource(Res.string.decks_no_results, query),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = stringResource(Res.string.decks_no_results_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
