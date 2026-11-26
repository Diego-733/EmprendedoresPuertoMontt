package com.example.trabajodea10

import com.example.trabajodea10.data.Tienda
import com.example.trabajodea10.data.TramaStore
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

/**
 * Revisión crítica de las etapas 1 y 2: cada prueba reproduce un problema encontrado con frases
 * que no se usaron durante el desarrollo (y que antes fallaba).
 */
class AsistenteRevisionTest {

    private class Ctx : ContextoAsistente {
        override fun ventasDelMes() = ContextoAsistente.ResumenVentas(45000, 3, 1)
        override fun proximaFeria(): ContextoAsistente.ResumenFeria? = null
        override fun invitacionesPorResponder() = 0
    }

    private fun sesion() = SesionAsistente(Ctx())
    private fun datos(f: String) = InterpreteAlerce.interpretar(f).datos

    // ---------------------------------------------------------------- nunca inventar

    @Test
    fun `una pregunta en medio no se anota como nombre del producto`() {
        val s = sesion()
        s.responder("quiero agregar un producto")
        val r = s.responder("¿cuánto vendí este mes?")
        assertNull("antes quedaba «¿cuánto vendí este mes?» como nombre", s.producto!!.nombre)
        assertTrue(r.texto.contains("\$45.000"))
        assertTrue(r.texto.contains("sigue anotado"))
    }

    @Test
    fun `no se o un numero suelto no se anotan como nombre`() {
        val s = sesion()
        s.responder("quiero subir algo")
        s.responder("no sé")
        assertNull(s.producto!!.nombre)
        s.responder("unos 10 más o menos")
        assertNull(s.producto!!.nombre)
    }

    @Test
    fun `ya no quiero cancela y no abre el formulario`() {
        val s = sesion()
        s.responder("agrega un gorro a 5000")
        val r = s.responder("ya no quiero")
        assertNull("antes «ya» contaba como sí y abría el formulario", r.accion)
        assertNull(s.producto)
        for (frase in listOf("chao", "no importa, déjalo", "mejor no", "no gracias")) {
            val t = sesion(); t.responder("agrega un gorro a 5000")
            assertNull(frase, t.responder(frase).accion); assertNull(frase, t.producto)
        }
    }

    @Test
    fun `una negacion nunca es un si`() {
        assertFalse(InterpreteAlerce.esAfirmacion("ya no"))
        assertFalse(InterpreteAlerce.esAfirmacion("bueno no"))
        assertTrue(InterpreteAlerce.esAfirmacion("ya"))
        assertTrue(InterpreteAlerce.esAfirmacion("listo, continuar"))
    }

    @Test
    fun `cancelar sin nada a medio hacer lo dice tal cual`() {
        val r = sesion().responder("cancelar")
        assertTrue(r.texto.contains("No había nada a medio hacer"))
    }

    // ---------------------------------------------------------------- precios y cantidades

    @Test
    fun `formatos de precio del dictado`() {
        assertEquals(8500L, datos("quiero poner unos aros de cobre a \$8,500").precio)
        assertEquals(15000L, datos("agregar un chaleco a \$15 000").precio)
        assertEquals(15000L, datos("agregar un chaleco a quince luquitas").precio)
        assertEquals(12990L, datos("publica una tabla de quesos en 12.990").precio)
        assertEquals(1500L, datos("pon un queque a luca y media").precio)
        assertEquals(2500L, datos("agrega un pan amasado a dos lucas y media").precio)
        assertEquals(120000L, datos("subir cuadro pintado a mano a ciento veinte mil").precio)
    }

    @Test
    fun `docenas y cantidades aproximadas`() {
        assertEquals("12", NumerosEs.aDigitos("una docena"))
        assertEquals("6", NumerosEs.aDigitos("media docena"))
        assertEquals("24", NumerosEs.aDigitos("dos docenas"))
        assertEquals(6, datos("agrega una bufanda a 8000 y tengo como 6").cantidad)
        assertEquals(6, datos("crea un producto: miel de ulmo, 5 mil pesos, tengo media docena").cantidad)
        assertEquals(10, InterpreteAlerce.cantidadSuelta("unos 10 más o menos"))
        assertEquals(12, InterpreteAlerce.cantidadSuelta("una docena"))
    }

    @Test
    fun `la autocorreccion dentro de la frase gana`() {
        assertEquals(40000L, datos("sube un poncho a 35 mil, no, a 40 mil").precio)
        assertEquals(40000L, datos("sube un poncho a 35 mil no a 40 mil").precio)       // el dictado no pone comas
        assertEquals(4, InterpreteAlerce.extraerDatos("tengo 5, no, 4").cantidad)
        assertEquals(9000L, datos("agrega un gorro a 8000 perdón 9000").precio)
        // «no» sin dato después no es una corrección: «no tengo foto» no cambia el precio.
        assertEquals(15000L, datos("agrega un chaleco a 15000, no tengo foto").precio)
    }

