package com.example.trabajodea10

import com.example.trabajodea10.data.Categoria
import com.example.trabajodea10.data.ItemVenta
import com.example.trabajodea10.data.MedioPago
import com.example.trabajodea10.data.Modalidad
import com.example.trabajodea10.data.Producto
import com.example.trabajodea10.data.Variante
import com.example.trabajodea10.data.asistente.ContextoAsistente
import com.example.trabajodea10.data.asistente.InterpreteAlerce
import com.example.trabajodea10.data.asistente.InterpreteAlerce.Clase
import com.example.trabajodea10.data.asistente.InterpreteAlerce.Intencion
import com.example.trabajodea10.data.asistente.LectorVentas
import com.example.trabajodea10.data.asistente.MotorInterpretacion
import com.example.trabajodea10.data.asistente.ResolutorProductos
import com.example.trabajodea10.data.asistente.SesionAsistente
import com.example.trabajodea10.data.asistente.SesionAsistente.Accion
import com.example.trabajodea10.data.asistente.SesionAsistente.TipoCambio
import com.example.trabajodea10.data.asistente.ValidadorResultado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 3 del Asistente Alerce: ventas, cantidades, quién ve un producto, edición, ferias y
 * consultas, con un catálogo de prueba. Lo importante: nada se ejecuta sin confirmación, cada
 * confirmación vale una vez, no se elige un producto si hay dudas y no se inventan datos.
 */
class AsistenteOperacionesTest {

    private fun prod(id: String, nombre: String, precio: Double, stock: Int, publicado: Boolean = true, variantes: List<Variante>? = null,
                     modalidad: Modalidad = Modalidad.REGULAR, disponible: Boolean = true) = Producto(
        id = id, tiendaId = "t1", nombre = nombre, categoria = Categoria.TEXTIL, precio = precio, publicado = publicado,
        destacado = false, colorFoto = 0xFF000000, variantes = variantes ?: listOf(Variante("${id}_v1", "Única", stock)),
        modalidad = modalidad, disponible = disponible
    )

    private val catalogo = listOf(
        prod("g1", "Gorro azul", 5000.0, 3),
        prod("g2", "Gorro rojo", 5500.0, 2),
        prod("c1", "Chaleco de lana", 18000.0, 4),
        prod("b1", "Bufanda gris", 8000.0, 5),
        prod("b2", "Bufanda roja", 8000.0, 1, publicado = false),
        prod("p1", "Polera bordada", 12000.0, 0, variantes = listOf(Variante("p1_s", "Talla S", 2), Variante("p1_m", "Talla M", 4))),
        prod("a1", "Aros de cobre", 6000.0, 0, modalidad = Modalidad.A_PEDIDO)
    )

    private inner class Ctx : ContextoAsistente {
        var pendientes = listOf(ContextoAsistente.ResumenFeria("Feria Navideña", "el domingo 29 de noviembre", "Plaza de Alerce", "f1", -41.4, -72.9))
        var confirmadas = emptyList<ContextoAsistente.ResumenFeria>()
        override fun ventasDelMes() = ContextoAsistente.ResumenVentas(45000, 3, 1)
        override fun proximaFeria() = confirmadas.firstOrNull()
        override fun invitacionesPorResponder() = pendientes.size
        override fun productos() = catalogo
        override fun unidadesVendidasEsteMes(productoId: String) = when (productoId) { "g1" -> 4; "g2" -> 1; else -> 0 }
        override fun invitacionesPendientes() = pendientes
        override fun feriasConfirmadas() = confirmadas
        override fun tienda() = ContextoAsistente.ResumenTienda("Tejidos Austral", "+56 9 1234 5678", "https://alerce/?tienda=t1", "WhatsApp y formulario")
        // Igual que «Venta»: no más unidades de las que hay.
        override fun validarVenta(items: List<ItemVenta>): String? = items.firstNotNullOfOrNull { i ->
            val p = catalogo.first { it.id == i.productoId }
            val v = p.variantes.first { it.id == i.varianteId }
            if (!i.encargo && p.manejaStock && i.cantidad > v.stock) "«${p.nombre}»: Solo quedan ${v.stock}." else null
        }
    }

