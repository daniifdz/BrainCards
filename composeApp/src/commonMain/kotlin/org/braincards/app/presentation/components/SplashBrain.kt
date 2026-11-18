package org.braincards.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.splash_logo
import org.braincards.app.presentation.screens.navigation.SplashPose
import org.braincards.app.presentation.screens.navigation.splashUnitsToPx
import org.jetbrains.compose.resources.painterResource

/**
 * Cerebro del splash ANIMADO (solo EntryPending): el MISMO vector de `splash_logo` dibujado dos veces, cada vez recortado a una
 * mitad en el eje central (x = 128 del viewBox: los hemisferios no se tocan, el izquierdo acaba en 124 y el derecho empieza en
 * 132) y trasladado por separado. Asi no hay grupos nuevos en el XML ni copias del trazado, y `SplashLogoConsistencyTest`
 * sigue valiendo. [pose] se lee en la fase de DIBUJO: animar no recompone. [onFirstDraw] se avisa una vez, en el primer dibujo.
 */
@Composable
fun SplashBrain(size: Dp, pose: () -> SplashPose, modifier: Modifier = Modifier, onFirstDraw: (() -> Unit)? = null) {
    SplashBrainContent(size, painterResource(Res.drawable.splash_logo), pose, modifier, onFirstDraw)
}

/** Cuerpo con el painter inyectado (los tests de render pasan el vector ya decodificado). */
@Composable
internal fun SplashBrainContent(
    size: Dp,
    painter: Painter,
    pose: () -> SplashPose,
    modifier: Modifier = Modifier,
    onFirstDraw: (() -> Unit)? = null
) {
    val drawn = remember { BooleanArray(1) }
    // La opacidad se aplica UNA vez sobre la capa ya compuesta (Offscreen): si cada mitad se dibujara con su alpha, la franja
    // en que se cruzan se pintaria dos veces y saldria mas clara.
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                alpha = pose().alpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        if (!drawn[0]) {
            drawn[0] = true
            onFirstDraw?.invoke()
        }
        val p = pose()
        val side = this.size.width
        withTransform({ scale(scaleX = p.scale, scaleY = p.scale, pivot = center) }) {
            drawHalf(painter, dxPx = splashUnitsToPx(p.leftUnits, side), leftHalf = true)
            drawHalf(painter, dxPx = splashUnitsToPx(p.rightUnits, side), leftHalf = false)
        }
    }
}

private fun DrawScope.drawHalf(painter: Painter, dxPx: Float, leftHalf: Boolean) {
    val w = size.width
    val h = size.height
    val half = w / 2f
    // El recorte va DENTRO de la traslacion: la mitad y su recorte se mueven juntos (el cruce del overshoot no corta nada).
    translate(left = dxPx, top = 0f) {
        clipRect(left = if (leftHalf) 0f else half, top = 0f, right = if (leftHalf) half else w, bottom = h) {
            with(painter) { draw(size = Size(w, h)) }
        }
    }
}
