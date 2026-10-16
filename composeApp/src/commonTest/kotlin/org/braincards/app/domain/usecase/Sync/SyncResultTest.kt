package org.braincards.app.domain.usecase.Sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Los helpers que usan isFullySuccessful() y AuthViewModel.toUserMessage() tienen que
 * contar las CUATRO tablas, incluida card_scheduling.
 */
class SyncResultTest {

    private val okPull = PullTableResult(applied = 0, aborted = false)
    private val abortedPull = PullTableResult(applied = 0, aborted = true)
    private val okPush = PushTableResult(succeeded = 0, failed = 0)

    private fun pull(
        decks: PullTableResult = okPull,
        topics: PullTableResult = okPull,
        flashcards: PullTableResult = okPull,
        cardScheduling: PullTableResult = okPull
    ) = PullResult(decks, topics, flashcards, cardScheduling)

    private fun push(
        decks: PushTableResult = okPush,
        topics: PushTableResult = okPush,
        flashcards: PushTableResult = okPush,
        cardScheduling: PushTableResult = okPush
    ) = PushResult(decks, topics, flashcards, cardScheduling)

    @Test
    fun `todo correcto es totalmente exitoso`() {
        assertTrue(SyncResult.Completed(pull(), push()).isFullySuccessful())
    }

    @Test
    fun `un pull de scheduling abortado cuenta y rompe el exito total`() {
        val result = SyncResult.Completed(pull(cardScheduling = abortedPull), push())

        assertEquals(1, result.pull.abortedTableCount())
        assertFalse(result.isFullySuccessful())
    }

    @Test
    fun `filas de scheduling sin subir cuentan y rompen el exito total`() {
        val result = SyncResult.Completed(pull(), push(cardScheduling = PushTableResult(succeeded = 3, failed = 2)))

        assertEquals(2, result.push.failedRowCount())
        assertFalse(result.isFullySuccessful())
    }

    @Test
    fun `los contadores suman las cuatro tablas`() {
        val pull = pull(decks = abortedPull, topics = abortedPull, flashcards = abortedPull, cardScheduling = abortedPull)
        val push = push(
            decks = PushTableResult(0, 1), topics = PushTableResult(0, 2),
            flashcards = PushTableResult(0, 3), cardScheduling = PushTableResult(0, 4)
        )

        assertEquals(4, pull.abortedTableCount())
        assertEquals(10, push.failedRowCount())
    }
}
