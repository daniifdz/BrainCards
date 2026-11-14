package org.braincards.app.presentation.sync

import kotlinx.datetime.Instant
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.usecase.Sync.PullFailure
import org.braincards.app.domain.usecase.Sync.PullResult
import org.braincards.app.domain.usecase.Sync.PullTableResult
import org.braincards.app.domain.usecase.Sync.PushResult
import org.braincards.app.domain.usecase.Sync.PushTableResult
import org.braincards.app.domain.usecase.Sync.SyncResult
import kotlin.test.Test
import kotlin.test.assertEquals

/** Funcion pura: los 9 estados del indicador y su prioridad (+ Unknown mientras no se lee la ultima sync). */
class SyncIndicatorDeriveTest {

    private val now = Instant.parse("2026-10-28T10:00:00Z")
    private val earlier = Instant.parse("2026-10-27T10:00:00Z")
    private val confirmed = AuthState.Authenticated("u", "a@b.c", sessionConfirmed = true)
    private val unconfirmed = AuthState.Authenticated("u", "a@b.c", sessionConfirmed = false)

    private val ok = PullTableResult(applied = 0, aborted = false)
    private fun aborted(f: PullFailure?) = PullTableResult(applied = 0, aborted = true, failure = f)
    private val pushOk = PushTableResult(succeeded = 0, failed = 0)

    private fun completed(
        decks: PullTableResult = ok,
        pushDecks: PushTableResult = pushOk
    ) = SyncResult.Completed(
        pull = PullResult(decks, ok, ok, ok),
        push = PushResult(pushDecks, pushOk, pushOk, pushOk)
    )

    private val rejected get() = completed(decks = aborted(PullFailure.REJECTED))
    private val localFailure get() = completed(decks = aborted(PullFailure.LOCAL))
    private val partial get() = completed(pushDecks = PushTableResult(succeeded = 1, failed = 2))
    private val general get() = completed(decks = aborted(PullFailure.NETWORK), pushDecks = PushTableResult(0, 3))
    private val networkPull get() = completed(decks = aborted(PullFailure.NETWORK))

    private fun indicator(
        auth: AuthState = confirmed,
        online: Boolean = true,
        syncing: Boolean = false,
        result: SyncResult? = completed(),
        hasOutcome: Boolean = false,
        lastSuccess: Instant? = earlier,
        loaded: Boolean = true
    ) = deriveSyncIndicator(
        auth, online, syncing, if (hasOutcome) SyncOutcome(result, now) else null, lastSuccess, loaded
    )

    // --- regla 1: sin sesion -> Guest ------------------------------------------------------------------------------

    @Test
    fun sin_sesion_es_Guest() {
        assertEquals(SyncIndicator.Guest, indicator(auth = AuthState.NotAuthenticated))
    }

    @Test
    fun sin_sesion_manda_sobre_todo_lo_demas() {
        assertEquals(
            SyncIndicator.Guest,
            indicator(
                auth = AuthState.NotAuthenticated, online = false, syncing = true,
                result = rejected, hasOutcome = true, loaded = false
            )
        )
    }

    // --- regla 2: sesion sin confirmar o sin conectividad -> Offline ------------------------------------------------

    @Test
    fun sesion_sin_confirmar_es_Offline() {
        assertEquals(SyncIndicator.Offline, indicator(auth = unconfirmed))
    }

    @Test
    fun sin_conectividad_es_Offline() {
        assertEquals(SyncIndicator.Offline, indicator(online = false))
    }

    @Test
    fun sin_conexion_manda_sobre_sincronizando() {
        assertEquals(SyncIndicator.Offline, indicator(online = false, syncing = true))
        assertEquals(SyncIndicator.Offline, indicator(auth = unconfirmed, syncing = true))
    }

