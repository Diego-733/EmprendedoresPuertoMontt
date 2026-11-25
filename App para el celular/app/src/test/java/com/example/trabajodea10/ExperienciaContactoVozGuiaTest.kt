package com.example.trabajodea10

import com.example.trabajodea10.data.Categoria
import com.example.trabajodea10.data.ContactoWhatsApp
import com.example.trabajodea10.data.GuiaProducto
import com.example.trabajodea10.data.GuiaProducto.Intencion
import com.example.trabajodea10.data.GuiaProducto.Respuesta
import com.example.trabajodea10.data.ModoContacto
import com.example.trabajodea10.data.Producto
import com.example.trabajodea10.data.ReglasBusqueda
import com.example.trabajodea10.data.ReglasVitrina
import com.example.trabajodea10.data.Tienda
import com.example.trabajodea10.data.Variante
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contacto claro, búsqueda por voz o texto y guía del primer producto (misma lógica que la web). */
class ExperienciaContactoVozGuiaTest {

    // ---------- A qué tienda le escribo

    @Test
    fun unNumeroDeLosDatosDeEjemploNoAbreWhatsApp() {
        for (demo in listOf("+56 9 8765 4321", "+56991234567", "+56 9 7654 3210", "+56 9 6543 2109", "+56 9 1234 5678", "+56 9 0000 0000", "+56 9 6123 4505", "+56 9 6123 4520")) {
            assertTrue("$demo es de ejemplo", ContactoWhatsApp.esDemostracion(demo))
            assertNull("$demo no debe abrir WhatsApp", ContactoWhatsApp.enlace(demo, "Hola"))
        }
        for (real in listOf("+56 9 4821 7735", "+56 9 6123 4599", "+56 65 212 3456")) assertFalse(real, ContactoWhatsApp.esDemostracion(real))
        val conEjemplo = Tienda(nombre = "Minimarket Ejemplo", whatsapp = "+56 9 8765 4321", contactoModo = ModoContacto.AMBOS)
        assertFalse(conEjemplo.ofreceWhatsApp)
        assertTrue(conEjemplo.ofreceFormulario)
        assertEquals(ContactoWhatsApp.SIN_WHATSAPP, conEjemplo.avisoContacto)
    }

    @Test
    fun losRotulosDicenDestinoYCanalYSinCanalSeExplicaEnUnaFrase() {
        assertEquals("Enviar WhatsApp a Tejidos Marta", ContactoWhatsApp.etiquetaWhatsApp("Tejidos Marta"))
        assertEquals("Enviar mensaje a Tejidos Marta", ContactoWhatsApp.etiquetaMensaje("Tejidos Marta"))
        val soloMensajes = Tienda(whatsapp = "+56 9 4821 7735", contactoModo = ModoContacto.FORMULARIO)
        assertFalse(soloMensajes.ofreceWhatsApp)
        assertEquals("", soloMensajes.avisoContacto)
        val sinCanal = Tienda(whatsapp = "1234", contactoModo = ModoContacto.WHATSAPP)
        assertTrue(sinCanal.sinCanalContacto)
        assertEquals(ContactoWhatsApp.SIN_CONTACTO, sinCanal.avisoContacto)
        val valida = Tienda(whatsapp = "+56 9 4821 7735", contactoModo = ModoContacto.AMBOS)
        assertTrue(valida.ofreceWhatsApp)
        assertEquals("", valida.avisoContacto)
    }

    // ---------- Búsqueda (lo escrito y lo dictado pasan por la misma regla)

    @Test
    fun laBusquedaEsPorPalabrasSinTildesNiMuletillas() {
        val torta = listOf("Torta de chocolate para cumpleaños", "Repostería Sureña")
        assertTrue(ReglasBusqueda.coincide("torta de cumpleaños", torta))
        assertTrue(ReglasBusqueda.coincide("Quiero una TORTA de cumpleanos", torta))
        assertTrue(ReglasBusqueda.coincide("tortas", torta))
        assertTrue(ReglasBusqueda.coincide("pan amasado", listOf("Pan amasado")))
        assertTrue(ReglasBusqueda.coincide("", listOf("lo que sea")))
        assertFalse(ReglasBusqueda.coincide("torta de frambuesa", torta))
        assertEquals(listOf("torta", "cumpleanos"), ReglasBusqueda.palabras("una torta de cumpleaños"))
    }

