package org.braincards.app.presentation.screens.study

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
import kotlinx.datetime.Clock
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.ReviewQuality
import org.braincards.app.domain.models.StudyCardLabel
import org.braincards.app.domain.models.StudyQueue
import org.braincards.app.domain.models.StudyResults
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.domain.usecase.Flashcards.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardByIdUseCase
import org.braincards.app.domain.usecase.Flashcards.ResolveStudyLabelUseCase
import org.braincards.app.domain.usecase.Flashcards.ReviewFlashcardUseCase
import org.braincards.app.presentation.sync.AutoSyncPauseGate
import org.braincards.app.presentation.sync.PauseReason
import org.braincards.app.util.logError

sealed class StudyState {
    //data object: para un caso sin datos propios
    data object Loading : StudyState()
    data object Empty : StudyState()

    /** Fallo al leer las tarjetas pendientes o al avanzar la cola (la UI ofrece Reintentar). */
    data object Error : StudyState()

    //data classs: para un caso con datos propios, se acompaña de info concreta y
    //distinta cada vez que se usa
    /**
     * Tarjeta en pantalla. [remaining] (N) = tarjetas por revisar, la actual incluida; [total] (M) = tarjetas de la
     * sesion (fijo salvo que se salte una borrada). [isAnswerVisible] vive aqui para sobrevivir a la rotacion.
     */
    data class Reviewing(
        val currentCard: Flashcard,
        val label: StudyCardLabel?,
        val isAnswerVisible: Boolean = false,
        val remaining: Int,
        val total: Int
    ) : StudyState() {
        /** (M - N) / M: avanza solo cuando una tarjeta sale de la cola. */
        val progress: Float get() = if (total <= 0) 0f else (total - remaining).toFloat() / total
    }

    /** Fin de la sesion: pulsaciones por calidad (el total es su suma). */
    data class Finished(val results: StudyResults) : StudyState()
}

/** Mensajes de fallo general (Snackbar); el texto vive en recursos. */
enum class StudyMessage { RateFailed }

