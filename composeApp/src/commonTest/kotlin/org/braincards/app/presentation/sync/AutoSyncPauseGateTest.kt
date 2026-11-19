package org.braincards.app.presentation.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/** Puerta de pausa de la sync automatica: recuento de referencias, motivos y caducidad por inactividad (reloj falso). */
class AutoSyncPauseGateTest {

    private val time = TestTimeSource()
    private val gate = AutoSyncPauseGate(timeSource = time)

    @Test
    fun una_puerta_nueva_esta_abierta() {
        assertNull(gate.activeReason())
        assertFalse(gate.isPaused.value)
    }

    @Test
    fun un_lease_cierra_la_puerta_y_liberarlo_la_abre() {
        val lease = gate.acquire(PauseReason.STUDY)

        assertEquals(PauseReason.STUDY, gate.activeReason())
        assertTrue(gate.isPaused.value)

        lease.release()

        assertNull(gate.activeReason())
        assertFalse(gate.isPaused.value)
    }

    @Test
    fun con_dos_leases_del_mismo_motivo_la_puerta_sigue_cerrada_hasta_liberar_los_dos() {
        val a = gate.acquire(PauseReason.STUDY)
        val b = gate.acquire(PauseReason.STUDY)
        assertEquals(2, gate.activeCount(PauseReason.STUDY))

        a.release()
        assertEquals(PauseReason.STUDY, gate.activeReason())
        assertEquals(1, gate.activeCount(PauseReason.STUDY))

        b.release()
        assertNull(gate.activeReason())
    }

    @Test
    fun liberar_dos_veces_el_mismo_lease_no_libera_el_de_otro() {
        val a = gate.acquire(PauseReason.STUDY)
        val b = gate.acquire(PauseReason.STUDY)

        a.release()
        a.release()

        assertEquals(1, gate.activeCount(PauseReason.STUDY))
        assertEquals(PauseReason.STUDY, gate.activeReason())
        b.release()
    }

    @Test
    fun los_motivos_son_independientes_y_sign_out_manda_sobre_study() {
        val study = gate.acquire(PauseReason.STUDY)
        val signOut = gate.acquire(PauseReason.SIGN_OUT)

        assertEquals(PauseReason.SIGN_OUT, gate.activeReason())

        signOut.release()
        assertEquals(PauseReason.STUDY, gate.activeReason())

        study.release()
        assertNull(gate.activeReason())
    }

    @Test
    fun sign_out_caduca_a_su_propio_plazo_mas_corto_que_el_de_estudio() {
        assertTrue(SIGN_OUT_PAUSE_TTL < STUDY_PAUSE_INACTIVITY_TTL)
        gate.acquire(PauseReason.SIGN_OUT)
        gate.acquire(PauseReason.STUDY)

        time += SIGN_OUT_PAUSE_TTL - 1.seconds
        assertEquals(PauseReason.SIGN_OUT, gate.activeReason())

        time += 2.seconds
        assertEquals(PauseReason.STUDY, gate.activeReason(), "caducado SIGN_OUT, STUDY sigue")
    }

    @Test
    fun study_caduca_tras_su_plazo_de_inactividad() {
        gate.acquire(PauseReason.STUDY)

        time += STUDY_PAUSE_INACTIVITY_TTL - 1.seconds
        assertEquals(PauseReason.STUDY, gate.activeReason())

        time += 2.seconds
        assertNull(gate.activeReason())
        assertFalse(gate.isPaused.value)
    }

    @Test
    fun renovar_reinicia_la_inactividad_no_es_un_plazo_fijo() {
        val lease = gate.acquire(PauseReason.STUDY)

        // 3 x 20 min = 60 min en total: con un plazo fijo de 30 min habria caducado; renovando cada 20, no.
        repeat(3) {
            time += 20.minutes
            assertEquals(PauseReason.STUDY, gate.activeReason(), "tras ${(it + 1) * 20} min con renovaciones")
            lease.renew()
        }

        time += STUDY_PAUSE_INACTIVITY_TTL + 1.seconds
        assertNull(gate.activeReason(), "sin renovar mas, caduca por inactividad")
    }

    @Test
    fun un_lease_caducado_pero_no_liberado_se_reactiva_al_renovarlo() {
        val lease = gate.acquire(PauseReason.STUDY)
        time += STUDY_PAUSE_INACTIVITY_TTL + 1.minutes
        assertNull(gate.activeReason())

        lease.renew()

        assertEquals(PauseReason.STUDY, gate.activeReason())
    }

    @Test
    fun renovar_un_lease_ya_liberado_no_lo_reactiva() {
        val lease = gate.acquire(PauseReason.STUDY)
        lease.release()

        lease.renew()

        assertNull(gate.activeReason())
    }

    @Test
    fun un_lease_caducado_no_cuenta_en_el_recuento() {
        gate.acquire(PauseReason.STUDY)
        val vivo = gate.acquire(PauseReason.STUDY)
        time += STUDY_PAUSE_INACTIVITY_TTL - 1.minutes
        vivo.renew()
        time += 2.minutes // el primero lleva 31 min sin renovar; el segundo, 2

        assertEquals(1, gate.activeCount(PauseReason.STUDY))
    }
}
