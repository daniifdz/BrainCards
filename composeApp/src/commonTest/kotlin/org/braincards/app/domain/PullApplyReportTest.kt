package org.braincards.app.domain

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.braincards.app.domain.fakes.FakeCardSchedulingRepository
import org.braincards.app.domain.models.CardScheduling
import org.braincards.app.domain.models.PullApplyReport
import kotlin.test.Test
import kotlin.test.assertEquals

/** Valores por defecto del informe y método por defecto del repositorio. */
class PullApplyReportTest {

    @Test
    fun `discardedIds tiene valor por defecto vacio y no rompe a quien construye el informe a mano`() {
        val report = PullApplyReport(applied = 3, discarded = 2)

        assertEquals(emptyList(), report.discardedIds)
        assertEquals(PullApplyReport(applied = 3, discarded = 2, discardedIds = emptyList()), report)
        assertEquals(PullApplyReport(applied = 0, discarded = 0, discardedIds = emptyList()), PullApplyReport(0))
    }

    @Test
    fun `la implementacion por defecto de applyRemotePullReporting delega en applyRemotePull sin descartes`() = runBlocking {
        val repository = FakeCardSchedulingRepository()
        val t = Instant.fromEpochMilliseconds(1000)
        val rows = listOf(
            CardScheduling("c1", 2.5, 1, 1, t, t, "user-1", null),
            CardScheduling("c2", 2.5, 1, 1, t, t, "user-1", null)
        )

        val report = repository.applyRemotePullReporting(rows)

        assertEquals(PullApplyReport(applied = repository.applyRemotePullCalls.single().size), report)
        assertEquals(0, report.discarded)
        assertEquals(emptyList(), report.discardedIds)
    }
}
