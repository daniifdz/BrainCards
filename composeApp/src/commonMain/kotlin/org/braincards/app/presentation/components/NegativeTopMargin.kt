package org.braincards.app.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Equivale a un `margin-top` negativo: el elemento sube [amount] y además deja de reservar ese espacio (lo que va debajo sube
 * con él). Lo usan los avisos de Resultados y de Home («Todo al día»), ambos con -8 dp.
 */
fun Modifier.negativeTopMargin(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}
