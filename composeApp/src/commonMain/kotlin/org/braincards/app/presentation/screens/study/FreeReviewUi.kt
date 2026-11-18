package org.braincards.app.presentation.screens.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.free_review
import braincards.composeapp.generated.resources.study_free_review_notice
import braincards.composeapp.generated.resources.study_results_title
import braincards.composeapp.generated.resources.study_results_title_free
import org.braincards.app.presentation.components.PILL_CHIP_ICON_SIZE
import org.braincards.app.presentation.components.Pill
import org.braincards.app.presentation.theme.BrainCardsTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Repaso libre en Estudio y Resultados (handoff v4.25; mockups `estudio.html`, `resultados.html` y sus espejos): un chip gris informativo
 * «Repaso libre» durante TODA la sesion, el aviso «No cambia tu programacion.» a su lado solo en ancho >= 600 dp, y en Resultados el
 * titulo propio «¡Repaso completado!» con el aviso «No afecta a tu programacion de repaso.». Las decisiones son funciones puras.
 */

/** Ancho a partir del cual (Desktop/tablet) el chip lleva al lado el aviso «No cambia tu programacion.». En Android estrecho, solo el chip. */
const val FREE_REVIEW_NOTICE_MIN_WIDTH_DP = 600f

/** ¿Se muestra el aviso junto al chip? Por ANCHO (>= 600 dp), no por plataforma. */
fun showFreeReviewNotice(widthDp: Float): Boolean = widthDp >= FREE_REVIEW_NOTICE_MIN_WIDTH_DP

/** El chip «Repaso libre» sale durante toda la sesion de repaso libre (y nunca en la normal). */
fun showFreeReviewChip(freeReview: Boolean): Boolean = freeReview

/** Resultados: el aviso «No afecta a tu programacion de repaso.» solo en el repaso libre. */
fun showResultsFreeNotice(freeReview: Boolean): Boolean = freeReview

/** Titulo de Resultados: «¡Repaso completado!» en el repaso libre (para que no parezca una sesion que cuenta) y «¡Sesion completada!» en la normal. */
fun studyResultsTitleRes(freeReview: Boolean): StringResource =
    if (freeReview) Res.string.study_results_title_free else Res.string.study_results_title

/** Chip gris neutro (mismo componente que la pildora de sync de Home, `.pill.sm`: no es de exito ni de error, solo informa). */
@Composable
fun FreeReviewChip(label: String, modifier: Modifier = Modifier) {
    val extra = BrainCardsTheme.extraColors
    Pill(
        label = label,
        icon = Icons.Default.Shuffle,
        container = extra.chipGrayBg,
        content = extra.chipGray,
        compact = true,
        modifier = modifier,
        iconSize = PILL_CHIP_ICON_SIZE
    )
}

/**
 * Fila de Estudio bajo la cabecera: chip centrado y, si [notice] no es null, el aviso a su lado (mockup `estudio.html`: hueco de 10,
 * aviso con el estilo `.hint`). Textos como parametros para poder probar el layout sin recursos.
 */
@Composable
internal fun FreeReviewBanner(chipLabel: String, notice: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FreeReviewChip(label = chipLabel)
        if (notice != null) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** La fila de Estudio con los textos de recursos; [showNotice] sale de [showFreeReviewNotice] segun el ancho. */
@Composable
fun FreeReviewHeader(showNotice: Boolean, modifier: Modifier = Modifier) {
    FreeReviewBanner(
        chipLabel = stringResource(Res.string.free_review),
        notice = if (showNotice) stringResource(Res.string.study_free_review_notice) else null,
        modifier = modifier
    )
}
