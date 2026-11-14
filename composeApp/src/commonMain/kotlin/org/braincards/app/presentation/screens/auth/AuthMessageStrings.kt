package org.braincards.app.presentation.screens.auth

import androidx.compose.runtime.Composable
import braincards.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Traduccion de cada clave de error de auth a su texto de strings.xml (la capa pura no resuelve recursos). */
private fun AuthMessage.resource(): StringResource = when (this) {
    AuthMessage.CodeInvalidOrExpired -> Res.string.auth_msg_code_invalid
    AuthMessage.WeakPassword -> Res.string.auth_msg_weak_password
    AuthMessage.TooManyAttempts -> Res.string.auth_msg_too_many_attempts
    AuthMessage.InvalidCredentials -> Res.string.auth_msg_invalid_credentials
    AuthMessage.EmailNotConfirmed -> Res.string.auth_msg_email_not_confirmed
    AuthMessage.NetworkError -> Res.string.auth_msg_network
    AuthMessage.GenericError -> Res.string.auth_msg_generic
    AuthMessage.EmailEmpty -> Res.string.auth_msg_email_empty
    AuthMessage.EmailInvalid -> Res.string.auth_msg_email_invalid
    AuthMessage.PasswordEmpty -> Res.string.auth_msg_password_empty
    AuthMessage.PasswordTooShort -> Res.string.auth_msg_password_short
    AuthMessage.PasswordsDontMatch -> Res.string.auth_msg_passwords_dont_match
    AuthMessage.CodeIncomplete -> Res.string.auth_msg_code_incomplete
}

@Composable
fun AuthMessage.text(): String = stringResource(resource())

@Composable
fun AuthNotice.text(): String = stringResource(
    when (this) {
        AuthNotice.CodeResent -> Res.string.auth_notice_code_resent
    }
)
