package org.braincards.app.domain.fakes

import kotlinx.coroutines.delay
import org.braincards.app.domain.repository.LocalDataRepository
import kotlin.time.Duration

/**
 * Fake de LocalDataRepository. [callLog] se comparte con FakeAuthRepository para comprobar el
 * orden entre el borrado y el cierre de sesion. La atomicidad y el SQL reales se prueban en
 * jvmTest (LocalDataRepositoryRoomTest) con Room.
 */
class FakeLocalDataRepository(
    private val callLog: MutableList<String>? = null
) : LocalDataRepository {

    var wipeFailure: Throwable? = null

    // Si no es null, wipeAll() espera este tiempo (delay, tiempo virtual) antes de terminar.
    var wipeDelay: Duration? = null

    var unownedRows = 0

    var wipeCallCount = 0

    // true solo si wipeAll() llego al final sin fallar ni cancelarse.
    var wipeCompleted = false

    override suspend fun wipeAll() {
        callLog?.add("wipeAll")
        wipeCallCount++
        wipeDelay?.let { delay(it) }
        wipeFailure?.let { throw it }
        wipeCompleted = true
    }

    override suspend fun countUnownedRows(): Int = unownedRows
}
