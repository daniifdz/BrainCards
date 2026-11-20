package org.braincards.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MailtoUriTest {

    @Test
    fun destinatario_asunto_y_cuerpo_simples() {
        assertEquals("mailto:a@b.es?subject=Hola&body=Adios", buildMailtoUri("a@b.es", "Hola", "Adios"))
    }

    @Test
    fun el_espacio_se_codifica_como_20_y_nunca_como_mas() {
        val uri = buildMailtoUri("a@b.es", "Feedback para BrainCards v1.0.0", "uno dos")
        assertEquals("mailto:a@b.es?subject=Feedback%20para%20BrainCards%20v1.0.0&body=uno%20dos", uri)
        assertFalse('+' in uri)
    }

    @Test
    fun un_mas_literal_se_codifica_como_2B() {
        assertEquals("mailto:a@b.es?subject=a%2Bb&body=", buildMailtoUri("a@b.es", "a+b", ""))
    }

    @Test
    fun los_saltos_de_linea_y_la_puntuacion_del_cuerpo_se_codifican() {
        val uri = buildMailtoUri("a@b.es", "s", "Hola, tengo una sugerencia/problema:\n\n")
        assertEquals("mailto:a@b.es?subject=s&body=Hola%2C%20tengo%20una%20sugerencia%2Fproblema%3A%0A%0A", uri)
    }

    @Test
    fun los_acentos_y_la_enie_se_codifican_en_utf8() {
        val uri = buildMailtoUri("a@b.es", "Sugerencia para el año", "¿Qué?")
        assertEquals("mailto:a@b.es?subject=Sugerencia%20para%20el%20a%C3%B1o&body=%C2%BFQu%C3%A9%3F", uri)
    }

    @Test
    fun los_caracteres_reservados_no_pueden_romper_la_consulta() {
        val uri = buildMailtoUri("a@b.es", "a&body=x", "b?c=d")
        assertEquals("mailto:a@b.es?subject=a%26body%3Dx&body=b%3Fc%3Dd", uri)
    }
}
