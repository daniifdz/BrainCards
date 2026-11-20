package org.braincards.app.presentation.screens.flashcards

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import braincards.composeapp.generated.resources.deckcontent_add_card_short
import braincards.composeapp.generated.resources.sample_deck_created
import braincards.composeapp.generated.resources.deckcontent_back_to_deck
import braincards.composeapp.generated.resources.deckcontent_back_to_decks
import braincards.composeapp.generated.resources.deckcontent_delete_failed
import braincards.composeapp.generated.resources.deckcontent_empty_cta
import braincards.composeapp.generated.resources.deckcontent_empty_text
import braincards.composeapp.generated.resources.deckcontent_empty_title
import braincards.composeapp.generated.resources.deckcontent_inside
import braincards.composeapp.generated.resources.deckcontent_load_error
import braincards.composeapp.generated.resources.deckcontent_loading
import braincards.composeapp.generated.resources.deckcontent_new_card
import braincards.composeapp.generated.resources.free_review
import braincards.composeapp.generated.resources.deckcontent_new_topic
import braincards.composeapp.generated.resources.deckcontent_no_results
import braincards.composeapp.generated.resources.deckcontent_no_results_hint
import braincards.composeapp.generated.resources.deckcontent_save_card_failed
import braincards.composeapp.generated.resources.deckcontent_save_topic_failed
import braincards.composeapp.generated.resources.deckcontent_search_clear
import braincards.composeapp.generated.resources.deckcontent_search_placeholder
import braincards.composeapp.generated.resources.deckcontent_section_cards
import braincards.composeapp.generated.resources.deckcontent_section_loose
import braincards.composeapp.generated.resources.deckcontent_section_topics
import braincards.composeapp.generated.resources.deckcontent_study
import braincards.composeapp.generated.resources.deckcontent_study_button
import braincards.composeapp.generated.resources.deckcontent_topic_none
import braincards.composeapp.generated.resources.deckcontent_topic_empty_text
import braincards.composeapp.generated.resources.deckcontent_topic_empty_title
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.usecase.Topics.CardHit
import org.braincards.app.domain.usecase.Topics.TopicWithCount
import org.braincards.app.platform.SystemBackHandler
import org.braincards.app.platform.rememberReducedMotion
import org.braincards.app.presentation.components.form.FormBanner
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf

/** Ancho máximo del contenido en pantallas anchas (Desktop o tablet): 820 dp con padding. */
private val CONTENT_MAX_WIDTH = 820.dp

/** Por debajo de este ancho, los botones de la cabecera bajan a una segunda línea. Provisional: se afina a mano. */
private val COMPACT_WIDTH = 720.dp

/**
 * Contenido de un mazo. Nivel del mazo (temas + tarjetas sin tema + buscador) y subnivel de un tema. El tema abierto vive en el
 * [DeckContentViewModel], no en la ruta.
 *
 * [onBackClick] vuelve a Mis Mazos desde el nivel del mazo; [onDeckGone] se llama si el mazo desaparece (App.kt lo conecta a
 * popBackStackSafely, nunca a popBackStack).
 */
