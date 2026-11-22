package com.example.trabajodea10.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.trabajodea10.avisos.AvisosCelular
import com.example.trabajodea10.data.remoto.ClienteSupabase
import com.example.trabajodea10.data.remoto.ConfigSupabase
import com.example.trabajodea10.data.remoto.ErrorSupabase
import com.example.trabajodea10.data.remoto.FeriasRepositorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Estado de la Entrega 7 en la app: mis ferias (con copia en el celular para usarla
 * sin señal), seguimientos, consentimiento, bandeja de avisos y la acción que una
 * visitante intentó antes de iniciar sesión. El servidor es la fuente de verdad.
 */
object FeriasStore {

    private const val TAG = "FeriasStore"
    private const val PREFS = "ferias_e7"
    private const val CLAVE_PENDIENTE = "accion_pendiente"
    private const val CLAVE_TOKEN = "token_fcm"

    private var prefs: SharedPreferences? = null
    private var contexto: Context? = null
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val misFerias = mutableStateListOf<MiFeria>()
    val avisos = mutableStateListOf<AvisoCuenta>()
    var seguidos by mutableStateOf<Set<String>>(emptySet())
        private set
    var consentimiento by mutableStateOf<Boolean?>(null)
        private set
    var cargandoFerias by mutableStateOf(false)
        private set
    var errorFerias by mutableStateOf<String?>(null)
        private set
    /** Acción pendiente que se acaba de completar al iniciar sesión (la pantalla la abre). */
    var accionRecienCompletada by mutableStateOf<AccionPendiente?>(null)
    /** Tras seguir al volver del ingreso, la ficha pregunta por los avisos (RNF-PRI-02). */
    var ofrecerConsentimientoTrasIngreso by mutableStateOf(false)
    /** Feria del aviso que se tocó (la pantalla la abre). */
    var feriaDesdeAviso by mutableStateOf<String?>(null)

    private var uid: String? = null

    fun inicializar(context: Context) {
        if (prefs != null) return
        contexto = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        AvisosCelular.crearCanal(context)
        AvisosCelular.iniciarFirebase(context)
    }

    private fun claveFerias(u: String) = "misferias_$u"
    private fun claveRespuestas(u: String) = "respuestas_pendientes_$u"

    // ================================================================ Sesión

    /** Al iniciar sesión (después de prepararSesion). Carga lo de esta cuenta y completa la acción pendiente. */
    fun alIniciarSesion(rol: Rol) {
        val nuevo = ClienteSupabase.sesion?.userId ?: return
        if (uid != nuevo) {
            uid = nuevo
            misFerias.clear(); avisos.clear(); seguidos = emptySet(); consentimiento = null
            leerFeriasGuardadas(nuevo)
        }
        val ctx = contexto ?: return
        ambito.launch {
            runCatching { seguidos = FeriasRepositorio.misSeguimientos() }
            runCatching { consentimiento = FeriasRepositorio.miConsentimiento() }
            if (rol == Rol.EMPRENDEDORA) cargarMisFerias()
            completarAccionPendiente()
            AvisosCelular.actualizarRespaldo(ctx)
            AvisosCelular.obtenerToken { token -> alRenovarToken(token) }
        }
    }

    /** Antes de cerrar la sesión en el servidor: este celular deja de recibir los avisos de la cuenta. */
    suspend fun alCerrarSesion() {
        val token = prefs?.getString(CLAVE_TOKEN, null)
        if (token != null && ClienteSupabase.sesion != null) runCatching { FeriasRepositorio.quitarDispositivo(token) }
        prefs?.edit()?.remove(CLAVE_TOKEN)?.remove(CLAVE_PENDIENTE)?.apply()
        AvisosCelular.borrarToken()
        contexto?.let { AvisosCelular.cancelar(it) }
        uid = null
        misFerias.clear(); avisos.clear(); seguidos = emptySet(); consentimiento = null
        accionRecienCompletada = null
    }

    fun alRenovarToken(token: String) {
        prefs?.edit()?.putString(CLAVE_TOKEN, token)?.apply()
        if (ClienteSupabase.sesion == null || !ConfigSupabase.configurado) return
        ambito.launch { runCatching { FeriasRepositorio.registrarDispositivo(token) }.onFailure { Log.w(TAG, "Token sin registrar: ${it.message}") } }
    }

    /** Tras conceder el permiso de notificaciones (o quitarlo), se ajusta el respaldo periódico. */
    fun permisoCambiado() { contexto?.let { if (ClienteSupabase.sesion != null) AvisosCelular.actualizarRespaldo(it) } }