    @Test
    fun `dos precios distintos no se elige uno solo`() {
        val d = datos("agrega un gorro a 5000 y una bufanda a 8000")
        assertEquals(listOf(5000L, 8000L), d.preciosOidos)
        val s = sesion()
        val r = s.responder("agrega un gorro a 5000 y una bufanda a 8000")
        assertNull("no se anota ninguno de los dos sin preguntar", s.producto!!.precio)
        assertTrue(r.texto.contains("más de un precio"))
        assertEquals(listOf("\$5.000", "\$8.000", "Cancelar"), r.opciones.map { it.etiqueta })
        s.responder("8000 pesos")
        assertEquals(8000L, s.producto!!.precio)
    }

    // ---------------------------------------------------------------- nombres

    @Test
    fun `nombres con verbo con tilde sin articulo o antes del verbo`() {
        assertEquals("Collar de plata", datos("agrégame un collar de plata, cuesta 18 lucas y tengo 3").nombre)
        assertEquals("Gorro de lana", datos("agrega gorro de lana a 5000").nombre)
        assertEquals("Mermelada de murta", datos("quiero agregar mermelada de murta a dos mil quinientos").nombre)
        assertEquals("Gorro de lana", datos("a 15 lucas un gorro de lana quiero subir").nombre)
        assertEquals("Chalecos de lana", datos("Tengo 4 chalecos de lana para subir a 20 mil cada uno").nombre)
        assertEquals("Miel de ulmo", datos("crea un producto: miel de ulmo, 5 mil pesos").nombre)
        assertEquals("Empanadas de pino", InterpreteAlerce.nombreSuelto("unas empanadas de pino")!!.first)
    }

    @Test
    fun `poner una foto no crea un producto llamado foto`() {
        val r = InterpreteAlerce.interpretar("quiero poner una foto")
        assertEquals(Intencion.ENSENAME, r.intencion)
        assertEquals(TemasAyuda.Tema.AGREGAR_FOTO, r.tema)
    }

    @Test
    fun `nombre y precio en la misma respuesta`() {
        val s = sesion()
        s.responder("quiero agregar un producto")
        s.responder("gorro de lana a 5000")
        assertEquals("Gorro de lana", s.producto!!.nombre)
        assertEquals(5000L, s.producto!!.precio)
    }

    // ---------------------------------------------------------------- correcciones

    @Test
    fun `correcciones consecutivas con valores sueltos`() {
        val s = sesion()
        s.responder("agrega gorro de lana a 5000")
        s.responder("después")
        s.responder("no, quince mil")
        assertEquals(15000L, s.producto!!.precio)
        s.responder("no, es una bufanda")
        assertEquals("Bufanda", s.producto!!.nombre)
        val r = s.responder("cambia el precio")
        assertTrue(r.texto.contains("precio correcto"))
        s.responder("8 lucas")
        assertEquals(8000L, s.producto!!.precio)
    }

    @Test
    fun `un numero suelto se entiende segun lo ultimo preguntado`() {
        val s = sesion()
        s.responder("agrega un gorro a 5000")          // pregunta cuántos tiene
        s.responder("4")
        s.responder("no, 6")
        assertEquals(6, s.producto!!.cantidad)
        // Sin contexto claro se pregunta qué es.
        val t = sesion()
        t.responder("agrega un gorro a 5000 tengo 3")
        t.responder("el nombre es gorro azul")
        val r = t.responder("no, 7")
        assertTrue(r.texto.contains("precio o cuántos"))
    }

    // ---------------------------------------------------------------- cambios de intención

    @Test
    fun `ir a otra pantalla en medio no pierde el producto ni lo usa como precio`() {
        val s = sesion()
        s.responder("agrega un gorro")
        val r = s.responder("mejor muéstrame mis productos")
        assertEquals(SesionAsistente.Accion.Navegar(TemasAyuda.Destino.MIS_PRODUCTOS), r.accion)
        assertEquals("Gorro", s.producto!!.nombre)
        assertNull(s.producto!!.precio)
    }

    @Test
    fun `una palabra suelta de feria no saca de la creacion`() {
        val s = sesion()
        s.responder("quiero agregar un producto")
        val r = s.responder("chaleco para la feria")
        assertNull(r.accion)
        assertEquals("Chaleco", s.producto!!.nombre)
    }

    @Test
    fun `otro producto a mitad pregunta antes de dejar el anterior`() {
        val s = sesion()
        s.responder("agrega un gorro a 5000")
        val r = s.responder("quiero agregar otro producto, una bufanda a 8000")
        assertEquals("Gorro", s.producto!!.nombre)
        assertEquals("el precio del gorro no se pisa", 5000L, s.producto!!.precio)
        assertTrue(r.texto.contains("«Bufanda»"))
        s.responder("no, seguir con el anterior")
        assertEquals("Gorro", s.producto!!.nombre)
        s.responder("agrega una bufanda a 8000")
        s.responder("sí, empezar el nuevo")
        assertEquals("Bufanda", s.producto!!.nombre)
        assertEquals(8000L, s.producto!!.precio)
    }

