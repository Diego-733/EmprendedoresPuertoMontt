package com.example.trabajodea10.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.trabajodea10.data.remoto.AdminRepositorio
import com.example.trabajodea10.data.remoto.CodigoActivacion
import com.example.trabajodea10.data.remoto.ConfigSupabase
import com.example.trabajodea10.data.remoto.ErrorSupabase
import com.example.trabajodea10.data.remoto.FormFeria

/*
 * Panel de administración en Android.
 *
 * Todo sale del servidor y todo se guarda en el servidor, con las MISMAS operaciones del panel web
 * (supabase/07, 09, 10 y 11, protegidas con es_admin()). Ninguna operación cambia solo el celular:
 * si el servidor la rechaza, se muestra el motivo real.
 *
 * Sin servidor configurado (APK de pruebas) se muestran datos de EJEMPLO rotulados como tales y
 * las operaciones responden «no disponible sin servidor» en vez de aparentar que guardan.
 */

enum class EstadoCuentaAdmin(val etiqueta: String) {
    ACTIVA("Activa"),
    DESACTIVADA("Desactivada"),
    SIN_TIENDA("Sin tienda"),
    PENDIENTE_ACTIVACION("Falta activar con código"),
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
    val creadoEn: String?,
    /** null = el servidor aún no informa (sin migración 11). false = emprendedora antigua sin autorización registrada. */
    val autorizada: Boolean? = null,
    val codigoVigente: Boolean = false,
    val codigoExpira: String? = null,
    val intentos: Int = 0,
    val asociaciones: List<String> = emptyList(),
    /** La cuenta con ese correo existía antes del código: podría ser de otra persona (correo autoconfirmado). */
    val cuentaAnterior: Boolean = false
) {
    val pendiente: Boolean get() = estado == EstadoCuentaAdmin.PENDIENTE_REGISTRO || estado == EstadoCuentaAdmin.PENDIENTE_ACTIVACION
}

data class CuentaAdmin(val email: String, val nombre: String, val estado: String, val esYo: Boolean)

data class EntradaBitacora(val en: String, val quien: String, val accion: String, val objetoTipo: String, val objetoId: String)

data class TipoAdmin(val id: String, val categoria: String, val nombre: String, val activo: Boolean, val orden: Int, val productos: Int)

/** Una fila del checklist de una feria (checklist_feria de 07). */
data class FilaChecklist(val tiendaId: String, val nombre: String, val invitacion: String, val asistencia: String, val origen: String, val viaAsociacion: String?)

/** Reglas puras del panel (probadas en RedisenoAdminTest y AdministracionIntegralTest). */
object ReglasAdmin {

    data class Resumen(val activas: Int, val pendientes: Int, val desactivadas: Int, val sinTienda: Int, val sinAutorizacion: Int = 0) {
        val total: Int get() = activas + pendientes + desactivadas + sinTienda
    }