    private val ctx = Ctx()
    private fun sesion() = SesionAsistente(ctx)
    private fun confirmar(s: SesionAsistente, r: SesionAsistente.Respuesta) = s.responder("@confirmar:${r.confirmacion!!.id}")

    // ---------------------------------------------------------------- resolutor

    @Test
    fun `el resolutor nunca elige si hay dudas`() {
        assertTrue(ResolutorProductos.buscar("gorros", catalogo) is ResolutorProductos.Resultado.Varios)
        assertEquals("g1", (ResolutorProductos.buscar("gorro azul", catalogo) as ResolutorProductos.Resultado.Unico).producto.id)
        assertEquals("c1", (ResolutorProductos.buscar("chalecos", catalogo) as ResolutorProductos.Resultado.Unico).producto.id)
        assertEquals(ResolutorProductos.Resultado.Ninguno, ResolutorProductos.buscar("poncho", catalogo))
        // «chaleco rojo» no existe: se muestra el chaleco como opción, no se da por hecho.
        assertTrue(ResolutorProductos.buscar("chaleco rojo", catalogo) is ResolutorProductos.Resultado.Varios)
        val gorros = (ResolutorProductos.buscar("gorros", catalogo) as ResolutorProductos.Resultado.Varios).opciones
        assertEquals(listOf("g1"), ResolutorProductos.refinar("el azul", gorros).map { it.id })
        assertEquals(listOf("g2"), ResolutorProductos.refinar("el segundo", gorros).map { it.id })
        assertEquals(listOf("g2"), ResolutorProductos.refinar("el de cinco mil quinientos", gorros).map { it.id })
    }

    // ---------------------------------------------------------------- lector de ventas

    @Test
    fun `lee varias lineas precios medio y lugar`() {
        val v = LectorVentas.leer("Vendí una bufanda y dos gorros a cinco mil cada uno, en efectivo, en la feria")
        assertEquals(2, v.lineas.size)
        assertEquals(1, v.lineas[0].cantidad); assertEquals("bufanda", v.lineas[0].producto)
        assertEquals(2, v.lineas[1].cantidad); assertEquals(5000L, v.lineas[1].precio)
        assertEquals(MedioPago.EFECTIVO, v.medio)
        assertTrue(v.enFeria)
        val total = LectorVentas.leer("vendí dos chalecos por treinta mil en total")
        assertEquals(30000L, total.lineas.single().precio); assertTrue(total.lineas.single().precioEsTotal)
        assertEquals("credito", LectorVentas.leer("vendí un gorro con tarjeta de crédito").medioNoDisponible)
        assertEquals(3, LectorVentas.leer("no vendí dos, vendí tres gorros").lineas.single().cantidad)
    }

    @Test
    fun `correccion de cantidad con y sin coma`() {
        assertEquals(4 to "gorros", LectorVentas.correccionCantidad("No, eran cuatro gorros"))
        assertEquals(4, LectorVentas.correccionCantidad("no eran cuatro")?.first)
        assertEquals(3, LectorVentas.correccionCantidad("no vendí dos, vendí tres")?.first)
        assertEquals(3, LectorVentas.correccionCantidad("vendí tres, no dos")?.first)
        assertNull(LectorVentas.correccionCantidad("¿cuánto vendí este mes?"))
    }

    // ---------------------------------------------------------------- ventas

    @Test
    fun `vender pregunta cual si hay dudas y no ejecuta sin confirmar`() {
        val s = sesion()
        var r = s.responder("Vendí tres gorros")
        assertNull(r.accion)
        assertEquals(listOf("g1", "g2"), r.opciones.mapNotNull { it.productoId })
        r = s.responder("los azules")
        val c = r.confirmacion
        assertNotNull("muestra el resumen antes", c)
        assertTrue(c!!.lineas.first().contains("3 × Gorro azul"))
        assertTrue(c.lineas.any { it.contains("\$15.000") })
        assertNull(r.accion)
        r = s.responder("@confirmar:${c.id}")
        val a = r.accion as Accion.PrepararVenta
        assertEquals(listOf(SesionAsistente.LineaPreparada("g1", "g1_v1", 3)), a.lineas)
        // Un segundo toque no vuelve a preparar nada.
        assertNull(s.responder("@confirmar:${c.id}").accion)
    }

