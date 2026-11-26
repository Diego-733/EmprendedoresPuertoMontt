package com.example.trabajodea10.data.remoto

import com.example.trabajodea10.data.CuentaAdmin
import com.example.trabajodea10.data.CuentaEmprendedora
import com.example.trabajodea10.data.EntradaBitacora
import com.example.trabajodea10.data.EstadoCuentaAdmin
import com.example.trabajodea10.data.TipoAdmin
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/*
 * Lecturas de administración (rediseño v2). Son las mismas funciones que usa el panel web
 * (js/servicio.js) y que el servidor ya protege con es_admin() (supabase/09 y 10).
 * No hay rutas nuevas, ni SQL, ni cambios de permisos. La única escritura es
 * cambiar_estado_emprendedora, la misma del botón «Desactivar / Activar» de la web.
 */
object AdminRepositorio {

    private fun JsonObject.txt(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull
    private fun lista(t: String): List<JsonObject> = ClienteSupabase.json.parseToJsonElement(t).jsonArray.map { it.jsonObject }

    suspend fun emprendedoras(): List<CuentaEmprendedora> =
        lista(ClienteSupabase.pedir("POST", "/rest/v1/rpc/listar_emprendedoras", cuerpoJson = "{}")).map { o ->
            CuentaEmprendedora(
                usuarioId = o.txt("usuario_id"),
                email = o.txt("email").orEmpty(),
                nombre = o.txt("nombre")?.takeIf { it.isNotBlank() } ?: o.txt("email").orEmpty(),
                tiendaId = o.txt("tienda_id"),
                tiendaNombre = o.txt("tienda_nombre"),
                estado = EstadoCuentaAdmin.desde(o.txt("estado")),
                creadoEn = o.txt("creado_en")
            )
        }

    suspend fun admins(): List<CuentaAdmin> =
        lista(ClienteSupabase.pedir("POST", "/rest/v1/rpc/listar_admins", cuerpoJson = "{}")).map { o ->
            CuentaAdmin(o.txt("email").orEmpty(), o.txt("nombre").orEmpty(), o.txt("estado").orEmpty(),
                o["es_yo"]?.jsonPrimitive?.booleanOrNull ?: false)
        }

    suspend fun bitacora(limite: Int): List<EntradaBitacora> =
        lista(ClienteSupabase.pedir("POST", "/rest/v1/rpc/ver_bitacora",
            cuerpoJson = buildJsonObject { put("p_limite", limite.coerceIn(1, 500)) }.toString())).map { o ->
            EntradaBitacora(o.txt("en").orEmpty(), o.txt("quien").orEmpty(), o.txt("accion").orEmpty(),
                o.txt("objeto_tipo").orEmpty(), o.txt("objeto_id").orEmpty())
        }

    suspend fun tipos(): List<TipoAdmin> {
        val uso = runCatching {
            lista(ClienteSupabase.pedir("POST", "/rest/v1/rpc/uso_tipos_producto", cuerpoJson = "{}"))
                .associate { (it.txt("tipo_id").orEmpty()) to (it["productos"]?.jsonPrimitive?.intOrNull ?: 0) }
        }.getOrDefault(emptyMap())
        return lista(ClienteSupabase.pedir("GET", "/rest/v1/tipos_producto?select=id,categoria,nombre,activo,orden&order=orden.asc")).map { o ->
            val id = o.txt("id").orEmpty()
            TipoAdmin(id, o.txt("categoria").orEmpty(), o.txt("nombre").orEmpty(),
                o["activo"]?.jsonPrimitive?.booleanOrNull ?: true, o["orden"]?.jsonPrimitive?.intOrNull ?: 0, uso[id] ?: 0)
        }
    }

    suspend fun cambiarEstadoEmprendedora(usuarioId: String, activa: Boolean) {
        ClienteSupabase.pedir("POST", "/rest/v1/rpc/cambiar_estado_emprendedora",
            cuerpoJson = buildJsonObject { put("p_usuario", usuarioId); put("p_activa", activa) }.toString())
    }
}