    @Test
    fun elDirectorioEncuentraLaTiendaPorLoQueVende() {
        val reposteria = Tienda(id = "t1", nombre = "Dulces Marta", rubro = "Repostería")
        val barberia = Tienda(id = "t2", nombre = "Barbería Norte", rubro = "Barbería")
        val torta = Producto(id = "p1", tiendaId = "t1", nombre = "Torta de chocolate para cumpleaños", categoria = Categoria.GOURMET,
            precio = 18000.0, publicado = true, destacado = false, colorFoto = 0, variantes = listOf(Variante("v1", "Única", 2)))
        val productos = mapOf("t1" to listOf(torta))
        val r = ReglasVitrina.filtrarTiendas(listOf(reposteria, barberia), "torta de cumpleaños", null, null, null) { productos[it].orEmpty() }
        assertEquals(listOf("t1"), r.map { it.id })
        assertEquals(listOf("t2"), ReglasVitrina.filtrarTiendas(listOf(reposteria, barberia), "barberia", null, null, null).map { it.id })
    }

    // ---------- Guía del primer producto

    @Test
    fun laGuiaEntiendeFrasesNaturalesYOfreceDosOpcionesSiHayDuda() {
        assertEquals(Respuesta.Ir(Intencion.PRODUCTO), GuiaProducto.interpretar("quiero vender algo"))
        assertEquals(Respuesta.Ir(Intencion.PRODUCTO), GuiaProducto.interpretar("Quiero subir un producto"))
        assertEquals(Respuesta.Ir(Intencion.FOTO), GuiaProducto.interpretar("¿Cómo agrego una foto?"))
        assertEquals(Respuesta.Ir(Intencion.PRODUCTO), GuiaProducto.interpretar("subir un producto con foto"))
        assertEquals(Respuesta.Elegir(listOf(Intencion.PRODUCTO, Intencion.VENTA)), GuiaProducto.interpretar("vender"))
        assertEquals(Respuesta.Elegir(listOf(Intencion.PRODUCTO, Intencion.FOTO)), GuiaProducto.interpretar("no sé"))
        assertEquals(Respuesta.Ir(Intencion.VISIBILIDAD), GuiaProducto.interpretar("que solo yo lo vea"))
    }

    @Test
    fun laGuiaSoloOfreceLoQueLaCuentaPuedeHacer() {
        val sinVentas = Intencion.entries.filter { it != Intencion.VENTA }.toSet()
        assertEquals(Respuesta.Ir(Intencion.PRODUCTO), GuiaProducto.interpretar("vender", sinVentas))
        val r = GuiaProducto.interpretar("vendí dos panes", sinVentas)
        assertTrue(r !is Respuesta.Ir || r.intencion != Intencion.VENTA)
    }

    @Test
    fun cadaPasoDelAltaTieneIndicacionYAyudaConLosNombresDeLosBotones() {
        assertEquals(5, GuiaProducto.AYUDAS.size)
        assertTrue(GuiaProducto.AYUDAS[0].indicacion.contains("«Elegir de la galería»"))
        assertTrue(GuiaProducto.AYUDAS[0].ayuda.contains("Si no das permiso a la cámara"))
        assertTrue(GuiaProducto.AYUDAS[4].indicacion.contains("«Solo yo lo veo»"))
        assertTrue(GuiaProducto.AYUDAS[4].indicacion.contains("«Guardar en Mis productos»"))
        GuiaProducto.iniciar(Intencion.FOTO)
        assertTrue(GuiaProducto.activa && GuiaProducto.abrirEjemploFoto)
        GuiaProducto.salir()
        assertFalse(GuiaProducto.activa)
    }
}
