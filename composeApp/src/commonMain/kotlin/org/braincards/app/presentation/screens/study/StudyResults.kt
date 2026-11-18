package org.braincards.app.presentation.screens.study

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.study_again
import braincards.composeapp.generated.resources.study_back_deck
import braincards.composeapp.generated.resources.study_back_home
import braincards.composeapp.generated.resources.study_easy
import braincards.composeapp.generated.resources.study_good
import braincards.composeapp.generated.resources.study_hard
import braincards.composeapp.generated.resources.study_results_all
import braincards.composeapp.generated.resources.study_results_count
import braincards.composeapp.generated.resources.study_results_stat_a11y
import braincards.composeapp.generated.resources.study_results_title
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.models.StudyResults
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.platform.rememberReducedMotion
import org.braincards.app.presentation.components.BrandBadge
import org.braincards.app.presentation.theme.BrainCardsTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * De donde se entro a la sesion de estudio (handoff v4.18): Home ("Estudiar Todo", [StudyScope.All]) o un mazo concreto
 * (Contenido de un mazo, [StudyScope.Deck]). El ✕, Esc, el atras del sistema y el boton de Resultados / del estado vacio
 * vuelven SIEMPRE al origen (popBackStackSafely); lo unico que cambia es la etiqueta del boton.
 */
enum class StudyOrigin { Home, Deck }

/** Origen de la sesion segun su alcance. */
fun studyOrigin(scope: StudyScope): StudyOrigin = when (scope) {
    is StudyScope.All -> StudyOrigin.Home
    is StudyScope.Deck -> StudyOrigin.Deck
}

/** Etiqueta de Resultados: "Estudiar Todo" en la sesion global; solo el NOMBRE DEL MAZO (sin tema) en la de un mazo. */
sealed interface StudyResultsLabel {
    data object AllDecks : StudyResultsLabel
    data class DeckName(val name: String) : StudyResultsLabel
}

/**
 * Etiqueta de Resultados por alcance. Sesion de un mazo cuyo nombre no se pudo resolver (borrado, fallo de lectura):
 * null = sin etiqueta (la pantalla sigue siendo valida).
 */
fun resultsLabel(scope: StudyScope, deckName: String?): StudyResultsLabel? = when (scope) {
    is StudyScope.All -> StudyResultsLabel.AllDecks
    is StudyScope.Deck -> deckName?.let { StudyResultsLabel.DeckName(it) }
}

/** Texto del boton que vuelve al origen: "Volver al inicio" (Home) o "Volver al mazo" (mazo). */
@Composable
fun studyBackLabel(origin: StudyOrigin): String = when (origin) {
    StudyOrigin.Home -> stringResource(Res.string.study_back_home)
    StudyOrigin.Deck -> stringResource(Res.string.study_back_deck)
}

private class ResultStat(val label: String, val count: Int, val color: Color)

/**
 * Resultados de la sesion (mockup `resultados.html`): badge del logo (64 dp, 56 dp en ancho compacto; sustituye al check), etiqueta, «¡Sesión completada!», «N repasos» (pulsaciones,
 * suma de las 4 casillas), desglose por calidad con los colores de Estudio y un UNICO boton hacia el origen.
 *
 * Foco inicial en ese boton [PROPUESTO]: es el unico control y Enter/Espacio lo activan (el teclado de la sesion solo
 * trata Esc aqui). Esc y el atras del sistema vuelven al mismo sitio. El titulo es un encabezado y live region educada.
 *
 * Celebracion al entrar (13c, `ResultsMotion.kt`): el badge da UN pulso (muelle 0.45 / 500, pico 1.08) y los 5 numeros cuentan de 0
 * a su valor en 500 ms, solapados. Accesibilidad: los textos que cuentan llevan `clearAndSetSemantics {}` (TalkBack nunca anuncia
 * un numero intermedio) y el valor final vive en un nodo estatico (el total, con live region) o en la descripcion de cada casilla.
 * Rotacion: la bandera `rememberSaveable` de «ya vista» hace que, tras rotar, salgan los valores finales sin pulso.
 */