class StudyViewModel(
    private val getDueFlashcardsUseCase: GetDueFlashcardsUseCase,
    private val reviewFlashcardUseCase: ReviewFlashcardUseCase,
    private val getFlashcardByIdUseCase: GetFlashcardByIdUseCase,
    private val resolveStudyLabelUseCase: ResolveStudyLabelUseCase,
    private val syncPauseGate: AutoSyncPauseGate
) : ViewModel() {

    private val _state = MutableStateFlow<StudyState>(StudyState.Loading)
    val state: StateFlow<StudyState> = _state.asStateFlow()

    private val messages = Channel<StudyMessage>(Channel.BUFFERED)
    val messageEvent: Flow<StudyMessage> = messages.receiveAsFlow()

    private var studyQueue: StudyQueue? = null

    // Alcance de la sesion en curso: initSession es IDEMPOTENTE por alcance (rotar la pantalla vuelve a llamarlo y no
    // debe reiniciar la cola ni cambiar M).
    private var sessionKey: String? = null
    private var currentScope: StudyScope? = null

    private var isSubmitting: Boolean = false

    // Mientras dura la sesion de estudio la sync automatica esta en pausa (no se aplican pulls bajo los pies de una
    // cola que es una foto fija); caduca por inactividad y se renueva con cada tarjeta puntuada.
    private var pauseLease: AutoSyncPauseGate.Lease? = null

    private fun pauseAutoSync() {
        pauseLease?.release()
        pauseLease = syncPauseGate.acquire(PauseReason.STUDY)
    }

    private fun resumeAutoSync() {
        pauseLease?.release()
        pauseLease = null
    }

    override fun onCleared() {
        resumeAutoSync()
        super.onCleared()
    }

    /**
     * Empieza la sesion de [scope]. Si ya hay una sesion para ese mismo alcance (p. ej. la pantalla se recompone tras
     * rotar) no hace nada: ni vuelve a leer la cola ni toca los contadores.
     */
    fun initSession(scope: StudyScope) {
        val key = when (scope) {
            is StudyScope.All -> "all"
            is StudyScope.Deck -> "deck:${scope.deckId}"
        }
        if (sessionKey == key) return
        sessionKey = key
        currentScope = scope
        startSession(scope)
    }

    /** "Reintentar" tras un error: vuelve a leer la cola (lo ya puntuado no vuelve a salir: se guardo al instante). */
    fun retry() {
        val scope = currentScope ?: return
        startSession(scope)
    }

    private fun startSession(scope: StudyScope) {
        pauseAutoSync()
        viewModelScope.launch {
            _state.value = StudyState.Loading
            try {
                val dueCards = getDueFlashcardsUseCase(scope, Clock.System.now())
                studyQueue = StudyQueue(queue = dueCards)
                advance()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failSession("No se pudo cargar la sesion de estudio", e)
            }
        }
    }

    private fun failSession(context: String, e: Exception) {
        logError(context, e)
        _state.value = StudyState.Error
        resumeAutoSync()
    }

    /**
     * Muestra la siguiente tarjeta de la cola. Antes de mostrarla comprueba que SIGUE existiendo (getFlashcardById solo
     * devuelve las vivas): una tarjeta borrada mientras tanto se salta sin puntuarla y el total M baja en 1.
     */
    private suspend fun advance() {
        var queue = studyQueue ?: return
        while (true) {
            val head = queue.currentCard
            if (head == null) {
                finishOrEmpty(queue)
                return
            }
            val card = getFlashcardByIdUseCase(head.id)
            if (card == null) {
                queue = queue.skipCurrent()
                studyQueue = queue
                continue
            }
            _state.value = StudyState.Reviewing(
                currentCard = card,
                label = resolveStudyLabelUseCase(card),
                isAnswerVisible = false,
                remaining = queue.remaining,
                total = queue.total
            )
            return
        }
    }

    private fun finishOrEmpty(queue: StudyQueue) {
        // Cola agotada: con alguna pulsacion es el final de la sesion; si no (todas borradas), no habia nada que estudiar.
        _state.value = if (queue.totalReviews > 0) StudyState.Finished(queue.results()) else StudyState.Empty
        resumeAutoSync()
    }

    fun showAnswer() {
        val currentState = _state.value
        if (currentState is StudyState.Reviewing) {
            _state.value = currentState.copy(isAnswerVisible = true)
        }
    }

    /**
     * Puntua la tarjeta en pantalla (solo con la respuesta visible). El repaso se GUARDA primero; solo si sale bien entra en los contadores y la cola
     * avanza. Si falla al guardar: la tarjeta se queda en pantalla, la cola no avanza, el repaso no se cuenta y llega un
     * [StudyMessage.RateFailed] (se puede volver a puntuar).
     */
    fun rateCard(quality: ReviewQuality) {
        //usamos el valor de la variable antes de la llamada
        if (isSubmitting) return //puntuación en curso, ignora pulsación

        val currentState = _state.value as? StudyState.Reviewing ?: return
        // Segunda barrera (la primera es la UI/el teclado): no se puntua una tarjeta cuya respuesta no se ha visto.
        if (!currentState.isAnswerVisible) return
        val card = currentState.currentCard
        isSubmitting = true
        pauseLease?.renew()

        viewModelScope.launch {
            try {
                try {
                    reviewFlashcardUseCase(card, quality)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Fallo de disco / base de datos: no se cuenta ni se avanza; se puede reintentar.
                    logError("No se pudo guardar el repaso", e)
                    messages.trySend(StudyMessage.RateFailed)
                    return@launch
                }

                // Guardado: AHORA cuenta y la cola avanza.
                studyQueue = studyQueue?.submitResult(card, quality)
                try {
                    advance()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failSession("No se pudo avanzar la sesion de estudio", e)
                }
            } finally {
                isSubmitting = false
            }
        }
    }
}
