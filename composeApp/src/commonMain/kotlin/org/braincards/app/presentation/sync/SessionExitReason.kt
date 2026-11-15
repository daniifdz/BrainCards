package org.braincards.app.presentation.sync

/**
 * Por que salio el USUARIO de su sesion (cierre de sesion o borrado de cuenta confirmados). Una sesion que
 * desaparece sin ninguno de los dos es una sesion perdida (caducada o revocada) y no lleva motivo.
 * Lo publica [SyncCoordinator] y lo lee AppEntryViewModel para decidir el aviso de la Bienvenida.
 */
enum class SessionExitReason { SignedOut, AccountDeleted }
