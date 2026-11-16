package org.braincards.app.presentation.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.theme_dark
import braincards.composeapp.generated.resources.theme_light
import braincards.composeapp.generated.resources.theme_system
import org.braincards.app.platform.rememberReducedMotion
import org.braincards.app.presentation.theme.BrainCardsPalette
import org.braincards.app.presentation.theme.DarkPalette
import org.braincards.app.presentation.theme.LightPalette
import org.jetbrains.compose.resources.stringResource

// Duraciones del handoff (v4.8): progreso de seleccion 250 ms, rebote 320 ms, fundido del check 180 ms.
internal const val THEME_SELECT_MS = 250
internal const val THEME_BOUNCE_MS = 320
internal const val THEME_CHECK_MS = 180
private val OvershootEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Tres tarjetas en fila (Sistema / Claro / Oscuro), grupo de radio accesible. */
@Composable
fun ThemeCards(current: AppTheme, onThemeChange: (AppTheme) -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppTheme.entries.forEach { theme ->
            ThemeCard(
                theme = theme,
                selected = theme == current,
                reducedMotion = reduced,
                onSelect = { onThemeChange(theme) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeCard(
    theme: AppTheme,
    selected: Boolean,
    reducedMotion: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brand = MaterialTheme.colorScheme.primary
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else THEME_SELECT_MS),
        label = "themeSelect"
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else THEME_CHECK_MS),
        label = "themeCheck"
    )

    // Rebote 0.92 -> 1.045 -> 1 solo al ELEGIR (no en la composicion inicial ni al deseleccionar).
    val bounce = remember { Animatable(1f) }
    var firstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (firstComposition) {
            firstComposition = false
            return@LaunchedEffect
        }
        if (selected && !reducedMotion) {
            bounce.snapTo(0.92f)
            bounce.animateTo(1f, keyframes {
                durationMillis = THEME_BOUNCE_MS
                1.045f at (THEME_BOUNCE_MS * 6 / 10) using OvershootEasing
                1f at THEME_BOUNCE_MS
            })
        } else {
            bounce.snapTo(1f)
        }
    }

    val label = stringResource(
        when (theme) {
            AppTheme.SYSTEM -> Res.string.theme_system
            AppTheme.LIGHT -> Res.string.theme_light
            AppTheme.DARK -> Res.string.theme_dark
        }
    )
    val borderColor = lerp(Color.Transparent, brand, progress)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(18.dp))
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            ThemePreview(theme, Modifier.fillMaxWidth().scale(bounce.value))
            if (checkAlpha > 0f) {
                // Anillo de 3 dp del color de la superficie + circulo de marca de 22 dp con el check.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-10).dp)
                        .graphicsLayer {
                            alpha = checkAlpha
                            // Fundido con crecimiento 0.6 -> 1 (bc-fade-in).
                            val s = 0.6f + 0.4f * checkAlpha
                            scaleX = s; scaleY = s
                        }
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(22.dp).background(brand, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ThemePreview(theme: AppTheme, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, outline, RoundedCornerShape(13.dp))
    ) {
        when (theme) {
            AppTheme.LIGHT -> drawAppMiniature(LightPalette)
            AppTheme.DARK -> drawAppMiniature(DarkPalette)
            AppTheme.SYSTEM -> {
                // Partida en diagonal: mitad clara arriba-izquierda, mitad oscura abajo-derecha.
                val light = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(0f, size.height); close()
                }
                val dark = Path().apply {
                    moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                clipPath(light) {
                    drawRect(LightPalette.ground)
                    pill(LightPalette.cta, 0.12f, 0.12f, 0.30f, 0.11f)
                }
                clipPath(dark) {
                    drawRect(DarkPalette.ground)
                    pill(DarkPalette.cta, 1f - 0.12f - 0.30f, 1f - 0.12f - 0.11f, 0.30f, 0.11f)
                }
            }
        }
    }
}

// Mini-vista previa de la app (mockup ajustes-apariencia.html): barra superior, pildora, dos lineas y boton.
private fun DrawScope.drawAppMiniature(p: BrainCardsPalette) {
    val w = size.width
    val h = size.height
    drawRect(p.ground)
    drawRect(p.surface, size = Size(w, h * 0.30f))
    drawRect(p.border, topLeft = Offset(0f, h * 0.30f - 1f), size = Size(w, 1f))
    pill(p.cta, 0.14f, 0.09f, 0.28f, 0.09f)
    val line = p.text.copy(alpha = if (p === LightPalette) 0.14f else 0.16f)
    bar(line, 0.14f, 0.44f, 0.72f, 0.08f, 4f)
    bar(line, 0.14f, 0.58f, 0.52f, 0.08f, 4f)
    bar(p.brand, 0.14f, 1f - 0.10f - 0.15f, 0.72f, 0.15f, 8f)
}

private fun DrawScope.pill(color: Color, x: Float, y: Float, w: Float, h: Float) =
    bar(color, x, y, w, h, 999f)

private fun DrawScope.bar(color: Color, x: Float, y: Float, w: Float, h: Float, radiusDp: Float) {
    val r = (radiusDp.dp.toPx()).coerceAtMost(size.height * h / 2f)
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * x, size.height * y),
        size = Size(size.width * w, size.height * h),
        cornerRadius = CornerRadius(r, r)
    )
}
