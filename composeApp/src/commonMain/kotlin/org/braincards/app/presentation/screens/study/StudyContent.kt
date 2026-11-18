package org.braincards.app.presentation.screens.study

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.study_again
import braincards.composeapp.generated.resources.study_close
import braincards.composeapp.generated.resources.study_easy
import braincards.composeapp.generated.resources.study_good
import braincards.composeapp.generated.resources.study_hard
import braincards.composeapp.generated.resources.study_label_deck_topic
import braincards.composeapp.generated.resources.study_progress
import braincards.composeapp.generated.resources.study_question_a11y
import braincards.composeapp.generated.resources.study_question_answer_a11y
import braincards.composeapp.generated.resources.study_show_answer
import braincards.composeapp.generated.resources.study_show_answer_hint
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.models.StudyCardLabel
import org.braincards.app.platform.rememberReducedMotion
import org.braincards.app.platform.showKeyboardHints
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.compose.resources.stringResource

private val CARD_SHAPE = RoundedCornerShape(24.dp)
private val BUTTON_SHAPE = RoundedCornerShape(14.dp)

/** Por debajo de este ancho los 4 botones de puntuacion pasan a una cuadricula de 2x2 (si no, el texto no cabe). */
private val QUALITY_GRID_WIDTH = 360.dp

/** Tarjeta en pantalla: cabecera (✕ + progreso), etiqueta "Mazo · Tema", tarjeta y controles (mockup `estudio.html`). */
@Composable
fun StudyReviewing(
    state: StudyState.Reviewing,
    onClose: () -> Unit,
    onReveal: () -> Unit,
    onRate: (ReviewQuality) -> Unit,
    modifier: Modifier = Modifier,
    ratePulse: RatePulseState = RatePulseState()
) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StudyHeader(onClose = onClose, progress = state.progress, remaining = state.remaining, total = state.total)
        state.label?.let { StudyLabel(it) }
        StudyCard(state = state, onReveal = onReveal, modifier = Modifier.weight(1f))
        if (state.isAnswerVisible) {
            QualityButtons(onRate = onRate, ratePulse = ratePulse)
        } else {
            RevealControls(onReveal = onReveal)
        }
    }
}

/** ✕ sin titulo ni flecha (la sesion no tiene barra): vuelve al origen. Solo el boton, en los estados sin tarjeta. */
@Composable
fun StudyCloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClose, modifier = modifier) {
        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.study_close))
    }
}

/**
 * Cabecera: ✕ + barra de progreso (M - N) / M + "Quedan N de M". El texto del recuento es una live region educada (se
 * anuncia en cada tarjeta); la barra es decorativa para los lectores de pantalla (el texto ya dice lo mismo).
 */
