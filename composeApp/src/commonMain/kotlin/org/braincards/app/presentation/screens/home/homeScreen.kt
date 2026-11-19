package org.braincards.app.presentation.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.*
import org.braincards.app.presentation.components.EmptyActionCard
import org.braincards.app.presentation.sync.SyncErrorKind
import org.braincards.app.presentation.sync.SyncIndicator
import org.braincards.app.presentation.sync.SyncStatusViewModel
import org.braincards.app.presentation.components.BrandBadge
import org.braincards.app.presentation.components.FreeReviewGhostButton
import org.braincards.app.presentation.components.HOME_FREE_REVIEW_BUTTON
import org.braincards.app.presentation.components.negativeTopMargin
import org.braincards.app.presentation.components.Pill
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI

/** Por debajo de este ancho se usa el diseno movil (tarjetas en fila, una por linea). */
private val COMPACT_WIDTH_BREAKPOINT = 600.dp

@OptIn(KoinExperimentalAPI::class)
@Composable
fun HomeScreen(
    onNavigateToDecks: () -> Unit,
    onNavigateToStudy: () -> Unit,
    onNavigateToFreeReview: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToAuth: () -> Unit,
    homeViewModel: HomeViewModel = koinViewModel(),
    syncStatusViewModel: SyncStatusViewModel = koinViewModel()
) {
    val state by homeViewModel.state.collectAsState()
    val indicator by syncStatusViewModel.indicator.collectAsState()

    HomeContent(
        state = state,
        indicator = indicator,
        onRetry = homeViewModel::retry,
        onNavigateToDecks = onNavigateToDecks,
        onNavigateToStudy = onNavigateToStudy,
        onNavigateToFreeReview = onNavigateToFreeReview,
        onNavigateToStats = onNavigateToStats,
        // Con sesion (incluido "sin conexion") la pildora lleva a Ajustes; en modo invitado, a iniciar sesion.
        onPillClick = if (indicator == SyncIndicator.Guest) onNavigateToAuth else onNavigateToSettings,
        onNavigateToSettings = onNavigateToSettings
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    indicator: SyncIndicator?,
    onRetry: () -> Unit,
    onNavigateToDecks: () -> Unit,
    onNavigateToStudy: () -> Unit,
    onNavigateToFreeReview: () -> Unit,
    onNavigateToStats: () -> Unit,
    onPillClick: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val compact = maxWidth < COMPACT_WIDTH_BREAKPOINT
        val extra = BrainCardsTheme.extraColors
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 900.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (compact) 20.dp else 56.dp,
                    vertical = if (compact) 28.dp else 56.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 24.dp else 32.dp)
        ) {
            HomeHeader(indicator = indicator, compact = compact, onPillClick = onPillClick)

            if (state is HomeUiState.Error) {
                ErrorBanner(onRetry = onRetry)
            }

            when (state) {
                HomeUiState.Loading -> {
                    SkeletonBlock(height = if (compact) 88.dp else 112.dp, radius = if (compact) 18.dp else 20.dp)
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            repeat(3) { SkeletonBlock(height = 76.dp, radius = 16.dp) }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            repeat(3) { SkeletonBlock(height = 128.dp, radius = 18.dp, modifier = Modifier.weight(1f)) }
                        }
                    }
                }

                else -> {
                    // Repaso libre (paso 14c): SOLO bajo «Todo al dia» (homeShowsFreeReview), con el hueco de 14 del mockup entre el bloque de
                    // estado, el boton ghost y su linea de ayuda. En el resto de estados la columna solo tiene el bloque de siempre.
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (state) {
                            is HomeUiState.Content -> StudyAllButton(
                                dueCount = state.dueCount, compact = compact, onClick = onNavigateToStudy
                            )
                            HomeUiState.NoDecks -> FirstUseCard(compact = compact, onCreateDeck = onNavigateToDecks)
                            // 5.º estado: mazos sin ninguna tarjeta. Sin "Estudiar Todo": no hay nada que estudiar.
                            HomeUiState.NoCards -> NoCardsCard(compact = compact, onGoToDecks = onNavigateToDecks)
                            HomeUiState.Empty -> AllDoneButton(compact = compact, onClick = onNavigateToStudy)
                            else -> StudyAllOutlineButton(compact = compact, onClick = onNavigateToStudy)
                        }
                        if (homeShowsFreeReview(state)) {
                            FreeReviewGhostButton(
                                label = stringResource(Res.string.free_review),
                                onClick = onNavigateToFreeReview,
                                metrics = HOME_FREE_REVIEW_BUTTON
                            )
                            Text(
                                text = stringResource(Res.string.home_free_review_hint),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.negativeTopMargin(8.dp)
                            )
                        }
                    }
                    val cards = listOf(
                        NavCardData(
                            stringResource(Res.string.home_my_decks), Icons.Default.Layers,
                            extra.brandChip, MaterialTheme.colorScheme.primary, onNavigateToDecks
                        ),
                        NavCardData(
                            stringResource(Res.string.home_stats), Icons.Default.BarChart,
                            extra.chipTealBg, extra.chipTeal, onNavigateToStats
                        ),
                        NavCardData(
                            stringResource(Res.string.home_settings), Icons.Default.Tune,
                            extra.chipGrayBg, extra.chipGray, onNavigateToSettings
                        )
                    )
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            cards.forEach { NavCard(it, compact = true, modifier = Modifier.fillMaxWidth()) }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            cards.forEach { NavCard(it, compact = false, modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

// --- Cabecera: marca + wordmark + pildora de sync -----------------------------------------------------------------

@Composable
private fun HomeHeader(indicator: SyncIndicator?, compact: Boolean, onPillClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
            BrandBadge(size = if (compact) 30.dp else 40.dp)
            Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
            Column {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = BrainCardsTheme.wordmark.copy(
                        fontSize = if (compact) 22.sp else 30.sp,
                        lineHeight = if (compact) 26.sp else 33.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(Res.string.home_tagline),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = if (compact) 13.sp else 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // null (arranque en frio) y Unknown (lectura de la ultima sync sin terminar): sin pildora.
        if (indicator != null && indicator != SyncIndicator.Unknown) {
            Spacer(Modifier.width(12.dp))
            SyncPill(indicator = indicator, compact = compact, onClick = onPillClick)
        }
    }
}

private data class PillStyle(val container: Color, val content: Color, val icon: ImageVector, val spinning: Boolean = false)

@Composable
internal fun SyncPill(indicator: SyncIndicator, compact: Boolean, onClick: () -> Unit) {
    val extra = BrainCardsTheme.extraColors
    val scheme = MaterialTheme.colorScheme
    val style = when (indicator) {
        SyncIndicator.Syncing -> PillStyle(extra.chipGrayBg, extra.chipGray, Icons.Default.Refresh, spinning = true)
        is SyncIndicator.Synced -> PillStyle(extra.chipTealBg, extra.chipTeal, Icons.Default.CloudDone)
        SyncIndicator.NeverSynced -> PillStyle(extra.chipGrayBg, extra.chipGray, Icons.Default.Schedule)
        SyncIndicator.Offline -> PillStyle(extra.chipGrayBg, extra.chipGray, Icons.Default.WifiOff)
        is SyncIndicator.Error -> PillStyle(scheme.errorContainer, scheme.error, Icons.Default.ErrorOutline)
        // Invitado: neutro, solo contorno.
        SyncIndicator.Guest -> PillStyle(Color.Transparent, scheme.onSurfaceVariant, Icons.Default.PersonOutline)
        SyncIndicator.Unknown -> return
    }
    val label = stringResource(
        when (indicator) {
            SyncIndicator.Syncing -> Res.string.sync_pill_syncing
            is SyncIndicator.Synced -> Res.string.sync_pill_synced
            SyncIndicator.NeverSynced -> Res.string.sync_pill_never
            SyncIndicator.Offline -> Res.string.sync_pill_offline
            is SyncIndicator.Error -> when (indicator.kind) {
                SyncErrorKind.LOCAL -> Res.string.sync_pill_error_local
                SyncErrorKind.REJECTED -> Res.string.sync_pill_error_rejected
                SyncErrorKind.PUSH_PARTIAL -> Res.string.sync_pill_error_push_partial
                SyncErrorKind.FAILED -> Res.string.sync_pill_error_failed
            }
            SyncIndicator.Guest -> Res.string.sync_pill_guest
            SyncIndicator.Unknown -> Res.string.sync_pill_synced // inalcanzable (return arriba)
        }
    )
    // El aspecto lo da el Pill compartido (mismas medidas que antes: ver pillMetrics y PillMetricsTest).
    Pill(
        label = label,
        icon = style.icon,
        container = style.container,
        content = style.content,
        compact = compact,
        spinning = style.spinning,
        onClick = onClick
    )
}

// --- Estudiar Todo (segun estado) ---------------------------------------------------------------------------------

@Composable
private fun StudyAllButton(dueCount: Int, compact: Boolean, onClick: () -> Unit) {
    val extra = BrainCardsTheme.extraColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (compact) 18.dp else 20.dp),
        color = extra.cta,
        contentColor = extra.ctaText,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 22.dp else 32.dp,
                vertical = if (compact) 20.dp else 24.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 20.dp)
        ) {
            val box = if (compact) 48.dp else 56.dp
            Box(
                modifier = Modifier
                    .size(box)
                    .background(extra.ctaText, RoundedCornerShape(if (compact) 14.dp else 16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = extra.cta, modifier = Modifier.size(28.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)) {
                Text(
                    text = stringResource(Res.string.home_study_all),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = if (compact) 19.sp else 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = pluralStringResource(Res.plurals.home_due_cards, dueCount, dueCount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (compact) 13.sp else 14.sp),
                    modifier = Modifier.alpha(0.85f)
                )
            }
        }
    }
}

/**
 * Primer uso (0 mazos): bienvenida y un unico CTA a Mis Mazos. Sin "Estudiar Todo" (no hay nada que estudiar) ni
 * "Todo al dia" (sugeriria que el usuario ya hizo algo).
 */
@Composable
private fun FirstUseCard(compact: Boolean, onCreateDeck: () -> Unit) {
    EmptyActionCard(
        title = stringResource(Res.string.home_first_use_title),
        text = stringResource(Res.string.home_first_use_text),
        cta = stringResource(Res.string.home_first_use_cta),
        compact = compact,
        onClick = onCreateDeck
    )
}

/** 5.º estado: hay mazos pero ninguna tarjeta. Mismo patron visual que el primer uso; lleva a Mis Mazos. */
@Composable
private fun NoCardsCard(compact: Boolean, onGoToDecks: () -> Unit) {
    EmptyActionCard(
        title = stringResource(Res.string.home_no_cards_title),
        text = stringResource(Res.string.home_no_cards_text),
        cta = stringResource(Res.string.home_no_cards_cta),
        compact = compact,
        onClick = onGoToDecks
    )
}

/** 0 pendientes: "Todo al dia". Sigue llevando a Estudio (que muestra su propio "Estamos al dia"). */
@Composable
private fun AllDoneButton(compact: Boolean, onClick: () -> Unit) {
    val extra = BrainCardsTheme.extraColors
    OutlineRowButton(
        label = stringResource(Res.string.home_all_done),
        icon = Icons.Default.CheckCircleOutline,
        iconBg = extra.chipTealBg,
        iconTint = extra.chipTeal,
        compact = compact,
        onClick = onClick
    )
}

/** Con el contador en error, "Estudiar Todo" sigue disponible (la sesion lee sus propias tarjetas). */
@Composable
private fun StudyAllOutlineButton(compact: Boolean, onClick: () -> Unit) {
    val extra = BrainCardsTheme.extraColors
    OutlineRowButton(
        label = stringResource(Res.string.home_study_all),
        icon = Icons.Default.PlayArrow,
        iconBg = extra.chipGrayBg,
        iconTint = extra.chipGray,
        compact = compact,
        onClick = onClick
    )
}

@Composable
private fun OutlineRowButton(
    label: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    compact: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (compact) 18.dp else 20.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 22.dp else 32.dp,
                vertical = if (compact) 20.dp else 24.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 48.dp else 56.dp)
                    .background(iconBg, RoundedCornerShape(if (compact) 14.dp else 16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (compact) 19.sp else 23.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

// --- Tarjetas de navegacion ---------------------------------------------------------------------------------------

private data class NavCardData(
    val title: String,
    val icon: ImageVector,
    val chipBg: Color,
    val chipTint: Color,
    val onClick: () -> Unit
)

@Composable
private fun NavCard(data: NavCardData, compact: Boolean, modifier: Modifier = Modifier) {
    Surface(
        onClick = data.onClick,
        shape = RoundedCornerShape(if (compact) 16.dp else 18.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        val chip: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .size(if (compact) 40.dp else 44.dp)
                    .background(data.chipBg, RoundedCornerShape(if (compact) 11.dp else 12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(data.icon, contentDescription = null, tint = data.chipTint, modifier = Modifier.size(22.dp))
            }
        }
        val title: @Composable () -> Unit = {
            Text(
                text = data.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            )
        }
        if (compact) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                chip()
                title()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                chip()
                title()
            }
        }
    }
}

// --- Cargando y error ---------------------------------------------------------------------------------------------

@Composable
private fun SkeletonBlock(height: Dp, radius: Dp, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse)
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(pulse)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(radius))
    )
}

@Composable
private fun ErrorBanner(onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = scheme.onErrorContainer, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(Res.string.home_load_error),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = scheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(Res.string.home_retry),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            ),
            color = scheme.onErrorContainer,
            modifier = Modifier.clickable(onClick = onRetry)
        )
    }
}
