package org.braincards.app.presentation.screens.sample

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import braincards.composeapp.generated.resources.*
import org.braincards.app.domain.sample.SampleDeckNames
import org.jetbrains.compose.resources.stringResource

/** Nombres del mazo y de sus temas, tal como están en `strings.xml`. */
@Composable
fun sampleDeckNames(): SampleDeckNames = SampleDeckNames(
    deck = stringResource(Res.string.sample_deck_name),
    topics = listOf(stringResource(Res.string.sample_deck_topic_study), stringResource(Res.string.sample_deck_topic_organize))
)

/**
 * Atiende el resultado de «Crear mazo de ejemplo»: con el mazo listo llama a [onReady] (la pantalla navega a su contenido), si ya había
 * mazos llama a [onHasDecks] (la pantalla lleva a la Lista de mazos) y si falla muestra el Snackbar de error en [snackbarHostState].
 * El Snackbar de éxito lo muestra la pantalla de destino.
 */
@Composable
fun CollectSampleDeckEvents(
    viewModel: SampleDeckViewModel,
    snackbarHostState: SnackbarHostState,
    onReady: (String) -> Unit,
    onHasDecks: () -> Unit
) {
    val failedText = stringResource(Res.string.sample_deck_failed)
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnHasDecks by rememberUpdatedState(onHasDecks)
    LaunchedEffect(viewModel, failedText) {
        viewModel.events.collect { event ->
            when (event) {
                is SampleDeckEvent.Ready -> currentOnReady(event.deckId)
                SampleDeckEvent.AlreadyHasDecks -> currentOnHasDecks()
                SampleDeckEvent.Failed -> snackbarHostState.showSnackbar(failedText)
            }
        }
    }
}
