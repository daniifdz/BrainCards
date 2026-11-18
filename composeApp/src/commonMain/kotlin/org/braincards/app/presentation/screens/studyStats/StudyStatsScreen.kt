package org.braincards.app.presentation.screens.studyStats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.stats_back
import braincards.composeapp.generated.resources.stats_empty_cta
import braincards.composeapp.generated.resources.stats_empty_text
import braincards.composeapp.generated.resources.stats_empty_title
import braincards.composeapp.generated.resources.stats_load_error
import braincards.composeapp.generated.resources.stats_title
import org.braincards.app.presentation.components.EmptyActionCard
import org.braincards.app.presentation.components.form.FormBanner
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val STATS_MAX_WIDTH = 780.dp
private val COMPACT_WIDTH = 600.dp

/** Estadisticas (handoff 5.7): casillas + madurez de las tarjetas. Estados: Loading, Error, Empty y Content. */
@Composable
fun StudyStatsScreen(
    onBackClick: () -> Unit,
    onCreateDeck: () -> Unit,
    viewModel: StudyStatsVM = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val compact = maxWidth < COMPACT_WIDTH
            Column(
                modifier = Modifier
                    .widthIn(max = STATS_MAX_WIDTH)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(if (compact) 16.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.stats_back))
                    }
                    Text(
                        text = stringResource(Res.string.stats_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                when (val s = state) {
                    StatsUiState.Loading -> StatsSkeleton()
                    StatsUiState.Error -> FormBanner(
                        message = stringResource(Res.string.stats_load_error),
                        onRetry = viewModel::retry
                    )
                    StatsUiState.Empty -> EmptyActionCard(
                        title = stringResource(Res.string.stats_empty_title),
                        text = stringResource(Res.string.stats_empty_text),
                        cta = stringResource(Res.string.stats_empty_cta),
                        compact = compact,
                        onClick = onCreateDeck
                    )
                    is StatsUiState.Content -> StatsContent(content = s, compact = compact)
                }
            }
        }
    }
}