    @Test
    fun `precio dicho distinto al del catalogo se avisa y no se usa`() {
        val s = sesion()
        val r = s.responder("Vendí dos chalecos a quince mil cada uno")
        val c = r.confirmacion!!
        assertTrue(c.lineas.first().contains("\$18.000 c/u"))
        assertTrue(c.nota!!.contains("Dijiste \$15.000"))
        assertTrue(c.nota!!.contains("precio del catálogo"))
    }

    @Test
    fun `varias lineas medio de pago y correcciones`() {
        val s = sesion()
        var r = s.responder("Vendí una bufanda y dos gorros")
        // Bufandas: hay dos; gorros: dos. Primero pregunta la bufanda.
        assertEquals(listOf("b1", "b2"), r.opciones.mapNotNull { it.productoId })
        r = s.responder("@producto:b1")
        r = s.responder("el rojo")
        var c = r.confirmacion!!
        assertEquals(2, c.lineas.count { it.contains(" × ") })
        r = s.responder("Fue en efectivo")
        c = r.confirmacion!!
        assertTrue(c.lineas.any { it == "Pago: Efectivo" })
        r = s.responder("No, eran cuatro gorros")
        // Gorro rojo tiene 2: no se puede, y se dice por qué (sin preparar nada).
        assertNull(r.confirmacion)
        assertTrue(r.texto.contains("Solo quedan 2"))
        r = s.responder("eran dos")
        assertNotNull(r.confirmacion)
        r = s.responder("Los vendí en la feria")
        assertTrue(r.confirmacion!!.lineas.any { it.startsWith("Lugar: en la feria") })
        val a = confirmar(s, r).accion as Accion.PrepararVenta
        assertEquals(MedioPago.EFECTIVO, a.medio)
        assertTrue(a.enFeria)
    }

    @Test
    fun `sin decir que producto se pregunta y opcion de talla`() {
        val s = sesion()
        var r = s.responder("Vendí dos productos por veinte mil en total")
        assertTrue(r.texto.contains("¿Qué producto vendiste?"))
        r = s.responder("la polera bordada")
        assertTrue(r.texto.contains("¿Qué opción"))
        r = s.responder("talla M")
        assertTrue(r.confirmacion!!.lineas.first().contains("(Talla M)"))
    }

    @Test
    fun `consulta frente a accion frente a ayuda`() {
        assertEquals(Intencion.CONSULTAR_VENTAS_PRODUCTO, InterpreteAlerce.interpretar("¿Cuántos gorros vendí?").intencion)
        assertEquals(Intencion.REGISTRAR_VENTA, InterpreteAlerce.interpretar("Vendí dos gorros").intencion)
        assertEquals(Intencion.ENSENAME, InterpreteAlerce.interpretar("¿Cómo registro una venta?").intencion)
        assertEquals(Intencion.RECHAZAR, InterpreteAlerce.interpretar("No quiero registrar una venta").intencion)
        assertEquals(Intencion.OTRA_COSA, InterpreteAlerce.interpretar("Quiero hacer otra cosa").intencion)
        assertEquals(Clase.CONSULTA, Intencion.CONSULTAR_VENTAS_PRODUCTO.clase)
        assertEquals(Clase.ACCION, Intencion.REGISTRAR_VENTA.clase)
        val r = sesion().responder("¿Cuántos gorros vendí?")
        assertTrue(r.texto.contains("4 «Gorro azul»"))
        assertTrue(r.texto.contains("1 «Gorro rojo»"))
    }

