package com.example.trabajodea10.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.trabajodea10.data.remoto.ConfigSupabase
import com.example.trabajodea10.data.remoto.VitrinaRepositorio
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reglas puras de la vitrina (sin Android): se prueban en app/src/test (Entrega6Test).
 */
object ReglasVitrina {

    /** Filtro del directorio (RF-CMP-01). asociacion = "__ninguna" busca las que no tienen. */
    fun filtrarTiendas(
        tiendas: List<Tienda>,
        texto: String,
        tipo: String?,
        sector: String?,
        asociacion: String?,
        pertenencias: Map<String, List<String>> = emptyMap()
    ): List<Tienda> {
        val t = texto.trim()
        return tiendas.filter { tienda ->
            val coincideTexto = t.isEmpty() || listOf(tienda.nombre, tienda.rubro, tienda.sector, tienda.bio)
                .any { it.contains(t, ignoreCase = true) }
            val coincideTipo = tipo.isNullOrBlank() || tienda.rubro == tipo
            val coincideSector = sector.isNullOrBlank() || tienda.sector == sector
            val coincideAsoc = asociacion.isNullOrBlank() || perteneceA(tienda, asociacion, pertenencias)
            coincideTexto && coincideTipo && coincideSector && coincideAsoc
        }
    }

    const val SIN_ASOCIACION = "__ninguna"

    /** Entrega 7: una tienda puede estar en varias asociaciones; sin la tabla (servidor sin 07) se usa la única. */
    fun perteneceA(tienda: Tienda, asociacion: String, pertenencias: Map<String, List<String>>): Boolean {
        val lista = pertenencias[tienda.id]?.takeIf { it.isNotEmpty() } ?: listOfNotNull(tienda.asociacionId)
        return if (asociacion == SIN_ASOCIACION) lista.isEmpty() else asociacion in lista
    }

    /** Fecha AAAA-MM-DD de hoy (en Santiago) más [dias] (sin java.time: la app soporta Android 7). */
    fun fechaIso(dias: Int = 0): String {
        // Las reglas de calendario del proyecto usan America/Santiago (no la zona del celular).
        val zona = java.util.TimeZone.getTimeZone("America/Santiago")
        val c = Calendar.getInstance(zona)
        c.add(Calendar.DAY_OF_YEAR, dias)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zona }.format(c.time)
    }

    /** "sábado 21 de noviembre" a partir de AAAA-MM-DD. */
    fun fechaLegible(iso: String?): String {
        if (iso.isNullOrBlank()) return "Fecha por confirmar"
        return runCatching {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso.take(10))!!
            SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "CL")).format(d)
        }.getOrDefault(iso)
    }

    fun rangoFechas(f: FeriaPublica): String =
        if (!f.fechaFin.isNullOrBlank() && f.fechaFin != f.fechaInicio) "${fechaLegible(f.fechaInicio)} al ${fechaLegible(f.fechaFin)}"
        else fechaLegible(f.fechaInicio)

    /** Próximas: publicadas y que no terminaron. Ordenadas por fecha (RNF-PRI-04 sin ubicación). */
    fun feriasProximas(ferias: List<FeriaPublica>, hoyTxt: String = fechaIso()): List<FeriaPublica> {
        return ferias.filter { f ->
            f.estado !in setOf("BORRADOR", "CANCELADA", "FINALIZADA") &&
                (f.fechaFin.isNullOrBlank() || f.fechaFin >= hoyTxt)
        }.sortedWith(compareBy(nullsLast<String>()) { it.fechaInicio })
    }

    /** Distancia en metros (fórmula del haversine). */
    fun distanciaMetros(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Con ubicación: de la más cercana a la más lejana (las sin punto al final, por fecha).
     * Sin ubicación: por fecha (RF-MAP-03).
     */
    fun ordenarFerias(ferias: List<FeriaPublica>, lat: Double?, lng: Double?): List<FeriaPublica> {
        if (lat == null || lng == null) return ferias.sortedWith(compareBy(nullsLast<String>()) { it.fechaInicio })
        return ferias.sortedWith(
            compareBy<FeriaPublica> { if (it.tieneUbicacion) 0 else 1 }
                .thenBy { if (it.tieneUbicacion) distanciaMetros(lat, lng, it.lat!!, it.lng!!) else 0.0 }
                .thenBy(nullsLast<String>()) { it.fechaInicio }
        )
    }

    fun textoDistancia(metros: Double): String =
        if (metros < 1000) "${metros.toInt()} m" else String.format(java.util.Locale("es", "CL"), "%.1f km", metros / 1000)

    /** "Consultar precio" cuando la emprendedora ocultó sus precios (RF-CFG-01). */
    fun textoPrecio(tienda: Tienda?, precio: Double): String =
        if (tienda != null && !tienda.mostrarPrecios) "Consultar precio" else formatearMoneda(precio)

    /** Enlace "Cómo llegar" que abre la aplicación de mapas en ese punto. */
    fun uriComoLlegar(lat: Double, lng: Double, nombre: String): String =
        "geo:$lat,$lng?q=$lat,$lng(" + java.net.URLEncoder.encode(nombre, "UTF-8").replace("+", "%20") + ")"
}

