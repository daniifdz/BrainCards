package org.braincards.app.domain.repository

/**
 * Cómo trata el MOTOR DE SYNC un fallo al hablar con el servidor. Es una decisión interna del motor, con tres resultados:
 * [NETWORK] (no hubo respuesta útil: sin conexión, timeout o fallo del lado del servidor; se corta tras varios seguidos),
 * [REJECTED] (el servidor rechazó la petición: sesión, permisos o datos) y [UNCLASSIFIED] (cualquier otra cosa).
 *
 * No confundir con `PullFailure` (la causa de un pull abortado que acaba en el resultado del sync: añade `LOCAL`, un fallo al guardar
 * en el dispositivo, que el clasificador no puede conocer) ni con `SyncErrorKind` (presentación: qué error muestra el indicador).
 */
enum class SyncFailureKind { NETWORK, REJECTED, UNCLASSIFIED }

/**
 * Traduce una excepción del acceso remoto a un [SyncFailureKind]. Existe para que el dominio no dependa de las excepciones de la
 * librería de red: la implementación vive en data, que sí las conoce. Se aplica solo a excepciones ordinarias; la cancelación de
 * corrutinas (`CancellationException`) se relanza antes de llegar aquí y nunca se clasifica.
 */
fun interface SyncFailureClassifier {
    fun classify(error: Exception): SyncFailureKind
}