@Composable
fun StudyFinished(
    results: StudyResults,
    label: StudyResultsLabel?,
    origin: StudyOrigin,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val extra = BrainCardsTheme.extraColors
    val stats = listOf(
        ResultStat(stringResource(Res.string.study_again), results.countOf(ReviewQuality.AGAIN), scheme.error),
        ResultStat(stringResource(Res.string.study_hard), results.countOf(ReviewQuality.HARD), extra.cta),
        ResultStat(stringResource(Res.string.study_good), results.countOf(ReviewQuality.GOOD), extra.chipTeal),
        ResultStat(stringResource(Res.string.study_easy), results.countOf(ReviewQuality.EASY), scheme.primary)
    )
    val labelText = when (label) {
        StudyResultsLabel.AllDecks -> stringResource(Res.string.study_results_all)
        is StudyResultsLabel.DeckName -> label.name
        null -> null
    }
    val buttonFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { buttonFocus.requestFocus() }
    }

    // Celebracion: una sola vez por pantalla (la bandera sobrevive a la rotacion) y nunca con "reducir animaciones".
    val reducedMotion = rememberReducedMotion()
    var celebrationSeen by rememberSaveable { mutableStateOf(false) }
    val play = remember { shouldPlayResultsCelebration(alreadySeen = celebrationSeen, reducedMotion = reducedMotion) }
    val countProgress = remember { Animatable(if (play) 0f else 1f) }
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        celebrationSeen = true // marcada ANTES de animar: si se rota a mitad, la nueva composicion sale en su valor final
        if (play) {
            launch { countProgress.animateTo(1f, tween(RESULTS_COUNT_MS, easing = LinearEasing)) }
            launch { pulseScale.animateTo(1f, RESULTS_PULSE_SPRING, initialVelocity = RESULTS_PULSE_KICK_VELOCITY) }
        }
    }
    val totalText = pluralStringResource(Res.plurals.study_results_count, results.totalReviews, results.totalReviews)

    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val badge = resultsBadgeSpec(maxWidth.value)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = scheme.surface,
                border = BorderStroke(1.dp, scheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BrandBadge(
                        size = badge.sizeDp.dp,
                        cornerPercent = badge.cornerPercent,
                        modifier = Modifier.graphicsLayer {
                            scaleX = pulseScale.value
                            scaleY = pulseScale.value
                        }
                    )
                    if (labelText != null) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = stringResource(Res.string.study_results_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        }
                    )
                    // Nodo ESTATICO con el valor final (live region educada); el texto que cuenta no aporta semantica.
                    Box(
                        modifier = Modifier.semantics {
                            contentDescription = totalText
                            liveRegion = LiveRegionMode.Polite
                        }
                    ) {
                        CountingTotal(total = results.totalReviews, progress = { countProgress.value })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        stats.forEach { StatTile(it, progress = { countProgress.value }, modifier = Modifier.weight(1f)) }
                    }
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).focusRequester(buttonFocus),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(studyBackLabel(origin), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(stat: ResultStat, progress: () -> Float, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.study_results_stat_a11y, stat.label, stat.count)
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(10.dp).background(stat.color, CircleShape).clearAndSetSemantics { })
        CountingNumber(target = stat.count, progress = progress)
        Text(
            text = stat.label,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Numero de una casilla que cuenta de 0 a [target]. Hoja propia: solo ESTE texto recompone mientras cuenta. Sin semantica. */
@Composable
private fun CountingNumber(target: Int, progress: () -> Float) {
    Text(
        text = countUpValue(target, progress()).toString(),
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clearAndSetSemantics { }
    )
}

/** «N repasos» que cuenta de 0 a [total] (plural segun el valor mostrado). Hoja propia y sin semantica: la lee el nodo estatico. */
@Composable
private fun CountingTotal(total: Int, progress: () -> Float) {
    val shown = countUpValue(total, progress())
    Text(
        text = pluralStringResource(Res.plurals.study_results_count, shown, shown),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.clearAndSetSemantics { }
    )
}
