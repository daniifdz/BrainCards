package org.braincards.app.data.sync

import androidx.room.useWriterConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.braincards.app.data.repository.PendingChangesObserverImpl
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.usecase.Auth.SignOutAndWipeUseCase
import org.braincards.app.presentation.sync.AutoSyncConfig
import org.braincards.app.presentation.sync.AutoSyncController
import org.braincards.app.presentation.sync.AutoSyncHost
import org.braincards.app.presentation.sync.AutoSyncOutcome
import org.braincards.app.presentation.sync.AutoSyncPauseGate
import org.braincards.app.presentation.sync.ConnectivityMonitor
import org.braincards.app.presentation.sync.ForegroundMonitor
import org.braincards.app.presentation.sync.SyncCoordinator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Sync AUTOMATICA entre DOS dispositivos, de punta a punta y en TIEMPO REAL con plazos escalados: cada dispositivo con
 * su Room real, su SyncCoordinator real, su PendingChangesObserverImpl real y su AutoSyncController (monitores de
 * primer plano y red falsos), contra el FakeServer compartido (server_updated_at propio y paginacion por cursor, paginas
 * de 2 filas) y el RemoteDataSourceImpl real.
 *
 * Escala de tiempo: el periodico de 5 min se simula con [PERIODIC] = 300 ms y la ventana de "1 hora" con 12 periodos
 * (3,6 s). El resto de plazos (debounce, intervalo minimo, backoff) usan valores cortos, todos DESDE AutoSyncConfig.
 *
 * Los cambios locales usan `Clock.System.now()` como updatedAt (el push marca lastSyncedAt con el reloj del sistema:
 * una fila con updatedAt en el futuro queda pendiente para siempre; eso es el escenario 5).
 *
 * Lo que ya cubren TwoDeviceSyncRoomTest (cursores, solape, relojes desfasados, paginacion, LWW y tombstone llamando
 * al motor a mano) y AutoSyncNoLoopRoomTest (un dispositivo, un cambio = un sync) NO se repite aqui: esto prueba que los
 * DISPARADORES automaticos de dos dispositivos convergen y no entran en bucle.
 */
class TwoDeviceAutoSyncRoomTest {

