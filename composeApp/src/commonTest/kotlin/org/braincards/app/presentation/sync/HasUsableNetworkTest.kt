package org.braincards.app.presentation.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Desktop: ¿hay alguna interfaz de red utilizable? */
class HasUsableNetworkTest {

    private fun nic(
        up: Boolean = true,
        loopback: Boolean = false,
        virtual: Boolean = false,
        address: Boolean = true
    ) = NetworkInterfaceInfo(isUp = up, isLoopback = loopback, isVirtual = virtual, hasUsableAddress = address)

    @Test
    fun sin_interfaces_no_hay_red() {
        assertFalse(hasUsableNetwork(emptyList()))
    }

    @Test
    fun una_interfaz_activa_real_con_direccion_es_red() {
        assertTrue(hasUsableNetwork(listOf(nic())))
    }

    @Test
    fun el_loopback_no_cuenta() {
        assertFalse(hasUsableNetwork(listOf(nic(loopback = true))))
    }

    @Test
    fun una_interfaz_caida_no_cuenta() {
        assertFalse(hasUsableNetwork(listOf(nic(up = false))))
    }

    @Test
    fun una_interfaz_virtual_no_cuenta() {
        assertFalse(hasUsableNetwork(listOf(nic(virtual = true))))
    }

    @Test
    fun una_interfaz_sin_direccion_usable_no_cuenta() {
        assertFalse(hasUsableNetwork(listOf(nic(address = false))))
    }

    @Test
    fun basta_con_una_buena_entre_varias_malas() {
        val list = listOf(nic(loopback = true), nic(virtual = true), nic(up = false), nic(address = false), nic())

        assertTrue(hasUsableNetwork(list))
    }
}