    @Test
    fun `cancelar todo y otra cosa limpian la venta en curso`() {
        val s = sesion()
        s.responder("Vendí tres gorros")
        assertTrue(s.responder("Mejor cancela todo").texto.contains("no se guardó ni se cambió nada"))
        assertTrue(s.responder("@confirmar:1").texto.contains("ya no está vigente"))
        s.responder("Vendí un chaleco")
        assertTrue(s.responder("Quiero hacer otra cosa").texto.contains("dejé eso de lado"))
        assertTrue(s.responder("No quiero registrar una venta").texto.contains("no hago nada"))
    }

    @Test
    fun `pregunta intermedia conserva la venta y seguir la retoma`() {
        val s = sesion()
        s.responder("Vendí un chaleco")
        val r = s.responder("¿Cómo registro una venta?")
        assertTrue(r.pasos.isNotEmpty())
        assertTrue(r.texto.contains("Tu venta sigue anotada"))
        assertNotNull(s.responder("seguir").confirmacion)
    }

    // ---------------------------------------------------------------- cantidades

    @Test
    fun `fijar y sumar cantidades con confirmacion y cantidad conocida`() {
        val s = sesion()
        var r = s.responder("Me quedan cinco chalecos")
        assertTrue(r.confirmacion!!.lineas.single().contains("4 → 5"))
        var a = confirmar(s, r).accion as Accion.CambiarInventario
        assertEquals(SesionAsistente.CambioInventario("c1", "c1_v1", TipoCambio.FIJAR, 5, conocida = 4), a.cambios.single())
        r = s.responder("Tengo diez chalecos disponibles")
        assertTrue(r.confirmacion!!.lineas.single().contains("4 → 10"))
        s.responder("cancelar")
        r = s.responder("Me llegaron tres chalecos más")
        assertTrue(r.confirmacion!!.lineas.single().contains("4 + 3 = 7"))
        r = s.responder("no, cinco")
        assertTrue(r.confirmacion!!.lineas.single().contains("4 + 5 = 9"))
        a = confirmar(s, r).accion as Accion.CambiarInventario
        assertEquals(9, a.cambios.single().cantidad)
    }

    @Test
    fun `se acabaron las bufandas ofrece todas y anotar venta`() {
        val s = sesion()
        val r = s.responder("Se me acabaron las bufandas")
        assertTrue(r.opciones.any { it.entrada == "@todos" })
        val r2 = s.responder("todas")
        assertEquals(2, r2.confirmacion!!.lineas.size)
        val a = confirmar(s, r2).accion as Accion.CambiarInventario
        assertTrue(a.cambios.all { it.tipo == TipoCambio.SE_ACABO })
        val r3 = sesion().responder("Se acabó el chaleco")
        assertTrue(r3.opciones.any { it.etiqueta == "Mejor anotar una venta" })
    }

    @Test
    fun `a pedido no tiene cantidades y este producto usa el ultimo`() {
        val s = sesion()
        assertTrue(s.responder("Me quedan tres aros").texto.contains("no lleva la cuenta"))
        s.responder("¿Cuántos chalecos vendí?")
        val r = s.responder("Quiero volver a tener disponible este producto")
        assertTrue(r.texto.contains("Chaleco de lana") || r.confirmacion?.lineas?.any { it.contains("Chaleco") } == true)
    }

    @Test
    fun `talla obligatoria antes de cambiar cantidad`() {
        val s = sesion()
        val r = s.responder("Me quedan 3 poleras")
        assertTrue(r.texto.contains("¿De qué opción"))
        assertTrue(s.responder("@opcion:p1_m").confirmacion!!.lineas.single().contains("(Talla M): 4 → 3"))
    }

    // ---------------------------------------------------------------- quién lo ve y edición