    private val userA = "user-a"
    private val serverStart: Instant = Instant.parse("2025-03-01T07:13:41.250Z")
    private val base: Instant = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)

    private lateinit var scope: CoroutineScope
    private val nodes = mutableListOf<Node>()

    @BeforeTest
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() {
        nodes.forEach { it.controller.stop() }
        scope.cancel()
        nodes.forEach { it.dev.close() }
        nodes.clear()
    }

    private fun config(periodic: Duration = PERIODIC) = AutoSyncConfig(
        debounce = 80.milliseconds, maxChangeWait = 400.milliseconds, minInterval = MIN_INTERVAL,
        periodicInterval = periodic, backgroundTimeout = 1_000.milliseconds,
        backoffBase = 100.milliseconds, backoffMax = 400.milliseconds, pausedRetry = 100.milliseconds
    )

    /** Un dispositivo completo: Room real + coordinador real + observador real + controlador con monitores falsos. */
    private inner class Node(val name: String, val server: FakeServer, user: String, cfg: AutoSyncConfig, seed: Int) {
        val dev = Device(name, server, user, MutableClock(base))
        val foreground = MutableStateFlow(true)
        val online = MutableStateFlow(true)
        val syncs = AtomicInteger(0)
        val observer = PendingChangesObserverImpl(dev.db.deckDao(), dev.db.topicDao(), dev.db.flashcardDao(), dev.db.cardSchedulingDao())
        private val gate = AutoSyncPauseGate()
        val coordinator = SyncCoordinator(
            authRepository = dev.auth,
            syncEngineUseCase = dev.engine,
            decksRepository = dev.decks,
            topicsRepository = dev.topics,
            flashcardRepository = dev.flashcards,
            cardSchedulingRepository = dev.scheduling,
            dataStore = dev.dataStore,
            localDataRepository = dev.localData,
            signOutAndWipeUseCase = SignOutAndWipeUseCase(dev.auth, dev.localData),
            coroutineScope = scope,
            pauseGate = gate,
            pendingObserver = observer
        )
        /** Peticiones de sync automatico que el CONTROLADOR hizo al coordinador (aunque este las omita). */
        val requests = AtomicInteger(0)
        private val countingHost = object : AutoSyncHost {
            override suspend fun requestAutoSync(): AutoSyncOutcome {
                requests.incrementAndGet()
                return coordinator.requestAutoSync()
            }
            override val lastOutcome get() = coordinator.lastOutcome
        }
        val controller = AutoSyncController(
            scope = scope,
            timeSource = TimeSource.Monotonic,
            random = Random(seed),
            host = countingHost,
            authRepository = dev.auth,
            pendingObserver = observer,
            pauseGate = gate,
            foregroundMonitor = object : ForegroundMonitor { override val isForeground = foreground },
            connectivityMonitor = object : ConnectivityMonitor { override val isOnline = online },
            config = cfg
        )

        init {
            nodes += this
            // Un sync = un resultado nuevo en lastOutcome (los syncs distan >= minInterval, no se funden).
            scope.launch { coordinator.lastOutcome.collect { if (it != null) syncs.incrementAndGet() } }
        }

        fun start() = controller.start()

        /** Peticiones POST (pushes) hechas al servidor por este dispositivo. */
        val pushes get() = server.postCount(name)
        val pushedRows get() = server.postedRows(name)

        suspend fun pendingCount(user: String = dev.userId): Int = observer.observe(user).first().count
    }

    private fun node(name: String, server: FakeServer, cfg: AutoSyncConfig = config(), user: String = userA) =
        Node(name, server, user, cfg, seed = name.hashCode())

    // --- utilidades ---------------------------------------------------------------------------------------------------------

    private fun now(): Instant = Clock.System.now()

    private suspend fun waitUntil(what: String = "condicion", timeoutMs: Long = 8_000, condition: () -> Boolean) {
        try {
            withTimeout(timeoutMs) { while (!condition()) delay(10) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("no se cumplio a tiempo: $what")
        }
    }

    private suspend fun Node.waitFirstSync() = waitUntil("primer sync de $name") { syncs.get() >= 1 }

    /** Lo que hay en las tres tablas (incluidos los tombstones), ordenado y comparable entre dispositivos. */
    private suspend fun snapshot(n: Node): List<String> {
        suspend fun table(sql: String, label: String): List<String> = n.dev.db.useWriterConnection { tx ->
            tx.usePrepared(sql) { st ->
                val rows = mutableListOf<String>()
                while (st.step()) rows += "$label|${st.getText(0)}|${st.getText(1)}|deleted=${st.getLong(2)}"
                rows
            }
        }
        return table("SELECT id, name, deletedAt IS NOT NULL FROM decks ORDER BY id", "deck") +
            table("SELECT id, name, deletedAt IS NOT NULL FROM topics ORDER BY id", "topic") +
            table("SELECT id, question, deletedAt IS NOT NULL FROM flashcards ORDER BY id", "card")
    }

    private suspend fun awaitSame(a: Node, b: Node, what: String, expected: List<String>? = null) {
        waitUntil(what) {
            val sa = runBlocking { snapshot(a) }
            val sb = runBlocking { snapshot(b) }
            sa == sb && (expected == null || sa == expected)
        }
    }

    /** A crea 3 decks, 1 topic y 2 tarjetas; el sync automatico los sube y B los baja. Ambos acaban iguales. */
    private suspend fun seedAndConverge(a: Node, b: Node) {
        a.start(); b.start()
        a.waitFirstSync(); b.waitFirstSync()
        a.dev.addDeck("d1", at = now()); delay(3)
        a.dev.addDeck("d2", at = now()); delay(3)
        a.dev.addDeck("d3", at = now()); delay(3)
        a.dev.addTopic("t1", "d1", at = now()); delay(3)
        a.dev.addCard("c1", "d1", at = now(), topicId = "t1"); delay(3)
        a.dev.addCard("c2", "d2", at = now())
        waitUntil("el servidor recibe todo lo de A") {
            a.server.rowCount("decks") == 3 && a.server.rowCount("topics") == 1 && a.server.rowCount("flashcards") == 2
        }
        awaitSame(a, b, "B baja lo de A")
        assertEquals(6, snapshot(b).size, "B tiene los 3 decks, el topic y las 2 tarjetas")
    }

    // --- 1) convergencia -------------------------------------------------------------------------------------------------

    private suspend fun convergence(viaForeground: Boolean) {
        val server = FakeServer(serverStart)
        val a = node("A", server)
        // Por primer plano: el periodico de B no cuenta (1 h); B baja SOLO al volver a primer plano.
        val b = node("B", server, if (viaForeground) config(periodic = 1.hours) else config())
        a.start(); b.start()
        a.waitFirstSync(); b.waitFirstSync()

        a.dev.addDeck("d1", at = now()); delay(3)
        a.dev.addDeck("d2", at = now()); delay(3)
        a.dev.addDeck("d3", at = now()); delay(3)
        a.dev.addTopic("t1", "d1", at = now()); delay(3)
        a.dev.addCard("c1", "d1", at = now(), topicId = "t1")
        waitUntil("el servidor recibe lo de A (debounce)") { server.rowCount("decks") == 3 && server.rowCount("flashcards") == 1 }
        if (viaForeground) { b.foreground.value = false; delay(50); b.foreground.value = true }
        awaitSame(a, b, "B baja lo de A")

        // Edicion en A, borrado logico en A (d2) y borrado logico en B (d3): ambos sentidos.
        a.dev.editDeck("d1", "d1 editado", at = now()); delay(3)
        a.dev.tombstoneDeck("d2", at = now())
        waitUntil("el servidor recibe la edicion y el tombstone de A") {
            server.nameOf("decks", "d1") == "d1 editado" && server.isDeleted("decks", "d2") == true
        }
        if (viaForeground) { b.foreground.value = false; delay(50); b.foreground.value = true }
        awaitSame(a, b, "B baja la edicion y el tombstone de A")
        b.dev.tombstoneDeck("d3", at = now())
        waitUntil("el servidor recibe el tombstone de B") { server.isDeleted("decks", "d3") == true }
        awaitSame(a, b, "A baja el tombstone de B")

        val expected = listOf(
            "deck|d1|d1 editado|deleted=0", "deck|d2|Deck d2|deleted=1", "deck|d3|Deck d3|deleted=1",
            "topic|t1|Topic t1|deleted=0", "card|c1|Qc1|deleted=0"
        )
        assertEquals(expected, snapshot(a), "A")
        assertEquals(expected, snapshot(b), "B")
    }

    @Test
    fun una_edicion_en_A_se_sube_sola_y_B_la_baja_con_el_periodico_ambos_iguales_con_tombstones_en_los_dos_sentidos() =
        runBlocking<Unit> { convergence(viaForeground = false) }

    @Test
    fun una_edicion_en_A_se_sube_sola_y_B_la_baja_al_volver_a_primer_plano_ambos_iguales_con_tombstones() =
        runBlocking<Unit> { convergence(viaForeground = true) }

    // --- 2) sin ping-pong ---------------------------------------------------------------------------------------------------

    @Test
    fun tras_converger_no_hay_pushes_y_los_pulls_estan_acotados_por_el_periodico_y_el_intervalo_minimo() = runBlocking<Unit> {
        val server = FakeServer(serverStart)
        val a = node("A", server)
        val b = node("B", server)
        seedAndConverge(a, b)
        delay(400) // que termine lo que haya en vuelo
        val stampBefore = server.stampOf("decks", "d1")
        val pushesA = a.pushes; val pushesB = b.pushes
        val rowsA = a.pushedRows; val rowsB = b.pushedRows
        val syncsA = a.syncs.get(); val syncsB = b.syncs.get()

        delay(WINDOW_MS) // "1 hora" virtual: 12 periodos de 5 min

        assertEquals(pushesA, a.pushes, "A no sube nada tras converger")
        assertEquals(pushesB, b.pushes, "B no sube nada tras converger")
        assertEquals(rowsA, a.pushedRows)
        assertEquals(rowsB, b.pushedRows)
        assertEquals(0, b.pushedRows, "B solo bajo filas: aplicar un pull no las marca como pendientes")
        assertEquals(stampBefore, server.stampOf("decks", "d1"), "el servidor no recibio escrituras")
        assertEquals(0, a.pendingCount(), "A sin pendientes")
        assertEquals(0, b.pendingCount(), "B sin pendientes tras bajar filas del servidor")
        for ((n, before) in listOf(a to syncsA, b to syncsB)) {
            val pulls = n.syncs.get() - before
            assertTrue(pulls >= 5, "el periodico sigue vivo en ${n.name}: $pulls pulls")
            assertTrue(pulls <= WINDOW_MS / PERIODIC.inWholeMilliseconds + 3, "pulls acotados por el periodico en ${n.name}: $pulls")
            assertTrue(pulls <= WINDOW_MS / MIN_INTERVAL.inWholeMilliseconds + 2, "pulls acotados por el intervalo minimo en ${n.name}: $pulls")
        }
    }

    @Test
    fun una_rafaga_de_disparos_queda_acotada_por_el_intervalo_minimo() = runBlocking<Unit> {
        val server = FakeServer(serverStart)
        val a = node("A", server, config(periodic = 1.hours))
        a.start(); a.waitFirstSync()
        delay(400)
        val before = a.syncs.get()
        val startedAt = System.nanoTime()

        // Volver a primer plano 40 veces seguidas (sin pendientes: la salida a segundo plano no hace "ultimo intento").
        repeat(40) {
            a.foreground.value = false; delay(15)
            a.foreground.value = true; delay(15)
        }
        delay(300)

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val syncs = a.syncs.get() - before
        assertTrue(syncs >= 1, "al menos un sync")
        assertTrue(
            syncs <= elapsedMs / MIN_INTERVAL.inWholeMilliseconds + 2,
            "40 disparos en $elapsedMs ms dieron $syncs syncs: el intervalo minimo (${MIN_INTERVAL.inWholeMilliseconds} ms) los une"
        )
    }

    // --- 3) ediciones concurrentes de la misma fila (LWW) --------------------------------------------------------------

    /**
     * A y B editan d1 SIN conexion (la de B mas reciente o la de A) y recuperan la red UNO DETRAS DE OTRO (el primero en
     * volver es [firstIsB]; se espera a que su push llegue al servidor antes de dar la red al otro).
     */
    private suspend fun concurrentEdits(newestIsB: Boolean, firstIsB: Boolean) {
        val server = FakeServer(serverStart)
        val a = node("A", server)
        val b = node("B", server)
        a.start(); b.start()
        a.waitFirstSync(); b.waitFirstSync()
        a.dev.addDeck("d1", name = "orig", at = now())
        waitUntil("d1 en el servidor") { server.nameOf("decks", "d1") == "orig" }
        awaitSame(a, b, "B baja d1")
        a.online.value = false; b.online.value = false
        delay(100)

        if (newestIsB) {
            a.dev.editDeck("d1", "edit-A", at = now()); delay(25)
            b.dev.editDeck("d1", "edit-B", at = now())
        } else {
            b.dev.editDeck("d1", "edit-B", at = now()); delay(25)
            a.dev.editDeck("d1", "edit-A", at = now())
        }
        val winner = if (newestIsB) "edit-B" else "edit-A"
        val (first, second) = if (firstIsB) b to a else a to b
        val firstValue = if (firstIsB) "edit-B" else "edit-A"

        first.online.value = true
        waitUntil("el primero sube su edicion") { server.nameOf("decks", "d1") == firstValue }
        second.online.value = true
        awaitSame(a, b, "convergen", expected = listOf("deck|d1|$winner|deleted=0"))
        assertEquals(winner, server.nameOf("decks", "d1"), "el servidor tambien converge en la mas reciente")

        // Sin re-subidas indefinidas: tras converger, ni pushes ni pendientes.
        delay(400)
        val pa = a.pushes; val pb = b.pushes
        delay(PERIODIC.inWholeMilliseconds * 5)
        assertEquals(pa, a.pushes, "A no re-sube")
        assertEquals(pb, b.pushes, "B no re-sube")
        assertEquals(0, a.pendingCount()); assertEquals(0, b.pendingCount())
        assertTrue(a.pushedRows <= 3 && b.pushedRows <= 3, "filas subidas acotadas: A=${a.pushedRows} B=${b.pushedRows}")
    }

    @Test fun edicion_concurrente_gana_la_de_B_y_vuelve_primero_B() = runBlocking<Unit> { concurrentEdits(newestIsB = true, firstIsB = true) }
    @Test fun edicion_concurrente_gana_la_de_B_y_vuelve_primero_A() = runBlocking<Unit> { concurrentEdits(newestIsB = true, firstIsB = false) }
    @Test fun edicion_concurrente_gana_la_de_A_y_vuelve_primero_B() = runBlocking<Unit> { concurrentEdits(newestIsB = false, firstIsB = true) }
    @Test fun edicion_concurrente_gana_la_de_A_y_vuelve_primero_A() = runBlocking<Unit> { concurrentEdits(newestIsB = false, firstIsB = false) }

    // --- 4) sin red acumula, al volver UNA sync sube todo -------------------------------------------------------------------

    @Test
    fun un_dispositivo_sin_red_acumula_ediciones_y_al_volver_la_red_hace_una_sola_sync_que_sube_todo() = runBlocking<Unit> {
        val server = FakeServer(serverStart)
        val a = node("A", server)
        val b = node("B", server)
        a.start(); b.start()
        a.waitFirstSync(); b.waitFirstSync()
        a.online.value = false
        delay(100)
        val syncsOffline = a.syncs.get()

        a.dev.addDeck("d1", at = now()); delay(3)
        a.dev.addDeck("d2", at = now()); delay(3)
        a.dev.addDeck("d3", at = now()); delay(3)
        a.dev.addTopic("t1", "d1", at = now()); delay(3)
        a.dev.addCard("c1", "d1", at = now(), topicId = "t1"); delay(3)
        a.dev.editDeck("d2", "d2 editado", at = now())
        delay(PERIODIC.inWholeMilliseconds * 3) // varios periodos y debounces vencidos: sin red no se intenta nada
        assertEquals(syncsOffline, a.syncs.get(), "sin red no hay ni un sync")
        assertEquals(0, a.pushes, "sin red no se sube nada")
        assertEquals(0, server.rowCount("decks"))
        assertEquals(0, b.dev.count("decks"))

        a.online.value = true
        waitUntil("la sync al volver la red") { a.syncs.get() > syncsOffline }

        assertEquals(syncsOffline + 1, a.syncs.get(), "UNA sola sync al volver la red")
        assertEquals(3, server.rowCount("decks")); assertEquals(1, server.rowCount("topics")); assertEquals(1, server.rowCount("flashcards"))
        assertEquals("d2 editado", server.nameOf("decks", "d2"))
        assertEquals(5, a.pushedRows, "3 decks + 1 topic + 1 tarjeta, cada fila una sola vez")
        awaitSame(a, b, "B converge")
    }

    // --- 5) updated_at en el futuro (limite conocido) ---------------------------------------------------------------------

    @Test
    fun una_fila_con_updated_at_en_el_futuro_queda_pendiente_pero_acotada_sin_bucle_apretado() = runBlocking<Unit> {
        val server = FakeServer(serverStart)
        val a = node("A", server)
        val b = node("B", server)
        a.start(); b.start()
        a.waitFirstSync(); b.waitFirstSync()

        val future = now() + 1.hours
        a.dev.addDeck("fut", at = future)
        waitUntil("el servidor recibe la fila") { server.nameOf("decks", "fut") != null }
        awaitSame(a, b, "B la baja")
        delay(400)
        val pushesA = a.pushes; val rowsA = a.pushedRows; val syncsA = a.syncs.get(); val syncsB = b.syncs.get()

        delay(WINDOW_MS)

        // LIMITE CONOCIDO (FINAL_SPRINT.md, deuda (c)): markPushed fija lastSyncedAt = hora del sistema < updatedAt, asi que
        // A sigue viendo la fila como pendiente y la vuelve a subir en cada sync. Lo que se prueba es que queda ACOTADO:
        // el observador no cambia (misma huella), asi que solo lo mueve el periodico, y nunca hay un bucle apretado.
        assertEquals(1, a.pendingCount(), "la fila con updated_at futuro sigue pendiente en A (limite conocido)")
        val syncsAdelta = a.syncs.get() - syncsA
        assertTrue(syncsAdelta <= WINDOW_MS / PERIODIC.inWholeMilliseconds + 3, "syncs de A acotados por el periodico: $syncsAdelta")
        assertTrue(syncsAdelta <= WINDOW_MS / MIN_INTERVAL.inWholeMilliseconds + 2, "y por el intervalo minimo: $syncsAdelta")
        assertTrue(a.pushes - pushesA <= syncsAdelta, "como mucho un push por sync")
        assertTrue(a.pushedRows - rowsA >= 1, "documentado: la fila se vuelve a subir en los syncs siguientes")
        // B no entra en el ping-pong: baja la fila, no la marca pendiente y no sube nada.
        assertEquals(0, b.pendingCount())
        assertEquals(0, b.pushedRows)
        assertTrue(b.syncs.get() - syncsB <= WINDOW_MS / PERIODIC.inWholeMilliseconds + 3, "pulls de B acotados")
    }

    // --- 6) cambio de usuario / cierre de sesion ---------------------------------------------------------------------------

    @Test
    fun tras_NotAuthenticated_no_hay_syncs_ni_subidas_aunque_haya_cambios_red_y_primer_plano() = runBlocking<Unit> {
        val server = FakeServer(serverStart)
        val a = node("A", server)
        a.start(); a.waitFirstSync()
        a.dev.auth.setState(AuthState.NotAuthenticated)
        delay(200)
        val syncs = a.syncs.get(); val pushes = a.pushes; val requests = a.requests.get()

        // Datos locales sin cuenta (userId NULL) + todos los disparos posibles.
        a.dev.decks.addDeck(Deck("huerfano", "huerfano", now(), now(), null, null, null, null))
        a.online.value = false; delay(50); a.online.value = true
        a.foreground.value = false; delay(50); a.foreground.value = true
        delay(PERIODIC.inWholeMilliseconds * 5)

        assertEquals(requests, a.requests.get(), "el controlador ni siquiera pide un sync sin sesion")
        assertEquals(syncs, a.syncs.get(), "ni un sync tras NotAuthenticated")
        assertEquals(pushes, a.pushes, "ni una subida tras NotAuthenticated")
        assertNull(server.nameOf("decks", "huerfano"), "nada llego al servidor")
    }

    @Test
    fun al_cambiar_de_usuario_el_observador_se_resuscribe_y_no_se_sube_nada_del_usuario_anterior() = runBlocking<Unit> {
        val server = FakeServer(serverStart)
        // Periodico de 1 h: solo un cambio detectado por el observador puede provocar el sync de "d-b".
        val a = node("A", server, config(periodic = 1.hours))
        a.start(); a.waitFirstSync()

        a.online.value = false
        delay(100)
        a.dev.addDeck("d-a", at = now())           // pendiente del usuario A, sin conexion
        a.dev.signInAs("user-b")                   // otro usuario inicia sesion en el mismo dispositivo
        a.online.value = true
        delay(300)
        a.dev.addDeck("d-b", at = now())           // cambio del usuario nuevo

        waitUntil("d-b sube por el debounce (el observador sigue al usuario nuevo)") { server.nameOf("decks", "d-b") != null }
        delay(PERIODIC.inWholeMilliseconds * 3)

        assertNull(server.nameOf("decks", "d-a"), "lo pendiente del usuario anterior NO se sube con la sesion del nuevo")
        assertEquals(1, a.pushedRows, "solo se subio d-b")
    }

    private companion object {
        val PERIODIC: Duration = 300.milliseconds
        val MIN_INTERVAL: Duration = 150.milliseconds
        const val WINDOW_MS = 3_600L // "1 hora" = 12 periodos de 300 ms
    }
}
