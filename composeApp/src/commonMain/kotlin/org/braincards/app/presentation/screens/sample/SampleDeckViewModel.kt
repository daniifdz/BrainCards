package org.braincards.app.presentation.screens.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.braincards.app.domain.sample.SampleDeckNames
import org.braincards.app.domain.usecase.Decks.CreateSampleDeckUseCase
import org.braincards.app.domain.usecase.Decks.SampleDeckResult
import org.braincards.app.util.logError

/** Resultado de pulsar «Crear mazo de ejemplo». Se entrega una sola vez. */
sealed interface SampleDeckEvent {
    /** El mazo está listo (recién creado o ya existía): hay que abrirlo. */
    data class Ready(val deckId: String) : SampleDeckEvent

    /** Ya hay mazos (y ninguno es el de ejemplo): no se crea nada y se lleva al usuario a la Lista de mazos. */
    data object AlreadyHasDecks : SampleDeckEvent

    /** No se pudo crear: se avisa y el botón vuelve a reposo. */
    data object Failed : SampleDeckEvent
}

/**
 * Acción «Crear mazo de ejemplo», la misma desde Home (primer uso) y desde la Lista de mazos vacía.
 *
 * [creating] es `true` mientras se escribe: la UI deshabilita el botón y un segundo toque se ignora (no se duplica el mazo).
 * Cualquier fallo (disco, base de datos) se registra sin datos sensibles y llega como [SampleDeckEvent.Failed] sin tumbar la app.
 */
class SampleDeckViewModel(private val createSampleDeck: CreateSampleDeckUseCase) : ViewModel() {

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    private val _events = Channel<SampleDeckEvent>(Channel.BUFFERED)
    val events: Flow<SampleDeckEvent> = _events.receiveAsFlow()

    fun create(names: SampleDeckNames) {
        if (!_creating.compareAndSet(expect = false, update = true)) return // toque repetido: ya se está creando
        viewModelScope.launch {
            try {
                val event = when (val result = createSampleDeck(names)) {
                    is SampleDeckResult.Ready -> SampleDeckEvent.Ready(result.deckId)
                    SampleDeckResult.AlreadyHasDecks -> SampleDeckEvent.AlreadyHasDecks
                }
                _events.trySend(event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("No se pudo crear el mazo de ejemplo", e)
                _events.trySend(SampleDeckEvent.Failed)
            } finally {
                _creating.value = false
            }
        }
    }
}
