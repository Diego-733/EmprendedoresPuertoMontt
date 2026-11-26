package com.example.trabajodea10.voz

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import java.util.Locale

/**
 * Lectura en voz alta con la voz del propio celular (TextToSpeech de Android): sin internet
 * obligatorio, sin costo y sin enviar textos a servicios de la app.
 *
 * - Prefiere español de Chile; si no está, otra voz en español; si no hay ninguna, avisa y la app
 *   sigue igual (solo texto).
 * - Nunca habla sola si TalkBack está activo (para no hablar encima del lector de pantalla); la
 *   persona igual puede tocar «Escuchar».
 * - Se detiene al salir de la app y antes de abrir el micrófono (el dictado no escucha a la app).
 */
// Solo guarda el contexto de la APLICACIÓN (nunca una pantalla), que vive lo mismo que la app.
@SuppressLint("StaticFieldLeak")
object LectorVoz : TextToSpeech.OnInitListener {

    private const val TAG = "LectorVoz"
    private const val PREFS = "voz_preferencias"

    enum class Velocidad(val etiqueta: String, val factor: Float) { LENTA("Lenta", 0.8f), NORMAL("Normal", 1.0f), RAPIDA("Rápida", 1.25f) }

    /** null = aún no se intenta; true/false = hay o no hay voz en español. */
    var disponible by mutableStateOf<Boolean?>(null)
        private set
    /** Id del texto que se está leyendo (para mostrar «Detener» en el botón correcto). */
    var leyendo by mutableStateOf<String?>(null)
        private set
    var lecturaAutomatica by mutableStateOf(false)
        private set
    var velocidad by mutableStateOf(Velocidad.NORMAL)
        private set

    private var contexto: Context? = null
    private var tts: TextToSpeech? = null
    private var listo = false
    private var pendiente: Pair<String, String>? = null
    /**
     * Cada lectura lleva un número propio: el aviso tardío de una lectura cortada (mismo botón)
     * no debe marcar como terminada la que está sonando.
     */
    private var turno = 0
    private var lecturaActual: String? = null
    /** Los avisos del motor llegan en otro hilo; el estado de la pantalla se cambia en el principal. */
    private val principal by lazy { Handler(Looper.getMainLooper()) }

    /** Lee las preferencias (no crea el motor de voz: eso pasa recién al primer uso). */
    fun prepararPreferencias(context: Context) {
        contexto = context.applicationContext
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lecturaAutomatica = p.getBoolean("auto", false)
        velocidad = runCatching { Velocidad.valueOf(p.getString("velocidad", Velocidad.NORMAL.name)!!) }.getOrDefault(Velocidad.NORMAL)
    }

    fun cambiarLecturaAutomatica(activa: Boolean) {
        lecturaAutomatica = activa
        contexto?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit { putBoolean("auto", activa) }
        if (!activa) detener()
    }

    fun cambiarVelocidad(v: Velocidad) {
        velocidad = v
        tts?.setSpeechRate(v.factor)
        contexto?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit { putString("velocidad", v.name) }
    }

    /** true si hay un lector de pantalla (TalkBack) explorando la pantalla. */
    fun lectorDePantallaActivo(context: Context): Boolean =
        (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

    /** ¿Leer sin que lo pidan? Solo con la lectura automática encendida y sin TalkBack. */
    fun debeLeerSolo(context: Context): Boolean = lecturaAutomatica && !lectorDePantallaActivo(context)

    private fun asegurarMotor() {
        if (tts != null) return
        val c = contexto ?: return
        tts = runCatching { TextToSpeech(c, this) }.onFailure { Log.w(TAG, "Sin motor de voz: ${it.message}") }.getOrNull()
        if (tts == null) disponible = false
    }

    override fun onInit(status: Int) {
        val motor = tts
        if (status != TextToSpeech.SUCCESS || motor == null) { disponible = false; pendiente = null; return }
        val idioma = ReglasVoz.elegirIdioma { loc ->
            runCatching { motor.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)
        }
        if (idioma == null) { disponible = false; pendiente = null; return }
        motor.language = idioma
        motor.setSpeechRate(velocidad.factor)
        motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) = termino(id)
            @Deprecated("Requerido por versiones antiguas de Android")
            override fun onError(id: String?) = termino(id)
            override fun onStop(id: String?, interrupted: Boolean) = termino(id)
        })
        listo = true
        disponible = true
        pendiente?.let { (id, texto) -> pendiente = null; hablarAhora(id, texto) }
    }

    /** Lee [texto] (corta lo que se estuviera leyendo). [id] identifica el botón que lo pidió. */
    fun hablar(id: String, texto: String) {
        if (texto.isBlank() || disponible == false) return
        asegurarMotor()
        if (!listo) { pendiente = id to texto; return }
        hablarAhora(id, texto)
    }

    private fun hablarAhora(id: String, texto: String) {
        val lectura = "$id#${++turno}"
        lecturaActual = lectura
        leyendo = id
        val r = runCatching { tts?.speak(ReglasVoz.paraLeer(texto), TextToSpeech.QUEUE_FLUSH, null, lectura) }.getOrNull()
        if (r != TextToSpeech.SUCCESS) termino(lectura)
    }

    /** Terminó (o se cortó) una lectura: solo cuenta si es la que está sonando ahora. */
    private fun termino(lectura: String?) {
        principal.post { if (lectura != null && lectura == lecturaActual) { lecturaActual = null; leyendo = null } }
    }

    /** Detiene la lectura (al salir de la pantalla, de la app o antes de usar el micrófono). */
    fun detener() {
        pendiente = null
        lecturaActual = null
        leyendo = null
        runCatching { tts?.stop() }
    }

    /**
     * Si antes no hubo voz en español (o el motor falló), se vuelve a intentar: la persona puede
     * haber instalado una voz después. Se llama al abrir Ajustes.
     */
    fun reintentarSiNoHabia() {
        if (disponible != false) return
        runCatching { tts?.shutdown() }
        tts = null; listo = false; pendiente = null
        disponible = null
    }
}

/** Reglas de la voz que no dependen de Android (se prueban en el computador). */
object ReglasVoz {

    /** Orden de preferencia: Chile, Latinoamérica, Estados Unidos (español), México, España, español a secas. */
    val PREFERENCIA: List<Locale> = listOf(
        Locale("es", "CL"), Locale.forLanguageTag("es-419"), Locale("es", "US"), Locale("es", "MX"), Locale("es", "ES"), Locale("es")
    )

    fun elegirIdioma(disponible: (Locale) -> Boolean): Locale? = PREFERENCIA.firstOrNull(disponible)

    /**
     * Texto preparado para la voz: «$12.000» se lee «12.000 pesos» (si no, algunas voces dicen
     * «dólares»), sin comillas angulares ni íconos que se leerían como símbolos.
     */
    fun paraLeer(texto: String): String = texto
        .replace(Regex("\\$\\s?(\\d{1,3}(?:\\.\\d{3})+|\\d+)")) { it.groupValues[1] + " pesos" }
        .replace(Regex("[«»\"]"), "")
        .replace(Regex("[\\p{So}\\p{Cn}\\uFE0F]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