    /**
     * Destino de ESTE celular: su token FCM si existe; si no, un identificador de instalación.
     * La web y el celular confirman cada uno lo suyo, sin pisarse.
     */
    fun destino(): String {
        prefs?.getString(CLAVE_TOKEN, null)?.let { return it }
        val p = prefs ?: return "android:sin-preferencias"
        return p.getString("destino_instalacion", null) ?: ("android:" + java.util.UUID.randomUUID()).also {
            p.edit().putString("destino_instalacion", it).apply()
        }
    }

    /** Este celular mostró el aviso (MOSTRADO) o la persona lo tocó (ABIERTO). Un evento por destino. */
    fun confirmarAviso(avisoId: String, evento: String) {
        if (ClienteSupabase.sesion == null) return
        val d = destino()
        ambito.launch { runCatching { FeriasRepositorio.confirmarAviso(avisoId, d, evento) } }
    }

    // ================================================================ Mis ferias (emprendedora)

    private fun leerFeriasGuardadas(u: String) {
        val texto = prefs?.getString(claveFerias(u), null) ?: return
        runCatching { ClienteSupabase.json.decodeFromString(ListSerializer(MiFeria.serializer()), texto) }
            .onSuccess { misFerias.clear(); misFerias.addAll(aplicarPendientes(it, u)) }
    }

    private fun guardarFerias() {
        val u = uid ?: return
        prefs?.edit()?.putString(claveFerias(u), ClienteSupabase.json.encodeToString(ListSerializer(MiFeria.serializer()), misFerias.toList()))?.apply()
    }

    private fun respuestasPendientes(u: String): Map<String, Boolean> =
        prefs?.getString(claveRespuestas(u), null)?.let {
            runCatching { ClienteSupabase.json.decodeFromString(MapSerializer(String.serializer(), Boolean.serializer()), it) }.getOrNull()
        } ?: emptyMap()

    private fun guardarRespuestas(u: String, m: Map<String, Boolean>) {
        prefs?.edit()?.putString(claveRespuestas(u), ClienteSupabase.json.encodeToString(MapSerializer(String.serializer(), Boolean.serializer()), m))?.apply()
    }

    private fun aplicarPendientes(lista: List<MiFeria>, u: String): List<MiFeria> {
        val p = respuestasPendientes(u)
        return lista.map { f -> p[f.feriaId]?.let { f.copy(respuestaPendiente = it) } ?: f.copy(respuestaPendiente = null) }
    }

    suspend fun cargarMisFerias() {
        val u = uid ?: return
        cargandoFerias = true
        try {
            reintentarRespuestas()
            val lista = FeriasRepositorio.misFerias()
            misFerias.clear(); misFerias.addAll(aplicarPendientes(lista, u))
            guardarFerias()
            errorFerias = null
        } catch (e: Exception) {
            errorFerias = if (e is ErrorSupabase) e.message else "Sin conexión: se muestran las ferias guardadas en el celular."
        } finally {
            cargandoFerias = false
        }
    }

    enum class ResultadoRespuesta { CONFIRMADA_POR_SERVIDOR, PENDIENTE_DE_CONEXION, RECHAZADA }

    /** «Participo» / «No participo». Sin señal queda pendiente en el celular y se reintenta. */
    suspend fun responder(feriaId: String, participo: Boolean): Pair<ResultadoRespuesta, String?> {
        val u = uid ?: return ResultadoRespuesta.RECHAZADA to "Inicia sesión para responder."
        return try {
            val estado = FeriasRepositorio.responder(feriaId, participo)
            guardarRespuestas(u, respuestasPendientes(u) - feriaId)
            reemplazar(feriaId) { it.copy(invitacion = estado.ifBlank { if (participo) "CONFIRMADA" else "RECHAZADA" }, respuestaPendiente = null) }
            ResultadoRespuesta.CONFIRMADA_POR_SERVIDOR to null
        } catch (e: ErrorSupabase) {
            if (e.esRechazoDeRegla) {
                ResultadoRespuesta.RECHAZADA to e.message
            } else {
                guardarRespuestas(u, respuestasPendientes(u) + (feriaId to participo))
                reemplazar(feriaId) { it.copy(respuestaPendiente = participo) }
                ResultadoRespuesta.PENDIENTE_DE_CONEXION to null
            }
        } catch (e: IOException) {
            guardarRespuestas(u, respuestasPendientes(u) + (feriaId to participo))
            reemplazar(feriaId) { it.copy(respuestaPendiente = participo) }
            ResultadoRespuesta.PENDIENTE_DE_CONEXION to null
        }
    }

