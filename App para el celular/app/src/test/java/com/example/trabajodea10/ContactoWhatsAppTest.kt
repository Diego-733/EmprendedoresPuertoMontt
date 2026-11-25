package com.example.trabajodea10

import com.example.trabajodea10.data.ContactoWhatsApp
import com.example.trabajodea10.data.ModoContacto
import com.example.trabajodea10.data.Tienda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WhatsApp de cada tienda: formato chileno, sin números inventados ni de respaldo. */
class ContactoWhatsAppTest {
    @Test
    fun aceptaElFormatoChilenoConEspaciosYSinMas56() {
        assertEquals("56912345678", ContactoWhatsApp.normalizar("+56 9 1234 5678"))
        assertEquals("56912345678", ContactoWhatsApp.normalizar("56912345678"))
        assertEquals("56912345678", ContactoWhatsApp.normalizar("9 1234 5678"))
        assertEquals("56912345678", ContactoWhatsApp.normalizar("0056 9 1234-5678"))
        assertEquals("56652123456", ContactoWhatsApp.normalizar("+56 65 212 3456"))
        assertEquals("+56 9 1234 5678", ContactoWhatsApp.formatear("56912345678"))
        assertEquals("+56 65 212 3456", ContactoWhatsApp.formatear("+56652123456"))
    }

    @Test
    fun rechazaVacioIncompletoOExtranjero() {
        for (malo in listOf(null, "", "   ", "12345678", "+56 9 1234 567", "+54 9 11 1234 5678", "+56 1 2345 6789", "hola")) {
            assertNull("debía rechazar «$malo»", ContactoWhatsApp.normalizar(malo))
        }
    }

    @Test
    fun laTiendaSinNumeroNoOfreceWhatsAppYLoAvisa() {
        val sinNumero = Tienda(contactoModo = ModoContacto.AMBOS)
        assertEquals("", sinNumero.whatsapp)   // ya no trae un número inventado por defecto
        assertFalse(sinNumero.ofreceWhatsApp)
        assertTrue(sinNumero.faltaWhatsApp)
        assertTrue(sinNumero.ofreceFormulario)
        val invalida = Tienda(whatsapp = "1234", contactoModo = ModoContacto.WHATSAPP)
        assertFalse(invalida.ofreceWhatsApp)
        assertTrue(invalida.faltaWhatsApp)
        val conNumero = Tienda(whatsapp = "+56 9 8765 1111", contactoModo = ModoContacto.WHATSAPP)
        assertTrue(conNumero.ofreceWhatsApp)
        assertFalse(conNumero.faltaWhatsApp)
        assertFalse(Tienda(whatsapp = "+56 9 8765 1111", contactoModo = ModoContacto.FORMULARIO).faltaWhatsApp)
    }

    @Test
    fun elEnlaceVaAlNumeroDeEsaTiendaConElMensaje() {
        assertEquals("https://wa.me/56987651111?text=Hola%20Tejidos%2C%20me%20interesa", ContactoWhatsApp.enlace("+56 9 8765 1111", "Hola Tejidos, me interesa"))
        assertNull(ContactoWhatsApp.enlace("", "Hola"))
    }
}
