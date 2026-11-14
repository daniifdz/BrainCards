package org.braincards.app.presentation.screens.navigation

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.braincards.app.domain.models.AuthState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ultimo estado estable de auth (Loading no pisa al estable anterior). */
class StableAuthTest {

    private val signedIn = AuthState.Authenticated("u", "a@b.co", sessionConfirmed = true)
    private val none = AuthState.NotAuthenticated

    @Test
    fun hasta_el_primer_estado_estable_es_null() = runTest {
        assertEquals(listOf(null), flowOf(AuthState.Loading).stableAuth().toList())
    }

    @Test
    fun un_Loading_despues_de_un_estable_no_cambia_nada() = runTest {
        val seen = flowOf(AuthState.Loading, signedIn, AuthState.Loading, signedIn, AuthState.Loading).stableAuth().toList()
        assertEquals(listOf(null, signedIn), seen)
    }

    @Test
    fun un_cambio_real_Authenticated_a_NotAuthenticated_pasa_aunque_haya_Loading_entre_medias() = runTest {
        val seen = flowOf(signedIn, AuthState.Loading, none).stableAuth().toList()
        assertEquals(listOf(null, signedIn, none), seen)
    }

    @Test
    fun el_arranque_sin_sesion_es_un_estable_NotAuthenticated() = runTest {
        assertEquals(listOf(null, none), flowOf(AuthState.Loading, none, AuthState.Loading).stableAuth().toList())
    }
}
