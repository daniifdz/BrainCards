package org.braincards.app.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Medidas de la pastilla (`.pill` / `.pill.sm` de los mockups): relleno horizontal y vertical, hueco icono-texto y cuerpo de la fuente. */
data class PillMetrics(val horizontalDp: Int, val verticalDp: Int, val gapDp: Int, val fontSp: Int)

/** `.pill.sm` (compacta: 6 / 10 px, hueco 6, fuente 12) y `.pill` (normal: 8 / 14 px, hueco 8, fuente 13). */
fun pillMetrics(compact: Boolean): PillMetrics =
    if (compact) PillMetrics(horizontalDp = 10, verticalDp = 6, gapDp = 6, fontSp = 12)
    else PillMetrics(horizontalDp = 14, verticalDp = 8, gapDp = 8, fontSp = 13)

/** Tamano del icono de la pastilla de sincronizacion de Home (16 dp); el chip informativo usa 13 dp (mockup `estudio.html`). */
val PILL_SYNC_ICON_SIZE: Dp = 16.dp
val PILL_CHIP_ICON_SIZE: Dp = 13.dp

/**
 * Pastilla compartida (extraida de la pildora de sincronizacion de Home, sin cambiar su aspecto): contorno de 1 dp, fondo [container],
 * icono + etiqueta en [content]. Con [onClick] es un boton (la pildora de sync); sin el es informativa (el chip «Repaso libre»).
 * [spinning] gira el icono (sincronizando).
 */
@Composable
fun Pill(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = PILL_SYNC_ICON_SIZE,
    spinning: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val metrics = pillMetrics(compact)
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = metrics.horizontalDp.dp, vertical = metrics.verticalDp.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.gapDp.dp)
        ) {
            if (spinning) {
                val angle by rememberInfiniteTransition().animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart)
                )
                Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize).rotate(angle))
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = metrics.fontSp.sp, fontWeight = FontWeight.SemiBold)
            )
        }
    }
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = CircleShape, color = container, contentColor = content, border = border) { body() }
    } else {
        Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content, border = border) { body() }
    }
}
