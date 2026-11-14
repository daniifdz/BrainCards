package org.braincards.app.presentation.screens.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
import org.braincards.app.domain.models.AuthState

/**
 * Ultimo estado ESTABLE de auth: `null` hasta el primer estado distinto de [AuthState.Loading]; despues, un Loading
 * (Android lo emite al volver de segundo plano) NO cambia nada y se conserva el ultimo estable. Un cambio real
 * (Authenticated -> NotAuthenticated) si pasa. Lo comparten SyncStatusViewModel y AppEntryViewModel.
 */
fun Flow<AuthState>.stableAuth(): Flow<AuthState?> =
    scan<AuthState, AuthState?>(null) { lastStable, next -> if (next is AuthState.Loading) lastStable else next }
        .distinctUntilChanged()
