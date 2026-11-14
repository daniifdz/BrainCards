package org.braincards.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.usecase.Decks.GetDeckUseCase
import org.braincards.app.util.logError

/**
 * Dato de la nota de fusion de Bienvenida («Tus mazos actuales se guardaran en tu cuenta…»): hay datos locales si
 * existe algun mazo no borrado SIN dueño (userId null): solo esos se anadiran a la cuenta al iniciar sesion; los de
 * otra cuenta (userId con valor) no se adoptan. Sale de GetDeckUseCase, sin codigo de datos nuevo ni cambios en los repositorios.
 * En esta pantalla (sin sesion) todo dato local es de invitado: el cierre de sesion vacia la BD.
 *
 * `null` = aun no se sabe (no se muestra la nota). Un fallo de lectura cuenta como "no hay" (sin nota).
 */
class WelcomeViewModel(getDecks: GetDeckUseCase) : ViewModel() {

    val hasLocalData: StateFlow<Boolean?> = getDecks()
        .map<List<Deck>, Boolean?> { decks -> decks.any { it.userId == null } }
        .distinctUntilChanged()
        .catch { e ->
            logError("No se pudo comprobar si hay mazos locales", e)
            emit(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
