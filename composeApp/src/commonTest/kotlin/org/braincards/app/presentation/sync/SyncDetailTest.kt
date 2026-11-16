package org.braincards.app.presentation.sync

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncDetailTest {

    private val utc = TimeZone.UTC
    private val madrid = TimeZone.of("Europe/Madrid") // CET (UTC+1) a finales de octubre de 2026

    private class Case(val name: String, val at: String, val now: String, val zone: TimeZone, val expected: LastSyncLabel)

    @Test
    fun formatLastSync_tabla_con_bordes() {
        val cases = listOf(
            Case("mismo instante", "2026-10-28T14:32:00Z", "2026-10-28T14:32:00Z", utc, LastSyncLabel.Today("14:32")),
            Case("hoy, horas antes", "2026-10-28T02:05:00Z", "2026-10-28T23:00:00Z", utc, LastSyncLabel.Today("02:05")),
            Case("00:00 exacto es hoy", "2026-10-29T00:00:00Z", "2026-10-29T10:00:00Z", utc, LastSyncLabel.Today("00:00")),
            Case("23:59 vs 00:01 = ayer (solo 2 min)", "2026-10-28T23:59:00Z", "2026-10-29T00:01:00Z", utc, LastSyncLabel.Yesterday("23:59")),
            Case("casi 48 h pero dia civil ayer", "2026-10-28T00:00:00Z", "2026-10-29T23:59:00Z", utc, LastSyncLabel.Yesterday("00:00")),
            Case("hace 25 h = ayer", "2026-10-28T10:00:00Z", "2026-10-29T11:00:00Z", utc, LastSyncLabel.Yesterday("10:00")),
            Case("hace 2 dias = fecha", "2026-10-27T14:32:00Z", "2026-10-29T10:00:00Z", utc, LastSyncLabel.Date(27, 10, "14:32")),
            Case("cruce de mes: ayer", "2026-09-30T12:00:00Z", "2026-10-01T12:00:00Z", utc, LastSyncLabel.Yesterday("12:00")),
            Case("cruce de ano: ayer", "2026-12-31T23:30:00Z", "2027-01-01T00:30:00Z", utc, LastSyncLabel.Yesterday("23:30")),
            Case("fecha de 3 oct", "2026-10-03T14:32:00Z", "2026-10-28T10:00:00Z", utc, LastSyncLabel.Date(3, 10, "14:32")),
            Case("hora con cero a la izquierda", "2026-10-28T09:05:00Z", "2026-10-28T10:00:00Z", utc, LastSyncLabel.Today("09:05")),
            // Misma pareja de instantes: en UTC es ayer; en Madrid (+1) los dos caen el 29 -> hoy.
            Case("zona UTC", "2026-10-28T23:30:00Z", "2026-10-29T00:30:00Z", utc, LastSyncLabel.Yesterday("23:30")),
            Case("zona Madrid, mismo par = hoy", "2026-10-28T23:30:00Z", "2026-10-29T00:30:00Z", madrid, LastSyncLabel.Today("00:30")),
            Case("zona Madrid, medianoche local", "2026-10-28T22:59:00Z", "2026-10-28T23:01:00Z", madrid, LastSyncLabel.Yesterday("23:59")),
            Case("instante futuro (reloj desajustado) = hoy", "2026-10-29T12:00:00Z", "2026-10-28T12:00:00Z", utc, LastSyncLabel.Today("12:00"))
        )
        for (c in cases) {
            assertEquals(c.expected, formatLastSync(Instant.parse(c.at), Instant.parse(c.now), c.zone), c.name)
        }
    }

    private val now = Instant.parse("2026-10-28T15:00:00Z")
    private fun detail(ind: SyncIndicator?, pending: Int = 0) = syncDetail(ind, pending, now, utc)

    @Test
    fun invitado_unknown_y_sin_estado_no_se_dibujan() {
        assertNull(detail(SyncIndicator.Guest))
        assertNull(detail(SyncIndicator.Unknown))
        assertNull(detail(null))
    }

    @Test
    fun sincronizando_sin_contador_ni_reintentar() {
        assertEquals(SyncDetail(SyncDetailState.Syncing), detail(SyncIndicator.Syncing, pending = 5))
    }

    @Test
    fun sincronizado_con_ultima_vez_y_contador_solo_si_queda_alguno() {
        val at = Instant.parse("2026-10-28T14:32:00Z")
        assertEquals(
            SyncDetail(SyncDetailState.Synced, lastSync = LastSyncLabel.Today("14:32"), pending = null),
            detail(SyncIndicator.Synced(at), pending = 0)
        )
        assertEquals(3, detail(SyncIndicator.Synced(at), pending = 3)!!.pending)
    }

    @Test
    fun nunca_sincronizado_sin_contador() {
        assertEquals(SyncDetail(SyncDetailState.NeverSynced), detail(SyncIndicator.NeverSynced, pending = 4))
    }

    @Test
    fun sin_conexion_lleva_contador_incluso_a_cero() {
        assertEquals(SyncDetail(SyncDetailState.Offline, pending = 0), detail(SyncIndicator.Offline, 0))
        assertEquals(SyncDetail(SyncDetailState.Offline, pending = 7), detail(SyncIndicator.Offline, 7))
    }

    @Test
    fun los_cuatro_errores_llevan_contador_y_reintentar() {
        val expected = mapOf(
            SyncErrorKind.LOCAL to SyncDetailState.ErrorLocal,
            SyncErrorKind.REJECTED to SyncDetailState.ErrorRejected,
            SyncErrorKind.PUSH_PARTIAL to SyncDetailState.ErrorPushPartial,
            SyncErrorKind.FAILED to SyncDetailState.ErrorFailed
        )
        for ((kind, state) in expected) {
            assertEquals(SyncDetail(state, pending = 2, canRetry = true), detail(SyncIndicator.Error(kind), 2), kind.name)
        }
    }
}
