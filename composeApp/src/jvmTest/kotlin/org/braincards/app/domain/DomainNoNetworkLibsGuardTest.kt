package org.braincards.app.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guarda de la frontera del dominio: ningún fichero de `domain/` importa librerías de red (Ktor, Supabase) ni las capas de datos y
 * presentación. Las excepciones de red se traducen en data (`SupabaseFailureClassifier`) y el dominio solo conoce
 * `SyncFailureClassifier`. Lee el código fuente y mira solo las líneas `import`.
 */
class DomainNoNetworkLibsGuardTest {

    private val domainDir: File
        get() {
            val relative = "composeApp/src/commonMain/kotlin/org/braincards/app/domain"
            val found = generateSequence(File("").absoluteFile) { it.parentFile }.take(6).map { File(it, relative) }.firstOrNull { it.isDirectory }
            assertNotNull(found, "no se encuentra $relative subiendo desde ${File("").absolutePath}")
            return found
        }

    private fun importsOf(file: File): List<String> =
        file.readLines().map { it.trim() }.filter { it.startsWith("import ") }

    private fun violations(forbiddenPrefixes: List<String>): List<String> =
        domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            importsOf(file).filter { line -> forbiddenPrefixes.any { line.removePrefix("import ").startsWith(it) } }
                .map { "${file.name}: $it" }
        }.toList()

    @Test
    fun el_guardian_lee_el_dominio_de_verdad() {
        val files = domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.size > 50, "se esperaban muchos ficheros en domain/ y salieron ${files.size}")
        assertTrue(files.any { it.name == "SyncEngineUseCase.kt" })
    }

    @Test
    fun el_dominio_no_importa_ktor_ni_supabase() {
        val found = violations(listOf("io.ktor", "io.github.jan"))
        assertTrue(found.isEmpty(), "el dominio no puede importar librerías de red: $found")
    }

    @Test
    fun el_dominio_no_importa_data_ni_presentation() {
        val found = violations(listOf("org.braincards.app.data", "org.braincards.app.presentation"))
        assertTrue(found.isEmpty(), "el dominio no puede importar data ni presentation: $found")
    }
}