    fun resumen(cuentas: List<CuentaEmprendedora>): Resumen = Resumen(
        activas = cuentas.count { it.estado == EstadoCuentaAdmin.ACTIVA },
        pendientes = cuentas.count { it.pendiente },
        desactivadas = cuentas.count { it.estado == EstadoCuentaAdmin.DESACTIVADA },
        sinTienda = cuentas.count { it.estado == EstadoCuentaAdmin.SIN_TIENDA },
        sinAutorizacion = cuentas.count { it.autorizada == false }
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

    /** Filtro de la lista de cuentas: texto en nombre, correo o tienda, y estado opcional. «Pendientes» agrupa ambas esperas. */
    fun filtrar(cuentas: List<CuentaEmprendedora>, texto: String, estado: EstadoCuentaAdmin?): List<CuentaEmprendedora> {
        val q = texto.trim().lowercase()
        return cuentas.filter { c ->
            (estado == null || c.estado == estado ||
                (estado == EstadoCuentaAdmin.PENDIENTE_REGISTRO && c.estado == EstadoCuentaAdmin.PENDIENTE_ACTIVACION)) &&
                (q.isEmpty() || c.nombre.lowercase().contains(q) || c.email.lowercase().contains(q) ||
                    (c.tiendaNombre ?: "").lowercase().contains(q))
        }
    }

    /** «Cuántas tiendas integra cada asociación», con las pertenencias públicas (tienda → asociaciones). */
    fun integrantesPorAsociacion(pertenencias: Map<String, List<String>>, tiendas: List<Tienda>): Map<String, Int> {
        val conteo = mutableMapOf<String, Int>()
        pertenencias.values.flatten().forEach { conteo[it] = (conteo[it] ?: 0) + 1 }
        tiendas.forEach { t ->
            val id = t.asociacionId
            if (!id.isNullOrBlank() && pertenencias[t.id]?.contains(id) != true) conteo[id] = (conteo[id] ?: 0) + 1
        }
        return conteo
    }

    /** Validación del formulario de feria (la misma regla de fechas del servidor y de la web). null = válido. */
    fun validarFeria(f: FormFeria, hoy: String): String? {
        val faltan = buildList {
            if (f.nombre.isBlank()) add("nombre"); if (f.lugar.isBlank()) add("lugar")
            if (f.fechaInicio.isBlank()) add("fecha"); if (f.horario.isBlank()) add("horario")
        }
        if (faltan.isNotEmpty()) return "Falta: " + faltan.joinToString(", ") + "."
        val fecha = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        if (!fecha.matches(f.fechaInicio) || (f.fechaFin.isNotBlank() && !fecha.matches(f.fechaFin))) return "Escribe las fechas como AAAA-MM-DD."
        if (f.fechaFin.isNotBlank() && f.fechaFin < f.fechaInicio) return "La fecha de término no puede ser antes del inicio."
        if (f.esNueva && f.fechaInicio < hoy) return "La fecha de inicio ya pasó."
        return null
    }

    /** Código de activación en palabras para entregarlo a la emprendedora (sin contraseñas). */
    fun mensajeCodigo(nombre: String, c: CodigoActivacion): String =
        "Hola $nombre: la administración de Alerce autorizó tu cuenta de emprendedora.\n" +
            "1) Crea tu cuenta en la app o en la web con el correo ${c.email} y una contraseña que elijas tú.\n" +
            "2) Entra y escribe este código de activación: ${c.codigo}\n" +
            "El código es personal, sirve una sola vez y vence en 14 días."
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
    /** false si el servidor todavía no tiene 11_registro_controlado (autorización sin código). */
    var registroControlado by mutableStateOf(true); private set

    /** Filtro con que se abre «Emprendedoras» al tocar un pendiente del inicio. */
    var filtroAlAbrir by mutableStateOf<EstadoCuentaAdmin?>(null)
    /** Texto que el asistente pidió buscar al abrir «Cuentas» (no cambia nada). */
    var busquedaAlAbrir: String? = null

    private const val SIN_SERVIDOR = "Versión de prueba sin servidor: esta operación no se guarda en ninguna parte."

    fun mensajeDe(e: Throwable): String = (e as? ErrorSupabase)?.message ?: e.message ?: "No se pudo completar. Revisa la conexión."

    suspend fun cargar() {
        if (!ConfigSupabase.configurado) { cargarEjemplo(); return }
        cargando = true
        try {
            val c = AdminRepositorio.emprendedoras()
            cuentas.clear(); cuentas.addAll(c)
            registroControlado = AdminRepositorio.registroControlado != false
            runCatching { AdminRepositorio.admins() }.onSuccess { admins.clear(); admins.addAll(it) }
            runCatching { AdminRepositorio.bitacora(40) }.onSuccess { bitacora.clear(); bitacora.addAll(it) }
            runCatching { AdminRepositorio.tipos() }.onSuccess { tipos.clear(); tipos.addAll(it) }
            error = null
            sonEjemplo = false
            cargadoAlgunaVez = true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cargar la administración: ${e.javaClass.simpleName}")
            error = if (e.message?.contains("Solo la administración") == true) "Esta cuenta no tiene permisos de administración en el servidor."
                else "No se pudieron traer los datos del servidor. Revisa la conexión y vuelve a intentar."
        } finally {
            cargando = false
        }
    }

    /** Ejecuta una operación real del servidor y recarga. Nunca cambia datos solo en el celular. */
    private suspend fun <T> operar(recargar: Boolean = true, bloque: suspend () -> T): Result<T> {
        if (sonEjemplo || !ConfigSupabase.configurado) return Result.failure(IllegalStateException(SIN_SERVIDOR))
        return runCatching { bloque() }.onSuccess { if (recargar) cargar() }
            .recoverCatching { throw IllegalStateException(mensajeDe(it)) }
    }

    // ---- Cuentas de emprendedoras ----
    suspend fun autorizar(email: String, nombre: String, asociacionId: String?): Result<CodigoActivacion> {
        val correo = email.trim().lowercase()
        if (nombre.isBlank()) return Result.failure(IllegalArgumentException("Escribe el nombre de la emprendedora."))
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(correo)) return Result.failure(IllegalArgumentException("Revisa el correo: debe tener la forma nombre@dominio.cl."))
        if (!registroControlado) return Result.failure(IllegalStateException(
            "El servidor todavía no tiene el registro controlado (migración 11). Hasta aplicarla, autoriza desde el panel web."))
        return operar { AdminRepositorio.autorizar(correo, nombre, asociacionId) }
    }
    suspend fun regenerarCodigo(c: CuentaEmprendedora) = operar { AdminRepositorio.regenerarCodigo(c.email) }
    suspend fun revocar(c: CuentaEmprendedora) = operar { AdminRepositorio.revocar(c.email) }
    suspend fun permitirCuentaExistente(c: CuentaEmprendedora) = operar { AdminRepositorio.permitirCuentaExistente(c.email) }
    suspend fun regularizar(c: CuentaEmprendedora) = operar { AdminRepositorio.regularizar(c.usuarioId ?: error("Sin cuenta")) }
    suspend fun editar(c: CuentaEmprendedora, nombre: String, tiendaNombre: String) =
        operar { AdminRepositorio.editarEmprendedora(c.usuarioId ?: error("Sin cuenta"), nombre, tiendaNombre) }
    suspend fun cambiarEstado(c: CuentaEmprendedora, activa: Boolean): Result<Unit> {
        val id = c.usuarioId ?: return Result.failure(IllegalStateException("Esta cuenta todavía no se registra."))
        return operar { AdminRepositorio.cambiarEstadoEmprendedora(id, activa) }
    }
    suspend fun cambiarAsociacion(tiendaId: String, asociacionId: String, integrar: Boolean) = operar {
        if (integrar) AdminRepositorio.agregarIntegrante(tiendaId, asociacionId) else AdminRepositorio.quitarIntegrante(tiendaId, asociacionId)
        VitrinaStore.cargar()
    }

