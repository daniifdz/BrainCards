package org.braincards.app.domain.usecase.Sync

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeAuthRepository
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.fakes.FakeDecksRepository
import org.braincards.app.domain.fakes.FakeFlashcardRepository
import org.braincards.app.domain.fakes.FakeRemoteDataSource
import org.braincards.app.domain.fakes.FakeTopicsRepository
import org.braincards.app.domain.models.AuthState
import org.braincards.app.domain.models.Deck
import org.braincards.app.domain.repository.DecksRepository
import org.braincards.app.domain.repository.SyncFailureClassifier
import org.braincards.app.domain.repository.SyncFailureKind
import org.braincards.app.domain.usecase.Auth.BackfillLocalDataUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * El motor DELEGA la clasificación de fallos en el [SyncFailureClassifier] y no conoce ninguna librería de red: aquí el clasificador es
 * un guion (devuelve el tipo que se le pida) y las excepciones son ordinarias, así que lo único que puede explicar el resultado es lo
 * que el clasificador responde. Además fija cuándo NO se consulta: éxito, fase de aplicar las filas y cancelación de corrutinas.
 */
class SyncEngineFailureDelegationTest {

    private val userId = "user-1"

    /** Clasificador guionizado: responde [answer] y registra cada excepción que se le consulta. */
    private class ScriptedClassifier(var answer: SyncFailureKind) : SyncFailureClassifier {
        val consulted = mutableListOf<Exception>()
        override fun classify(error: Exception): SyncFailureKind {
            consulted += error
            return answer
        }
    }

    /** Decks cuyo applyRemotePull lanza: simula un fallo de Room al guardar las filas descargadas. */
    private class ApplyFailsDecksRepository(delegate: DecksRepository) : DecksRepository by delegate {
        override suspend fun applyRemotePull(decks: List<Deck>): Int = throw IllegalStateException("FOREIGN KEY constraint failed")
    }

    private fun engine(
        remote: FakeRemoteDataSource,
        classifier: SyncFailureClassifier,
        decks: DecksRepository = FakeDecksRepository()
    ): SyncEngineUseCase {
        val auth = FakeAuthRepository(AuthState.Authenticated(userId, "a@b.c", sessionConfirmed = true))
        val topics = FakeTopicsRepository()
        val flashcards = FakeFlashcardRepository()
        val scheduling = FakeCardSchedulingRepository()
        return SyncEngineUseCase(
            auth, remote, decks, topics, flashcards, scheduling,
            BackfillLocalDataUseCase(decks, topics, flashcards, scheduling),
            failureClassifier = classifier
        )
    }

    private suspend fun pendingDecks(count: Int) = FakeDecksRepository().apply {
        repeat(count) { i ->
            addDeck(Deck("d$i", "Deck $i", Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(2), null, userId = userId))
        }
    }

    @Test
    fun al_descargar_la_causa_del_pull_es_la_que_dice_el_clasificador() = runBlocking {
        val expected = mapOf(
            SyncFailureKind.NETWORK to PullFailure.NETWORK,
            SyncFailureKind.REJECTED to PullFailure.REJECTED,
            SyncFailureKind.UNCLASSIFIED to null
        )
        for ((kind, pullFailure) in expected) {
            val boom = IllegalStateException("fallo ordinario")
            val remote = FakeRemoteDataSource().apply { pullDecksFailure = boom }
            val classifier = ScriptedClassifier(kind)

            val result = assertIs<SyncResult.Completed>(engine(remote, classifier)()).pull.decks

            assertTrue(result.aborted, "$kind: el pull debe abortar")
            assertEquals(pullFailure, result.failure, "$kind: causa del pull")
            assertEquals(1, classifier.consulted.size, "$kind: se consulta una vez")
            assertSame(boom, classifier.consulted.single(), "$kind: se consulta la excepción original")
        }
    }

    @Test
    fun al_subir_solo_la_respuesta_red_corta_a_los_tres_intentos() = runBlocking {
        val rows = 4
        val expectedAttempts = mapOf(
            SyncFailureKind.NETWORK to 3,
            SyncFailureKind.REJECTED to rows,
            SyncFailureKind.UNCLASSIFIED to rows
        )
        for ((kind, attempts) in expectedAttempts) {
            val remote = FakeRemoteDataSource().apply {
                pushDeckIdsThatFail = (0 until rows).map { "d$it" }.toSet()
                pushFailureFactory = { IllegalStateException("fallo ordinario") }
            }
            val classifier = ScriptedClassifier(kind)

            assertIs<SyncResult.Completed>(engine(remote, classifier, pendingDecks(rows))())

            assertEquals(attempts, remote.calls.count { it == "pushDecks" }, "$kind: intentos de subida")
            assertEquals(attempts, classifier.consulted.size, "$kind: una consulta por intento fallido")
        }
    }

    @Test
    fun un_fallo_al_aplicar_las_filas_es_LOCAL_sin_consultar_al_clasificador() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            decksToPull = listOf(Deck("d1", "Deck", Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(2), null, userId = userId))
        }
        val classifier = ScriptedClassifier(SyncFailureKind.NETWORK)

        val result = assertIs<SyncResult.Completed>(engine(remote, classifier, ApplyFailsDecksRepository(FakeDecksRepository()))()).pull.decks

        assertEquals(PullFailure.LOCAL, result.failure)
        assertTrue(classifier.consulted.isEmpty(), "la fase de aplicar no pasa por el clasificador")
    }

    @Test
    fun un_sync_sin_fallos_no_consulta_al_clasificador() = runBlocking {
        val classifier = ScriptedClassifier(SyncFailureKind.NETWORK)

        val result = assertIs<SyncResult.Completed>(engine(FakeRemoteDataSource(), classifier)())

        assertNull(result.pull.decks.failure)
        assertTrue(classifier.consulted.isEmpty())
    }

    @Test
    fun una_CancellationException_al_descargar_se_relanza_sin_consultar_al_clasificador() = runBlocking {
        val remote = FakeRemoteDataSource().apply { pullDecksFailure = CancellationException("cancelado") }
        val classifier = ScriptedClassifier(SyncFailureKind.NETWORK)

        assertFailsWith<CancellationException> { engine(remote, classifier)() }

        assertTrue(classifier.consulted.isEmpty(), "la cancelación no se clasifica")
    }

    @Test
    fun una_CancellationException_al_subir_se_relanza_sin_consultar_al_clasificador() = runBlocking {
        val remote = FakeRemoteDataSource().apply {
            pushDeckIdsThatFail = setOf("d0", "d1", "d2", "d3")
            pushFailureFactory = { CancellationException("cancelado") }
        }
        val classifier = ScriptedClassifier(SyncFailureKind.NETWORK)

        assertFailsWith<CancellationException> { engine(remote, classifier, pendingDecks(4))() }

        assertTrue(classifier.consulted.isEmpty(), "la cancelación no se clasifica")
        assertEquals(1, remote.calls.count { it == "pushDecks" })
    }
}
