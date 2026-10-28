package org.braincards.app.presentation.sync

/** Lo que importa de una interfaz de red del sistema para decidir si hay conexion (Desktop). */
data class NetworkInterfaceInfo(
    val isUp: Boolean,
    val isLoopback: Boolean,
    val isVirtual: Boolean,
    /** Tiene alguna direccion que no es de loopback ni de enlace local (169.254.x.x / fe80::). */
    val hasUsableAddress: Boolean
)

/**
 * Desktop: hay "conexion" si alguna interfaz esta activa, no es loopback ni virtual y tiene una direccion usable.
 * Es una aproximacion barata (no sondea el servidor): una VPN o un adaptador activo sin salida real cuentan como red,
 * y el fallo del propio sync mas el backoff cubren esos casos.
 */
fun hasUsableNetwork(interfaces: List<NetworkInterfaceInfo>): Boolean =
    interfaces.any { it.isUp && !it.isLoopback && !it.isVirtual && it.hasUsableAddress }
