package com.example.trabajodea10.data

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
 * Entrega 7 — modelos y reglas puras (sin Android) de ferias, seguidores y avisos.
 * Mismo contrato que supabase/07_ferias_seguidores.sql y la web (js/catalogo.js).
 * Se prueban en app/src/test (Entrega7Test).
 */

/** Estado de la invitación en el servidor. */
enum class EstadoInvitacion7(val etiqueta: String) {
    PENDIENTE("Invitada"),
    CONFIRMADA("Confirmada"),
    RECHAZADA("Rechazada"),
    AGREGADA("Agregada por administración");

    val participa: Boolean get() = this == CONFIRMADA || this == AGREGADA

    companion object {
        fun desde(v: String?): EstadoInvitacion7 = entries.firstOrNull { it.name == v } ?: PENDIENTE
    }
}

/** Una feria a la que mi tienda está invitada o en la que participa (rpc mis_ferias). */
@Serializable
data class MiFeria(
    val feriaId: String,
    val nombre: String,
    val ubicacion: String = "",
    val fechaInicio: String? = null,     // AAAA-MM-DD
    val fechaFin: String? = null,
    val horario: String = "",
    val estado: String = "PROXIMA",
    val lat: Double? = null,
    val lng: Double? = null,
    val invitacion: String = "PENDIENTE",
    val asistencia: String = "PENDIENTE",
    /** Respuesta tocada sin conexión: aún no la confirma el servidor. */
    val respuestaPendiente: Boolean? = null
) {
    val estadoInvitacion: EstadoInvitacion7 get() = EstadoInvitacion7.desde(invitacion)
}

/** Participante confirmada que se muestra en la ficha pública (rpc participantes_feria). */
data class ParticipantePublico(val tiendaId: String, val nombre: String, val rubro: String, val sector: String)

/** Aviso de la bandeja de la cuenta (tabla avisos). */
data class AvisoCuenta(
    val id: String,
    val tipo: String,
    val titulo: String,
    val cuerpo: String,
    val feriaId: String?,
    val estado: String,
    val creadoEn: String,
    val recibido: Boolean,
    val leido: Boolean
)

/** Ubicación aproximada del celular en el momento de vender. Nunca se guarda ni se envía. */
data class UbicacionMomento(val lat: Double, val lng: Double, val precisionMetros: Float, val edadMs: Long)

enum class MotivoSugerencia { UBICACION, FECHA }
data class SugerenciaFeria(val feria: MiFeria, val motivo: MotivoSugerencia, val distanciaMetros: Double? = null)

/** Acción que una visitante intentó antes de iniciar sesión (RF-ACC-08). */
@Serializable
data class AccionPendiente(val accion: String, val tipo: String, val id: String, val nombre: String, val creada: Long)

object ReglasFerias {

    const val ZONA = "America/Santiago"
    const val VIGENCIA_PENDIENTE_MS = 15 * 60 * 1000L
    /** Cercanía: dentro de 500 m del punto de la feria, más la imprecisión del GPS (máx. 1 km). */
    const val RADIO_BASE_M = 500.0
    const val PRECISION_MAXIMA_M = 1000f
    /** Una ubicación de hace más de 15 minutos no sirve para decir dónde está ahora. */
    const val EDAD_MAXIMA_MS = 15 * 60 * 1000L

