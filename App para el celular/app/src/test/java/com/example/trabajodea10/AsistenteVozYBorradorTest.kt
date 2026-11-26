package com.example.trabajodea10

import com.example.trabajodea10.data.EstadoSync
import com.example.trabajodea10.data.FormularioProducto
import com.example.trabajodea10.data.GuiaProducto
import com.example.trabajodea10.data.MedioPago
import com.example.trabajodea10.data.Modalidad
import com.example.trabajodea10.data.Tienda
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.ValoresProducto
import com.example.trabajodea10.data.Venta
import com.example.trabajodea10.data.asistente.SesionAsistente
import com.example.trabajodea10.data.mesActualIso
import com.example.trabajodea10.ui.asistente.ContextoAsistenteApp
import com.example.trabajodea10.voz.ReglasVoz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Etapas 1 y 2 del Asistente Alerce, lado Android sin pantallas:
 * reglas de la voz, locuciones de la guía, borrador asistido (nunca guarda solo ni pisa
 * un borrador con datos) y consultas que leen solo el estado local de la cuenta.
 */
class AsistenteVozYBorradorTest {

    private val miTienda = "t_prueba_asistente"

    @Before
    fun preparar() {
        TramaStore.guardarTienda(Tienda(id = miTienda, nombre = "Tienda de prueba"))
        TramaStore.productos.removeAll { it.tiendaId == miTienda }
        TramaStore.ventas.clear()
        TramaStore.descartarBorrador()
    }

    // ---------------------------------------------------------------- voz

    @Test
    fun `elige espanol de Chile y si no hay usa otra variante del espanol`() {
        assertEquals(Locale("es", "CL"), ReglasVoz.elegirIdioma { true })
        assertEquals(Locale("es", "MX"), ReglasVoz.elegirIdioma { it.country == "MX" || it.country == "ES" })
        assertEquals(Locale("es"), ReglasVoz.elegirIdioma { it.country.isEmpty() && it.language == "es" && it.toLanguageTag() == "es" })
        // Sin ninguna voz en español: null (la app sigue solo con texto).
        assertNull(ReglasVoz.elegirIdioma { false })
    }

    @Test
    fun `los precios se leen en pesos y sin simbolos`() {
        assertEquals("Precio: 15.000 pesos", ReglasVoz.paraLeer("Precio: \$15.000"))
        assertEquals("Cuesta 2500 pesos", ReglasVoz.paraLeer("Cuesta $ 2500"))
        assertEquals("Toca Guardar en Mis productos", ReglasVoz.paraLeer("Toca «Guardar en Mis productos»"))
        assertEquals("Guía paso a paso", ReglasVoz.paraLeer("🧭 Guía paso a paso"))
    }

    @Test
    fun `la guia tiene una locucion natural por paso y la revision agrega el resumen`() {
        assertEquals(GuiaProducto.AYUDAS.size, GuiaProducto.LOCUCIONES.size)
        assertTrue(GuiaProducto.locucion(0).startsWith("Primero, vamos a agregar una foto"))
        assertTrue(GuiaProducto.locucion(1).contains("cómo se llama tu producto y cuánto cuesta"))
        val revision = GuiaProducto.locucion(4, "Vas a guardar: Chaleco de lana, a \$15.000.")
        assertTrue(revision.contains("Guardar en Mis productos"))
        assertTrue(revision.endsWith("Vas a guardar: Chaleco de lana, a \$15.000."))
        // Mismo registro del proyecto: sin voseo ni «stock».
        GuiaProducto.LOCUCIONES.forEach { l ->
            assertFalse(l, Regex("\\b(tenés|podés|querés|stock)\\b", RegexOption.IGNORE_CASE).containsMatchIn(l))
        }
    }

    // ---------------------------------------------------------------- borrador asistido

    @Test
    fun `prepara el borrador con lo dicho pero no lo guarda en Mis productos`() {
        val antes = TramaStore.productos.size
        val r = TramaStore.prepararProductoAsistido("Chaleco de lana", "Chaleco de lana hecho a mano", 15000, 4)
        assertTrue(r is TramaStore.PreparacionAsistida.Listo)
        val b = TramaStore.borrador!!
        assertEquals((r as TramaStore.PreparacionAsistida.Listo).id, b.producto.id)
        assertTrue(b.esNuevo)
        assertEquals(0, b.paso)
        assertEquals("Chaleco de lana", b.producto.nombre)
        assertEquals("Chaleco de lana hecho a mano", b.producto.descripcion)
        assertEquals(15000.0, b.producto.precio, 0.0)
        assertEquals(4, b.producto.variantes.first().stock)
        assertTrue(b.producto.manejaStock)
        // Nada entra al catálogo ni a la cola de subida hasta «Guardar en Mis productos».
        assertEquals(antes, TramaStore.productos.size)
        // Pasa las mismas validaciones que el formulario de siempre.
        assertTrue(FormularioProducto.errores(b.producto, null).isEmpty())
    }