    // ---------------------------------------------------------------- peticiones que no hace

    @Test
    fun `borrar y sumar se explican sin hacerlos`() {
        val borrar = sesion().responder("borra el producto gorro")
        assertNull(borrar.accion)
        assertTrue(borrar.texto.contains("Yo no borro productos"))
        assertTrue(borrar.texto.contains("«gorro»"))
        val sumar = InterpreteAlerce.interpretar("me llegaron 10 gorros más")
        assertEquals(Intencion.SUMAR_CANTIDAD, sumar.intencion)
        assertEquals(10, sumar.datos.cantidad)
        // Etapa 3: la venta se prepara con productos reales; sin catálogo no hay nada que preparar y nunca se anota sola.
        val venta = sesion().responder("registra que vendí 2 gorros a 5000")
        assertNull(venta.accion)
        assertTrue(venta.texto.contains("No encontré «gorros»"))
    }

    @Test
    fun `temas de ayuda que faltaban`() {
        assertEquals(TemasAyuda.Tema.BORRAR_PRODUCTO, InterpreteAlerce.interpretar("¿cómo borro un producto?").tema)
        assertEquals(TemasAyuda.Tema.TEXTO_Y_VOZ, InterpreteAlerce.interpretar("¿cómo agrando la letra?").tema)
        assertEquals(TemasAyuda.Tema.TEXTO_Y_VOZ, InterpreteAlerce.interpretar("cómo escucho en voz alta").tema)
        assertEquals(Intencion.CONSULTAR_VENTAS_MES, InterpreteAlerce.interpretar("cuanto e vendido este mes").intencion)
        assertEquals(Intencion.CAMBIAR_PRECIO, InterpreteAlerce.interpretar("súbele el precio a los aros").intencion)
        assertEquals(Intencion.DESCONOCIDA, InterpreteAlerce.interpretar("quiero cambiar mi contraseña").intencion)
    }

    /**
     * Android compila las expresiones regulares con ICU, no con Java: «(?U)» funciona en estas pruebas
     * pero cierra la app en el celular (pasó en la revisión). Se revisa el código fuente del asistente.
     */
    @Test
    fun `los patrones no usan opciones que Android no acepta`() {
        val carpeta = java.io.File("src/main/java/com/example/trabajodea10/data/asistente")
        assertTrue("no se encontró ${carpeta.absolutePath}", carpeta.isDirectory)
        carpeta.walk().filter { it.extension == "kt" }.forEach { f ->
            assertFalse("${f.name} usa (?U)", f.readText().contains("(?U)"))
        }
    }

    // ---------------------------------------------------------------- tarjeta y borrador

    @Test
    fun `la tarjeta deja escribir espacios mientras se escribe`() {
        val s = sesion()
        s.responder("agrega un gorro a 5000")
        s.editarAMano("Gorro ", null, 5000, null)
        assertEquals("antes se borraba el espacio y no se podía escribir «Gorro de lana»", "Gorro ", s.producto!!.nombre)
        s.editarAMano("Gorro de lana", null, 5000, null)
        assertEquals("Gorro de lana", s.producto!!.nombre)
        s.editarAMano("   ", null, 5000, null)
        assertNull(s.producto!!.nombre)
    }

    @Test
    fun `cambios sin guardar de un producto existente se informan como tales`() {
        TramaStore.guardarTienda(Tienda(id = "t_revision", nombre = "Tienda revisión"))
        TramaStore.descartarBorrador()
        TramaStore.prepararProductoAsistido("Chal gris", null, 12000, 1)
        val guardado = TramaStore.finalizarBorrador()!!
        TramaStore.abrirEdicion(guardado.id)
        TramaStore.actualizarBorrador(TramaStore.borrador!!.producto.copy(nombre = "Chal gris oscuro"), 1)
        val r = TramaStore.prepararProductoAsistido("Gorro", null, 5000, null)
        assertEquals(TramaStore.PreparacionAsistida.HayBorrador("Chal gris oscuro", esNuevo = false), r)
        TramaStore.prepararProductoAsistido("Gorro", null, 5000, null, reemplazar = true)
        // Descartar los cambios no borra el producto: sigue en el catálogo como estaba.
        val enCatalogo = TramaStore.productos.firstOrNull { it.id == guardado.id }
        assertNotNull(enCatalogo)
        assertEquals("Chal gris", enCatalogo!!.nombre)
        TramaStore.descartarBorrador()
        TramaStore.productos.removeAll { it.tiendaId == "t_revision" }
    }
}
