package org.braincards.app.presentation.screens.sample

import androidx.datastore.core.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.Flashcard
import org.braincards.app.domain.models.Topic
import org.braincards.app.domain.repository.SampleDeckStore
import org.braincards.app.domain.sample.SampleDeckNames
import org.braincards.app.domain.usecase.Decks.CreateSampleDeckUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SampleDeckViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val names = SampleDeckNames("Primeros pasos", listOf("Cómo estudiar con BrainCards", "Cómo organizar y sincronizar tus mazos"))

    /** Almacén controlado a mano: puede quedarse esperando una compuerta o fallar. */
    private class ControlledStore : SampleDeckStore {
        var gate: CompletableDeferred<Unit>? = null
        var failure: Throwable? = null
        var calls = 0
        val decks = mutableListOf<Deck>()

        override suspend fun insert(deck: Deck, topics: List<Topic>, cards: List<Flashcard>) {
            calls++
            gate?.await()
            failure?.let { throw it }
            decks.add(deck)
        }
    }

    private class Env(val decksRepository: FakeDecksRepository = FakeDecksRepository(), val store: ControlledStore = ControlledStore()) {
        val vm = SampleDeckViewModel(CreateSampleDeckUseCase(decksRepository, store))
    }

    @Test
    fun al_crear_emite_ready_con_el_id_del_mazo_y_el_boton_vuelve_a_reposo() = runTest {
        val env = Env()
        val events = mutableListOf<SampleDeckEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.events.toList(events) }

        env.vm.create(names)

        val ready = assertIs<SampleDeckEvent.Ready>(events.single())
        assertEquals(env.store.decks.single().id, ready.deckId)
        assertFalse(env.vm.creating.value)
    }

    @Test
    fun mientras_crea_el_estado_es_creando_y_un_doble_toque_no_duplica() = runTest {
        val env = Env()
        env.store.gate = CompletableDeferred()
        val events = mutableListOf<SampleDeckEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.events.toList(events) }

        env.vm.create(names)
        assertTrue(env.vm.creating.value, "creando…")
        env.vm.create(names)
        env.vm.create(names)
        env.store.gate!!.complete(Unit)

        assertEquals(1, env.store.calls, "un único intento de creación")
        assertEquals(1, env.store.decks.size)
        assertEquals(1, events.size)
        assertFalse(env.vm.creating.value)
    }

    @Test
    fun si_falla_emite_failed_y_el_boton_vuelve_a_reposo() = runTest {
        val env = Env()
        env.store.failure = IOException("disco lleno")
        val events = mutableListOf<SampleDeckEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.events.toList(events) }

        env.vm.create(names)

        assertEquals(listOf<SampleDeckEvent>(SampleDeckEvent.Failed), events)
        assertFalse(env.vm.creating.value)
        assertTrue(env.store.decks.isEmpty())
    }

    @Test
    fun tras_un_fallo_se_puede_reintentar() = runTest {
        val env = Env()
        env.store.failure = IOException("disco lleno")
        val events = mutableListOf<SampleDeckEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.events.toList(events) }
        env.vm.create(names)

        env.store.failure = null
        env.vm.create(names)

        assertEquals(2, events.size)
        assertEquals(SampleDeckEvent.Failed, events[0])
        assertIs<SampleDeckEvent.Ready>(events[1])
        assertEquals(1, env.store.decks.size)
    }

    @Test
    fun si_el_mazo_ya_existe_emite_ready_con_el_existente_sin_escribir() = runTest {
        val env = Env()
        val t = Instant.fromEpochMilliseconds(1_000)
        env.decksRepository.addDeck(Deck("existente", "Primeros pasos", t, t, null))
        val events = mutableListOf<SampleDeckEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.vm.events.toList(events) }

        env.vm.create(names)

        assertEquals(listOf<SampleDeckEvent>(SampleDeckEvent.Ready("existente")), events)
        assertEquals(0, env.store.calls)
    }

    @Test
    fun el_estado_inicial_es_en_reposo() = runTest {
        assertFalse(Env().vm.creating.value)
    }
}
