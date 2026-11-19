package org.braincards.app.data.repository

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.data.fakes.FakeCardSchedulingDao
import org.braincards.app.data.fakes.FakeTransactionRunner
import org.braincards.app.data.local.entity.CardSchedulingEntity
import org.braincards.app.domain.models.CardScheduling
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// LWW por fila (ver DeckRepositoryImplTest) + desempate, guard de flashcard inexistente,
// claim / pendientes / markPushed / release. El SQL real (JOIN, claim con dueno de flashcard)
// se prueba con Room real en jvmTest/CardSchedulingDaoSyncTest.
class CardSchedulingRepositoryImplTest {

    private fun ms(v: Long) = Instant.fromEpochMilliseconds(v)

    private fun localEntity(
        cardId: String,
        updatedAt: Long,
        repetitions: Int = 1,
        interval: Int = 1,
        nextReview: Long = 5000,
        userId: String? = "user-1",
        lastSyncedAt: Long? = null
    ) = CardSchedulingEntity(
        cardId = cardId, easeFactor = 2.5, interval = interval, repetitions = repetitions,
        nextReview = ms(nextReview), updatedAt = ms(updatedAt),
        userId = userId, lastSyncedAt = lastSyncedAt?.let { ms(it) }
    )

    private fun remote(
        cardId: String,
        updatedAt: Long,
        repetitions: Int = 1,
        interval: Int = 1,
        nextReview: Long = 5000
    ) = CardScheduling(
        cardId = cardId, easeFactor = 2.5, interval = interval, repetitions = repetitions,
        nextReview = ms(nextReview), updatedAt = ms(updatedAt),
        userId = "user-1", lastSyncedAt = null
    )

    private class Fixture {
        val dao = FakeCardSchedulingDao()
        val runner = FakeTransactionRunner()
        val repository = CardSchedulingRepositoryImpl(dao, runner)
    }

    @Test
    fun `applyRemotePull sobrescribe cuando el remoto es mas nuevo y marca lastSyncedAt`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("card-1")
        f.dao.seed(localEntity("card-1", updatedAt = 1000, repetitions = 1))

        val applied = f.repository.applyRemotePull(listOf(remote("card-1", updatedAt = 2000, repetitions = 7)))

