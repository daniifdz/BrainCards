package org.braincards.app.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Medidas de un botón «ghost» del repaso libre. */
data class GhostButtonMetrics(val iconDp: Int, val fontSp: Float, val gapDp: Int, val horizontalDp: Int, val verticalDp: Int)

/** Home, «Todo al dia» (`home-states.html`): icono 18, fuente 14,5, hueco 8, relleno 10 / 4. */
val HOME_FREE_REVIEW_BUTTON = GhostButtonMetrics(iconDp = 18, fontSp = 14.5f, gapDp = 8, horizontalDp = 4, verticalDp = 10)

/** Estado vacio de Estudio (`estudio-vacio.html`): icono 15, fuente 13,5, hueco 7, relleno 6. */
val EMPTY_FREE_REVIEW_BUTTON = GhostButtonMetrics(iconDp = 15, fontSp = 13.5f, gapDp = 7, horizontalDp = 6, verticalDp = 6)

/** Boton ghost «Repaso libre» (icono `Shuffle` + texto en gris secundario, sin fondo ni borde). Se comparte entre Home y el vacio de Estudio. */
@Composable
fun FreeReviewGhostButton(label: String, onClick: () -> Unit, metrics: GhostButtonMetrics, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = metrics.horizontalDp.dp, vertical = metrics.verticalDp.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(metrics.iconDp.dp))
        Spacer(Modifier.width(metrics.gapDp.dp))
        Text(text = label, fontSize = metrics.fontSp.sp, fontWeight = FontWeight.SemiBold)
    }
}
