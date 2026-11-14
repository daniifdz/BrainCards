package org.braincards.app.presentation.components.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Codigo de [length] digitos en casillas. Un unico campo oculto lo gestiona todo: teclado numerico, borrar,
 * y PEGAR (se filtran los digitos y se recortan a [length], asi "123 456" o "123-456" funcionan).
 *
 *  - [onComplete]: se llama al ESCRIBIR el ultimo digito (llegar a [length]); no se usa para enviar por defecto.
 *  - [onSubmit]: accion Done / Go del teclado en pantalla (el mismo codigo que el boton principal).
 *  - Con [imeAction] = Next, la accion del teclado pasa al campo siguiente.
 *
 * Las casillas se pintan con [isError] (borde de error). El texto de error lo pone quien lo usa debajo
 * ([FormCaption]). El orden de foco y Enter en Desktop los pone [formField] sobre el `modifier`.
 */
@Composable
fun CodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onComplete: (() -> Unit)? = null,
    onSubmit: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(length)
            if (digits != value) {
                onValueChange(digits)
                if (digits.length == length) onComplete?.invoke()
            }
        },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { onSubmit?.invoke() },
            onGo = { onSubmit?.invoke() }
        ),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(length) { index ->
                    val focusedBox = enabled && index == value.length.coerceAtMost(length - 1)
                    val border = when {
                        isError -> scheme.error
                        focusedBox -> scheme.primary
                        else -> scheme.outlineVariant
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surface,
                        border = BorderStroke(if (isError || focusedBox) 2.dp else 1.dp, border),
                        modifier = Modifier.weight(1f).widthIn(max = 52.dp).height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = value.getOrNull(index)?.toString().orEmpty(),
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                                color = if (isError) scheme.error else scheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    )
}
