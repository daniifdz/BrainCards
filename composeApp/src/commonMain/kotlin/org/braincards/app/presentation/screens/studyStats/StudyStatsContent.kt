package org.braincards.app.presentation.screens.studyStats

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.stats_donut_center
import braincards.composeapp.generated.resources.stats_learning
import braincards.composeapp.generated.resources.stats_legend_a11y
import braincards.composeapp.generated.resources.stats_loading
import braincards.composeapp.generated.resources.stats_maturity_subtitle
import braincards.composeapp.generated.resources.stats_maturity_subtitle_compact
import braincards.composeapp.generated.resources.stats_maturity_title
import braincards.composeapp.generated.resources.stats_mature
import braincards.composeapp.generated.resources.stats_new
import braincards.composeapp.generated.resources.stats_tile_a11y
import braincards.composeapp.generated.resources.stats_tile_cards
import braincards.composeapp.generated.resources.stats_tile_decks
import braincards.composeapp.generated.resources.stats_tile_due
import org.braincards.app.platform.rememberReducedMotion
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.compose.resources.stringResource

private val CARD_BORDER_WIDTH = 1.dp
private val DONUT_SIZE = 160.dp
private val DONUT_STROKE = 20.dp

/** Las tres casillas (Mazos, Tarjetas totales, Pendientes hoy) y la tarjeta de madurez. */
@Composable
fun StatsContent(content: StatsUiState.Content, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
            StatTile(content.deckCount, stringResource(Res.string.stats_tile_decks), compact, Modifier.weight(1f))
            StatTile(content.totalCards, stringResource(Res.string.stats_tile_cards), compact, Modifier.weight(1f))
            StatTile(content.dueCount, stringResource(Res.string.stats_tile_due), compact, Modifier.weight(1f))
        }
        MaturityCard(content = content, compact = compact)
    }
}

@Composable
private fun StatTile(value: Int, label: String, compact: Boolean, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.stats_tile_a11y, label, value)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CARD_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = value.toString(), fontSize = if (compact) 22.sp else 26.sp, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private class Slice(val label: String, val count: Int, val percent: Int, val color: Color)

@Composable
private fun MaturityCard(content: StatsUiState.Content, compact: Boolean) {
    val extra = BrainCardsTheme.extraColors
    val stats = content.stats
    val percents = stats.percentages()
    val slices = listOf(
        Slice(stringResource(Res.string.stats_new), stats.newCards, percents[0], extra.chipGray),
        Slice(stringResource(Res.string.stats_learning), stats.learningCards, percents[1], extra.cta),
        Slice(stringResource(Res.string.stats_mature), stats.matureCards, percents[2], extra.chipTeal)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(CARD_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 20.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.stats_maturity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = stringResource(
                        if (compact) Res.string.stats_maturity_subtitle_compact else Res.string.stats_maturity_subtitle
                    ),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Ancho: donut a la izquierda y leyenda a la derecha; Compact: leyenda debajo del donut.
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (content.hasCards) MaturityDonut(slices, content.totalCards)
                    Legend(slices)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (content.hasCards) MaturityDonut(slices, content.totalCards)
                    Legend(slices, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Donut (sin librería): un arco de [DONUT_STROKE] por categoría desde -90°, en el orden Nuevas / Aprendiendo / Maduras, con los
 * colores del tema. Es decorativo para los lectores de pantalla: los números están en la leyenda y en las casillas. Estático,
 * así que no hay animación que reducir. Solo se dibuja si hay tarjetas.
 */
@Composable
private fun MaturityDonut(slices: List<Slice>, total: Int) {
    Box(
        modifier = Modifier.size(DONUT_SIZE).clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(DONUT_SIZE)) {
            val stroke = DONUT_STROKE.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            var start = -90f
            slices.forEach { slice ->
                if (slice.count > 0) {
                    val sweep = slice.count.toFloat() / total * 360f
                    drawArc(
                        color = slice.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = total.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(Res.string.stats_donut_center),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Legend(slices: List<Slice>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        slices.forEach { LegendRow(it) }
    }
}

/** Una fila de la leyenda: punto de color + nombre + numero + porcentaje (el numero va en texto: no depende del color). */
@Composable
private fun LegendRow(slice: Slice) {
    val description = stringResource(Res.string.stats_legend_a11y, slice.label, slice.count, slice.percent)
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(slice.color, CircleShape).clearAndSetSemantics { })
        Text(
            text = slice.label,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 10.dp).weight(1f)
        )
        Text(text = slice.count.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "${slice.percent}%",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
    }
}

/** Esqueleto de carga: tres casillas y la tarjeta. Sin pulso si se piden menos animaciones. */
@Composable
fun StatsSkeleton() {
    val reduced = rememberReducedMotion()
    val alpha = if (reduced) {
        1f
    } else {
        rememberInfiniteTransition().animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
        ).value
    }
    val loading = stringResource(Res.string.stats_loading)
    Column(
        modifier = Modifier.semantics {
            contentDescription = loading
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) { SkeletonBlock(height = 76.dp, radius = 16.dp, alpha = alpha, modifier = Modifier.weight(1f)) }
        }
        SkeletonBlock(height = 200.dp, radius = 20.dp, alpha = alpha, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SkeletonBlock(height: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp, alpha: Float, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(height)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(radius))
    )
}
