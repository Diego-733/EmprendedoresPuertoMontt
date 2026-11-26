package com.example.trabajodea10

import com.example.trabajodea10.data.asistente.InterpreteAlerce
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrato compartido con la web (tests/contrato/asistente_frases.json): las mismas frases deben
 * dar la misma intención, clase, tema, nombre y precio en Android y en js/asistenteWeb.js.
 */
class ContratoAsistenteTest {

    @Test
    fun `android cumple el contrato compartido con la web`() {
        val archivo = listOf("../../tests/contrato/asistente_frases.json", "../tests/contrato/asistente_frases.json")
            .map { File(it) }.firstOrNull { it.exists() }
        assertTrue("no se encontró tests/contrato/asistente_frases.json", archivo != null)
        val raiz = Json.parseToJsonElement(archivo!!.readText()).jsonObject
        val clases = raiz["clases"]!!.jsonObject
        val casos = raiz["casos"]!!.jsonArray
        assertTrue(casos.size >= 20)
        for (c in casos.map { it.jsonObject }) {
            val frase = c["frase"]!!.jsonPrimitive.content
            val r = InterpreteAlerce.interpretar(frase)
            val esperada = c["intencion"]!!.jsonPrimitive.content
            assertEquals(frase, esperada, r.intencion.name)
            assertEquals("clase de $frase", clases[esperada]!!.jsonPrimitive.content, r.intencion.clase.name)
            c["tema"]?.let { assertEquals("tema de $frase", it.jsonPrimitive.content, r.tema?.name) }
            c["precio"]?.let { assertEquals("precio de $frase", it.jsonPrimitive.longOrNull, r.datos.precio) }
            c["nombre"]?.let { assertEquals("nombre de $frase", it.jsonPrimitive.content, r.datos.nombre) }
        }
    }
}
