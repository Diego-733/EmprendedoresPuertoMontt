package com.example.trabajodea10

import com.example.trabajodea10.data.asistente.ContextoAsistente
import com.example.trabajodea10.data.asistente.InterpreteAlerce
import com.example.trabajodea10.data.asistente.InterpreteAlerce.Intencion
import com.example.trabajodea10.data.asistente.NumerosEs
import com.example.trabajodea10.data.asistente.SesionAsistente
import com.example.trabajodea10.data.asistente.TemasAyuda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Asistente Alerce: números en palabras, intenciones, datos, ambigüedad y conversación (reglas locales, sin IA). */
class AsistenteAlerceTest {

    // ---------- Números y montos chilenos ----------
    @Test
    fun numerosEnPalabrasYMontos() {
        assertEquals("cuesta 15000 pesos", NumerosEs.aDigitos("cuesta quince mil pesos"))
        assertEquals("12500", NumerosEs.aDigitos("doce mil quinientos"))
        assertEquals("1500", NumerosEs.aDigitos("mil quinientos"))
        assertEquals("20 lucas", NumerosEs.aDigitos("veinte lucas"))
        assertEquals("1 luca", NumerosEs.aDigitos("una luca"))
        assertEquals("15000", NumerosEs.aDigitos("15 mil"))
        assertEquals("15500", NumerosEs.aDigitos("15 mil 500"))
        assertEquals("15000 pesos", NumerosEs.aDigitos("$15.000 pesos"))
        assertEquals("35", NumerosEs.aDigitos("treinta y cinco"))
        assertEquals("tengo 4 disponibles", NumerosEs.aDigitos("tengo cuatro disponibles"))
        assertEquals("$15.000", NumerosEs.pesos(15000))
    }

    @Test
    fun unChalecoNoEsUnNumero() {
        assertEquals("es un chaleco", NumerosEs.aDigitos("es un chaleco"))
        assertEquals("torta de una capa", NumerosEs.aDigitos("torta de una capa"))
    }

    // ---------- El ejemplo pedido ----------
    @Test
    fun productoCompletoPorVoz() {
        val r = InterpreteAlerce.interpretar("Quiero agregar un producto. Es un chaleco de lana hecho a mano, cuesta quince mil pesos y tengo cuatro disponibles")
        assertEquals(Intencion.CREAR_PRODUCTO, r.intencion)
        assertEquals("Chaleco de lana", r.datos.nombre)
        assertEquals("Chaleco de lana hecho a mano", r.datos.descripcion)
        assertEquals(15000L, r.datos.precio)
        assertEquals(4, r.datos.cantidad)
        assertFalse(r.datos.precioEsTotal)
    }

    @Test
    fun variasFormasDeCrear() {
        listOf("Quiero agregar un producto", "Necesito poner algo nuevo en mi tienda", "Quiero subir un producto nuevo").forEach {
            assertEquals(it, Intencion.CREAR_PRODUCTO, InterpreteAlerce.interpretar(it).intencion)
        }
        val lucas = InterpreteAlerce.interpretar("Agrega un chaleco que cuesta veinte lucas")
        assertEquals(Intencion.CREAR_PRODUCTO, lucas.intencion)
        assertEquals("Chaleco", lucas.datos.nombre)
        assertEquals(20000L, lucas.datos.precio)
        assertNull("no dijo cuántos tiene", lucas.datos.cantidad)
        assertNull("no dijo descripción", lucas.datos.descripcion)
    }

    @Test
    fun noInventaDatosQueNoSeDijeron() {
        val r = InterpreteAlerce.interpretar("Quiero agregar un producto")
        assertNull(r.datos.nombre); assertNull(r.datos.precio); assertNull(r.datos.cantidad); assertNull(r.datos.descripcion)
    }

    // ---------- Distinciones ----------
    @Test
    fun precioYCantidadNoSeConfunden() {
        val d = InterpreteAlerce.extraerDatos("es una bufanda, tengo cinco y cuesta ocho mil")
        assertEquals(8000L, d.precio)
        assertEquals(5, d.cantidad)
        val d2 = InterpreteAlerce.extraerDatos("son 3 gorros a 5000 cada uno")
        assertEquals(5000L, d2.precio)
        assertFalse(d2.precioEsTotal)
    }

    @Test
    fun precioTotalSeReconoce() {
        val d = InterpreteAlerce.extraerDatos("los 4 en 60 mil en total")
        assertEquals(60000L, d.precio)
        assertTrue(d.precioEsTotal)
    }

