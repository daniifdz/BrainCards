package org.braincards.app.presentation.screens.auth

import org.braincards.app.domain.usecase.Sync.PullFailure
import org.braincards.app.domain.usecase.Sync.PullResult
import org.braincards.app.domain.usecase.Sync.PullTableResult
import org.braincards.app.domain.usecase.Sync.PushResult
import org.braincards.app.domain.usecase.Sync.PushTableResult
import org.braincards.app.domain.usecase.Sync.SyncResult
import kotlin.test.Test
import kotlin.test.assertEquals

/** Mapeo puro SyncResult -> SyncUserMessage (el texto de cada caso vive en strings.xml). */
class SyncUserMessageTest {

    private val ok = PullTableResult(applied = 3, aborted = false)
    private fun aborted(failure: PullFailure?) = PullTableResult(applied = 0, aborted = true, failure = failure)

    private val pushOk = PushTableResult(succeeded = 1, failed = 0)
    private val pushFailed = PushTableResult(succeeded = 0, failed = 2)

    private fun completed(
        decks: PullTableResult = ok,
        topics: PullTableResult = ok,
        flashcards: PullTableResult = ok,
        scheduling: PullTableResult = ok,
        pushDecks: PushTableResult = pushOk,
        pushScheduling: PushTableResult = pushOk
    ) = SyncResult.Completed(
        pull = PullResult(decks, topics, flashcards, scheduling),
        push = PushResult(pushDecks, pushOk, pushOk, pushScheduling)
    )

    @Test
    fun sin_sesion_pide_iniciar_sesion() {
        assertEquals(SyncUserMessage.NotAuthenticated, SyncResult.NotAuthenticated.toSyncUserMessage())
    }

    @Test
    fun todo_bien_es_sincronizado() {
        assertEquals(SyncUserMessage.Synced, completed().toSyncUserMessage())
    }

    @Test
    fun un_pull_con_filas_descartadas_sigue_siendo_sincronizado() {
        val withDiscards = PullTableResult(applied = 3, aborted = false, discarded = 9)

        assertEquals(SyncUserMessage.Synced, completed(flashcards = withDiscards).toSyncUserMessage())
    }

    // --- pull abortado: un mensaje por causa ---

    @Test
    fun pull_abortado_por_red_da_el_mensaje_de_red() {
        assertEquals(SyncUserMessage.PullNetwork, completed(topics = aborted(PullFailure.NETWORK)).toSyncUserMessage())
    }

    @Test
    fun pull_abortado_sin_clasificar_se_trata_como_red() {
        assertEquals(SyncUserMessage.PullNetwork, completed(topics = aborted(null)).toSyncUserMessage())
    }

    @Test
    fun pull_abortado_por_rechazo_del_servidor_da_el_mensaje_de_rechazo() {
        assertEquals(SyncUserMessage.PullRejected, completed(decks = aborted(PullFailure.REJECTED)).toSyncUserMessage())
    }

    @Test
    fun pull_abortado_por_fallo_local_da_el_mensaje_local() {
        assertEquals(SyncUserMessage.PullLocal, completed(flashcards = aborted(PullFailure.LOCAL)).toSyncUserMessage())
    }

    // --- prioridad cuando varias tablas fallan por causas distintas: LOCAL > REJECTED > NETWORK ---

    @Test
    fun local_manda_sobre_rechazo_y_red() {
        val result = completed(
            decks = aborted(PullFailure.NETWORK),
            topics = aborted(PullFailure.REJECTED),
            flashcards = aborted(PullFailure.LOCAL)
        )

        assertEquals(SyncUserMessage.PullLocal, result.toSyncUserMessage())
    }

    @Test
    fun rechazo_manda_sobre_red() {
        val result = completed(decks = aborted(PullFailure.NETWORK), topics = aborted(PullFailure.REJECTED))

        assertEquals(SyncUserMessage.PullRejected, result.toSyncUserMessage())
    }

    @Test
    fun una_tabla_no_abortada_no_aporta_su_causa() {
        // failure solo cuenta en tablas abortadas: una tabla correcta con failure no nulo se ignora.
        val result = completed(
            decks = PullTableResult(applied = 1, aborted = false, failure = PullFailure.LOCAL),
            topics = aborted(PullFailure.NETWORK)
        )

        assertEquals(SyncUserMessage.PullNetwork, result.toSyncUserMessage())
    }

    // --- push ---

    @Test
    fun push_parcial_lleva_el_numero_de_filas_pendientes() {
        assertEquals(SyncUserMessage.PushPartial(4), completed(pushDecks = pushFailed, pushScheduling = pushFailed).toSyncUserMessage())
    }

    @Test
    fun pull_y_push_fallidos_a_la_vez_dan_el_mensaje_combinado() {
        val result = completed(flashcards = aborted(PullFailure.LOCAL), pushDecks = pushFailed)

        assertEquals(SyncUserMessage.SyncFailed, result.toSyncUserMessage())
    }
}
