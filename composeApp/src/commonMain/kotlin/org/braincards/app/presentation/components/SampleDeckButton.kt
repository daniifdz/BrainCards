package org.braincards.app.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.*
import org.braincards.app.platform.rememberReducedMotion
import org.jetbrains.compose.resources.stringResource

/**
 * Botón secundario «Crear mazo de ejemplo» (Home en primer uso y Lista de mazos vacía). Mientras [creating] queda deshabilitado, con
 * el icono de sincronización girando (quieto con «reducir animaciones») y el texto «Creando…»; un toque en ese estado no hace nada.
 * Alto mínimo de 48 dp.
 */
@Composable
fun SampleDeckButton(creating: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        enabled = !creating,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        if (creating) {
            SpinningIcon()
        } else {
            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(if (creating) Res.string.sample_deck_creating else Res.string.sample_deck_create),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun SpinningIcon() {
    val reduced = rememberReducedMotion()
    val angle by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart)
    )
    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp).rotate(if (reduced) 0f else angle))
}