@Composable
private fun StudyHeader(onClose: () -> Unit, progress: Float, remaining: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StudyCloseButton(onClose = onClose)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StudyProgressBar(progress)
            Text(
                text = stringResource(Res.string.study_progress, remaining, total),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun StudyProgressBar(progress: Float) {
    val reduced = rememberReducedMotion()
    // Muelle al avanzar (rebote). Con "reducir animaciones" salta directo. El valor animado pasa de largo del destino y puede
    // superar 1.0 (ultimo repaso) o bajar de 0: lo que se DIBUJA va acotado (studyBarVisibleFraction).
    val animated by animateFloatAsState(
        targetValue = studyBarTarget(progress),
        animationSpec = if (reduced) snap() else STUDY_PROGRESS_SPRING
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .clearAndSetSemantics { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(studyBarVisibleFraction(animated))
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

@Composable
private fun StudyLabel(label: StudyCardLabel) {
    val text = if (label.topicName == null) {
        label.deckName
    } else {
        stringResource(Res.string.study_label_deck_topic, label.deckName, label.topicName)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * La tarjeta: con la pregunta en pantalla es un boton (tocar o hacer clic la revela); al revelar, la respuesta aparece
 * DEBAJO de la pregunta (sin giro 3D) con un fundido que se omite con movimiento reducido. Es una live region educada
 * solo con la respuesta visible, para que se anuncie al revelarla.
 */
@Composable
private fun StudyCard(state: StudyState.Reviewing, onReveal: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val card = state.currentCard
    val description = if (state.isAnswerVisible) {
        stringResource(Res.string.study_question_answer_a11y, card.question, card.answer)
    } else {
        stringResource(Res.string.study_question_a11y, card.question)
    }
    val revealLabel = stringResource(Res.string.study_show_answer)

    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 220.dp),
        shape = CARD_SHAPE,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (state.isAnswerVisible) {
                        Modifier
                    } else {
                        Modifier.clickable(onClickLabel = revealLabel, role = Role.Button, onClick = onReveal)
                    }
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    if (state.isAnswerVisible) liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = state.isAnswerVisible, animationSpec = if (reduced) snap() else tween(180)) { revealed ->
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (!revealed) {
                        Text(
                            text = card.question,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 29.sp),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = card.question,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Box(
                            Modifier
                                .width(48.dp)
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                        Text(
                            text = card.answer,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RevealControls(onReveal: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onReveal,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = BUTTON_SHAPE
        ) {
            Text(stringResource(Res.string.study_show_answer), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (showKeyboardHints) {
            Text(
                text = stringResource(Res.string.study_show_answer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private class QualityStyle(
    val quality: ReviewQuality,
    val label: String,
    val number: String,
    val container: Color,
    val content: Color
)

/**
 * Los 4 botones de "peor" a "mejor" con los colores del tema (nada fijo): error, cta, chipTeal y brand. El texto va
 * sobre el contenedor con el color del mockup (ground o *Text): contraste >= 4.9:1 en claro y oscuro.
 */
@Composable
private fun QualityButtons(onRate: (ReviewQuality) -> Unit, ratePulse: RatePulseState) {
    val scheme = MaterialTheme.colorScheme
    val extra = BrainCardsTheme.extraColors
    val styles = listOf(
        QualityStyle(ReviewQuality.AGAIN, stringResource(Res.string.study_again), "1", scheme.error, scheme.background),
        QualityStyle(ReviewQuality.HARD, stringResource(Res.string.study_hard), "2", extra.cta, extra.ctaText),
        QualityStyle(ReviewQuality.GOOD, stringResource(Res.string.study_good), "3", extra.chipTeal, scheme.background),
        QualityStyle(ReviewQuality.EASY, stringResource(Res.string.study_easy), "4", scheme.primary, scheme.onPrimary)
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < QUALITY_GRID_WIDTH) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                styles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { QualityButton(it, onRate, ratePulse, Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                styles.forEach { QualityButton(it, onRate, ratePulse, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QualityButton(
    style: QualityStyle,
    onRate: (ReviewQuality) -> Unit,
    ratePulse: RatePulseState,
    modifier: Modifier = Modifier
) {
    // Pulso al puntuar (D7): lo dispara el ESTADO COMPARTIDO `ratePulse` (clic y teclas 1-4), no el interactionSource. 0 = reposo,
    // 1 = pulsado (escala 0.95 + brillo); un pulso nuevo reinicia la animacion, no la acumula. Un boton recien compuesto no repite
    // un pulso viejo (baseToken). Con "reducir animaciones", nada.
    val reduced = rememberReducedMotion()
    val press = remember { Animatable(0f) }
    val baseToken = remember { ratePulse.token }
    LaunchedEffect(ratePulse.token) {
        if (shouldRunStudyPress(reduced, ratePulse.token, baseToken, ratePulse.quality, style.quality)) {
            press.animateTo(1f, STUDY_PRESS_DOWN_SPEC)
            press.animateTo(0f, STUDY_PRESS_UP_SPRING)
        }
    }
    val cornerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
    Button(
        onClick = { onRate(style.quality) },
        modifier = modifier
            .heightIn(min = 56.dp)
            .graphicsLayer {
                val scale = studyPressScale(press.value)
                scaleX = scale
                scaleY = scale
            }
            // Brillo: superposicion blanca SOLO de dibujo (no intercepta foco ni clics), en la fase de dibujo (no recompone).
            .drawWithContent {
                drawContent()
                drawRoundRect(Color.White, cornerRadius = CornerRadius(cornerPx), alpha = studyPressOverlayAlpha(press.value))
            },
        shape = BUTTON_SHAPE,
        colors = ButtonDefaults.buttonColors(containerColor = style.container, contentColor = style.content),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(style.label, maxLines = 1, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            if (showKeyboardHints) {
                // El numero es una pista visual (opacidad plena: al 75 % del mockup no llega a 4.5:1 sobre cta/teal en
                // claro); los lectores de pantalla leen solo la etiqueta.
                Text(
                    text = style.number,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }
        }
    }
}
