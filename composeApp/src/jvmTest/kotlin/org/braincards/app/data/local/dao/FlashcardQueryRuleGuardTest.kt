package org.braincards.app.data.local.dao

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guarda de la regla unica de "tarjeta activa" (deuda (e)): cada consulta `@Query` de FlashcardDAO.kt es una lectura que
 * DEBE usar `LIVE_DECK_JOIN` (lista [ENFORCED]) o una de las permitidas sin ella (lista [ALLOWED], con el motivo). Una
 * consulta nueva que no este en ninguna lista falla: obliga a decidir. Lee el codigo fuente; no depende del formato
 * (espacios, saltos de linea ni orden): solo busca la anotacion al principio de linea, el siguiente `fun nombre` y si
 * entre medias aparece el identificador `LIVE_DECK_JOIN`.
 */
class FlashcardQueryRuleGuardTest {

    private companion object {
        /** Lecturas de estudio, recuento y estadisticas: deben aplicar la regla. */
        val ENFORCED = setOf(
            "getFlashcardById", "getDueFlashcardsByDeck", "getAllFlashcards", "getAllDueFlashcards", "observeDueCount",
            "observeMaturityCounts"
        )

        /** Sin la regla A PROPOSITO. */
        val ALLOWED = mapOf(
            "getFlashcardByIdIncludingDeleted" to "sync: debe ver tombstones",
            "getFlashcardsByDeckId" to "deleteDeck/deleteTopic: el mazo ya esta marcado cuando se leen sus tarjetas",
            "softdeleteFlashcardById" to "escritura",
            "claimOrphanedFlashcards" to "sync",
            "getPendingSyncFlashcards" to "sync: debe subir tarjetas vivas bajo un mazo en tombstone",
            "observePendingStamp" to "sync: idem",
            "markFlashcardsPushed" to "sync",
            "releaseFlashcardsFromUser" to "sync",
            "deleteAllFlashcards" to "cierre de sesion",
            "countUnownedFlashcards" to "sync",
            "hasAnyFlashcard" to "sync",
            "getExistingDeckIds" to "guarda del pull (ids de mazos; no es una lectura de tarjetas)",
            "getExistingTopicIds" to "guarda del pull (ids de temas; no es una lectura de tarjetas)"
        )
    }

    private fun daoSource(): String {
        val relative = "composeApp/src/commonMain/kotlin/org/braincards/app/data/local/dao/FlashcardDAO.kt"
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .take(6)
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
        assertNotNull(file, "no se encuentra $relative subiendo desde ${File("").absolutePath}")
        return file.readText()
    }

    /** nombre del metodo -> ¿su consulta usa LIVE_DECK_JOIN? */
    private fun queries(): Map<String, Boolean> {
        val source = daoSource()
        val starts = Regex("""(?m)^\s*@Query\(""").findAll(source).map { it.range.first }.toList()
        assertTrue(starts.isNotEmpty(), "no se ha encontrado ninguna @Query: ¿cambio el formato del fichero?")
        return starts.associate { start ->
            val tail = source.substring(start)
            val fn = Regex("""\bfun\s+(\w+)""").find(tail)
            assertNotNull(fn, "una @Query sin metodo")
            val body = tail.substring(0, fn.range.first)
            fn.groupValues[1] to body.contains("LIVE_DECK_JOIN")
        }
    }

    @Test
    fun las_lecturas_de_estudio_recuento_y_estadisticas_usan_la_regla() {
        val q = queries()
        ENFORCED.forEach { name ->
            assertTrue(name in q, "la consulta $name ya no existe: actualiza la lista ENFORCED")
            assertTrue(q.getValue(name), "la consulta $name debe usar LIVE_DECK_JOIN (tarjetas activas)")
        }
    }

    @Test
    fun las_consultas_permitidas_sin_la_regla_no_la_usan_por_error_y_existen() {
        val q = queries()
        ALLOWED.keys.forEach { name ->
            assertTrue(name in q, "la consulta permitida $name ya no existe: actualiza la lista ALLOWED")
            assertEquals(false, q.getValue(name), "$name esta en ALLOWED (${ALLOWED[name]}) pero usa LIVE_DECK_JOIN: ¿pasa a ENFORCED?")
        }
    }

    // Mutacion: anadir una consulta de lectura nueva sin clasificar debe fallar aqui.
    @Test
    fun toda_consulta_esta_clasificada_una_nueva_obliga_a_decidir_si_aplica_la_regla() {
        val unclassified = queries().keys - ENFORCED - ALLOWED.keys
        assertEquals(emptySet(), unclassified, "consultas sin clasificar en FlashcardDAO.kt: $unclassified")
    }
}
