package org.braincards.app.presentation.screens.study

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.study_load_error
import braincards.composeapp.generated.resources.study_loading
import braincards.composeapp.generated.resources.study_rate_failed
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.presentation.components.form.FormBanner
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Ancho maximo del contenido en pantallas anchas (Desktop/tablet), como el mockup (640 dp). */
private val STUDY_MAX_WIDTH = 640.dp

/**
 * Sesion de estudio (handoff 5.5, v4.18). Anfitrion: elige que se dibuja segun el estado, recoge el teclado en el
 * CONTENEDOR (no en ningun boton: sin foco inicial en los botones de puntuacion, ver [studyKeys]) y muestra los errores.
 *
 * [onBackClick] vuelve al ORIGEN de la sesion (✕, Esc, boton de los estados sin tarjeta): App.kt lo conecta a
 * popBackStackSafely. El atras del sistema en Android hace pop por si solo.
 */
@Composable
fun StudyScreen(
    deckId: String?,
    onBackClick: () -> Unit,
    viewModel: StudyViewModel = koinViewModel()
) {
    // initSession es idempotente por alcance: rotar la pantalla no reinicia la sesion.
    val scope = remember(deckId) { if (deckId != null) StudyScope.Deck(deckId) else StudyScope.All }
    val origin = studyOrigin(scope)
    val state by viewModel.state.collectAsState()
    val reviewing = state as? StudyState.Reviewing

    // initSession es idempotente por alcance: rotar la pantalla no reinicia la sesion.
    LaunchedEffect(scope) { viewModel.initSession(scope) }

    val snackbarHostState = remember { SnackbarHostState() }
    val rateFailed = stringResource(Res.string.study_rate_failed)
    LaunchedEffect(viewModel, rateFailed) {
        viewModel.messageEvent.collect { message ->
            when (message) {
                StudyMessage.RateFailed -> snackbarHostState.showSnackbar(rateFailed)
            }
        }
    }

    // El foco vive en el contenedor, nunca en un boton de puntuacion. Se (re)pide al cambiar de estado/tarjeta/reveal:
    // al revelar desaparece el boton "Mostrar respuesta" y el foco no debe perderse.
    val containerFocus = remember { FocusRequester() }
    LaunchedEffect(state) {
        // En Resultados el foco inicial es del boton unico [PROPUESTO]: no se le quita.
        if (state is StudyState.Finished) return@LaunchedEffect
        withFrameNanos { }
        runCatching { containerFocus.requestFocus() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .focusRequester(containerFocus)
                .focusable()
                .studyKeys(
                    cardShown = reviewing != null,
                    answerVisible = reviewing?.isAnswerVisible ?: false,
                    onReveal = viewModel::showAnswer,
                    onRate = viewModel::rateCard,
                    onExit = onBackClick
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.widthIn(max = STUDY_MAX_WIDTH).fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                when (val current = state) {
                    StudyState.Loading -> {
                        StudyCloseButton(onClose = onBackClick)
                        StudyLoading()
                    }

                    StudyState.Error -> {
                        StudyCloseButton(onClose = onBackClick)
                        Spacer(Modifier.height(16.dp))
                        FormBanner(
                            message = stringResource(Res.string.study_load_error),
                            onRetry = viewModel::retry
                        )
                    }

                    StudyState.Empty -> StudyEmpty(origin = origin, onBack = onBackClick)

                    is StudyState.Reviewing -> StudyReviewing(
                        state = current,
                        onClose = onBackClick,
                        onReveal = viewModel::showAnswer,
                        onRate = viewModel::rateCard
                    )

                    is StudyState.Finished -> StudyFinished(
                        results = current.results,
                        label = current.label,
                        origin = origin,
                        onBack = onBackClick
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyLoading() {
    val loading = stringResource(Res.string.study_loading)
    Box(
        modifier = Modifier.fillMaxSize().semantics {
            contentDescription = loading
            liveRegion = LiveRegionMode.Polite
        },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

