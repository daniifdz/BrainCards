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

/** Funcion pura: sesion + sync en curso + ultimo resultado -> SyncStatus. */
class DeriveSyncStatusTest {

    private val now = Instant.parse("2026-10-28T10:00:00Z")
    private val earlier = Instant.parse("2026-10-27T10:00:00Z")
    private val confirmed = AuthState.Authenticated("u", "a@b.c", sessionConfirmed = true)
    private val unconfirmed = AuthState.Authenticated("u", "a@b.c", sessionConfirmed = false)

    private val ok = PullTableResult(applied = 0, aborted = false)
    private fun aborted(f: PullFailure?) = PullTableResult(applied = 0, aborted = true, failure = f)
    private val pushOk = PushTableResult(succeeded = 0, failed = 0)

    private fun completed(
        decks: PullTableResult = ok,
        topics: PullTableResult = ok,
        pushDecks: PushTableResult = pushOk
    ) = SyncResult.Completed(
        pull = PullResult(decks, topics, ok, ok),
        push = PushResult(pushDecks, pushOk, pushOk, pushOk)
    )

    private fun status(
        auth: AuthState = confirmed,
        syncing: Boolean = false,
        result: SyncResult? = completed(),
        hasOutcome: Boolean = true,
        lastSuccess: Instant? = null
    ) = deriveSyncStatus(auth, syncing, if (hasOutcome) SyncOutcome(result, now) else null, lastSuccess)

    @Test
    fun sin_sesion_o_cargando_es_SignedOut() {
        assertEquals(SyncStatus.SignedOut, status(auth = AuthState.NotAuthenticated))
        assertEquals(SyncStatus.SignedOut, status(auth = AuthState.Loading))
    }

    @Test
    fun sin_sesion_manda_sobre_un_sync_en_curso() {
        assertEquals(SyncStatus.SignedOut, status(auth = AuthState.NotAuthenticated, syncing = true))
    }

    @Test
    fun sesion_sin_confirmar_es_Offline_salvo_que_este_sincronizando() {
        assertEquals(SyncStatus.Offline, status(auth = unconfirmed))
        assertEquals(SyncStatus.Syncing, status(auth = unconfirmed, syncing = true))
    }

    @Test
    fun sincronizando_es_Syncing_aunque_haya_un_resultado_previo() {
        assertEquals(SyncStatus.Syncing, status(syncing = true))
    }

    @Test
    fun un_resultado_correcto_es_Synced_con_la_hora_del_sync() {
        assertEquals(SyncStatus.Synced(now), status(lastSuccess = earlier))
    }

    @Test
    fun sin_resultado_pero_con_ultima_sync_correcta_es_Synced_con_esa_hora() {
        assertEquals(SyncStatus.Synced(earlier), status(hasOutcome = false, lastSuccess = earlier))
    }

    @Test
    fun sin_resultado_ni_ultima_sync_con_sesion_confirmada_es_Syncing() {
        assertEquals(SyncStatus.Syncing, status(hasOutcome = false))
    }

    @Test
    fun pull_abortado_por_red_es_Offline() {
        assertEquals(SyncStatus.Offline, status(result = completed(decks = aborted(PullFailure.NETWORK))))
        assertEquals(SyncStatus.Offline, status(result = completed(decks = aborted(null))))
    }

    @Test
    fun pull_rechazado_es_Error_REJECTED() {
        assertEquals(
            SyncStatus.Error(SyncErrorKind.REJECTED),
            status(result = completed(decks = aborted(PullFailure.REJECTED)))
        )
    }

    @Test
    fun pull_con_fallo_local_es_Error_LOCAL() {
        assertEquals(
            SyncStatus.Error(SyncErrorKind.LOCAL),
            status(result = completed(decks = aborted(PullFailure.LOCAL)))
        )
    }

    @Test
    fun la_causa_mas_grave_manda_LOCAL_sobre_REJECTED_sobre_red() {
        assertEquals(
            SyncStatus.Error(SyncErrorKind.LOCAL),
            status(result = completed(decks = aborted(PullFailure.NETWORK), topics = aborted(PullFailure.LOCAL)))
        )
        assertEquals(
            SyncStatus.Error(SyncErrorKind.REJECTED),
            status(result = completed(decks = aborted(PullFailure.NETWORK), topics = aborted(PullFailure.REJECTED)))
        )
    }

    @Test
    fun push_parcial_es_Error_PUSH_PARTIAL() {
        assertEquals(
            SyncStatus.Error(SyncErrorKind.PUSH_PARTIAL),
            status(result = completed(pushDecks = PushTableResult(succeeded = 1, failed = 2)))
        )
    }

    @Test
    fun pull_y_push_fallidos_a_la_vez_es_Error_FAILED() {
        assertEquals(
            SyncStatus.Error(SyncErrorKind.FAILED),
            status(result = completed(decks = aborted(PullFailure.NETWORK), pushDecks = PushTableResult(0, 3)))
        )
    }

    @Test
    fun una_excepcion_inesperada_del_motor_es_Error_FAILED() {
        assertEquals(SyncStatus.Error(SyncErrorKind.FAILED), status(result = null))
    }

    @Test
    fun un_resultado_NotAuthenticated_es_SignedOut() {
        assertEquals(SyncStatus.SignedOut, status(result = SyncResult.NotAuthenticated))
    }
}