@OptIn(KoinExperimentalAPI::class)
@Composable
fun DeckContentScreen(
    deckId: String,
    /** Se llegó desde «Crear mazo de ejemplo»: se muestra una sola vez «Mazo de ejemplo creado». */
    sampleCreated: Boolean = false,
    onBackClick: () -> Unit,
    onDeckGone: () -> Unit,
    onStudyClick: (String) -> Unit,
    /** «Repaso libre» del mazo: App.kt abre la sesion libre con una semilla nueva. */
    onFreeReviewClick: (String) -> Unit
) {
    val viewModel = koinViewModel<DeckContentViewModel>(parameters = { parametersOf(deckId) })
    val state by viewModel.state.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val topicNameError by viewModel.topicNameError.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val topicOptions by viewModel.topicOptions.collectAsState()
    val cardFieldErrors by viewModel.cardFieldErrors.collectAsState()

    // Dialogos: sobreviven a la rotacion (solo guardan el id, nunca el objeto).
    var showCreateTopic by rememberSaveable { mutableStateOf(false) }
    var editTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddCard by rememberSaveable { mutableStateOf(false) }
    var editCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCardId by rememberSaveable { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val sampleCreatedText = stringResource(Res.string.sample_deck_created)
    var sampleNoticeShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sampleCreated) {
        if (sampleCreated && !sampleNoticeShown) {
            sampleNoticeShown = true
            snackbarHostState.showSnackbar(sampleCreatedText)
        }
    }
    val saveCardFailed = stringResource(Res.string.deckcontent_save_card_failed)
    val saveTopicFailed = stringResource(Res.string.deckcontent_save_topic_failed)
    val deleteFailed = stringResource(Res.string.deckcontent_delete_failed)
    LaunchedEffect(viewModel, saveCardFailed, saveTopicFailed, deleteFailed) {
        viewModel.messageEvent.collect { message ->
            snackbarHostState.showSnackbar(
                when (message) {
                    ContentMessage.SaveCardFailed -> saveCardFailed
                    ContentMessage.SaveTopicFailed -> saveTopicFailed
                    ContentMessage.DeleteFailed -> deleteFailed
                }
            )
        }
    }

    // El mazo desaparecio (p. ej. borrado en otro dispositivo): se sale a la pantalla anterior.
    val gone = state is DeckContentUiState.DeckGone
    LaunchedEffect(gone) { if (gone) onDeckGone() }

    // Tarjeta nueva: dentro del tema si estamos en su subnivel.
    val openTopicId = (state as? DeckContentUiState.TopicLevel)?.topic?.id

    // Atrás del sistema: dentro de un tema se comporta como la flecha de la barra (vuelve al mazo); en el nivel del mazo el handler
    // se desactiva y el atrás sale a Mis Mazos como siempre. En Desktop no hay botón de atrás del sistema: solo cuenta la flecha.
    SystemBackHandler(enabled = openTopicId != null) { viewModel.closeTopic() }

    fun openCreateTopic() {
        viewModel.clearErrors()
        showCreateTopic = true
    }

    fun openCreateCard() {
        viewModel.clearErrors()
        showAddCard = true
    }

    fun openEditCard(cardId: String) {
        viewModel.clearErrors()
        editCardId = cardId
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                when (val current = state) {
                    is DeckContentUiState.TopicLevel -> {
                        TopicHeader(
                            topicName = current.topic.name,
                            onBack = viewModel::closeTopic,
                            onAddCard = ::openCreateCard
                        )
                        Text(
                            text = stringResource(Res.string.deckcontent_inside, current.deckName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is DeckContentUiState.Empty -> DeckHeader(
                        deckName = current.deckName, showStudy = showStudyButton(current), showFreeReview = showFreeReviewButton(current), onBack = onBackClick,
                        onNewTopic = ::openCreateTopic, onNewCard = ::openCreateCard,
                        onStudy = { onStudyClick(deckId) },
                        onFreeReview = { onFreeReviewClick(deckId) }
                    )
                    is DeckContentUiState.NoResults -> DeckHeader(
                        deckName = current.deckName, showStudy = showStudyButton(current), showFreeReview = showFreeReviewButton(current), onBack = onBackClick,
                        onNewTopic = ::openCreateTopic, onNewCard = ::openCreateCard,
                        onStudy = { onStudyClick(deckId) },
                        onFreeReview = { onFreeReviewClick(deckId) }
                    )
                    is DeckContentUiState.Search -> DeckHeader(
                        deckName = current.deckName, showStudy = showStudyButton(current), showFreeReview = showFreeReviewButton(current), onBack = onBackClick,
                        onNewTopic = ::openCreateTopic, onNewCard = ::openCreateCard,
                        onStudy = { onStudyClick(deckId) },
                        onFreeReview = { onFreeReviewClick(deckId) }
                    )
                    is DeckContentUiState.Root -> DeckHeader(
                        deckName = current.deckName,
                        // Solo en el nivel del mazo con tarjetas y sin busqueda activa (ver showStudyButton).
                        showStudy = showStudyButton(current), showFreeReview = showFreeReviewButton(current),
                        onBack = onBackClick,
                        onNewTopic = ::openCreateTopic, onNewCard = ::openCreateCard,
                        onStudy = { onStudyClick(deckId) },
                        onFreeReview = { onFreeReviewClick(deckId) }
                    )
                    DeckContentUiState.Loading, DeckContentUiState.Error, DeckContentUiState.DeckGone ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.deckcontent_back_to_decks)
                                )
                            }
                        }
                }

                // El buscador solo tiene sentido en el nivel del mazo con contenido.
                if (state is DeckContentUiState.Root || state is DeckContentUiState.Search ||
                    state is DeckContentUiState.NoResults
                ) {
                    ContentSearchField(query = query, onQueryChange = viewModel::onSearchQueryChange)
                }

                when (val current = state) {
                    DeckContentUiState.Loading, DeckContentUiState.DeckGone -> ContentSkeleton()
                    DeckContentUiState.Error -> FormBanner(
                        message = stringResource(Res.string.deckcontent_load_error),
                        onRetry = viewModel::retry
                    )
                    is DeckContentUiState.Empty -> ContentEmpty(onAddCard = ::openCreateCard)
                    is DeckContentUiState.NoResults -> ContentNoResults(query = current.query)
                    is DeckContentUiState.Root -> RootList(
                        topics = current.topics,
                        cards = current.looseCards.map { CardHit(it, null) },
                        cardsTitle = Res.string.deckcontent_section_loose,
                        showTopicOnCards = false,
                        onOpenTopic = viewModel::openTopic,
                        onEditTopic = { viewModel.clearErrors(); editTopicId = it },
                        onDeleteTopic = { deleteTopicId = it },
                        onEditCard = ::openEditCard,
                        onDeleteCard = { deleteCardId = it }
                    )
                    is DeckContentUiState.Search -> RootList(
                        topics = current.topics,
                        cards = current.cards,
                        cardsTitle = Res.string.deckcontent_section_cards,
                        showTopicOnCards = true,
                        onOpenTopic = viewModel::openTopic,
                        onEditTopic = { viewModel.clearErrors(); editTopicId = it },
                        onDeleteTopic = { deleteTopicId = it },
                        onEditCard = ::openEditCard,
                        onDeleteCard = { deleteCardId = it }
                    )
                    is DeckContentUiState.TopicLevel ->
                        if (current.cards.isEmpty()) {
                            TopicEmpty()
                        } else {
                            RootList(
                                topics = emptyList(),
                                cards = current.cards.map { CardHit(it, null) },
                                cardsTitle = null,
                                showTopicOnCards = false,
                                onOpenTopic = {},
                                onEditTopic = {},
                                onDeleteTopic = {},
                                onEditCard = ::openEditCard,
                                onDeleteCard = { deleteCardId = it }
                            )
                        }
                }
            }
        }

        // Los dialogos se resuelven contra lo que se ve: si el tema o la tarjeta desaparece (p. ej. por sincronizacion),
        // se cierran.
        val topics: List<TopicWithCount> = when (val s = state) {
            is DeckContentUiState.Root -> s.topics
            is DeckContentUiState.Search -> s.topics
            else -> emptyList()
        }
        val cards: List<Flashcard> = when (val s = state) {
            is DeckContentUiState.Root -> s.looseCards
            is DeckContentUiState.Search -> s.cards.map { it.card }
            is DeckContentUiState.TopicLevel -> s.cards
            else -> emptyList()
        }

        if (showCreateTopic) {
            CreateTopicDialog(
                error = topicNameError,
                saving = saving,
                onNameChange = viewModel::clearErrors,
                onConfirm = { name -> viewModel.createTopic(name) { showCreateTopic = false } },
                onDismiss = {
                    viewModel.clearErrors()
                    showCreateTopic = false
                }
            )
        }

        topics.firstOrNull { it.topic.id == editTopicId }?.let { item ->
            EditTopicNameDialog(
                currentName = item.topic.name,
                error = topicNameError,
                saving = saving,
                onNameChange = viewModel::clearErrors,
                onConfirm = { name -> viewModel.updateTopic(item.topic, name) { editTopicId = null } },
                onDismiss = {
                    viewModel.clearErrors()
                    editTopicId = null
                }
            )
        }

        topics.firstOrNull { it.topic.id == deleteTopicId }?.let { item ->
            DeleteTopicDialog(
                topicName = item.topic.name,
                cardCount = item.cardCount,
                saving = saving,
                onConfirm = { viewModel.deleteTopic(item.topic) { deleteTopicId = null } },
                onDismiss = { deleteTopicId = null }
            )
        }

        if (showAddCard) {
            CreateCardDialog(
                initialTopicId = openTopicId, // dentro de un tema, la tarjeta nace ya asociada a el
                topicOptions = topicOptions,
                errors = cardFieldErrors,
                saving = saving,
                onFieldChange = viewModel::clearErrors,
                onConfirm = { q, a, topicId -> viewModel.createCard(q, a, topicId) { showAddCard = false } },
                onDismiss = {
                    viewModel.clearErrors()
                    showAddCard = false
                }
            )
        }

        cards.firstOrNull { it.id == editCardId }?.let { card ->
            EditCardDialog(
                card = card,
                topicOptions = topicOptions,
                errors = cardFieldErrors,
                saving = saving,
                onFieldChange = viewModel::clearErrors,
                onConfirm = { q, a, topicId -> viewModel.updateCard(card, q, a, topicId) { editCardId = null } },
                onDismiss = {
                    viewModel.clearErrors()
                    editCardId = null
                }
            )
        }

        cards.firstOrNull { it.id == deleteCardId }?.let { card ->
            DeleteCardDialog(
                saving = saving,
                onConfirm = { viewModel.deleteCard(card) { deleteCardId = null } },
                onDismiss = { deleteCardId = null }
            )
        }
    }
}

