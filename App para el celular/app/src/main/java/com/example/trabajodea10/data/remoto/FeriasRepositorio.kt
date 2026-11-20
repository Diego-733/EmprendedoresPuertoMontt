package com.example.trabajodea10.data.remoto

import com.example.trabajodea10.data.AvisoCuenta
import com.example.trabajodea10.data.MiFeria
import com.example.trabajodea10.data.ParticipantePublico
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
 * Entrega 7 (supabase/07_ferias_seguidores.sql): ferias de la emprendedora, ficha
 * pública, seguidores, consentimiento, dispositivos, avisos y calificaciones.
 * Las pantallas no llaman a la base de datos (RNF-DAT-02): todo pasa por aquí.
 * Cada permiso lo vuelve a comprobar el servidor.
 */
object FeriasRepositorio {

    private fun cod(v: String) = URLEncoder.encode(v, "UTF-8")
    private fun JsonObject.txt(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.num(k: String): Double? = this[k]?.jsonPrimitive?.doubleOrNull
    private fun lista(t: String): List<JsonObject> =
        if (t.isBlank()) emptyList() else ClienteSupabase.json.parseToJsonElement(t).jsonArray.map { it.jsonObject }
    private fun objeto(t: String): JsonObject = ClienteSupabase.json.parseToJsonElement(t).jsonObject

    private suspend fun rpc(nombre: String, cuerpo: JsonObject = buildJsonObject { }, conToken: Boolean = true): String =
        ClienteSupabase.pedir("POST", "/rest/v1/rpc/$nombre", cuerpoJson = cuerpo.toString(), conToken = conToken)

    // ---------- Emprendedora ----------

    suspend fun misFerias(): List<MiFeria> = lista(rpc("mis_ferias")).map { o ->
        MiFeria(
            feriaId = o.txt("feria_id").orEmpty(),
            nombre = o.txt("nombre") ?: "Feria",
            ubicacion = o.txt("ubicacion").orEmpty(),
            fechaInicio = o.txt("fecha_inicio")?.take(10),
            fechaFin = (o.txt("fecha_fin") ?: o.txt("fecha_inicio"))?.take(10),
            horario = o.txt("horario").orEmpty(),
            estado = o.txt("estado") ?: "PROXIMA",
            lat = o.num("latitud"),
            lng = o.num("longitud"),
            invitacion = o.txt("invitacion") ?: "PENDIENTE",
            asistencia = o.txt("asistencia") ?: "PENDIENTE"
        )
    }.filter { it.feriaId.isNotBlank() }

    /** «Participo» / «No participo». Devuelve el estado que guardó el servidor. Repetir es seguro. */
    suspend fun responder(feriaId: String, participo: Boolean): String =
        objeto(rpc("responder_invitacion", buildJsonObject { put("p_feria", feriaId); put("p_participo", participo) })).txt("invitacion") ?: ""

    suspend fun guardarAvisosContacto(activos: Boolean, desde: String, hasta: String) {
        rpc("guardar_avisos_contacto", buildJsonObject { put("p_activos", activos); put("p_desde", desde); put("p_hasta", hasta) })
    }

    /** Preferencia actual de avisos de contacto de mi tienda (solo la dueña la puede leer). */
    suspend fun avisosContacto(tiendaId: String): Triple<Boolean, String, String> {
        val o = lista(ClienteSupabase.pedir("GET", "/rest/v1/preferencias_tienda?select=*&tienda_id=eq.${cod(tiendaId)}")).firstOrNull()
            ?: return Triple(false, "09:00", "20:00")
        return Triple(o["avisos_contacto"]?.jsonPrimitive?.booleanOrNull ?: false,
            (o.txt("avisos_desde") ?: "09:00").take(5), (o.txt("avisos_hasta") ?: "20:00").take(5))
    }

    suspend fun cambiarCalificaciones(tiendaId: String, activas: Boolean) {
        val r = ClienteSupabase.pedir("PATCH", "/rest/v1/tiendas?id=eq.${cod(tiendaId)}&select=calificaciones_activas",
            cuerpoJson = buildJsonObject { put("calificaciones_activas", activas) }.toString(),
            cabeceras = mapOf("Prefer" to "return=representation"))
        if (lista(r).isEmpty()) throw ErrorSupabase(403, "No tienes permiso para cambiar esta tienda.")
    }

    // ---------- Público ----------

    suspend fun participantes(feriaId: String): List<ParticipantePublico> =
        lista(rpc("participantes_feria", buildJsonObject { put("p_feria", feriaId) }, conToken = ClienteSupabase.sesion != null)).map { o ->
            ParticipantePublico(o.txt("tienda_id").orEmpty(), o.txt("nombre") ?: "Tienda", o.txt("rubro").orEmpty(), o.txt("sector").orEmpty())
        }

    /** Pertenencias tienda ↔ asociación (una tienda puede estar en varias). */
    suspend fun pertenencias(): Map<String, List<String>> =
        lista(ClienteSupabase.pedir("GET", "/rest/v1/tienda_asociaciones?select=tienda_id,asociacion_id", conToken = false))
            .groupBy({ it.txt("tienda_id").orEmpty() }, { it.txt("asociacion_id").orEmpty() })

    /** Promedio y cantidad (sin identificar a nadie). Null si la tienda no las tiene activas. */
    suspend fun resumenCalificaciones(tiendaId: String): Pair<Double, Int>? {
        val o = objeto(rpc("resumen_calificaciones", buildJsonObject { put("p_tienda", tiendaId) }, conToken = ClienteSupabase.sesion != null))
        if (o["activas"]?.jsonPrimitive?.booleanOrNull != true) return null
        val t = o["tienda"]?.jsonObject ?: return 0.0 to 0
        return (t.num("promedio") ?: 0.0) to (t["cantidad"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0)
    }

    suspend fun calificar(tiendaId: String, productoId: String?, estrellas: Int, comentario: String) {
        rpc("calificar", buildJsonObject {
            put("p_tienda", tiendaId); put("p_producto", productoId); put("p_estrellas", estrellas); put("p_comentario", comentario.trim())
        })
    }

    // ---------- Seguidores, consentimiento, dispositivos y avisos ----------

    suspend fun misSeguimientos(): Set<String> =
        lista(ClienteSupabase.pedir("GET", "/rest/v1/seguimientos?select=tipo,objetivo_id")).map { it.txt("tipo") + ":" + it.txt("objetivo_id") }.toSet()

    suspend fun seguir(tipo: String, id: String) { rpc("seguir", buildJsonObject { put("p_tipo", tipo); put("p_id", id) }) }
    suspend fun dejarDeSeguir(tipo: String, id: String) { rpc("dejar_de_seguir", buildJsonObject { put("p_tipo", tipo); put("p_id", id) }) }

    suspend fun miConsentimiento(): Boolean =
        lista(ClienteSupabase.pedir("GET", "/rest/v1/preferencias_avisos?select=avisos_ferias")).firstOrNull()
            ?.get("avisos_ferias")?.jsonPrimitive?.booleanOrNull ?: false

    suspend fun guardarConsentimiento(si: Boolean) { rpc("guardar_consentimiento", buildJsonObject { put("p_avisos", si) }) }

    suspend fun registrarDispositivo(token: String) {
        rpc("registrar_dispositivo", buildJsonObject { put("p_token", token); put("p_plataforma", "ANDROID") })
    }

    suspend fun quitarDispositivo(token: String) { rpc("quitar_dispositivo", buildJsonObject { put("p_token", token) }) }

    suspend fun misAvisos(limite: Int = 30): List<AvisoCuenta> =
        lista(ClienteSupabase.pedir("GET", "/rest/v1/avisos?select=id,tipo,titulo,cuerpo,feria_id,estado,creado_en,recibido_en,leido_en&order=creado_en.desc&limit=$limite")).map { o ->
            AvisoCuenta(
                id = o.txt("id").orEmpty(), tipo = o.txt("tipo").orEmpty(), titulo = o.txt("titulo").orEmpty(),
                cuerpo = o.txt("cuerpo").orEmpty(), feriaId = o.txt("feria_id"), estado = o.txt("estado").orEmpty(),
                creadoEn = o.txt("creado_en").orEmpty(), recibido = o.txt("recibido_en") != null, leido = o.txt("leido_en") != null
            )
        }

    /** Avisos que corresponde mostrar AHORA en este celular (misma regla que el push; máximo 20). */
    suspend fun avisosParaMostrar(limite: Int = 10): List<AvisoCuenta> =
        lista(rpc("avisos_para_mostrar", buildJsonObject { put("p_limite", limite.coerceIn(1, 20)) })).map { o ->
            AvisoCuenta(id = o.txt("id").orEmpty(), tipo = o.txt("tipo").orEmpty(), titulo = o.txt("titulo").orEmpty(),
                cuerpo = o.txt("cuerpo").orEmpty(), feriaId = o.txt("feria_id"), estado = "", creadoEn = o.txt("creado_en").orEmpty(),
                recibido = false, leido = false)
        }

    suspend fun marcarAviso(id: String, leido: Boolean, recibido: Boolean) {
        rpc("marcar_aviso", buildJsonObject { put("p_id", id); put("p_leido", leido); put("p_recibido", recibido) })
    }
}
