package com.example.trabajodea10.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.trabajodea10.data.remoto.AdminRepositorio
import com.example.trabajodea10.data.remoto.ConfigSupabase

/*
 * Panel de administración en Android (rediseño v2).
 *
 * Antes, el inicio y «Emprendedoras» mostraban cuentas y ferias de ejemplo (MockData) aunque la app
 * estuviera conectada. Ahora todo sale del servidor con las MISMAS lecturas que usa el panel web
 * (js/servicio.js): listar_emprendedoras, listar_admins, ver_bitacora, uso_tipos_producto y la tabla
 * tipos_producto. Esas funciones ya exigen es_admin() en el servidor: la app no cambia permisos ni RLS.
 *
 * Sin servidor configurado (APK de pruebas), se muestran datos de EJEMPLO rotulados como tales.
 */

enum class EstadoCuentaAdmin(val etiqueta: String) {
    ACTIVA("Activa"),
    DESACTIVADA("Desactivada"),
    SIN_TIENDA("Sin tienda"),
    PENDIENTE_REGISTRO("Falta que se registre");

    companion object {
        fun desde(texto: String?): EstadoCuentaAdmin = entries.firstOrNull { it.name == texto } ?: SIN_TIENDA
    }
}

data class CuentaEmprendedora(
    val usuarioId: String?,
    val email: String,
    val nombre: String,
    val tiendaId: String?,
    val tiendaNombre: String?,
    val estado: EstadoCuentaAdmin,
    val creadoEn: String?
)

data class CuentaAdmin(val email: String, val nombre: String, val estado: String, val esYo: Boolean)

data class EntradaBitacora(val en: String, val quien: String, val accion: String, val objetoTipo: String, val objetoId: String)

data class TipoAdmin(val id: String, val categoria: String, val nombre: String, val activo: Boolean, val orden: Int, val productos: Int)

/** Reglas puras del panel (probadas en ReglasAdminTest). */
object ReglasAdmin {

    data class Resumen(val activas: Int, val pendientes: Int, val desactivadas: Int, val sinTienda: Int) {
        val total: Int get() = activas + pendientes + desactivadas + sinTienda
    }

    fun resumen(cuentas: List<CuentaEmprendedora>): Resumen = Resumen(
        activas = cuentas.count { it.estado == EstadoCuentaAdmin.ACTIVA },
        pendientes = cuentas.count { it.estado == EstadoCuentaAdmin.PENDIENTE_REGISTRO },
        desactivadas = cuentas.count { it.estado == EstadoCuentaAdmin.DESACTIVADA },
        sinTienda = cuentas.count { it.estado == EstadoCuentaAdmin.SIN_TIENDA }
    )

    data class Ferias(val enCurso: List<FeriaPublica>, val proximas: List<FeriaPublica>, val anteriores: List<FeriaPublica>, val canceladas: List<FeriaPublica>)

    /** En curso: hoy está entre inicio y fin. Próximas: empiezan después de hoy. Las canceladas aparte. */
    fun clasificarFerias(ferias: List<FeriaPublica>, hoy: String): Ferias {
        val vigentes = ferias.filter { it.estado != "CANCELADA" && it.estado != "BORRADOR" }
        fun inicio(f: FeriaPublica) = (f.fechaInicio ?: "9999-12-31").take(10)
        fun fin(f: FeriaPublica) = (f.fechaFin ?: f.fechaInicio ?: "9999-12-31").take(10)
        return Ferias(
            enCurso = vigentes.filter { inicio(it) <= hoy && fin(it) >= hoy }.sortedBy { fin(it) },
            proximas = vigentes.filter { inicio(it) > hoy }.sortedBy { inicio(it) },
            anteriores = vigentes.filter { fin(it) < hoy }.sortedByDescending { fin(it) },
            canceladas = ferias.filter { it.estado == "CANCELADA" }
        )
    }

    /** Filtro de la lista de cuentas: texto en nombre, correo o tienda, y estado opcional. */
    fun filtrar(cuentas: List<CuentaEmprendedora>, texto: String, estado: EstadoCuentaAdmin?): List<CuentaEmprendedora> {
        val q = texto.trim().lowercase()
        return cuentas.filter { c ->
            (estado == null || c.estado == estado) &&
                (q.isEmpty() || c.nombre.lowercase().contains(q) || c.email.lowercase().contains(q) ||
                    (c.tiendaNombre ?: "").lowercase().contains(q))
        }
    }

    /** «Cuántas tiendas integra cada asociación», con las pertenencias públicas (tienda → asociaciones). */
    fun integrantesPorAsociacion(pertenencias: Map<String, List<String>>, tiendas: List<Tienda>): Map<String, Int> {
        val conteo = mutableMapOf<String, Int>()
        pertenencias.values.flatten().forEach { conteo[it] = (conteo[it] ?: 0) + 1 }
        // Tiendas con su asociación principal que no aparezcan en las pertenencias.
        tiendas.forEach { t ->
            val id = t.asociacionId
            if (!id.isNullOrBlank() && pertenencias[t.id]?.contains(id) != true) conteo[id] = (conteo[id] ?: 0) + 1
        }
        return conteo
    }
}

object AdminStore {
    private const val TAG = "AdminStore"

