package org.braincards.app.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guarda ESTRUCTURAL del repaso libre (paso 14): lo unico que guarda un repaso es `ReviewFlashcardUseCase`, y solo puede referenciarse
 * desde `ScheduledRecorder` (ademas de su propia definicion y del modulo de inyeccion). Si el StudyViewModel (o cualquier otro fichero)
 * lo usara directamente, el repaso libre podria escribir en `card_scheduling` / `reviewlog`. Lee el codigo fuente y IGNORA los
 * comentarios.
 */
class ReviewFlashcardUseCaseGuardTest {

    private companion object {
        /** Unicos ficheros de produccion que pueden nombrar el caso de uso. */
        val ALLOWED = setOf(
            "reviewFlashcardsUseCase.kt" to "su propia definicion",
            "ScheduledRecorder.kt" to "el unico recorder que guarda",
            "AppModule.kt" to "inyeccion de dependencias (lo construye y se lo da al ScheduledRecorder)"
        ).map { it.first }.toSet()
    }

    private fun mainSources(): List<File> {
        val relative = "composeApp/src/commonMain/kotlin"
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .take(6).map { File(it, relative) }.firstOrNull { it.isDirectory }
        assertNotNull(root, "no se encuentra $relative subiendo desde ${File("").absolutePath}")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun stripComments(text: String): String =
        text.replace(Regex("""/\*[\s\S]*?\*/"""), "").replace(Regex("""(?m)//.*$"""), "")

    @Test
    fun solo_ScheduledRecorder_usa_el_caso_de_uso_que_guarda_el_repaso() {
        val offenders = mainSources()
            .filter { Regex("""\bReviewFlashcardUseCase\b""").containsMatchIn(stripComments(it.readText())) }
            .map { it.name }
            .filter { it !in ALLOWED }
        assertEquals(emptyList(), offenders, "ficheros que usan ReviewFlashcardUseCase sin estar permitidos")
    }

    @Test
    fun el_StudyViewModel_no_conoce_el_caso_de_uso_que_guarda() {
        val vm = mainSources().first { it.name == "StudyVM.kt" }
        val code = stripComments(vm.readText())
        assertTrue(!code.contains("ReviewFlashcardUseCase") && !code.contains("reviewFlashcardUseCase"), "StudyVM.kt no debe usarlo")
        assertTrue(code.contains("RatingRecorder"), "StudyVM.kt persiste solo a traves de un RatingRecorder")
    }

    @Test
    fun el_recorder_libre_no_tiene_dependencias_y_solo_scheduled_usa_el_caso_de_uso() {
        val free = stripComments(mainSources().first { it.name == "RatingRecorder.kt" }.readText())
        assertTrue(free.contains("object FreeRecorder"), "FreeRecorder es un object sin dependencias")
        val scheduled = stripComments(mainSources().first { it.name == "ScheduledRecorder.kt" }.readText())
        assertTrue(scheduled.contains("ReviewFlashcardUseCase"))
    }
}
