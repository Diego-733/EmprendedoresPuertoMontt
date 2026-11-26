package com.example.trabajodea10

import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.ui.navegacion.CodigoRuta
import com.example.trabajodea10.ui.navegacion.Ruta
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Girar el celular o cambiar el tamaño de letra ya no devuelve la app a Inicio: la pila de pantallas
 * se guarda como texto (solo nombres de pantalla e ids) y se restaura ligada a la sesión.
 */
class NavegacionPersistenteTest {

    private val todas = listOf(
        Ruta.Login, Ruta.AdminInicio, Ruta.AdminFerias, Ruta.AdminEmprendedoras, Ruta.Ajustes, Ruta.FeriaDetalle("f1"),
        Ruta.EmpInicio, Ruta.EmpCatalogo, Ruta.EmpVenta, Ruta.PagoVenta, Ruta.EmpFerias, Ruta.EmpTienda, Ruta.EmpTiendaQR("t|1"),
        Ruta.ProductoDetalle("p_1"), Ruta.VistaPrevia, Ruta.HistorialVentas, Ruta.ConfigVentas, Ruta.VisInicio, Ruta.VisProductos,
        Ruta.VisAyuda, Ruta.VisMarketplaceClasico, Ruta.TiendaPublica("t1"), Ruta.ProductoPublico("p2"), Ruta.AsociacionPublica("a1"),
        Ruta.FeriaPublica("f2"), Ruta.ConfigVitrina, Ruta.BandejaContactos, Ruta.Asistente, Ruta.CentroAyuda
    )

    @Test
    fun `cada pantalla se guarda y se recupera igual`() {
        todas.forEach { r -> assertEquals(r.toString(), r, CodigoRuta.desdeTexto(CodigoRuta.aTexto(r))) }
    }

    @Test
    fun `se restaura la pila de la misma sesion`() {
        val pila = listOf(Ruta.EmpInicio, Ruta.Asistente, Ruta.CentroAyuda)
        assertEquals(pila, CodigoRuta.restaurar(CodigoRuta.guardar(pila), Ruta.EmpInicio) { true })
    }

    @Test
    fun `otra sesion o datos danados vuelven al inicio`() {
        val guardado = CodigoRuta.guardar(listOf(Ruta.EmpInicio, Ruta.HistorialVentas))
        val deOtraCuenta = ArrayList(listOf("otra-cuenta|EMPRENDEDORA") + guardado.drop(1))
        assertEquals(listOf(Ruta.Login), CodigoRuta.restaurar(deOtraCuenta, Ruta.Login))
        assertEquals(listOf(Ruta.Login), CodigoRuta.restaurar(emptyList(), Ruta.Login))
        assertEquals(listOf(Ruta.Login), CodigoRuta.restaurar(listOf(CodigoRuta.firmaSesion(), "PantallaQueNoExiste"), Ruta.Login))
    }

    @Test
    fun `no se restauran pantallas que ya no tienen sentido`() {
        TramaStore.carritoVenta.clear()
        val restaurada = CodigoRuta.restaurar(CodigoRuta.guardar(listOf(Ruta.EmpInicio, Ruta.EmpVenta, Ruta.PagoVenta, Ruta.ProductoDetalle("no-existe"))), Ruta.EmpInicio)
        assertEquals(listOf(Ruta.EmpInicio, Ruta.EmpVenta), restaurada)
    }
}