/**
 * Estado de la vitrina pública en la app: asociaciones, ferias y la navegación
 * anónima. Se carga del servidor; sin configuración se usan datos de ejemplo.
 */
object VitrinaStore {

    private const val TAG = "VitrinaStore"
    private const val PREFS = "vitrina_e6"
    private const val CLAVE_NAV = "navegacion_anonima"

    private var prefs: SharedPreferences? = null

    val asociaciones = mutableStateListOf<AsociacionPublica>()
    /** tiendaId → asociaciones (Entrega 7). Vacío si el servidor aún no tiene la tabla. */
    var pertenencias by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    val ferias = mutableStateListOf<FeriaPublica>()
    var cargando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Última opción conocida (se usa si al abrir no hay conexión). */
    var navegacion by mutableStateOf(NavegacionAnonima.PERMITIR)
        private set

    fun inicializar(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        navegacion = NavegacionAnonima.desde(prefs?.getString(CLAVE_NAV, null))
    }

    /** Lee del servidor la navegación anónima. Si no responde a tiempo, se queda con la última conocida. */
    suspend fun actualizarNavegacion(): NavegacionAnonima {
        if (!ConfigSupabase.configurado) return navegacion
        val leida = withTimeoutOrNull(6_000) { runCatching { VitrinaRepositorio.navegacionAnonima() }.getOrNull() }
        if (leida != null) {
            navegacion = leida
            prefs?.edit()?.putString(CLAVE_NAV, leida.name)?.apply()
        }
        return navegacion
    }

    suspend fun cargar() {
        if (!ConfigSupabase.configurado) {
            if (asociaciones.isEmpty()) asociaciones.addAll(ASOCIACIONES_EJEMPLO)
            if (ferias.isEmpty()) ferias.addAll(feriasEjemplo())
            return
        }
        cargando = true
        try {
            val a = VitrinaRepositorio.asociaciones()
            val f = VitrinaRepositorio.ferias()
            asociaciones.clear(); asociaciones.addAll(a)
            ferias.clear(); ferias.addAll(f)
            pertenencias = runCatching { com.example.trabajodea10.data.remoto.FeriasRepositorio.pertenencias() }.getOrDefault(emptyMap())
            error = null
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cargar la vitrina: ${e.message}")
            error = e.message ?: "Sin conexión"
        } finally {
            cargando = false
        }
    }

    fun asociacion(id: String?): AsociacionPublica? = id?.let { i -> asociaciones.firstOrNull { it.id == i } }
    fun asociacionesDe(t: Tienda): List<AsociacionPublica> =
        (pertenencias[t.id]?.takeIf { it.isNotEmpty() } ?: listOfNotNull(t.asociacionId)).mapNotNull { asociacion(it) }
    fun perteneceA(t: Tienda, asociacionId: String): Boolean = ReglasVitrina.perteneceA(t, asociacionId, pertenencias)
    fun feria(id: String): FeriaPublica? = ferias.firstOrNull { it.id == id }

    private val ASOCIACIONES_EJEMPLO = listOf(
        AsociacionPublica("as1", "Manos del Sur", "Alerce", "Puerto Montt", "Red de artesanas y productoras de Alerce que venden juntas en ferias comunales."),
        AsociacionPublica("as2", "Red del Lago", "Alerce Sur", "Puerto Montt", "Agrupación de emprendedoras gastronómicas de la cuenca del lago Llanquihue.")
    )

    private fun feriasEjemplo(): List<FeriaPublica> {
        val d = ReglasVitrina::fechaIso
        return listOf(
            FeriaPublica("feria_alerce_plaza", "Feria de Emprendedoras de Alerce", "Tejidos, panadería artesanal y productos de campo.",
                null, "Plaza de Alerce, Puerto Montt", d(4), d(4), "10:00 a 18:00",
                "Municipalidad de Puerto Montt", "PROXIMA", -41.3959, -72.8955),
            FeriaPublica("feria_costanera_pm", "Mercado Costanera Puerto Montt", "Artesanía, diseño local y gastronomía sureña.",
                null, "Costanera de Puerto Montt", d(11), d(12), "11:00 a 19:00",
                "Red Trama", "PROXIMA", -41.4717, -72.9396)
        )
    }
}
