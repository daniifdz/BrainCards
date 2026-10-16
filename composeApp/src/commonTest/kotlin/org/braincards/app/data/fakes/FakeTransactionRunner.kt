package org.braincards.app.data.fakes

import org.braincards.app.data.local.TransactionRunner

/** Fake de TransactionRunner: ejecuta el bloque tal cual y cuenta las transacciones abiertas. */
class FakeTransactionRunner : TransactionRunner {
    var transactionCount = 0
        private set

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        transactionCount++
        return block()
    }
}