/**
 * Cabecera del nivel del mazo: flecha + nombre y, a la derecha (o debajo si no cabe), los botones en este orden: «Estudiar»
 * (acción primaria, rellena; solo con tarjetas y sin búsqueda: [showStudy]), «Nuevo tema» y «Nueva tarjeta» (ambos outline).
 * Los botones van en un FlowRow: si no caben en una línea, envuelven.
 */
/** Partes medibles de la cabecera (solo las usa el test de render: `inspect` es null en produccion). */
internal enum class DeckHeaderPart { TitleRow, Study, FreeReview, NewTopic, NewCard }

/** Textos de la cabecera ya resueltos (el test de render pasa literales: sin cargar recursos). */
internal class DeckHeaderLabels(
    val study: String,
    val studyDescription: String,
    val freeReview: String,
    val newTopic: String,
    val newCard: String,
    val back: String
)

private fun Modifier.inspectPart(part: DeckHeaderPart, inspect: ((DeckHeaderPart, Rect) -> Unit)?): Modifier =
    if (inspect == null) this else this.onGloballyPositioned { inspect(part, it.boundsInRoot()) }

@Composable
private fun DeckHeader(
    deckName: String,
    showStudy: Boolean,
    showFreeReview: Boolean,
    onBack: () -> Unit,
    onNewTopic: () -> Unit,
    onNewCard: () -> Unit,
    onStudy: () -> Unit,
    onFreeReview: () -> Unit
) {
    DeckHeaderContent(
        deckName = deckName,
        labels = DeckHeaderLabels(
            study = stringResource(Res.string.deckcontent_study_button),
            studyDescription = stringResource(Res.string.deckcontent_study),
            freeReview = stringResource(Res.string.free_review),
            newTopic = stringResource(Res.string.deckcontent_new_topic),
            newCard = stringResource(Res.string.deckcontent_new_card),
            back = stringResource(Res.string.deckcontent_back_to_decks)
        ),
        showStudy = showStudy,
        showFreeReview = showFreeReview,
        onBack = onBack,
        onNewTopic = onNewTopic,
        onNewCard = onNewCard,
        onStudy = onStudy,
        onFreeReview = onFreeReview
    )
}