    @Test
    fun ventaConsultaYCambiosNoSeConfundenConCrear() {
        assertEquals(Intencion.REGISTRAR_VENTA, InterpreteAlerce.interpretar("Vendí dos gorros a cinco mil pesos cada uno").intencion)
        assertEquals(Intencion.REGISTRAR_VENTA, InterpreteAlerce.interpretar("Vendí tres bufandas a diez mil cada una").intencion)
        assertEquals("bufandas", InterpreteAlerce.interpretar("Vendí tres bufandas a diez mil cada una").datos.producto)
        assertEquals(Intencion.CONSULTAR_VENTAS_MES, InterpreteAlerce.interpretar("¿Cuánto vendí este mes?").intencion)
        val acabo = InterpreteAlerce.interpretar("Se me acabaron los chalecos")
        assertEquals(Intencion.MARCAR_SE_ACABO, acabo.intencion)
        assertEquals("chalecos", acabo.datos.producto)
        val quedan = InterpreteAlerce.interpretar("Me quedan cinco gorros")
        assertEquals(Intencion.CAMBIAR_CANTIDAD, quedan.intencion)
        assertEquals(5, quedan.datos.cantidad)
        assertEquals("gorros", quedan.datos.producto)
        val precio = InterpreteAlerce.interpretar("Quiero cambiar el precio de mis bufandas")
        assertEquals(Intencion.CAMBIAR_PRECIO, precio.intencion)
        assertEquals("bufandas", precio.datos.producto)
        assertEquals(Intencion.OCULTAR_PRODUCTO, InterpreteAlerce.interpretar("Quiero ocultar un producto").intencion)
    }

    @Test
    fun navegacionYConsultas() {
        assertEquals(Intencion.VER_PRODUCTOS, InterpreteAlerce.interpretar("Muéstrame mis productos").intencion)
        assertEquals(Intencion.CONSULTAR_PROXIMA_FERIA, InterpreteAlerce.interpretar("¿Cuándo es mi próxima feria?").intencion)
        assertEquals(Intencion.VER_FERIAS, InterpreteAlerce.interpretar("Quiero participar en una feria").intencion)
        assertEquals(Intencion.COMPARTIR_TIENDA, InterpreteAlerce.interpretar("Quiero compartir mi tienda").intencion)
        assertEquals(Intencion.CAMBIAR_WHATSAPP, InterpreteAlerce.interpretar("Quiero cambiar mi número de teléfono").intencion)
        assertEquals(Intencion.VER_MENSAJES, InterpreteAlerce.interpretar("¿Tengo mensajes nuevos?").intencion)
    }

    @Test
    fun enseñameReconoceElTema() {
        fun tema(f: String) = InterpreteAlerce.interpretar(f).let { assertEquals(f, Intencion.ENSENAME, it.intencion); it.tema }
        assertEquals(TemasAyuda.Tema.AGREGAR_PRODUCTO, tema("¿Cómo agrego un producto?"))
        assertEquals(TemasAyuda.Tema.REGISTRAR_VENTA, tema("¿Cómo registro una venta?"))
        assertEquals(TemasAyuda.Tema.CAMBIAR_PRECIO, tema("¿Cómo cambio el precio de un producto?"))
        assertEquals(TemasAyuda.Tema.COMPARTIR_TIENDA, tema("¿Cómo comparto mi tienda?"))
        assertEquals(TemasAyuda.Tema.PARTICIPAR_FERIA, tema("¿Cómo participo en una feria?"))
        assertEquals(TemasAyuda.Tema.CAMBIAR_WHATSAPP, tema("¿Cómo cambio mi número de WhatsApp?"))
        assertEquals(TemasAyuda.Tema.AGREGAR_FOTO, tema("¿Cómo agrego una fotografía?"))
        assertEquals(TemasAyuda.Tema.SABER_SI_SE_GUARDO, tema("¿Cómo sé si mi producto se guardó correctamente?"))
    }

    @Test
    fun venderSinMasEsAmbiguo() {
        val r = InterpreteAlerce.interpretar("Quiero vender unos gorros")
        assertEquals(Intencion.AMBIGUA, r.intencion)
        assertEquals(listOf(Intencion.CREAR_PRODUCTO, Intencion.REGISTRAR_VENTA), r.opciones)
    }

