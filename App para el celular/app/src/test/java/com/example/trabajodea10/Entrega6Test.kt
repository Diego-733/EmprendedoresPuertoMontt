package com.example.trabajodea10

import com.example.trabajodea10.data.FeriaPublica
import com.example.trabajodea10.data.ModoContacto
import com.example.trabajodea10.data.NavegacionAnonima
import com.example.trabajodea10.data.ReglasVitrina
import com.example.trabajodea10.data.Tienda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de la Entrega 6 (checklist A02, A06, A07, A08, A13, A14). */
class Entrega6Test {

    private val tiendas = listOf(
        Tienda(id = "a", nombre = "Tejidos Austral", rubro = "Artesanía", sector = "Alerce Sur", asociacionId = "as1"),
        Tienda(id = "b", nombre = "Panadería Rosa", rubro = "Alimentos", sector = "Alerce Histórico", asociacionId = "as1"),
        Tienda(id = "c", nombre = "Barbería", rubro = "Servicios", sector = "Alerce Norte", asociacionId = null)
    )

    @Test fun filtrosDelDirectorio() {
        assertEquals(listOf("c"), ReglasVitrina.filtrarTiendas(tiendas, "", "Servicios", null, null).map { it.id })
        assertEquals(listOf("b"), ReglasVitrina.filtrarTiendas(tiendas, "", null, "Alerce Histórico", null).map { it.id })
        assertEquals(listOf("a", "b"), ReglasVitrina.filtrarTiendas(tiendas, "", null, null, "as1").map { it.id })
        assertEquals(listOf("c"), ReglasVitrina.filtrarTiendas(tiendas, "", null, null, ReglasVitrina.SIN_ASOCIACION).map { it.id })
        assertEquals(listOf("a"), ReglasVitrina.filtrarTiendas(tiendas, "tejidos", null, null, null).map { it.id })
        assertEquals(3, ReglasVitrina.filtrarTiendas(tiendas, "", "", "", "").size)
    }

    @Test fun precioOcultoMuestraConsultar() {
        assertEquals("Consultar precio", ReglasVitrina.textoPrecio(Tienda(mostrarPrecios = false), 5000.0))
        assertTrue(ReglasVitrina.textoPrecio(Tienda(mostrarPrecios = true), 5000.0).contains("5"))
    }

    @Test fun contactoSegunConfiguracion() {
        // Número neutro: +56 9 1234 5678 es de los datos de ejemplo y ya no abre WhatsApp (ContactoWhatsApp.esDemostracion).
        val conNumero = Tienda(whatsapp = "+56 9 4821 7735")
        assertTrue(conNumero.copy(contactoModo = ModoContacto.WHATSAPP).ofreceWhatsApp)
        assertFalse(conNumero.copy(contactoModo = ModoContacto.WHATSAPP).ofreceFormulario)
        assertFalse(conNumero.copy(contactoModo = ModoContacto.FORMULARIO).ofreceWhatsApp)
        assertTrue(conNumero.copy(contactoModo = ModoContacto.FORMULARIO).ofreceFormulario)
        assertTrue(conNumero.copy(contactoModo = ModoContacto.AMBOS).ofreceWhatsApp)
        assertTrue(conNumero.copy(contactoModo = ModoContacto.AMBOS).ofreceFormulario)
        assertFalse(Tienda(whatsapp = "", contactoModo = ModoContacto.AMBOS).ofreceWhatsApp)
        assertEquals(ModoContacto.AMBOS, ModoContacto.desde("cualquiera"))
        assertEquals(ModoContacto.FORMULARIO, ModoContacto.desde("formulario"))
    }

    @Test fun navegacionAnonimaDesconocidaPermite() {
        assertEquals(NavegacionAnonima.OBLIGAR, NavegacionAnonima.desde("OBLIGAR"))
        assertEquals(NavegacionAnonima.INVITAR, NavegacionAnonima.desde("invitar"))
        assertEquals(NavegacionAnonima.PERMITIR, NavegacionAnonima.desde(null))
    }

    private fun feria(id: String, fecha: String, lat: Double?, lng: Double?, estado: String = "PROXIMA") =
        FeriaPublica(id = id, nombre = id, fechaInicio = fecha, fechaFin = fecha, estado = estado, lat = lat, lng = lng)

    @Test fun feriasProximasYOrden() {
        val ferias = listOf(
            feria("lejos_pronto", "2026-11-20", -41.47, -72.94),      // Puerto Montt centro
            feria("cerca_tarde", "2026-12-01", -41.396, -72.896),     // Alerce
            feria("pasada", "2026-11-01", -41.39, -72.89),
            feria("borrador", "2026-11-25", -41.39, -72.89, "BORRADOR"),
            feria("sin_punto", "2026-11-22", null, null)
        )
        val proximas = ReglasVitrina.feriasProximas(ferias, "2026-11-18")
        assertEquals(listOf("lejos_pronto", "sin_punto", "cerca_tarde"), proximas.map { it.id })
        // Sin permiso: por fecha
        assertEquals(listOf("lejos_pronto", "sin_punto", "cerca_tarde"), ReglasVitrina.ordenarFerias(proximas, null, null).map { it.id })
        // Con ubicación en Alerce: la más cercana primero; las sin punto al final
        assertEquals(listOf("cerca_tarde", "lejos_pronto", "sin_punto"), ReglasVitrina.ordenarFerias(proximas, -41.395, -72.895).map { it.id })
    }

    @Test fun distanciaAproximada() {
        val d = ReglasVitrina.distanciaMetros(-41.3959, -72.8955, -41.4717, -72.9396)
        assertTrue("distancia Alerce–Puerto Montt ≈ 9,3 km, fue $d", d in 8_500.0..10_000.0)
    }

    @Test fun comoLlegarApuntaAlLugar() {
        assertEquals("geo:-41.47,-72.94?q=-41.47,-72.94(Feria%20de%20Alerce)", ReglasVitrina.uriComoLlegar(-41.47, -72.94, "Feria de Alerce"))
    }
}
