package org.braincards.app.presentation.screens.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.braincards.app.domain.repository.AutoSyncPreference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSyncSettingViewModelTest {

    /** Preferencia en memoria: la escritura puede fallar, quedarse esperando o cancelarse. */
    private class FakePreference(initial: Boolean = true) : AutoSyncPreference {
        val state = MutableStateFlow(initial)
        override val enabled: Flow<Boolean> = state
        var failure: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null
        var writes = 0

        override suspend fun setEnabled(value: Boolean) {
            writes++
            gate?.await()
            failure?.let { throw it }
            state.value = value
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.vm(pref: FakePreference): Pair<AutoSyncSettingViewModel, MutableList<AutoSyncSettingEvent>> {
        val vm = AutoSyncSettingViewModel(pref)
        val events = mutableListOf<AutoSyncSettingEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.enabled.collect { } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        return vm to events
    }

    @Test
    fun muestra_el_valor_persistido() = runTest {
        val (vm, _) = vm(FakePreference(initial = false))
        assertEquals(false, vm.enabled.value)
    }

    @Test
    fun sin_suscriptores_el_valor_es_null_hasta_leerlo() = runTest {
        val vm = AutoSyncSettingViewModel(FakePreference())
        assertNull(vm.enabled.value)
    }

    @Test
    fun al_cambiarlo_se_guarda_y_el_interruptor_refleja_el_valor_nuevo() = runTest {
        val pref = FakePreference(initial = true)
        val (vm, events) = vm(pref)

        vm.setEnabled(false)

        assertEquals(false, pref.state.value)
        assertEquals(false, vm.enabled.value)
        assertEquals(emptyList(), events)
    }

    @Test
    fun si_falla_la_escritura_avisa_SaveFailed_y_el_valor_no_cambia() = runTest {
        val pref = FakePreference(initial = true)
        pref.failure = RuntimeException("disco lleno")
        val (vm, events) = vm(pref)

        vm.setEnabled(false)

        assertEquals(listOf(AutoSyncSettingEvent.SaveFailed), events)
        assertEquals(true, vm.enabled.value, "no es optimista: sigue mostrando el valor persistido")
        assertEquals(true, pref.state.value)
    }

    @Test
    fun tras_un_fallo_se_puede_volver_a_intentar() = runTest {
        val pref = FakePreference(initial = true)
        pref.failure = RuntimeException("disco lleno")
        val (vm, _) = vm(pref)
        vm.setEnabled(false)

        pref.failure = null
        vm.setEnabled(false)

        assertEquals(false, vm.enabled.value)
        assertEquals(2, pref.writes)
    }

    @Test
    fun un_segundo_toque_con_la_escritura_en_curso_se_ignora() = runTest {
        val pref = FakePreference(initial = true)
        val gate = CompletableDeferred<Unit>()
        pref.gate = gate
        val (vm, _) = vm(pref)

        vm.setEnabled(false)
        vm.setEnabled(true) // ignorado
        gate.complete(Unit)

        assertEquals(1, pref.writes)
        assertEquals(false, vm.enabled.value)
    }

    @Test
    fun una_cancelacion_no_es_un_fallo_de_guardado_y_libera_el_bloqueo() = runTest {
        val pref = FakePreference(initial = true)
        pref.failure = CancellationException("cancelada")
        val (vm, events) = vm(pref)

        vm.setEnabled(false)

        assertEquals(emptyList(), events)
        pref.failure = null
        vm.setEnabled(false)
        assertEquals(2, pref.writes, "el bloqueo de escritura en curso debe liberarse tambien al cancelar")
        assertEquals(false, vm.enabled.value)
    }
}