    @Test
    fun elPrecioDichoConPalabrasNoQuedaEnElNombre() {
        val casos = mapOf(
            "quiero agregar un gorro de lana a ochenta lucas" to ("Gorro de lana" to 80000L),
            "agrega una bufanda a veinte mil pesos" to ("Bufanda" to 20000L),
            "subir unos aros de cobre en quince lucas" to ("Aros de cobre" to 15000L),
            "quiero agregar un kuchen por doce mil quinientos" to ("Kuchen" to 12500L)
        )
        casos.forEach { (frase, esperado) ->
            val d = InterpreteAlerce.interpretar(frase).datos
            assertEquals(frase, esperado.first, d.nombre)
            assertEquals(frase, esperado.second, d.precio)
        }
    }

    @Test
    fun productoSinArticuloDespuesDelVerbo() {
        val r = InterpreteAlerce.interpretar("quiero vender gorros")
        assertEquals(Intencion.AMBIGUA, r.intencion)
        assertEquals("gorros", r.datos.producto)
        assertEquals("chaleco", InterpreteAlerce.interpretar("se acabó el chaleco").datos.producto)
        assertNull(InterpreteAlerce.interpretar("quiero vender algo").datos.producto)
    }

    // ---------- Conversación ----------
    private class ContextoFalso(var ventas: ContextoAsistente.ResumenVentas = ContextoAsistente.ResumenVentas(0, 0, 0),
                                var feria: ContextoAsistente.ResumenFeria? = null, var invitaciones: Int = 0) : ContextoAsistente {
        override fun ventasDelMes() = ventas
        override fun proximaFeria() = feria
        override fun invitacionesPorResponder() = invitaciones
    }

    @Test
    fun conversacionCompletaConCorreccionesYConfirmacion() {
        val s = SesionAsistente(ContextoFalso())
        var r = s.responder("Quiero agregar un producto. Es un chaleco de lana hecho a mano, cuesta quince mil pesos y tengo cuatro disponibles")
        assertNull("aún no se abre nada", r.accion)
        assertEquals(15000L, s.producto?.precio)
        r = s.responder("No, el precio es doce mil pesos")
        assertEquals(12000L, s.producto?.precio)
        assertTrue(r.texto.contains("$12.000"))
        s.responder("Tengo cinco disponibles")
        assertEquals(5, s.producto?.cantidad)
        s.responder("Cambia el nombre a chaleco artesanal")
        assertEquals("Chaleco artesanal", s.producto?.nombre)
        r = s.responder("Quiero agregar una foto")
        assertTrue(r.texto.contains("foto"))
        assertNull(r.accion)
        r = s.responder("continuar")
        val accion = r.accion as SesionAsistente.Accion.AbrirFormularioProducto
        assertEquals("Chaleco artesanal", accion.datos.nombre)
        assertEquals(12000L, accion.datos.precio)
        assertEquals(5, accion.datos.cantidad)
        assertEquals("Chaleco de lana hecho a mano", accion.datos.descripcion)
        assertNull("terminó la preparación", s.producto)
    }

    @Test
    fun preguntaLoQueFaltaSinInventar() {
        val s = SesionAsistente(ContextoFalso())
        var r = s.responder("Quiero agregar un producto")
        assertTrue(r.texto.contains("¿Cómo se llama"))
        r = s.responder("una bufanda de lana")
        assertEquals("Bufanda de lana", s.producto?.nombre)
        assertTrue(r.texto.contains("¿Cuánto cuesta"))
        r = s.responder("continuar")   // sin precio no se puede continuar
        assertNull(r.accion)
        assertTrue(r.texto.contains("¿Cuánto cuesta"))
        r = s.responder("ocho mil")
        assertEquals(8000L, s.producto?.precio)
        assertTrue(r.texto.contains("¿Cuántos tienes"))
        r = s.responder("después")
        assertTrue(s.producto!!.cantidadOmitida)
        assertNull(s.producto!!.cantidad)
        r = s.responder("sí")
        assertNotNull(r.accion)
    }

    @Test
    fun precioDudosoSePregunta() {
        val s = SesionAsistente(ContextoFalso())
        s.responder("Agrega un gorro")
        val r = s.responder("veinte")
        assertNull("no se supone $20 ni $20.000", s.producto?.precio)
        assertTrue(r.texto.contains("$20") && r.texto.contains("$20.000"))
        s.responder("20000 pesos")
        assertEquals(20000L, s.producto?.precio)
    }

