package org.braincards.app.presentation.screens.decks

import androidx.datastore.core.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.models.DeckSummary
import org.braincards.app.domain.repository.DeckSummariesObserver
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.usecase.DeleteDeckUseCase
import org.braincards.app.domain.usecase.Decks.AddDeckUseCase
import org.braincards.app.domain.usecase.Decks.DeckNameError
import org.braincards.app.domain.usecase.Decks.ObserveDeckSummariesUseCase
import org.braincards.app.domain.usecase.Decks.UpdateDeckUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DecksViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val t0 = Instant.fromEpochMilliseconds(1_000)

    private fun deck(id: String, name: String, description: String? = null) = Deck(id, name, t0, t0, description)
    private fun summary(id: String, name: String, count: Int = 0) = DeckSummary(deck(id, name), count)

    /** Observador controlado a mano: null = aun sin emitir; Result.failure = la lectura falla. */
    private class ManualSummaries : DeckSummariesObserver {
        var subscriptions = 0
        val source = MutableStateFlow<Result<List<DeckSummary>>?>(null)
        override fun observeDeckSummaries(): Flow<List<DeckSummary>> = flow {
            subscriptions++
            source.filterNotNull().collect { emit(it.getOrThrow()) }
        }
    }

    /** Repositorio con escrituras que pueden fallar con IOException o quedarse esperando una compuerta. */
    private class ControlledRepo(val inner: FakeDecksRepository = FakeDecksRepository()) : DecksRepository by inner {
        var failWrites = false
        var gate: CompletableDeferred<Unit>? = null
        var addCalls = 0

        override suspend fun addDeck(deck: Deck) {
            addCalls++
            gate?.await()
            if (failWrites) throw IOException("disco lleno")
            inner.addDeck(deck)
        }

        override suspend fun deleteDeck(deck: Deck) {
            if (failWrites) throw IOException("disco lleno")
            inner.deleteDeck(deck)
        }
    }

    private class FixedClock(private val now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private class Env(val observer: ManualSummaries, val repo: ControlledRepo, val vm: DecksViewModel)

    private fun env(): Env {
        val observer = ManualSummaries()
        val repo = ControlledRepo()
        val vm = DecksViewModel(
            addDeckUseCase = AddDeckUseCase(repo),
            observeDeckSummaries = ObserveDeckSummariesUseCase(observer),
            updateDeckUseCase = UpdateDeckUseCase(repo),
            deleteDeckUseCase = DeleteDeckUseCase(repo),
            clock = FixedClock(Instant.fromEpochMilliseconds(9_000))
        )
        return Env(observer, repo, vm)
    }

    private fun TestScope.collectStates(vm: DecksViewModel): List<DecksUiState> {
        val seen = mutableListOf<DecksUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
        return seen
    }

    private fun TestScope.collectMessages(vm: DecksViewModel): List<DeckMessage> {
        val seen = mutableListOf<DeckMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messageEvent.collect { seen += it } }
        return seen
    }

    @Test
    fun antes_de_la_primera_lista_es_Loading() = runTest {
        val e = env()
        val seen = collectStates(e.vm)

        assertEquals(listOf<DecksUiState>(DecksUiState.Loading), seen)
    }

    @Test
    fun sin_mazos_es_Empty() = runTest {
        val e = env()
        val seen = collectStates(e.vm)

        e.observer.source.value = Result.success(emptyList())

        assertEquals(DecksUiState.Empty, seen.last())
    }

    @Test
    fun con_mazos_es_Content_en_el_orden_fijo_por_nombre() = runTest {
        val e = env()
        val seen = collectStates(e.vm)

        e.observer.source.value = Result.success(
            listOf(summary("1", "zoología", 2), summary("2", "Álgebra", 0), summary("3", "biología", 5))
        )

        val content = seen.last() as DecksUiState.Content
        assertEquals(listOf("Álgebra", "biología", "zoología"), content.rows.map { it.deck.name })
        assertEquals(listOf(0, 5, 2), content.rows.map { it.cardCount }) // el recuento viaja con su mazo
    }

    @Test
    fun el_buscador_filtra_sin_distinguir_acentos_y_NoResults_lleva_la_busqueda_recortada() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        e.observer.source.value = Result.success(listOf(summary("1", "Vocabulario Inglés"), summary("2", "Anatomía")))

        e.vm.onSearchQueryChange("ingles")
        assertEquals(listOf("Vocabulario Inglés"), (seen.last() as DecksUiState.Content).rows.map { it.deck.name })

        e.vm.onSearchQueryChange("  kotlin  ")
        assertEquals(DecksUiState.NoResults("kotlin"), seen.last())

        e.vm.onSearchQueryChange("")
        assertEquals(2, (seen.last() as DecksUiState.Content).rows.size)
    }

    @Test
    fun un_fallo_de_lectura_es_Error_y_Reintentar_vuelve_a_suscribir() = runTest {
        val e = env()
        val seen = collectStates(e.vm)
        e.observer.source.value = Result.failure(IllegalStateException("boom"))
        assertEquals(DecksUiState.Error, seen.last())
        assertEquals(1, e.observer.subscriptions)

        e.observer.source.value = Result.success(listOf(summary("1", "Uno")))
        e.vm.retry()

        assertEquals(2, e.observer.subscriptions)
        assertTrue(seen.last() is DecksUiState.Content)
    }

    @Test
    fun crear_un_mazo_ok_guarda_llama_a_onSuccess_y_no_deja_error() = runTest {
        val e = env()
        var done = false

        e.vm.createDeck("  Nuevo ") { done = true }

        assertTrue(done)
        assertEquals("Nuevo", e.repo.inner.addedDecks.single().name)
        assertNull(e.vm.nameError.value)
        assertFalse(e.vm.saving.value)
    }

    @Test
    fun un_nombre_repetido_es_error_de_campo_no_Snackbar_y_el_dialogo_no_se_cierra() = runTest {
        val e = env()
        e.repo.inner.addDeck(deck("1", "Inglés"))
        e.repo.inner.addedDecks.clear()
        val messages = collectMessages(e.vm)
        var done = false

        e.vm.createDeck(" inglés ") { done = true }

        assertEquals(DeckNameError.Duplicate, e.vm.nameError.value)
        assertFalse(done)
        assertTrue(messages.isEmpty())
        assertTrue(e.repo.inner.addedDecks.isEmpty())
        assertFalse(e.vm.saving.value)

        e.vm.clearNameError()
        assertNull(e.vm.nameError.value)
    }

    @Test
    fun un_fallo_de_disco_al_guardar_no_tumba_la_app_y_avisa_con_SaveFailed() = runTest {
        val e = env()
        e.repo.failWrites = true
        val messages = collectMessages(e.vm)
        var done = false

        e.vm.createDeck("Nuevo") { done = true }

        assertEquals(listOf(DeckMessage.SaveFailed), messages)
        assertFalse(done)
        assertNull(e.vm.nameError.value)
        assertFalse(e.vm.saving.value) // se puede volver a intentar
    }

    @Test
    fun un_fallo_de_disco_al_eliminar_avisa_con_DeleteFailed() = runTest {
        val e = env()
        e.repo.inner.addDeck(deck("1", "Uno"))
        e.repo.failWrites = true
        val messages = collectMessages(e.vm)
        var done = false

        e.vm.deleteDeck(deck("1", "Uno")) { done = true }

        assertEquals(listOf(DeckMessage.DeleteFailed), messages)
        assertFalse(done)
        assertEquals("Uno", e.repo.inner.getDeckById("1")?.name)
    }

    @Test
    fun eliminar_ok_borra_y_llama_a_onSuccess() = runTest {
        val e = env()
        e.repo.inner.addDeck(deck("1", "Uno"))
        var done = false

        e.vm.deleteDeck(deck("1", "Uno")) { done = true }

        assertTrue(done)
        assertNull(e.repo.inner.getDeckById("1"))
    }

    @Test
    fun un_segundo_toque_mientras_se_guarda_se_ignora() = runTest {
        val e = env()
        val gate = CompletableDeferred<Unit>()
        e.repo.gate = gate
        var successes = 0

        e.vm.createDeck("Uno") { successes++ }
        assertTrue(e.vm.saving.value)
        e.vm.createDeck("Uno") { successes++ } // doble toque: ignorado
        gate.complete(Unit)

        assertEquals(1, e.repo.addCalls)
        assertEquals(1, successes)
        assertFalse(e.vm.saving.value)
    }

    @Test
    fun editar_cambia_el_nombre_y_updatedAt_pero_conserva_la_descripcion() = runTest {
        val e = env()
        val original = deck("1", "Uno", description = "se conserva")
        e.repo.inner.addDeck(original)

        e.vm.updateDeck(original, "Dos") {}

        val saved = e.repo.inner.getDeckById("1")!!
        assertEquals("Dos", saved.name)
        assertEquals("se conserva", saved.description)
        assertEquals(Instant.fromEpochMilliseconds(9_000), saved.updatedAt)
    }
}
