package com.example.trabajodea10.data.remoto

import com.example.trabajodea10.data.AsociacionPublica
import com.example.trabajodea10.data.ContactoRecibido
import com.example.trabajodea10.data.FeriaPublica
import com.example.trabajodea10.data.NavegacionAnonima
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder

/*
 * Entrega 6 (supabase/06_vitrina_mapas.sql): asociaciones, ferias con ubicación,
 * navegación anónima y contactos por formulario. Las pantallas no llaman a la
 * base de datos directamente (RNF-DAT-02): todo pasa por aquí.
 */
object VitrinaRepositorio {

    private fun cod(valor: String): String = URLEncoder.encode(valor, "UTF-8")
    private fun JsonObject.txt(clave: String): String? = this[clave]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.num(clave: String): Double? = this[clave]?.jsonPrimitive?.doubleOrNull
    private fun lista(texto: String): List<JsonObject> =
        ClienteSupabase.json.parseToJsonElement(texto).jsonArray.map { it.jsonObject }

    /** Coordenadas válidas o null (nunca se dibuja un punto en 0,0 por error). */
    private fun coordenadas(lat: Double?, lng: Double?): Pair<Double, Double>? =
        if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null

    suspend fun asociaciones(): List<AsociacionPublica> =
        lista(ClienteSupabase.pedir("GET", "/rest/v1/asociaciones?select=*&order=nombre.asc", conToken = false)).map { o ->
            AsociacionPublica(
                id = o.txt("id").orEmpty(),
                nombre = o.txt("nombre") ?: "Asociación",
                sector = o.txt("sector").orEmpty(),
                localidad = o.txt("localidad").orEmpty(),
                descripcion = o.txt("descripcion").orEmpty(),
                activa = o["activa"]?.jsonPrimitive?.booleanOrNull ?: true
            )
        }.filter { it.id.isNotBlank() }

    /** conToken = true: con la sesión de administración también llegan los borradores (RLS de ferias). */
    suspend fun ferias(conToken: Boolean = false): List<FeriaPublica> =
        lista(ClienteSupabase.pedir("GET", "/rest/v1/ferias?select=*&order=fecha_inicio.asc.nullslast", conToken = conToken)).map { o ->
            val punto = coordenadas(o.num("latitud"), o.num("longitud"))
            FeriaPublica(
                id = o.txt("id").orEmpty(),
                nombre = o.txt("nombre") ?: "Feria",
                descripcion = o.txt("descripcion").orEmpty(),
                imagenUrl = o.txt("imagen_url")?.takeIf { it.startsWith("https://") },
                lugar = o.txt("ubicacion").orEmpty(),
                fechaInicio = o.txt("fecha_inicio")?.take(10),
                fechaFin = (o.txt("fecha_fin") ?: o.txt("fecha_inicio"))?.take(10),
                horario = o.txt("horario").orEmpty(),
                organizador = o.txt("organizador").orEmpty(),
                estado = o.txt("estado") ?: "PROXIMA",
                lat = punto?.first,
                lng = punto?.second
            )
        }.filter { it.id.isNotBlank() }

    /** Opción guardada por el administrador en la web (lectura pública, sin sesión). */
    suspend fun navegacionAnonima(): NavegacionAnonima {
        val filas = lista(ClienteSupabase.pedir("GET", "/rest/v1/configuracion?select=valor&clave=eq.navegacion_anonima", conToken = false))
        return NavegacionAnonima.desde(filas.firstOrNull()?.txt("valor"))
    }

    /** Envía un mensaje a una tienda (con o sin sesión). La RLS exige que la tienda tenga formulario. */
    suspend fun enviarContacto(tiendaId: String, nombre: String, medio: String, mensaje: String, productoId: String?) {
        val cuerpo = buildJsonObject {
            put("tienda_id", tiendaId)
            put("nombre", nombre.trim())
            put("medio", medio.trim())
            put("mensaje", mensaje.trim())
            put("producto_id", productoId)
        }.toString()
        ClienteSupabase.pedir("POST", "/rest/v1/contactos", cuerpoJson = cuerpo, cabeceras = mapOf("Prefer" to "return=minimal"))
    }

    /** Bandeja de la emprendedora (la RLS solo entrega los de su tienda). */
    suspend fun contactos(tiendaId: String): List<ContactoRecibido> =
        lista(ClienteSupabase.pedir("GET", "/rest/v1/contactos?select=*&tienda_id=eq.${cod(tiendaId)}&order=creado_en.desc")).map { o ->
            ContactoRecibido(
                id = o.txt("id").orEmpty(),
                nombre = o.txt("nombre").orEmpty(),
                medio = o.txt("medio").orEmpty(),
                mensaje = o.txt("mensaje").orEmpty(),
                productoId = o.txt("producto_id"),
                leido = o["leido"]?.jsonPrimitive?.booleanOrNull ?: false,
                creadoEn = o.txt("creado_en").orEmpty()
            )
        }

    suspend fun marcarLeido(id: String, leido: Boolean = true) {
        ClienteSupabase.pedir(
            "PATCH", "/rest/v1/contactos?id=eq.${cod(id)}",
            cuerpoJson = buildJsonObject { put("leido", leido) }.toString(),
            cabeceras = mapOf("Prefer" to "return=minimal")
        )
    }
}
