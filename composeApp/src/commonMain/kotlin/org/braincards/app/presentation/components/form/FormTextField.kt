package org.braincards.app.presentation.components.form

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import braincards.composeapp.generated.resources.Res
import braincards.composeapp.generated.resources.form_hide_password
import braincards.composeapp.generated.resources.form_show_password
import org.jetbrains.compose.resources.stringResource

/**
 * Campo de formulario: OutlinedTextField de una linea con error por campo (`isError` + `supportingText`: el
 * [error] manda sobre el [hint]), accion del teclado en pantalla ([imeAction]) y, si [isPassword], boton de
 * mostrar/ocultar.
 *
 * Acciones del teclado: Next pasa al siguiente campo; Done / Go / Send / Search ejecutan [onImeAction] (el mismo
 * codigo que el boton principal) o, sin ella, solo cierran el teclado. El orden de foco y Enter en Desktop los
 * pone [formField] sobre el `modifier`.
 *
 * El estado del campo es de quien lo usa. Una contrasena NO debe guardarse con rememberSaveable.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    hint: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    isPassword: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    // Solo la visibilidad se recuerda al rotar; el valor es del llamador.
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val hidden = isPassword && !passwordVisible

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = (error ?: hint)?.let { text -> { Text(text) } },
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { onImeAction?.invoke() ?: focusManager.clearFocus() },
            onGo = { onImeAction?.invoke() },
            onSend = { onImeAction?.invoke() },
            onSearch = { onImeAction?.invoke() }
        ),
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = enabled) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(
                            if (passwordVisible) Res.string.form_hide_password else Res.string.form_show_password
                        )
                    )
                }
            }
        } else null
    )
}