    private suspend fun reintentarRespuestas() {
        val u = uid ?: return
        val pendientes = respuestasPendientes(u)
        if (pendientes.isEmpty()) return
        val quedan = pendientes.toMutableMap()
        for ((feria, participo) in pendientes) {
            try {
                FeriasRepositorio.responder(feria, participo)
                quedan.remove(feria)
            } catch (e: ErrorSupabase) {
                if (e.esRechazoDeRegla) quedan.remove(feria)   // ya no aplica (feria cancelada, etc.)
                else break
            } catch (e: IOException) { break }
        }
        guardarRespuestas(u, quedan)
    }

    private fun reemplazar(feriaId: String, cambio: (MiFeria) -> MiFeria) {
        val i = misFerias.indexOfFirst { it.feriaId == feriaId }
        if (i >= 0) misFerias[i] = cambio(misFerias[i])
        guardarFerias()
    }

    /** Sugerencia de lugar al vender (por fecha y, si hay ubicación reciente, por cercanía). */
    fun sugerencia(ubicacion: UbicacionMomento?): SugerenciaFeria? =
        ReglasFerias.sugerirFeria(misFerias.toList(), ReglasFerias.fechaSantiago(), ReglasFerias.horaSantiago(), ubicacion)

    // ================================================================ Seguir (comprador)

    fun siguiendo(tipo: String, id: String): Boolean = seguidos.contains("$tipo:$id")

    suspend fun seguir(tipo: String, id: String): String? = try {
        FeriasRepositorio.seguir(tipo, id)
        seguidos = seguidos + "$tipo:$id"
        null
    } catch (e: Exception) { e.message ?: "No se pudo seguir." }

    suspend fun dejarDeSeguir(tipo: String, id: String): String? = try {
        FeriasRepositorio.dejarDeSeguir(tipo, id)
        seguidos = seguidos - "$tipo:$id"
        null
    } catch (e: Exception) { e.message ?: "No se pudo dejar de seguir." }

    suspend fun guardarConsentimiento(si: Boolean): String? = try {
        FeriasRepositorio.guardarConsentimiento(si)
        consentimiento = si
        if (si) AvisosCelular.obtenerToken { alRenovarToken(it) }
        null
    } catch (e: Exception) { e.message ?: "No se guardó tu preferencia." }

    suspend fun cargarAvisos() {
        runCatching { FeriasRepositorio.misAvisos(30) }.onSuccess { avisos.clear(); avisos.addAll(it) }
    }

    // ================================================================ Acción pendiente (RF-ACC-08)

    fun guardarPendiente(tipo: String, id: String, nombre: String) {
        val p = AccionPendiente("seguir", tipo, id, nombre, System.currentTimeMillis())
        prefs?.edit()?.putString(CLAVE_PENDIENTE, ClienteSupabase.json.encodeToString(AccionPendiente.serializer(), p))?.apply()
    }

    fun hayPendiente(): Boolean = prefs?.contains(CLAVE_PENDIENTE) == true

    /** Acción por completar (o recién completada) para volver a su ficha al iniciar sesión. */
    fun fichaPendiente(): AccionPendiente? = accionRecienCompletada ?: prefs?.getString(CLAVE_PENDIENTE, null)?.let {
        runCatching { ClienteSupabase.json.decodeFromString(AccionPendiente.serializer(), it) }.getOrNull()
    }?.takeIf { ReglasFerias.accionPendienteVigente(it, System.currentTimeMillis()) || it === accionRecienCompletada }

    /** Se cancela si la persona vuelve a la vitrina sin iniciar sesión. */
    fun descartarPendiente() { prefs?.edit()?.remove(CLAVE_PENDIENTE)?.apply() }

    /** Se ejecuta una sola vez: se borra antes de llamar al servidor. */
    private suspend fun completarAccionPendiente() {
        val texto = prefs?.getString(CLAVE_PENDIENTE, null) ?: return
        descartarPendiente()
        val p = runCatching { ClienteSupabase.json.decodeFromString(AccionPendiente.serializer(), texto) }.getOrNull() ?: return
        if (!ReglasFerias.accionPendienteVigente(p, System.currentTimeMillis())) return
        if (seguir(p.tipo, p.id) == null) {
            accionRecienCompletada = p
            if (consentimiento != true) ofrecerConsentimientoTrasIngreso = true
        }
    }

    /** "2026-11-19T15:04:05.123+00:00" → milisegundos (0 si no se entiende). */
    fun instanteIso(iso: String): Long = runCatching {
        val limpio = iso.replace(Regex("\\.\\d+"), "").replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2").replace("Z", "+0000")
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(limpio)!!.time
    }.getOrDefault(0L)
}
