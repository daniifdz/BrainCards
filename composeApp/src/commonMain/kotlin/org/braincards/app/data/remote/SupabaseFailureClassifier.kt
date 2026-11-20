package org.braincards.app.data.remote

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import org.braincards.app.domain.repository.SyncFailureClassifier
import org.braincards.app.domain.repository.SyncFailureKind

/**
 * Clasificación según el código fuente de supabase-kt 3.1.4 y ktor-client-core 3.1.2:
 * - HttpRequestTimeoutException / HttpRequestException: no hubo respuesta HTTP (timeout o fallo de transporte: DNS,
 *   conexión rechazada, TLS...) -> NETWORK.
 * - PostgrestRestException con statusCode >= 500: hubo respuesta, pero el fallo es del lado de Supabase, no un dato
 *   inválido nuestro; mismo trato que un timeout -> NETWORK.
 * - RestException con statusCode < 500 (400/401/403/404/409...; PostgREST o Auth): rechazo real del cliente -> REJECTED.
 * - Cualquier otra cosa, incluido un 5xx de Auth (RestException que no es de PostgREST), -> UNCLASSIFIED.
 */
object SupabaseFailureClassifier : SyncFailureClassifier {
    override fun classify(error: Exception): SyncFailureKind = when {
        error is HttpRequestTimeoutException -> SyncFailureKind.NETWORK
        error is HttpRequestException -> SyncFailureKind.NETWORK
        error is PostgrestRestException && error.statusCode >= 500 -> SyncFailureKind.NETWORK
        error is RestException && error.statusCode < 500 -> SyncFailureKind.REJECTED
        else -> SyncFailureKind.UNCLASSIFIED
    }
}
