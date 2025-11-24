package org.braincards.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun FlipCard(
    isFlipped: Boolean,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    // Animamos la rotación (0f es frente, 180f es dorso)
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(
            durationMillis = 400, // Duración de la vuelta
            easing = FastOutSlowInEasing
        )
    )

    // Calculamos si estamos mostrando el frente o el dorso
    // Si la rotación es <= 90 grados, vemos el frente. Si es > 90, vemos el dorso.
    val isBackVisible = rotation > 90f

    Card(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation // Rotación en el eje Y (como una puerta)
                cameraDistance = 12f * density // Da el efecto de profundidad 3D
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Si mostramos el dorso, tenemos que rotarlo 180 grados para que
                    // el texto no se lea al revés (efecto espejo).
                    if (isBackVisible) {
                        rotationY = 180f
                    }
                }
        ) {
            if (isBackVisible) {
                back()
            } else {
                front()
            }
        }
    }
}