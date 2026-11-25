package com.example.trabajodea10.seguridad

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * SOLO en el APK de depuración (src/debug). Proveedor de depuración de App Check:
 * el celular genera un token que se registra en la consola (App Check → Administrar
 * tokens de depuración). Firebase pide no distribuirlo: el APK público (src/release)
 * usa Play Integrity y no incluye esta biblioteca (debugImplementation).
 */
object FabricaAppCheck {
    const val esDepuracion = true
    const val nombre = "depuración (solo APK de prueba del equipo)"

    fun crear(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()

    /**
     * Token de depuración para registrarlo sin Logcat. Lo guarda el proveedor en sus
     * preferencias; si una versión de la biblioteca lo guarda en otro lugar, devuelve null
     * y se usa Logcat (etiqueta DebugAppCheckProvider).
     */
    fun tokenDepuracion(context: Context): String? = runCatching {
        val clave = FirebaseApp.getInstance().persistenceKey
        context.getSharedPreferences("com.google.firebase.appcheck.debug.store.$clave", Context.MODE_PRIVATE)
            .getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
    }.getOrNull()
}