    @Test
    fun `sin cantidad deja el valor de siempre de la tienda`() {
        TramaStore.prepararProductoAsistido("Gorro", null, 8000, null)
        val p = TramaStore.borrador!!.producto
        assertEquals("", p.descripcion)
        // crearProductoBorrador pone 1 cuando la tienda lleva la cuenta: no se inventa otro número.
        assertEquals(TramaStore.tienda.valoresProducto.manejaStock, p.manejaStock)
    }

    @Test
    fun `decir cuantos tiene saca un producto de la modalidad a pedido`() {
        TramaStore.guardarTienda(TramaStore.tienda.copy(valoresProducto = ValoresProducto(modalidad = Modalidad.A_PEDIDO, manejaStock = false)))
        TramaStore.prepararProductoAsistido("Mermelada", null, 3500, 6)
        val p = TramaStore.borrador!!.producto
        assertEquals(Modalidad.REGULAR, p.modalidad)
        assertEquals(6, p.variantes.first().stock)
    }

    @Test
    fun `nunca pisa un producto a medio hacer sin preguntar`() {
        val id = TramaStore.crearProductoBorrador()
        val b = TramaStore.borrador!!
        TramaStore.actualizarBorrador(b.producto.copy(nombre = "Bufanda"), 1)
        val r = TramaStore.prepararProductoAsistido("Chaleco", null, 15000, 2)
        assertEquals(TramaStore.PreparacionAsistida.HayBorrador("Bufanda"), r)
        assertEquals("Bufanda", TramaStore.borrador!!.producto.nombre)
        assertEquals(id, TramaStore.borrador!!.producto.id)
        // Con la confirmación de la persona, se reemplaza.
        val r2 = TramaStore.prepararProductoAsistido("Chaleco", null, 15000, 2, reemplazar = true)
        assertTrue(r2 is TramaStore.PreparacionAsistida.Listo)
        assertEquals("Chaleco", TramaStore.borrador!!.producto.nombre)
    }

    @Test
    fun `un borrador vacio se reutiliza sin preguntar`() {
        TramaStore.crearProductoBorrador()
        val r = TramaStore.prepararProductoAsistido("Chaleco", null, 15000, null)
        assertTrue(r is TramaStore.PreparacionAsistida.Listo)
        assertEquals("Chaleco", TramaStore.borrador!!.producto.nombre)
    }

    @Test
    fun `de la conversacion al formulario sin inventar datos`() {
        val s = SesionAsistente(ContextoAsistenteApp())
        s.responder("Quiero agregar un producto. Es un chaleco de lana hecho a mano, cuesta quince mil pesos y tengo cuatro disponibles")
        val r = s.responder("continuar")
        val a = r.accion as SesionAsistente.Accion.AbrirFormularioProducto
        TramaStore.prepararProductoAsistido(a.datos.nombre!!, a.datos.descripcion, a.datos.precio, a.datos.cantidad)
        val p = TramaStore.borrador!!.producto
        assertEquals("Chaleco de lana", p.nombre)
        assertEquals(15000.0, p.precio, 0.0)
        assertEquals(4, p.variantes.first().stock)
        assertNull(p.fotoUrl)
    }

    // ---------------------------------------------------------------- consultas

    private fun venta(id: String, fecha: String, total: Double, estado: EstadoSync = EstadoSync.CONFIRMADA) =
        Venta(id = id, fecha = fecha, items = emptyList(), medioPago = MedioPago.EFECTIVO, feriaId = null, total = total, estadoSync = estado)

    @Test
    fun `cuanto vendi suma solo este mes y dice cuantas faltan por subir`() {
        val mes = mesActualIso()
        TramaStore.ventas.addAll(listOf(
            venta("v1", "$mes-02 10:00", 12000.0),
            venta("v2", "$mes-03 11:00", 3000.0, EstadoSync.PENDIENTE),
            venta("v3", "2001-01-05 09:00", 99000.0)
        ))
        val r = ContextoAsistenteApp().ventasDelMes()
        assertEquals(15000L, r.total)
        assertEquals(2, r.cantidad)
        assertEquals(1, r.pendientes)
        val texto = SesionAsistente(ContextoAsistenteApp()).responder("¿Cuánto vendí este mes?").texto
        assertTrue(texto, texto.contains("\$15.000") && texto.contains("2 ventas") && texto.contains("aún se está subiendo"))
    }

    @Test
    fun `sin ventas no inventa montos`() {
        val texto = SesionAsistente(ContextoAsistenteApp()).responder("cuánto vendí este mes").texto
        assertEquals("Este mes aún no tienes ventas registradas.", texto)
    }
}
