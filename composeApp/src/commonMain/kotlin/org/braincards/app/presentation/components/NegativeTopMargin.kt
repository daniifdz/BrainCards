package org.braincards.app.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Equivalente al `margin-top` negativo de los mockups: el elemento sube [amount] y TAMBIEN deja de reservar ese espacio (lo que va debajo
 * sube con el). Lo usan los avisos `.hint` de Resultados y de Home («Todo al dia»), ambos con `margin-top: -8px`.
 */
fun Modifier.negativeTopMargin(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}