    val cuentas = mutableStateListOf<CuentaEmprendedora>()
    val admins = mutableStateListOf<CuentaAdmin>()
    val bitacora = mutableStateListOf<EntradaBitacora>()
    val tipos = mutableStateListOf<TipoAdmin>()

    var cargando by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** true en el APK de pruebas sin servidor: la interfaz lo rotula «Datos de ejemplo». */
    var sonEjemplo by mutableStateOf(false); private set
    var cargadoAlgunaVez by mutableStateOf(false); private set

    suspend fun cargar() {
        if (!ConfigSupabase.configurado) { cargarEjemplo(); return }
        cargando = true
        try {
            val c = AdminRepositorio.emprendedoras()
            cuentas.clear(); cuentas.addAll(c)
            // Lo secundario no impide ver lo principal.
            runCatching { AdminRepositorio.admins() }.onSuccess { admins.clear(); admins.addAll(it) }
            runCatching { AdminRepositorio.bitacora(30) }.onSuccess { bitacora.clear(); bitacora.addAll(it) }
            runCatching { AdminRepositorio.tipos() }.onSuccess { tipos.clear(); tipos.addAll(it) }
            error = null
            sonEjemplo = false
            cargadoAlgunaVez = true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cargar la administración: ${e.message}")
            error = if (e.message?.contains("SOLO_ADMIN") == true) "Esta cuenta no tiene permisos de administración en el servidor."
                else "No se pudieron traer los datos del servidor. Revisa la conexión y vuelve a intentar."
        } finally {
            cargando = false
        }
    }

    /** Activa o desactiva una tienda con la función del servidor (la misma del panel web). */
    suspend fun cambiarEstado(cuenta: CuentaEmprendedora, activa: Boolean): Result<Unit> {
        val id = cuenta.usuarioId ?: return Result.failure(IllegalStateException("Esta cuenta todavía no se registra."))
        if (sonEjemplo) return Result.failure(IllegalStateException("Con datos de ejemplo no se cambia nada en el servidor."))
        return runCatching { AdminRepositorio.cambiarEstadoEmprendedora(id, activa) }.onSuccess { cargar() }
    }

    /** Filtro con que se abre «Emprendedoras» al tocar un pendiente del inicio. */
    var filtroAlAbrir by mutableStateOf<EstadoCuentaAdmin?>(null)

    /**
     * Autoriza un correo como emprendedora (RN-01): misma escritura de siempre en cuentas_autorizadas.
     * Devuelve el resultado REAL del servidor; con datos de ejemplo solo se agrega en este celular.
     */
    suspend fun autorizar(email: String, nombre: String): Result<String> {
        val correo = email.trim().lowercase()
        if (nombre.isBlank()) return Result.failure(IllegalArgumentException("Escribe el nombre de la emprendedora."))
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(correo)) return Result.failure(IllegalArgumentException("Revisa el correo: debe tener la forma nombre@dominio.cl."))
        if (sonEjemplo) {
            cuentas.add(0, CuentaEmprendedora(null, correo, nombre.trim(), null, null, EstadoCuentaAdmin.PENDIENTE_REGISTRO, null))
            return Result.success("Agregado solo en este celular (datos de ejemplo, sin servidor).")
        }
        return runCatching { com.example.trabajodea10.data.remoto.CuentaRepositorio.autorizarEmprendedora(correo, nombre) }
            .map { cargar(); "Correo autorizado. Cuando $nombre se registre con $correo, quedará como emprendedora." }
    }

    fun limpiar() {
        cuentas.clear(); admins.clear(); bitacora.clear(); tipos.clear()
        error = null; sonEjemplo = false; cargadoAlgunaVez = false
    }

    private fun cargarEjemplo() {
        sonEjemplo = true
        cargadoAlgunaVez = true
        error = null
        if (cuentas.isEmpty()) {
            cuentas.addAll(SesionManager.TIENDAS_INICIALES_ALERCE.mapIndexed { i, t ->
                CuentaEmprendedora("u_ejemplo_$i", "tienda${i + 1}@ejemplo.cl", t.nombre, t.id, t.nombre,
                    if (i == 3) EstadoCuentaAdmin.DESACTIVADA else EstadoCuentaAdmin.ACTIVA, "2026-11-0${i + 1}")
            })
            cuentas.add(CuentaEmprendedora(null, "nueva@ejemplo.cl", "Correo autorizado de ejemplo", null, null, EstadoCuentaAdmin.PENDIENTE_REGISTRO, "2026-11-20"))
        }
        if (admins.isEmpty()) admins.add(CuentaAdmin("admin@ejemplo.cl", "Administración de prueba", "ACTIVA", true))
        if (bitacora.isEmpty()) {
            bitacora.add(EntradaBitacora("2026-11-24T10:12:00", "admin@ejemplo.cl", "Ejemplo: publicó una feria", "feria", ""))
            bitacora.add(EntradaBitacora("2026-11-22T16:40:00", "admin@ejemplo.cl", "Ejemplo: autorizó un correo", "cuenta", ""))
        }
        if (tipos.isEmpty()) {
            listOf("Panadería" to "Alimentos", "Tejidos" to "Artesanía", "Repostería" to "Alimentos", "Servicios" to "Servicios")
                .forEachIndexed { i, (n, c) -> tipos.add(TipoAdmin("tipo_$i", c, n, true, i, 0)) }
        }
    }
}