        assertEquals(1, applied)
        val result = f.dao.get("card-1")!!
        assertEquals(7, result.repetitions)
        assertEquals(ms(2000), result.lastSyncedAt)
    }

    @Test
    fun `applyRemotePull conserva la fila local cuando es mas nueva`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("card-1")
        f.dao.seed(localEntity("card-1", updatedAt = 3000, repetitions = 1))

        val applied = f.repository.applyRemotePull(listOf(remote("card-1", updatedAt = 2000, repetitions = 9)))

        assertEquals(0, applied)
        val result = f.dao.get("card-1")!!
        assertEquals(1, result.repetitions)
        assertNull(result.lastSyncedAt)
    }

    @Test
    fun `applyRemotePull inserta la fila cuando no existe local`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("card-new")

        f.repository.applyRemotePull(listOf(remote("card-new", updatedAt = 1000)))

        val result = f.dao.get("card-new")!!
        assertEquals(ms(1000), result.lastSyncedAt)
        assertEquals("user-1", result.userId)
    }

    @Test
    fun `con updatedAt igual gana el remoto con mas repetitions`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("c")
        f.dao.seed(localEntity("c", updatedAt = 1000, repetitions = 2, interval = 99))

        f.repository.applyRemotePull(listOf(remote("c", updatedAt = 1000, repetitions = 3, interval = 1)))

        assertEquals(3, f.dao.get("c")!!.repetitions)
    }

    @Test
    fun `con updatedAt igual gana el local con mas repetitions`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("c")
        f.dao.seed(localEntity("c", updatedAt = 1000, repetitions = 3, interval = 1))

        val applied = f.repository.applyRemotePull(listOf(remote("c", updatedAt = 1000, repetitions = 2, interval = 99)))

        assertEquals(0, applied)
        assertEquals(3, f.dao.get("c")!!.repetitions)
        assertNull(f.dao.get("c")!!.lastSyncedAt)
    }

    @Test
    fun `con updatedAt y repetitions iguales desempata por interval`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("a")
        f.dao.seedFlashcard("b")
        f.dao.seed(localEntity("a", updatedAt = 1000, repetitions = 2, interval = 5))
        f.dao.seed(localEntity("b", updatedAt = 1000, repetitions = 2, interval = 9))

        f.repository.applyRemotePull(
            listOf(
                remote("a", updatedAt = 1000, repetitions = 2, interval = 6), // remoto mayor -> gana
                remote("b", updatedAt = 1000, repetitions = 2, interval = 8)  // remoto menor -> pierde
            )
        )

        assertEquals(6, f.dao.get("a")!!.interval)
        assertEquals(9, f.dao.get("b")!!.interval)
    }

    @Test
    fun `con updatedAt repetitions e interval iguales desempata por nextReview`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("a")
        f.dao.seedFlashcard("b")
        f.dao.seed(localEntity("a", updatedAt = 1000, nextReview = 5000))
        f.dao.seed(localEntity("b", updatedAt = 1000, nextReview = 5000))

        f.repository.applyRemotePull(
            listOf(
                remote("a", updatedAt = 1000, nextReview = 6000), // remoto mayor -> gana
                remote("b", updatedAt = 1000, nextReview = 4000)  // remoto menor -> pierde
            )
        )

        assertEquals(ms(6000), f.dao.get("a")!!.nextReview)
        assertEquals(ms(5000), f.dao.get("b")!!.nextReview)
    }

    @Test
    fun `con todo igual el remoto no se aplica`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("c")
        f.dao.seed(localEntity("c", updatedAt = 1000, lastSyncedAt = null))

        val applied = f.repository.applyRemotePull(listOf(remote("c", updatedAt = 1000)))

        assertEquals(0, applied)
        assertNull(f.dao.get("c")!!.lastSyncedAt)
    }

    @Test
    fun `applyRemotePull descarta las filas cuya flashcard no existe y aplica las demas del lote`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("existe")

        val applied = f.repository.applyRemotePull(
            listOf(remote("huerfana", updatedAt = 1000), remote("existe", updatedAt = 1000))
        )

        assertEquals(1, applied)
        assertNull(f.dao.get("huerfana"))
        assertEquals(ms(1000), f.dao.get("existe")!!.lastSyncedAt)
    }

    @Test
    fun `applyRemotePull aplica el scheduling aunque la flashcard este borrada logicamente`() = runBlocking {
        val f = Fixture()
        // La tabla flashcards del fake no distingue borradas: existir basta (como getExistingFlashcardIds).
        f.dao.seedFlashcard("tombstone")

        val applied = f.repository.applyRemotePull(listOf(remote("tombstone", updatedAt = 1000)))

        assertEquals(1, applied)
    }

    @Test
    fun `applyRemotePull ejecuta todo en una unica transaccion`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("a")
        f.dao.seedFlashcard("b")

        f.repository.applyRemotePull(listOf(remote("a", 1000), remote("b", 1000)))

        assertEquals(1, f.runner.transactionCount)
    }

    @Test
    fun `applyRemotePull con lista vacia no abre transaccion`() = runBlocking {
        val f = Fixture()

        assertEquals(0, f.repository.applyRemotePull(emptyList()))
        assertEquals(0, f.runner.transactionCount)
    }

    @Test
    fun `claimOrphanedScheduling solo toca filas sin dueno de flashcards del usuario`() = runBlocking {
        val f = Fixture()
        f.dao.seedFlashcard("mia", userId = "user-1")
        f.dao.seedFlashcard("otro", userId = "user-2")
        f.dao.seed(localEntity("mia", updatedAt = 1000, userId = null))
        f.dao.seed(localEntity("otro", updatedAt = 1000, userId = null))
        f.dao.seed(localEntity("ya-asignada", updatedAt = 1000, userId = "user-9"))

        val claimed = f.repository.claimOrphanedScheduling("user-1")

        assertEquals(1, claimed)
        assertEquals("user-1", f.dao.get("mia")!!.userId)
        assertNull(f.dao.get("otro")!!.userId)
        assertEquals("user-9", f.dao.get("ya-asignada")!!.userId)
    }

    @Test
    fun `getPendingSync devuelve nuevas y modificadas, no las sincronizadas ni las de otro usuario ni las de flashcard sin subir`() = runBlocking {
        val f = Fixture()
        listOf("nueva", "modificada", "sincronizada", "ajena", "flashcard-sin-subir").forEach {
            f.dao.seedFlashcard(it, userId = if (it == "ajena") "user-2" else "user-1", lastSyncedAt = if (it == "flashcard-sin-subir") null else ms(1))
        }
        f.dao.seed(localEntity("nueva", updatedAt = 1000, lastSyncedAt = null))
        f.dao.seed(localEntity("modificada", updatedAt = 3000, lastSyncedAt = 2000))
        f.dao.seed(localEntity("sincronizada", updatedAt = 2000, lastSyncedAt = 2000))
        f.dao.seed(localEntity("ajena", updatedAt = 1000, userId = "user-2"))
        f.dao.seed(localEntity("flashcard-sin-subir", updatedAt = 1000))

        val pending = f.repository.getPendingSync("user-1").map { it.cardId }.toSet()

        assertEquals(setOf("nueva", "modificada"), pending)
    }

    @Test
    fun `markPushed fija lastSyncedAt solo en los ids indicados`() = runBlocking {
        val f = Fixture()
        f.dao.seed(localEntity("a", updatedAt = 1000))
        f.dao.seed(localEntity("b", updatedAt = 1000))

        f.repository.markPushed(listOf("a"), ms(9000))

        assertEquals(ms(9000), f.dao.get("a")!!.lastSyncedAt)
        assertNull(f.dao.get("b")!!.lastSyncedAt)
    }

    @Test
    fun `releaseFromUser limpia userId y lastSyncedAt solo del usuario`() = runBlocking {
        val f = Fixture()
        f.dao.seed(localEntity("mia", updatedAt = 1000, userId = "user-1", lastSyncedAt = 1000))
        f.dao.seed(localEntity("otra", updatedAt = 1000, userId = "user-2", lastSyncedAt = 1000))

        val released = f.repository.releaseFromUser("user-1")

        assertEquals(1, released)
        assertNull(f.dao.get("mia")!!.userId)
        assertNull(f.dao.get("mia")!!.lastSyncedAt)
        assertEquals("user-2", f.dao.get("otra")!!.userId)
        assertEquals(ms(1000), f.dao.get("otra")!!.lastSyncedAt)
    }
}
