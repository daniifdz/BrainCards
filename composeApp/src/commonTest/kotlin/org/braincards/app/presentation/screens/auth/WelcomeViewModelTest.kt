package org.braincards.app.presentation.screens.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.usecase.Decks.GetDeckUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** La nota de fusion de Bienvenida solo se muestra si hay mazos (no borrados) locales. */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val aDeck = Deck("d1", "Mazo", Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(0), null)

    private fun vmWith(decks: Flow<List<Deck>>): WelcomeViewModel {
        val repository = object : DecksRepository by FakeDecksRepository() {
            override fun getAllDecks(): Flow<List<Deck>> = decks
        }
        return WelcomeViewModel(GetDeckUseCase(repository))
    }

    private fun kotlinx.coroutines.test.TestScope.collect(vm: WelcomeViewModel): List<Boolean?> {
        val seen = mutableListOf<Boolean?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.hasLocalData.collect { seen += it } }
        return seen
    }

    @Test
    fun hasta_saber_si_hay_mazos_es_null_y_no_se_muestra_la_nota() = runTest {
        val vm = vmWith(flow { kotlinx.coroutines.awaitCancellation() })
        assertEquals(listOf<Boolean?>(null), collect(vm))
    }

    @Test
    fun con_mazos_hay_datos_locales() = runTest {
        val seen = collect(vmWith(MutableStateFlow(listOf(aDeck))))
        assertEquals(true, seen.last())
    }

    @Test
    fun sin_mazos_no_hay_datos_locales() = runTest {
        val seen = collect(vmWith(MutableStateFlow(emptyList())))
        assertEquals(false, seen.last())
    }

    @Test
    fun crear_o_borrar_mazos_actualiza_el_dato() = runTest {
        val decks = MutableStateFlow<List<Deck>>(emptyList())
        val seen = collect(vmWith(decks))
        assertEquals(false, seen.last())

        decks.value = listOf(aDeck)
        assertEquals(true, seen.last())

        decks.value = emptyList()
        assertEquals(false, seen.last())
    }

    @Test
    fun un_fallo_al_leer_los_mazos_cuenta_como_sin_datos() = runTest {
        val seen = collect(vmWith(flow { throw IllegalStateException("boom") }))
        assertEquals(false, seen.last())
    }
}
