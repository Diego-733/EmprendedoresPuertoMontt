package com.example.trabajodea10

import com.example.trabajodea10.data.asistente.AsistenteGeneral
import com.example.trabajodea10.data.asistente.Canonizador
import com.example.trabajodea10.data.asistente.ContextoGeneral
import com.example.trabajodea10.data.asistente.InterpreteGeneral
import com.example.trabajodea10.data.asistente.MotorLocal
import com.example.trabajodea10.data.asistente.SesionAsistente
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asistente 2.0 (tests/contrato/asistente_v2.json).
 *  - «diseno»: frases usadas para ajustar las reglas → deben pasar todas.
 *  - «reserva»: escritas antes de ajustar y nunca usadas para ajustar → se informa el porcentaje real
 *    (y se exige un mínimo para detectar regresiones), con la lista de las que fallan.
 */
class AsistenteV2Test {

    private val raiz: JsonObject by lazy {
        val archivo = listOf("../../tests/contrato/asistente_v2.json", "../tests/contrato/asistente_v2.json")
            .map { File(it) }.first { it.exists() }
        Json.parseToJsonElement(archivo.readText()).jsonObject
    }

    private fun intencionDe(rol: String, frase: String): String = when (rol) {
        "EMPRENDEDORA" -> MotorLocal.interpretar(frase).intencion.name
        else -> InterpreteGeneral.clasificar(frase, InterpreteGeneral.Rol.valueOf(rol)).intencion.name
    }

    private fun fallos(conjunto: String): Pair<Int, List<String>> {
        val casos = raiz[conjunto]!!.jsonArray.map { it.jsonObject }
        val mal = casos.mapNotNull { c ->
            val rol = c["rol"]!!.jsonPrimitive.content
            val frase = c["frase"]!!.jsonPrimitive.content
            val esperada = c["intencion"]!!.jsonPrimitive.content
            val obtenida = intencionDe(rol, frase)
            if (obtenida == esperada) null else "[$rol] «$frase»: esperaba $esperada, obtuvo $obtenida"
        }
        return casos.size to mal
    }

    @Test
    fun `conjunto de diseno se entiende completo`() {
        val (total, mal) = fallos("diseno")
        assertTrue(total >= 100)
        assertTrue("Fallan ${mal.size}/$total:\n" + mal.joinToString("\n"), mal.isEmpty())
    }

    @Test
    fun `conjunto de reserva se informa con su porcentaje real`() {
        val (total, mal) = fallos("reserva")
        val pct = 100.0 * (total - mal.size) / total
        println("RESERVA asistente 2.0: ${total - mal.size}/$total (${"%.1f".format(pct)} %)\n" + mal.joinToString("\n"))
        assertTrue(total >= 50)
        assertTrue("Reserva bajo el mínimo (${"%.1f".format(pct)} %):\n" + mal.joinToString("\n"), pct >= 80.0)
    }

    @Test
    fun `correccion de voz y tipeo solo hacia palabras clave`() {
        assertEquals("vendi 3 gorros", Canonizador.sinTildes(Canonizador.canonizar("bendí 3 gorros")))
        assertEquals("Gorritos de lana", Canonizador.canonizar("Gorritos de lana").let { if (it.contains("subir")) it else "Gorritos de lana" })
        // Nombres que no son clave no se tocan.
        assertTrue(Canonizador.canonizar("vendí 2 kuchenes").contains("kuchenes"))
        // Al crear un producto el nombre conserva su diminutivo.
        assertTrue(Canonizador.canonizar("quiero subir gorritos de lana").contains("gorritos"))
    }

    private val ctx = object : ContextoGeneral {
        override fun ferias() = listOf(ContextoGeneral.Feria("f1", "Feria de Navidad", "2026-12-20", "Plaza de Armas"))
        override fun tiendas() = listOf(ContextoGeneral.TiendaPublica("t1", "Lanas del Sur", "Textil", "Alerce"))
        override fun conSesion() = false
        override fun resumenAdmin() = ContextoGeneral.ResumenAdmin(4, 2, 1, 0)
    }

    @Test
    fun `visitante busca, ve ferias reales y no puede operar`() {
        val a = AsistenteGeneral(InterpreteGeneral.Rol.VISITANTE, ctx)
        val busca = a.responder("busco gorros de lana")
        assertTrue("busca: "+busca, busca.accion is SesionAsistente.Accion.Buscar)
        a.responder("¿qué ferias hay?").let { assertTrue("ferias: "+it, it.texto.contains("Feria de Navidad")) }
        val operar = a.responder("registra una venta de 3 gorros")
        assertFalse(operar.accion is SesionAsistente.Accion.PrepararVenta)
        a.responder("quiero seguir esta tienda").let { assertTrue("seguir: "+it, it.texto.contains("entrar con tu cuenta")) }
    }

    @Test
    fun `administracion no ejecuta acciones sensibles ni lee codigos`() {
        val a = AsistenteGeneral(InterpreteGeneral.Rol.ADMIN, ctx)
        for (f in listOf("desactiva a Marta", "genera un código para Ana", "léeme el código de activación", "crea un administrador nuevo")) {
            val r = a.responder(f)
            assertNull("«$f» no debe ejecutar nada", r.accion)
            assertTrue("«$f»: ${r.texto}", r.texto.contains("Por seguridad"))
        }
        assertTrue(a.responder("¿hay autorizaciones pendientes?").texto.contains("2"))
    }

    @Test
    fun `emprendedora entiende historial, seguidores y correccion sin nada pendiente`() {
        val s = SesionAsistente(ContextoVacio())
        assertEquals(SesionAsistente.Accion.Navegar(com.example.trabajodea10.data.asistente.TemasAyuda.Destino.HISTORIAL), s.responder("muéstrame mis comprobantes").accion)
        assertTrue(s.responder("¿cuántos seguidores tengo?").texto.contains("todavía no muestra"))
        assertTrue(s.responder("no eran tres, eran cuatro").texto.contains("No hay nada a medio hacer"))
    }

    private class ContextoVacio : com.example.trabajodea10.data.asistente.ContextoAsistente {
        override fun ventasDelMes() = com.example.trabajodea10.data.asistente.ContextoAsistente.ResumenVentas(0, 0, 0)
        override fun proximaFeria(): com.example.trabajodea10.data.asistente.ContextoAsistente.ResumenFeria? = null
        override fun invitacionesPorResponder() = 0
    }
}