    @Test
    fun sin_conexion_manda_sobre_un_ultimo_resultado_en_error() {
        assertEquals(SyncIndicator.Offline, indicator(online = false, result = rejected, hasOutcome = true))
        assertEquals(SyncIndicator.Offline, indicator(auth = unconfirmed, result = localFailure, hasOutcome = true))
    }

    // --- regla 3: sincronizando -------------------------------------------------------------------------------------

    @Test
    fun sincronizando_es_Syncing() {
        assertEquals(SyncIndicator.Syncing, indicator(syncing = true))
    }

    @Test
    fun sincronizando_manda_sobre_un_error_previo() {
        assertEquals(SyncIndicator.Syncing, indicator(syncing = true, result = general, hasOutcome = true))
    }

    // --- regla 4: error --------------------------------------------------------------------------------------------

    @Test
    fun los_cuatro_tipos_de_error() {
        assertEquals(SyncIndicator.Error(SyncErrorKind.LOCAL), indicator(result = localFailure, hasOutcome = true))
        assertEquals(SyncIndicator.Error(SyncErrorKind.REJECTED), indicator(result = rejected, hasOutcome = true))
        assertEquals(SyncIndicator.Error(SyncErrorKind.PUSH_PARTIAL), indicator(result = partial, hasOutcome = true))
        assertEquals(SyncIndicator.Error(SyncErrorKind.FAILED), indicator(result = general, hasOutcome = true))
    }

    @Test
    fun una_excepcion_inesperada_del_motor_es_Error_FAILED() {
        assertEquals(SyncIndicator.Error(SyncErrorKind.FAILED), indicator(result = null, hasOutcome = true))
    }

    @Test
    fun un_fallo_de_red_del_pull_con_conectividad_es_Offline() {
        assertEquals(SyncIndicator.Offline, indicator(result = networkPull, hasOutcome = true))
    }

    @Test
    fun el_error_no_espera_a_la_lectura_de_la_ultima_sync() {
        assertEquals(
            SyncIndicator.Error(SyncErrorKind.REJECTED),
            indicator(result = rejected, hasOutcome = true, lastSuccess = null, loaded = false)
        )
    }

    @Test
    fun un_resultado_correcto_no_es_un_error() {
        assertEquals(SyncIndicator.Synced(earlier), indicator(result = completed(), hasOutcome = true))
    }

    // --- reglas 5 y 6: Unknown / NeverSynced / Synced ---------------------------------------------------------------

    @Test
    fun lectura_sin_terminar_es_Unknown() {
        assertEquals(SyncIndicator.Unknown, indicator(lastSuccess = null, loaded = false))
    }

    @Test
    fun lectura_sin_terminar_es_Unknown_tambien_con_valor_ya_presente() {
        assertEquals(SyncIndicator.Unknown, indicator(lastSuccess = earlier, loaded = false))
    }

    @Test
    fun lectura_terminada_sin_ninguna_sync_correcta_es_NeverSynced() {
        assertEquals(SyncIndicator.NeverSynced, indicator(lastSuccess = null, loaded = true))
    }

    @Test
    fun lectura_terminada_con_sync_correcta_es_Synced_con_su_hora() {
        assertEquals(SyncIndicator.Synced(earlier), indicator(lastSuccess = earlier, loaded = true))
    }

    @Test
    fun la_hora_de_Synced_es_la_de_la_ultima_sync_correcta_no_la_del_ultimo_resultado() {
        assertEquals(SyncIndicator.Synced(earlier), indicator(result = completed(), hasOutcome = true, lastSuccess = earlier))
    }

    // --- las reglas 1-4 no esperan a la lectura --------------------------------------------------------------------

    @Test
    fun invitado_offline_y_syncing_se_resuelven_sin_esperar_a_la_lectura() {
        assertEquals(SyncIndicator.Guest, indicator(auth = AuthState.NotAuthenticated, loaded = false))
        assertEquals(SyncIndicator.Offline, indicator(online = false, loaded = false))
        assertEquals(SyncIndicator.Syncing, indicator(syncing = true, loaded = false))
    }
}
