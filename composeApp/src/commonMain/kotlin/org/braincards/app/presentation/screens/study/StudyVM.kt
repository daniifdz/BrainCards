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
import org.braincards.app.domain.models.StudyMode
import org.braincards.app.domain.models.StudyQueue
import org.braincards.app.domain.models.StudyResults
import org.braincards.app.domain.models.StudyScope
import org.braincards.app.domain.usecase.Flashcards.FreeRecorder
import org.braincards.app.domain.usecase.Flashcards.GetDueFlashcardsUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFlashcardByIdUseCase
import org.braincards.app.domain.usecase.Flashcards.GetFreeReviewCardsUseCase
import org.braincards.app.domain.usecase.Flashcards.RatingRecorder
import org.braincards.app.domain.usecase.Flashcards.ResolveStudyLabelUseCase
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
        val total: Int,
        /** Repaso libre: la sesión no guarda nada. La UI lo usa para el chip y el aviso. */
        val freeReview: Boolean = false
    ) : StudyState() {
        /** (M - N) / M: avanza solo cuando una tarjeta sale de la cola. */
        val progress: Float get() = if (total <= 0) 0f else (total - remaining).toFloat() / total
    }

    /**
     * Fin de la sesion: pulsaciones por calidad (el total es su suma) y la [label] de Resultados (nombre del mazo o
     * "Estudiar Todo"; null si el nombre del mazo no se pudo resolver).
     */
    data class Finished(val results: StudyResults, val label: StudyResultsLabel?, val freeReview: Boolean = false) : StudyState()
}

/** Mensajes de fallo general (Snackbar); el texto vive en recursos. */
enum class StudyMessage { RateFailed }

/**
 * Sesion de estudio. Dos modos (ver [StudyMode]): la normal (pendientes SM-2, cada repaso se guarda) y el REPASO LIBRE (todas las tarjetas
 * barajadas, NUNCA se guarda nada). Lo unico que puede persistir una puntuacion es un [RatingRecorder]: [scheduledRecorder] en la
 * normal y [FreeRecorder] (no hace nada) en el libre; este ViewModel no conoce el caso de uso que guarda el repaso.
 */
class StudyViewModel(
    private val getDueFlashcardsUseCase: GetDueFlashcardsUseCase,
    private val getFreeReviewCardsUseCase: GetFreeReviewCardsUseCase,
    private val scheduledRecorder: RatingRecorder,
    private val getFlashcardByIdUseCase: GetFlashcardByIdUseCase,
    private val resolveStudyLabelUseCase: ResolveStudyLabelUseCase,
    private val syncPauseGate: AutoSyncPauseGate
) : ViewModel() {

    private val _state = MutableStateFlow<StudyState>(StudyState.Loading)
    val state: StateFlow<StudyState> = _state.asStateFlow()

    private val messages = Channel<StudyMessage>(Channel.BUFFERED)
    val messageEvent: Flow<StudyMessage> = messages.receiveAsFlow()

    private var studyQueue: StudyQueue? = null

    // Repaso libre sin ninguna tarjeta (al cargar, o todas borradas durante la sesion sin haber puntuado ninguna): la UI vuelve al
    // origen (popBackStackSafely), sin pantalla ni texto nuevo. En la sesion normal sigue existiendo el estado Empty.
    private val exits = Channel<Unit>(Channel.CONFLATED)
    val exitEvent: Flow<Unit> = exits.receiveAsFlow()

    private var currentMode: StudyMode = StudyMode.Scheduled
    private val isFree: Boolean get() = currentMode is StudyMode.Free

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
    fun initSession(scope: StudyScope, mode: StudyMode = StudyMode.Scheduled) {
        val scopeKey = when (scope) {
            is StudyScope.All -> "all"
            is StudyScope.Deck -> "deck:${scope.deckId}"
        }
        // La clave incluye modo Y semilla: rotar (misma clave) no reinicia ni reordena; otra semilla u otro modo es OTRA sesion.
        val modeKey = when (mode) {
            StudyMode.Scheduled -> "scheduled"
            is StudyMode.Free -> "free:${mode.seed}"
        }
        val key = "$scopeKey|$modeKey"
        if (sessionKey == key) return
        sessionKey = key
        currentScope = scope
        currentMode = mode
        startSession(scope, mode)
    }

    /** "Reintentar" tras un error: vuelve a leer la cola (lo ya puntuado no vuelve a salir: se guardo al instante). */
    fun retry() {
        val scope = currentScope ?: return
        startSession(scope, currentMode)
    }

    private fun startSession(scope: StudyScope, mode: StudyMode) {
        // Repaso libre: NO se pausa la sync automatica (no se escribe nada y cada tarjeta se relee por id, asi que un pull no deja la
        // cola desfasada). La sesion normal si la pausa.
        if (mode is StudyMode.Scheduled) pauseAutoSync()
        viewModelScope.launch {
            _state.value = StudyState.Loading
            try {
                val cards = when (mode) {
                    StudyMode.Scheduled -> getDueFlashcardsUseCase(scope, Clock.System.now())
                    is StudyMode.Free -> getFreeReviewCardsUseCase(scope, mode.seed)
                }
                studyQueue = StudyQueue(queue = cards)
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
                total = queue.total,
                freeReview = isFree
            )
            return
        }
    }

    private suspend fun finishOrEmpty(queue: StudyQueue) {
        // Cola agotada: con alguna pulsacion es el final de la sesion; si no (todas borradas), no habia nada que estudiar.
        if (queue.totalReviews > 0) {
            _state.value = StudyState.Finished(queue.results(), resultsLabelFor(currentScope), freeReview = isFree)
        } else if (isFree) {
            // Repaso libre sin tarjetas: no hay pantalla de "al dia" (no aplica); se vuelve al origen.
            exits.trySend(Unit)
        } else {
            _state.value = StudyState.Empty
        }
        resumeAutoSync()
    }

    /** Etiqueta de Resultados: "Estudiar Todo" (global) o el nombre del mazo; un fallo al leerlo no impide mostrar Resultados. */
    private suspend fun resultsLabelFor(scope: StudyScope?): StudyResultsLabel? {
        if (scope == null) return null
        val deckName = when (scope) {
            is StudyScope.All -> null
            is StudyScope.Deck -> try {
                resolveStudyLabelUseCase.deckName(scope.deckId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("No se pudo leer el nombre del mazo", e)
                null
            }
        }
        return resultsLabel(scope, deckName)
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
        if (isSubmitting) return //puntuación en curso, ignora pulsación

        val currentState = _state.value as? StudyState.Reviewing ?: return
        // Segunda barrera (la primera es la UI/el teclado): no se puntua una tarjeta cuya respuesta no se ha visto.
        if (!currentState.isAnswerVisible) return
        val card = currentState.currentCard
        // Lo unico que puede persistir: el recorder del modo (el libre no hace nada).
        val recorder: RatingRecorder = if (isFree) FreeRecorder else scheduledRecorder
        isSubmitting = true
        pauseLease?.renew()

        viewModelScope.launch {
            try {
                try {
                    recorder.record(card, quality)
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
