package org.braincards.app.presentation.screens.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validacion del cliente de los formularios de auth. CRITERIO del correo (permisivo; el servidor decide): se recorta
 * y solo se exige UNA '@', parte local y dominio no vacios, sin espacios por dentro y un dominio con un punto con
 * texto a ambos lados. No se rechaza nada mas (+, guiones, subdominios...). La contraseña NO se recorta.
 */
class AuthValidationTest {

    @Test
    fun correos_validos_incluidos_los_raros_pero_legales() {
        listOf(
            "daniifdz@ejemplo.com", "a@b.co", "nombre+etiqueta@dominio.es", "nombre.apellido@sub.dominio.com",
            "x_y-z@mi-dominio.org", "UPPER@CASE.COM", "a@b.c"
        ).forEach { assertNull(validateEmail(it), "debe ser valido: $it") }
    }

    @Test
    fun se_recorta_antes_de_validar() {
        assertNull(validateEmail("  a@b.co  "))
        assertEquals(AuthMessage.EmailEmpty, validateEmail("   "))
    }

    @Test
    fun vacio_es_EmailEmpty() {
        assertEquals(AuthMessage.EmailEmpty, validateEmail(""))
    }

    @Test
    fun el_caso_del_mockup_sin_punto_en_el_dominio_es_invalido() {
        assertEquals(AuthMessage.EmailInvalid, validateEmail("daniifdz@ejemplo"))
    }

    @Test
    fun formatos_invalidos() {
        listOf(
            "sin-arroba.com", "dos@@arrobas.com", "a@b@c.com", "@dominio.com", "usuario@", "usuario@.com",
            "usuario@dominio.", "con espacio@dominio.com", "usuario@do minio.com"
        ).forEach { assertEquals(AuthMessage.EmailInvalid, validateEmail(it), "debe ser invalido: $it") }
    }

    @Test
    fun contrasena_nueva_exactamente_8_es_valida_y_7_no() {
        assertNull(validateNewPassword("12345678"))
        assertEquals(AuthMessage.PasswordTooShort, validateNewPassword("1234567"))
    }

    @Test
    fun contrasena_nueva_vacia_es_PasswordEmpty() {
        assertEquals(AuthMessage.PasswordEmpty, validateNewPassword(""))
    }

    @Test
    fun la_contrasena_no_se_recorta_los_espacios_cuentan() {
        assertNull(validateNewPassword("1234 678"))
        assertNull(validateNewPassword("        ")) // 8 espacios: lo decide el servidor, no el cliente
    }

    @Test
    fun el_login_no_exige_longitud_minima() {
        assertNull(validateLoginPassword("123"))
        assertNull(validateLoginPassword("1"))
        assertEquals(AuthMessage.PasswordEmpty, validateLoginPassword(""))
    }

    @Test
    fun confirmacion() {
        assertNull(validateConfirmation("12345678", "12345678"))
        assertEquals(AuthMessage.PasswordsDontMatch, validateConfirmation("12345678", "1234567"))
        assertEquals(AuthMessage.PasswordsDontMatch, validateConfirmation("12345678", "12345678 "))
    }

    @Test
    fun codigo_son_exactamente_6_digitos() {
        assertNull(validateCode("123456"))
        assertEquals(AuthMessage.CodeIncomplete, validateCode("12345"))
        assertEquals(AuthMessage.CodeIncomplete, validateCode("1234567"))
        assertEquals(AuthMessage.CodeIncomplete, validateCode("12345a"))
        assertEquals(AuthMessage.CodeIncomplete, validateCode(""))
    }

    @Test
    fun formulario_de_login_valido_no_tiene_errores() {
        assertTrue(validateLoginForm("a@b.co", "x").isEmpty)
    }

    @Test
    fun formulario_de_login_con_todo_mal_da_un_error_por_campo() {
        val errors = validateLoginForm("", "")
        assertEquals(AuthMessage.EmailEmpty, errors[AuthField.EMAIL])
        assertEquals(AuthMessage.PasswordEmpty, errors[AuthField.PASSWORD])
    }

    @Test
    fun el_registro_si_exige_el_minimo_pero_el_login_no() {
        assertTrue(validateLoginForm("a@b.co", "1234567").isEmpty)
        assertEquals(AuthMessage.PasswordTooShort, validateRegisterForm("a@b.co", "1234567")[AuthField.PASSWORD])
    }

    @Test
    fun el_error_de_formato_del_correo_se_atribuye_al_campo_correo() {
        val errors = validateRegisterForm("daniifdz@ejemplo", "12345678")
        assertEquals(AuthMessage.EmailInvalid, errors[AuthField.EMAIL])
        assertNull(errors[AuthField.PASSWORD])
    }

    @Test
    fun el_primer_campo_con_error_sigue_el_orden_de_foco() {
        val order = listOf(AuthField.EMAIL, AuthField.PASSWORD)
        assertEquals(AuthField.EMAIL, validateRegisterForm("", "1").firstIn(order))
        assertEquals(AuthField.PASSWORD, validateRegisterForm("a@b.co", "1").firstIn(order))
        assertNull(validateRegisterForm("a@b.co", "12345678").firstIn(order))
    }

    @Test
    fun editar_un_campo_quita_solo_su_error() {
        val errors = validateRegisterForm("", "1")
        val afterEditingEmail = errors.without(AuthField.EMAIL)
        assertNull(afterEditingEmail[AuthField.EMAIL])
        assertEquals(AuthMessage.PasswordTooShort, afterEditingEmail[AuthField.PASSWORD])
    }
}