/**
 * Cabecera del mazo: flecha + nombre y las acciones en este orden: «Estudiar» (brand, única acción primaria), «Repaso libre»
 * (outline, solo con las mismas condiciones que «Estudiar»), «Nuevo tema» y «Nueva tarjeta» (outline). Las dos de estudio van
 * antes que las de creación. Los botones van en un FlowRow: si no caben en una línea, envuelven.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeckHeaderContent(
    deckName: String,
    labels: DeckHeaderLabels,
    showStudy: Boolean,
    showFreeReview: Boolean,
    onBack: () -> Unit,
    onNewTopic: () -> Unit,
    onNewCard: () -> Unit,
    onStudy: () -> Unit,
    onFreeReview: () -> Unit,
    modifier: Modifier = Modifier,
    inspect: ((DeckHeaderPart, Rect) -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val actions: @Composable () -> Unit = {
            if (showStudy) {
                // La descripcion accesible sustituye a "Estudiar" por "Estudiar mazo".
                Button(
                    onClick = onStudy,
                    modifier = Modifier.semantics { contentDescription = labels.studyDescription }.inspectPart(DeckHeaderPart.Study, inspect)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(labels.study)
                }
            }
            if (showFreeReview) {
                OutlinedButton(onClick = onFreeReview, modifier = Modifier.inspectPart(DeckHeaderPart.FreeReview, inspect)) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(labels.freeReview)
                }
            }
            OutlinedButton(onClick = onNewTopic, modifier = Modifier.inspectPart(DeckHeaderPart.NewTopic, inspect)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(labels.newTopic)
            }
            OutlinedButton(onClick = onNewCard, modifier = Modifier.inspectPart(DeckHeaderPart.NewCard, inspect)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(labels.newCard)
            }
        }
        if (maxWidth < COMPACT_WIDTH) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BackAndTitle(
                    title = deckName, backLabel = labels.back, onBack = onBack,
                    modifier = Modifier.fillMaxWidth().inspectPart(DeckHeaderPart.TitleRow, inspect)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) { actions() }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BackAndTitle(
                    title = deckName, backLabel = labels.back, onBack = onBack,
                    modifier = Modifier.weight(1f).inspectPart(DeckHeaderPart.TitleRow, inspect)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) { actions() }
            }
        }
    }
}

@Composable
private fun BackAndTitle(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
    }
}

/** Cabecera del subnivel: flecha (vuelve al MAZO) + nombre del tema + "+ Tarjeta" (crea ya asociada al tema). */
@Composable
private fun TopicHeader(topicName: String, onBack: () -> Unit, onAddCard: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.deckcontent_back_to_deck)
            )
        }
        Text(
            text = topicName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Button(onClick = onAddCard) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(Res.string.deckcontent_add_card_short))
        }
    }
}

