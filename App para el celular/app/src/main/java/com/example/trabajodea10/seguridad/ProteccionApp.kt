package com.example.trabajodea10.seguridad

import android.content.Context
import android.util.Log
import com.example.trabajodea10.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Entrega 8 (RNF-SEG-06, criterio A11): Firebase App Check.
 *
 * El backend de datos es Supabase, que no verifica App Check. Por eso la protección se
 * demuestra sobre el servicio de Firebase que sí la aplica: Realtime Database, con la
 * ruta `comprobaciones_app_check`. Con «Enforce» activado en la consola, Firebase acepta
 * la escritura que hace esta app (con token de App Check) y rechaza la misma escritura
 * hecha desde otro cliente (por ejemplo, curl desde un computador).
 *
 * Proveedor según el tipo de APK (FabricaAppCheck en src/debug y src/release):
 *  - APK público (release): Play Integrity, sin tokens de depuración.
 *  - APK de prueba del equipo (debug): proveedor de depuración con token registrado en la consola.
 */
object ProteccionApp {
    private const val TAG = "ProteccionApp"
    const val RUTA = "comprobaciones_app_check"

    val configurada: Boolean get() = BuildConfig.FIREBASE_DATABASE_URL.isNotBlank()
    val usaDepuracion: Boolean get() = FabricaAppCheck.esDepuracion
    val nombreProveedor: String get() = FabricaAppCheck.nombre

    /** Se llama justo después de iniciar Firebase. */
    fun instalar() {
        if (!configurada) return
        runCatching {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(FabricaAppCheck.crear())
            FirebaseAppCheck.getInstance().setTokenAutoRefreshEnabled(true)
        }.onFailure { Log.w(TAG, "App Check no se pudo instalar: ${it.message}") }
    }

    sealed class Resultado {
        data object SinConfigurar : Resultado()
        data object Aceptada : Resultado()
        data class Rechazada(val motivo: String) : Resultado()
        data object SinRespuesta : Resultado()
    }

    /**
     * Escribe una comprobación sin datos personales ({plataforma, version, en}). Si App Check
     * está exigido y el token no es válido, Firebase la rechaza («Permission denied»).
     */
    suspend fun comprobar(): Resultado {
        if (!configurada || FirebaseApp.getApps(appContexto ?: return Resultado.SinConfigurar).isEmpty()) return Resultado.SinConfigurar
        val ref = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference(RUTA).push()
        val datos = mapOf("plataforma" to "android", "version" to BuildConfig.VERSION_NAME, "en" to ServerValue.TIMESTAMP)
        return withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { cont ->
                ref.setValue(datos) { error, _ ->
                    if (!cont.isActive) return@setValue
                    cont.resume(if (error == null) Resultado.Aceptada else Resultado.Rechazada(error.message.take(120)))
                }
            }
        } ?: Resultado.SinRespuesta
    }

    /**
     * Token de depuración de este celular, para registrarlo en la consola sin usar Logcat.
     * Lo genera el proveedor de depuración y lo guarda en sus preferencias; si la versión de
     * la biblioteca lo guarda en otro lugar, se devuelve null y se usa Logcat (DebugAppCheckProvider).
     */
    fun tokenDepuracion(): String? {
        val ctx = appContexto ?: return null
        return if (FabricaAppCheck.esDepuracion) FabricaAppCheck.tokenDepuracion(ctx) else null
    }

    private var appContexto: Context? = null
    fun recordarContexto(context: Context) { appContexto = context.applicationContext }
}
