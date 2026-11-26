package com.example.trabajodea10.data.remoto

import com.example.trabajodea10.data.CuentaAdmin
import com.example.trabajodea10.data.CuentaEmprendedora
import com.example.trabajodea10.data.EntradaBitacora
import com.example.trabajodea10.data.EstadoCuentaAdmin
import com.example.trabajodea10.data.FilaChecklist
import com.example.trabajodea10.data.TipoAdmin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/*
 * Operaciones de administración en Android. Son EXACTAMENTE las que usa el panel web
 * (js/servicio.js y js/vitrina6.js) y el servidor las protege con es_admin() y RLS
 * (supabase/07, 09, 10 y 11). No hay rutas nuevas ni permisos nuevos: si el servidor
 * rechaza, la app muestra el motivo real. Nada se guarda «solo en el celular».
 */
object AdminRepositorio {

    private fun JsonObject.txt(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull
    private fun lista(t: String): List<JsonObject> = ClienteSupabase.json.parseToJsonElement(t).jsonArray.map { it.jsonObject }
    private fun objeto(t: String): JsonObject = ClienteSupabase.json.parseToJsonElement(t).jsonObject
    private fun texto(t: String): String = ClienteSupabase.json.parseToJsonElement(t).jsonPrimitive.content
    private const val PREFER_UPSERT = "resolution=merge-duplicates,return=representation"
    private fun rpc(nombre: String, cuerpo: JsonObject = JsonObject(emptyMap())) =
        suspend { ClienteSupabase.pedir("POST", "/rest/v1/rpc/$nombre", cuerpoJson = cuerpo.toString()) }

    // ---------------------------------------------------------------- Cuentas de emprendedoras

    /** true si el servidor ya tiene 11_registro_controlado (código de activación). */
    var registroControlado: Boolean? = null
        private set

    suspend fun emprendedoras(): List<CuentaEmprendedora> {
        // Servidor con 11: estados completos. Sin 11: la función de 10 (las apps antiguas siguen igual).
        val conCodigo = runCatching { rpc("listar_cuentas_emprendedoras")() }
        conCodigo.onSuccess { registroControlado = true }
        val texto = conCodigo.getOrElse { e ->
            if ((e as? ErrorSupabase)?.crudo?.lowercase()?.contains("could not find the function") == true || (e as? ErrorSupabase)?.codigo == 404) {
                registroControlado = false
                rpc("listar_emprendedoras")()
            } else throw e
        }
        return lista(texto).map { o ->
            CuentaEmprendedora(
                usuarioId = o.txt("usuario_id"),
                email = o.txt("email").orEmpty(),
                nombre = o.txt("nombre")?.takeIf { it.isNotBlank() } ?: o.txt("email").orEmpty(),
                tiendaId = o.txt("tienda_id"),
                tiendaNombre = o.txt("tienda_nombre"),
                estado = EstadoCuentaAdmin.desde(o.txt("estado")),
                creadoEn = o.txt("creado_en"),
                autorizada = o["autorizada"]?.jsonPrimitive?.booleanOrNull,
                codigoVigente = o["codigo_vigente"]?.jsonPrimitive?.booleanOrNull ?: false,
                codigoExpira = o.txt("codigo_expira"),
                intentos = o["intentos"]?.jsonPrimitive?.intOrNull ?: 0,
                asociaciones = (o["asociaciones"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                cuentaAnterior = o["cuenta_anterior"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        }
    }

    /** Autoriza un correo y devuelve el código de activación (se muestra una sola vez). */
    suspend fun autorizar(email: String, nombre: String, asociacionId: String?): CodigoActivacion {
        val o = objeto(rpc("autorizar_emprendedora", buildJsonObject {
            put("p_email", email.trim().lowercase()); put("p_nombre", nombre.trim())
            if (asociacionId != null) put("p_asociacion", asociacionId) else put("p_asociacion", JsonNull)
        })())
        return CodigoActivacion(o.txt("email").orEmpty(), o.txt("codigo").orEmpty(), o.txt("expira"), o.txt("estado"))
    }

    suspend fun regenerarCodigo(email: String): CodigoActivacion {
        val o = objeto(rpc("regenerar_codigo_emprendedora", buildJsonObject { put("p_email", email) })())
        return CodigoActivacion(o.txt("email").orEmpty(), o.txt("codigo").orEmpty(), o.txt("expira"), null)
    }

    suspend fun revocar(email: String): String = texto(rpc("revocar_autorizacion", buildJsonObject { put("p_email", email) })())
    suspend fun permitirCuentaExistente(email: String): String = texto(rpc("permitir_cuenta_existente", buildJsonObject { put("p_email", email) })())
    suspend fun regularizar(usuarioId: String): String = texto(rpc("regularizar_emprendedora", buildJsonObject { put("p_usuario", usuarioId) })())

    suspend fun editarEmprendedora(usuarioId: String, nombre: String, tiendaNombre: String) {
        rpc("editar_emprendedora", buildJsonObject { put("p_usuario", usuarioId); put("p_nombre", nombre.trim()); put("p_tienda_nombre", tiendaNombre.trim()) })()
    }

    suspend fun cambiarEstadoEmprendedora(usuarioId: String, activa: Boolean) {
        rpc("cambiar_estado_emprendedora", buildJsonObject { put("p_usuario", usuarioId); put("p_activa", activa) })()
    }

    /** Pertenencia de una tienda a una asociación (tabla tienda_asociaciones; RLS: solo admin escribe). */
    suspend fun agregarIntegrante(tiendaId: String, asociacionId: String) {
        val r = ClienteSupabase.pedir("POST", "/rest/v1/tienda_asociaciones?on_conflict=tienda_id,asociacion_id",
            cuerpoJson = buildJsonObject { put("tienda_id", tiendaId); put("asociacion_id", asociacionId) }.toString(),
            cabeceras = mapOf("Prefer" to PREFER_UPSERT))
        if (lista(r).isEmpty()) throw IllegalStateException("permission denied: no se guardó la pertenencia")
    }

    suspend fun quitarIntegrante(tiendaId: String, asociacionId: String) {
        ClienteSupabase.pedir("DELETE", "/rest/v1/tienda_asociaciones?tienda_id=eq.${cod(tiendaId)}&asociacion_id=eq.${cod(asociacionId)}")
    }

    // ---------------------------------------------------------------- Administradores, bitácora, configuración

    suspend fun admins(): List<CuentaAdmin> =
        lista(rpc("listar_admins")()).map { o ->
            CuentaAdmin(o.txt("email").orEmpty(), o.txt("nombre").orEmpty(), o.txt("estado").orEmpty(),
                o["es_yo"]?.jsonPrimitive?.booleanOrNull ?: false)
        }

    suspend fun crearAdmin(email: String, nombre: String): String =
        texto(rpc("crear_admin", buildJsonObject { put("p_email", email.trim().lowercase()); put("p_nombre", nombre.trim()) })())

    suspend fun cambiarEstadoAdmin(email: String, activo: Boolean): String =
        texto(rpc("cambiar_estado_admin", buildJsonObject { put("p_email", email); put("p_activo", activo) })())

    suspend fun bitacora(limite: Int): List<EntradaBitacora> =
        lista(rpc("ver_bitacora", buildJsonObject { put("p_limite", limite.coerceIn(1, 500)) })()).map { o ->
            EntradaBitacora(o.txt("en").orEmpty(), o.txt("quien").orEmpty(), o.txt("accion").orEmpty(),
                o.txt("objeto_tipo").orEmpty(), o.txt("objeto_id").orEmpty())
        }

    /** Navegación sin cuenta (clave navegacion_anonima de la tabla configuracion; RLS: solo admin). */
    suspend fun guardarNavegacionAnonima(valor: String) {
        val r = ClienteSupabase.pedir("POST", "/rest/v1/configuracion?on_conflict=clave",
            cuerpoJson = buildJsonObject { put("clave", "navegacion_anonima"); put("valor", valor) }.toString(),
            cabeceras = mapOf("Prefer" to PREFER_UPSERT))
        if (lista(r).isEmpty()) throw IllegalStateException("permission denied: no se guardó la configuración")
    }

    // ---------------------------------------------------------------- Tipos de producto

    suspend fun tipos(): List<TipoAdmin> {
        val uso = runCatching {
            lista(rpc("uso_tipos_producto")()).associate { (it.txt("tipo_id").orEmpty()) to (it["productos"]?.jsonPrimitive?.intOrNull ?: 0) }
        }.getOrDefault(emptyMap())
        return lista(ClienteSupabase.pedir("GET", "/rest/v1/tipos_producto?select=id,categoria,nombre,activo,orden&order=orden.asc")).map { o ->
            val id = o.txt("id").orEmpty()
            TipoAdmin(id, o.txt("categoria").orEmpty(), o.txt("nombre").orEmpty(),
                o["activo"]?.jsonPrimitive?.booleanOrNull ?: true, o["orden"]?.jsonPrimitive?.intOrNull ?: 0, uso[id] ?: 0)
        }
    }

    /** Crea o edita un tipo (p_id nulo = nuevo). Los campos propios del tipo se conservan (p_campos nulo). */
    suspend fun guardarTipo(id: String?, nombre: String, categoria: String, orden: Int, activo: Boolean): String =
        texto(rpc("guardar_tipo_producto", buildJsonObject {
            if (id != null) put("p_id", id) else put("p_id", JsonNull)
            put("p_nombre", nombre.trim()); put("p_categoria", categoria.trim()); put("p_orden", orden)
            put("p_campos", JsonNull); put("p_activo", activo)
        })())

    suspend fun eliminarTipo(id: String): String = texto(rpc("eliminar_tipo_producto", buildJsonObject { put("p_id", id) })())

    // ---------------------------------------------------------------- Asociaciones

    suspend fun guardarAsociacion(id: String, nombre: String, sector: String, localidad: String, descripcion: String, activa: Boolean) {
        val r = ClienteSupabase.pedir("POST", "/rest/v1/asociaciones?on_conflict=id",
            cuerpoJson = buildJsonObject {
                put("id", id); put("nombre", nombre.trim()); put("sector", sector.trim())
                put("localidad", localidad.trim()); put("descripcion", descripcion.trim()); put("activa", activa)
            }.toString(),
            cabeceras = mapOf("Prefer" to PREFER_UPSERT))
        if (lista(r).isEmpty()) throw IllegalStateException("permission denied: la asociación no se guardó")
    }

    // ---------------------------------------------------------------- Ferias

    /** Crea o edita la fila de la feria (mismo upsert que la web). Publicar y cancelar van por sus funciones. */
    suspend fun guardarFeria(f: FormFeria) {
        val r = ClienteSupabase.pedir("POST", "/rest/v1/ferias?on_conflict=id",
            cuerpoJson = buildJsonObject {
                put("id", f.id); put("nombre", f.nombre.trim()); put("descripcion", f.descripcion.trim())
                put("ubicacion", f.lugar.trim()); put("fecha_inicio", f.fechaInicio); put("fecha_fin", f.fechaFin.ifBlank { f.fechaInicio })
                put("horario", f.horario.trim()); put("organizador", f.organizador.trim())
                if (f.esNueva) put("estado", "BORRADOR")
                if (f.lat != null && f.lng != null) { put("latitud", f.lat); put("longitud", f.lng) }
            }.toString(),
            cabeceras = mapOf("Prefer" to PREFER_UPSERT))
        if (lista(r).isEmpty()) throw IllegalStateException("permission denied: la feria no se guardó")
    }

    suspend fun publicarFeria(id: String) { rpc("publicar_feria", buildJsonObject { put("p_feria", id) })() }

    suspend fun cancelarFeria(id: String, motivo: String): Int =
        objeto(rpc("cancelar_feria", buildJsonObject { put("p_feria", id); put("p_motivo", motivo) })())["avisadas"]?.jsonPrimitive?.intOrNull ?: 0

    /** Invita (o agrega directamente) tiendas y asociaciones. Devuelve el resumen del servidor. */
    suspend fun invitar(feriaId: String, tiendas: List<String>, asociaciones: List<String>, directo: Boolean): JsonObject =
        objeto(rpc("invitar_a_feria", buildJsonObject {
            put("p_feria", feriaId)
            put("p_tiendas", buildJsonArray { tiendas.forEach { add(JsonPrimitive(it)) } })
            put("p_asociaciones", buildJsonArray { asociaciones.forEach { add(JsonPrimitive(it)) } })
            put("p_directo", directo)
        })())

    suspend fun checklist(feriaId: String): List<FilaChecklist> =
        lista(rpc("checklist_feria", buildJsonObject { put("p_feria", feriaId) })()).map { o ->
            FilaChecklist(o.txt("tienda_id").orEmpty(), o.txt("nombre").orEmpty(), o.txt("invitacion").orEmpty(),
                o.txt("asistencia").orEmpty(), o.txt("origen").orEmpty(), o.txt("via_asociacion"))
        }

    suspend fun registrarAsistencia(feriaId: String, tiendaId: String, asistencia: String) {
        rpc("registrar_asistencia", buildJsonObject { put("p_feria", feriaId); put("p_tienda", tiendaId); put("p_asistencia", asistencia) })()
    }

    private fun cod(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
}

data class CodigoActivacion(val email: String, val codigo: String, val expira: String?, val estado: String?)

data class FormFeria(
    val id: String, val esNueva: Boolean, val nombre: String, val descripcion: String, val lugar: String,
    val fechaInicio: String, val fechaFin: String, val horario: String, val organizador: String,
    val lat: Double? = null, val lng: Double? = null
)