/**
 * Lista del nivel del mazo, de los resultados de busqueda y del subnivel (solo tarjetas, [cardsTitle] = null):
 * seccion de temas con su contador y seccion de tarjetas, cada una con su titulo y recuento.
 */
@Composable
private fun RootList(
    topics: List<TopicWithCount>,
    cards: List<CardHit>,
    cardsTitle: org.jetbrains.compose.resources.StringResource?,
    showTopicOnCards: Boolean,
    onOpenTopic: (String) -> Unit,
    onEditTopic: (String) -> Unit,
    onDeleteTopic: (String) -> Unit,
    onEditCard: (String) -> Unit,
    onDeleteCard: (String) -> Unit
) {
    val noTopic = stringResource(Res.string.deckcontent_topic_none)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (topics.isNotEmpty()) {
            item(key = "title-topics") { SectionTitle(stringResource(Res.string.deckcontent_section_topics, topics.size)) }
            items(topics.size, key = { "t-" + topics[it].topic.id }) { index ->
                val item = topics[index]
                TopicRow(
                    item = item,
                    onClick = { onOpenTopic(item.topic.id) },
                    onEdit = { onEditTopic(item.topic.id) },
                    onDelete = { onDeleteTopic(item.topic.id) }
                )
            }
        }
        if (cards.isNotEmpty()) {
            if (cardsTitle != null) {
                item(key = "title-cards") { SectionTitle(stringResource(cardsTitle, cards.size)) }
            }
            items(cards.size, key = { "c-" + cards[it].card.id }) { index ->
                val hit = cards[index]
                CardRow(
                    card = hit.card,
                    // Solo en los resultados de busqueda: el tema de la tarjeta o "Sin tema". Lista normal y subnivel: sin segunda linea.
                    secondLine = if (showTopicOnCards) (hit.topicName ?: noTopic) else null,
                    onClick = { onEditCard(hit.card.id) },
                    onEdit = { onEditCard(hit.card.id) },
                    onDelete = { onDeleteCard(hit.card.id) }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun ContentSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.deckcontent_search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.deckcontent_search_clear))
                }
            }
        } else null
    )
}

/** Tres esqueletos de 64 dp (radio 14). La pulsacion se queda quieta si el sistema pide reducir el movimiento. */
@Composable
private fun ContentSkeleton() {
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
    val loading = stringResource(Res.string.deckcontent_loading)
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
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            )
        }
    }
}

@Composable
private fun ContentEmpty(onAddCard: () -> Unit) {
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
                    text = stringResource(Res.string.deckcontent_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(Res.string.deckcontent_empty_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onAddCard) { Text(stringResource(Res.string.deckcontent_empty_cta)) }
        }
    }
}

@Composable
private fun TopicEmpty() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(Res.string.deckcontent_topic_empty_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = stringResource(Res.string.deckcontent_topic_empty_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContentNoResults(query: String) {
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
            text = stringResource(Res.string.deckcontent_no_results, query),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = stringResource(Res.string.deckcontent_no_results_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