    // ---- Administradores, tipos, configuración ----
    suspend fun crearAdmin(email: String, nombre: String) = operar { AdminRepositorio.crearAdmin(email, nombre) }
    suspend fun cambiarEstadoAdmin(a: CuentaAdmin, activo: Boolean) = operar { AdminRepositorio.cambiarEstadoAdmin(a.email, activo) }
    suspend fun guardarTipo(id: String?, nombre: String, categoria: String, orden: Int, activo: Boolean) =
        operar { AdminRepositorio.guardarTipo(id, nombre, categoria, orden, activo) }
    suspend fun eliminarTipo(t: TipoAdmin) = operar { AdminRepositorio.eliminarTipo(t.id) }
    suspend fun guardarNavegacion(valor: NavegacionAnonima) = operar(recargar = false) {
        AdminRepositorio.guardarNavegacionAnonima(valor.name); VitrinaStore.actualizarNavegacion()
    }

    // ---- Asociaciones y ferias ----
    suspend fun guardarAsociacion(id: String?, nombre: String, sector: String, localidad: String, descripcion: String, activa: Boolean) =
        operar(recargar = false) {
            if (nombre.isBlank()) throw IllegalArgumentException("Escribe el nombre de la asociación.")
            AdminRepositorio.guardarAsociacion(id ?: ("asoc_" + java.lang.Long.toString(System.currentTimeMillis(), 36)), nombre, sector, localidad, descripcion, activa)
            VitrinaStore.cargar()
        }
    suspend fun guardarFeria(f: FormFeria) = operar(recargar = false) {
        ReglasAdmin.validarFeria(f, ReglasFerias.fechaSantiago())?.let { throw IllegalArgumentException(it) }
        AdminRepositorio.guardarFeria(f); VitrinaStore.cargarAdmin()
    }
    suspend fun publicarFeria(id: String) = operar(recargar = false) { AdminRepositorio.publicarFeria(id); VitrinaStore.cargarAdmin() }
    suspend fun cancelarFeria(id: String, motivo: String) = operar(recargar = false) { AdminRepositorio.cancelarFeria(id, motivo).also { VitrinaStore.cargarAdmin() } }
    suspend fun invitar(feriaId: String, tiendas: List<String>, asociaciones: List<String>, directo: Boolean) =
        operar(recargar = false) { AdminRepositorio.invitar(feriaId, tiendas, asociaciones, directo) }
    suspend fun checklist(feriaId: String) = operar(recargar = false) { AdminRepositorio.checklist(feriaId) }
    suspend fun asistencia(feriaId: String, tiendaId: String, valor: String) = operar(recargar = false) { AdminRepositorio.registrarAsistencia(feriaId, tiendaId, valor) }

    fun limpiar() {
        cuentas.clear(); admins.clear(); bitacora.clear(); tipos.clear()
        error = null; sonEjemplo = false; cargadoAlgunaVez = false; registroControlado = true
    }

    private fun cargarEjemplo() {
        sonEjemplo = true
        cargadoAlgunaVez = true
        error = null
        if (cuentas.isEmpty()) {
            cuentas.addAll(SesionManager.TIENDAS_INICIALES_ALERCE.mapIndexed { i, t ->
                CuentaEmprendedora("u_ejemplo_$i", "tienda${i + 1}@ejemplo.cl", t.nombre, t.id, t.nombre,
                    if (i == 3) EstadoCuentaAdmin.DESACTIVADA else EstadoCuentaAdmin.ACTIVA, "2026-11-0${i + 1}", autorizada = i != 2)
            })
            cuentas.add(CuentaEmprendedora(null, "nueva@ejemplo.cl", "Correo autorizado de ejemplo", null, null,
                EstadoCuentaAdmin.PENDIENTE_REGISTRO, "2026-11-20", autorizada = true, codigoVigente = true, codigoExpira = "2026-12-04"))
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