    @Test
    fun precioTotalPideElDeCadaUno() {
        val s = SesionAsistente(ContextoFalso())
        val r = s.responder("Quiero agregar un producto, son unos aros, los 4 en 20 mil en total")
        assertNull(s.producto?.precio)
        assertTrue(r.texto.contains("¿Cuánto cuesta cada uno"))
    }

    @Test
    fun cancelarNoDejaNadaPreparado() {
        val s = SesionAsistente(ContextoFalso())
        s.responder("Agrega un chaleco que cuesta veinte lucas")
        assertNotNull(s.producto)
        val r = s.responder("cancelar")
        assertNull(s.producto)
        assertNull(r.accion)
    }

    @Test
    fun ambiguoOfreceOpcionesYNoAdivina() {
        val s = SesionAsistente(ContextoFalso())
        var r = s.responder("Quiero vender unos gorros")
        assertNull(r.accion)
        assertEquals(2, r.opciones.size)
        assertNull(s.producto)
        r = s.responder("Subirlo a mi tienda")
        assertEquals("Gorros", s.producto?.nombre)
        assertTrue(r.texto.contains("¿Cuánto cuesta"))
    }

    @Test
    fun consultasUsanSoloLosDatosDeLaCuenta() {
        val ctx = ContextoFalso(ContextoAsistente.ResumenVentas(148500, 12, 2))
        val s = SesionAsistente(ctx)
        val r = s.responder("¿Cuánto vendí este mes?")
        assertTrue(r.texto.contains("$148.500"))
        assertTrue(r.texto.contains("12 ventas"))
        assertTrue("dice que hay ventas por subir", r.texto.contains("subiendo"))
        ctx.ventas = ContextoAsistente.ResumenVentas(0, 0, 0)
        assertTrue(s.responder("cuánto vendí este mes").texto.contains("aún no tienes ventas"))
        ctx.feria = ContextoAsistente.ResumenFeria("Feria Navideña", "el domingo 29 de noviembre", "Plaza de Alerce")
        assertTrue(s.responder("¿Cuándo es mi próxima feria?").texto.contains("Feria Navideña"))
        ctx.feria = null; ctx.invitaciones = 1
        assertTrue(s.responder("¿Cuándo es mi próxima feria?").texto.contains("1 invitación por responder"))
    }

    @Test
    fun operacionesQueCambianDatosNoSeEjecutanSolas() {
        val s = SesionAsistente(ContextoFalso())
        listOf("Vendí dos gorros a cinco mil cada uno", "Se me acabaron los chalecos", "Me quedan cinco gorros",
            "Quiero cambiar el precio de mis bufandas", "Quiero ocultar un producto").forEach {
            val r = s.responder(it)
            assertNull("«$it» no ejecuta ni navega solo: ofrece el camino", r.accion)
            assertTrue(it, r.opciones.isNotEmpty())
        }
    }

    @Test
    fun navegacionDirectaSoloParaVerCosas() {
        val s = SesionAsistente(ContextoFalso())
        assertEquals(SesionAsistente.Accion.Navegar(TemasAyuda.Destino.MIS_PRODUCTOS), s.responder("Muéstrame mis productos").accion)
        assertEquals(SesionAsistente.Accion.Navegar(TemasAyuda.Destino.COMPARTIR), s.responder("Quiero compartir mi tienda").accion)
        assertEquals(SesionAsistente.Accion.Navegar(TemasAyuda.Destino.MENSAJES), s.responder("¿Tengo mensajes nuevos?").accion)
    }

    @Test
    fun enseñameDaPasosYOfreceGuiaOAyuda() {
        val s = SesionAsistente(ContextoFalso())
        val r = s.responder("¿Cómo agrego un producto?")
        assertTrue(r.pasos.size >= 4)
        assertTrue(r.opciones.any { it.etiqueta == "Ayúdame a hacerlo" })
        assertTrue(r.opciones.any { it.entrada == SesionAsistente.ACCION_GUIA })
        assertTrue(TemasAyuda.Tema.AGREGAR_PRODUCTO.paraLeer().startsWith("Agregar un producto."))
    }

    @Test
    fun textosSinVoseoNiJerga() {
        val textos = TemasAyuda.Tema.entries.flatMap { it.pasos + it.titulo } +
            SesionAsistente(ContextoFalso()).saludo("Marta").texto
        textos.forEach { t ->
            listOf("stock", "tocá", "agregá", "elegí ", "escribí", "decime").forEach { mal ->
                assertFalse("«$mal» en: $t", t.lowercase().contains(mal))
            }
        }
    }
}