    @Test
    fun `ocultar y volver a mostrar`() {
        val s = sesion()
        var r = s.responder("Quiero ocultar los chalecos")
        val a = confirmar(s, r).accion as Accion.CambiarVisibilidad
        assertEquals(listOf("c1"), a.productoIds); assertFalse(a.visible)
        r = s.responder("Quiero volver a mostrar las bufandas")
        r = s.responder("todas")
        // Solo la que estaba oculta cambia.
        assertEquals(1, r.confirmacion!!.lineas.size)
        assertTrue(r.confirmacion!!.lineas.single().contains("Bufanda roja"))
    }

    @Test
    fun `editar precio descripcion y foto abre el formulario de siempre`() {
        val s = sesion()
        var r = s.responder("Cambia el precio del chaleco de lana a veinte mil")
        assertTrue(r.confirmacion!!.lineas.single().contains("\$18.000 → \$20.000"))
        assertEquals(Accion.AbrirEdicion("c1", 20000, 1), confirmar(s, r).accion)
        r = s.responder("El precio del chaleco era quince lucas, no, dieciocho")
        // «dieciocho» en la escala de lo dicho antes: $18.000.
        assertTrue(r.confirmacion!!.lineas.single().contains("→ \$18.000"))
        s.responder("cancelar")
        r = s.responder("Quiero cambiar la descripción de mis bufandas")
        assertEquals(listOf("b1", "b2"), r.opciones.mapNotNull { it.productoId })
        r = s.responder("la gris")
        assertEquals(Accion.AbrirEdicion("b1", null, 1), confirmar(s, r).accion)
        r = s.responder("Quiero agregar otra foto al gorro azul")
        assertEquals(Accion.AbrirEdicion("g1", null, 0), confirmar(s, r).accion)
        assertTrue(sesion().responder("Borra el chaleco").texto.contains("Yo no borro productos"))
    }

    // ---------------------------------------------------------------- ferias

    @Test
    fun `participar en la feria con la misma operacion que el boton`() {
        val s = sesion()
        assertTrue(s.responder("¿Tengo invitaciones pendientes?").texto.contains("Feria Navideña"))
        val r = s.responder("Quiero participar")
        assertTrue(r.confirmacion!!.lineas.contains("Tu respuesta: Participo"))
        assertEquals(Accion.ResponderFeria("f1", "Feria Navideña", true), confirmar(s, r).accion)
        assertTrue(s.responder("¿Dónde queda la feria?").texto.contains("Plaza de Alerce"))
        assertTrue(s.responder("¿Cómo llego a la feria?").opciones.any { it.entrada == "@mapa:f1" })
        assertTrue(s.responder("@mapa:f1").accion is Accion.ComoLlegar)
    }

    @Test
    fun `no poder asistir a una feria confirmada no se inventa`() {
        ctx.pendientes = emptyList()
        ctx.confirmadas = listOf(ContextoAsistente.ResumenFeria("Feria de Verano", "el sábado 6 de diciembre", null, "f2"))
        val r = sesion().responder("No podré asistir")
        assertNull(r.accion); assertNull(r.confirmacion)
        assertTrue(r.texto.contains("no se puede cambiar una participación confirmada"))
    }

    // ---------------------------------------------------------------- consultas y motor

    @Test
    fun `consultas de productos agotados y tienda`() {
        val s = sesion()
        assertTrue(s.responder("¿Cuántos productos tengo?").texto.contains("Tienes 7 productos"))
        assertTrue(s.responder("¿Qué productos se acabaron?").texto.contains("Ningún producto se acabó"))
        assertTrue(s.responder("¿Cuál es el link de mi tienda?").texto.contains("https://alerce/?tienda=t1"))
    }

    @Test
    fun `un motor externo no puede colar datos fuera de rango`() {
        val malo = object : MotorInterpretacion {
            override fun interpretar(frase: String) = InterpreteAlerce.Resultado(Intencion.CAMBIAR_CANTIDAD, InterpreteAlerce.Datos(cantidad = 999999, producto = "chaleco"))
        }
        assertEquals(Intencion.DESCONOCIDA, ValidadorResultado.validar(malo.interpretar("x")).intencion)
        val s = SesionAsistente(ctx, malo)
        assertNull(s.responder("lo que sea").confirmacion)
    }
}