    /** Fecha AAAA-MM-DD en America/Santiago (sin java.time: la app soporta Android 7). */
    fun fechaSantiago(instanteMs: Long = System.currentTimeMillis()): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        f.timeZone = TimeZone.getTimeZone(ZONA)
        return f.format(java.util.Date(instanteMs))
    }

    /** Hora HH:mm en America/Santiago. */
    fun horaSantiago(instanteMs: Long = System.currentTimeMillis()): String {
        val f = SimpleDateFormat("HH:mm", Locale.US)
        f.timeZone = TimeZone.getTimeZone(ZONA)
        return f.format(java.util.Date(instanteMs))
    }

    /** Publicada (PROXIMA/EN_CURSO) y su último día es hoy o después. */
    fun feriaActiva(estado: String, inicio: String?, fin: String?, hoy: String): Boolean {
        if (estado != "PROXIMA" && estado != "EN_CURSO") return false
        val ultimo = (fin ?: inicio ?: hoy).take(10)
        return ultimo >= hoy
    }

    /** Hoy está entre el primer y el último día de la feria (ferias de varios días incluidas). */
    fun ocurreHoy(f: MiFeria, hoy: String): Boolean {
        val ini = f.fechaInicio?.take(10) ?: return false
        val fin = (f.fechaFin ?: f.fechaInicio).take(10)
        return hoy in ini..fin
    }

    /** "10:00 a 18:00", "10 a 18 hrs" → minutos desde medianoche (inicio, fin). Null si no se entiende. */
    fun rangoHorario(horario: String): Pair<Int, Int>? {
        val numeros = Regex("(\\d{1,2})(?::(\\d{2}))?").findAll(horario).mapNotNull { m ->
            m.groupValues[1].toIntOrNull()?.let { it * 60 + (m.groupValues[2].toIntOrNull() ?: 0) }
        }.toList()
        if (numeros.size < 2) return null
        val (a, b) = numeros[0] to numeros[1]
        return if (a in 0..1440 && b in 0..1440 && a < b) a to b else null
    }

    /** ¿Está ocurriendo ahora? Si el horario no se entiende, basta con que sea el día. */
    fun enCurso(f: MiFeria, hoy: String, horaHHmm: String): Boolean {
        if (!ocurreHoy(f, hoy)) return false
        val r = rangoHorario(f.horario) ?: return true
        val p = horaHHmm.split(":")
        val ahora = (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
        return ahora in (r.first - 60)..(r.second + 60)   // una hora de margen para armar y desarmar
    }

    /**
     * Sugerencia de lugar al vender (RF-VEN-05, RF-MAP-05). Solo ferias donde mi tienda
     * participa (confirmada o agregada), publicadas y de hoy. Con una ubicación reciente y
     * precisa, la feria en curso más cercana dentro del radio; si no, la de hoy por fecha.
     * Nunca sugiere una feria en la que no está confirmada.
     */
    fun sugerirFeria(
        misFerias: List<MiFeria>,
        hoy: String,
        horaHHmm: String,
        ubicacion: UbicacionMomento?
    ): SugerenciaFeria? {
        val deHoy = misFerias.filter {
            it.estadoInvitacion.participa && it.respuestaPendiente != false &&
                feriaActiva(it.estado, it.fechaInicio, it.fechaFin, hoy) && ocurreHoy(it, hoy)
        }
        if (deHoy.isEmpty()) return null
        if (ubicacion != null && ubicacion.precisionMetros <= PRECISION_MAXIMA_M && ubicacion.edadMs in 0..EDAD_MAXIMA_MS) {
            val cerca = deHoy.filter { it.lat != null && it.lng != null && enCurso(it, hoy, horaHHmm) }
                .map { it to ReglasVitrina.distanciaMetros(ubicacion.lat, ubicacion.lng, it.lat!!, it.lng!!) }
                .filter { (_, d) -> d <= RADIO_BASE_M + ubicacion.precisionMetros }
                .minByOrNull { it.second }
            if (cerca != null) return SugerenciaFeria(cerca.first, MotivoSugerencia.UBICACION, cerca.second)
        }
        val porFecha = deHoy.sortedWith(compareBy({ !enCurso(it, hoy, horaHHmm) }, { it.fechaInicio })).first()
        return SugerenciaFeria(porFecha, MotivoSugerencia.FECHA)
    }

    /** Calendario de mis ferias (RF-FER-09): invitadas o que participo, sin rechazadas ni pasadas, por mes. */
    fun calendario(misFerias: List<MiFeria>, hoy: String): List<Pair<String, List<MiFeria>>> {
        val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
        return misFerias
            .filter { it.estadoInvitacion != EstadoInvitacion7.RECHAZADA && it.estado != "CANCELADA" && (it.fechaFin ?: it.fechaInicio ?: hoy).take(10) >= hoy }
            .sortedWith(compareBy(nullsLast<String>()) { it.fechaInicio })
            .groupBy { it.fechaInicio?.take(7) ?: "sin-fecha" }
            .map { (mes, lista) ->
                val p = mes.split("-")
                val etiqueta = if (p.size == 2) "${meses[(p[1].toIntOrNull() ?: 1) - 1]} de ${p[0]}" else "Fecha por confirmar"
                etiqueta to lista
            }
    }

    fun accionPendienteVigente(p: AccionPendiente?, ahoraMs: Long): Boolean {
        if (p == null || p.accion != "seguir" || p.tipo !in setOf("TIENDA", "ASOCIACION") || p.id.isBlank()) return false
        val edad = ahoraMs - p.creada
        return edad in 0..VIGENCIA_PENDIENTE_MS
    }

    /** Horario de avisos de contacto: igual que dentro_de_horario() del servidor. */
    fun dentroDeHorario(hora: String, desde: String, hasta: String): Boolean {
        fun m(t: String): Int { val p = t.split(":"); return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0) }
        val (h, d, a) = Triple(m(hora), m(desde), m(hasta))
        return when {
            d == a -> true
            d < a -> h >= d && h < a
            else -> h >= d || h < a
        }
    }
}
