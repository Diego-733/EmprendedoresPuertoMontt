package com.example.trabajodea10.seguridad

import android.content.Context
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * APK público (src/release): siempre Play Integrity. No contiene el proveedor de
 * depuración ni forma de ver o copiar tokens. Para un APK que se descarga e instala a
 * mano, la consola debe tener la app registrada con Play Integrity (huella SHA-256 de la
 * firma de entrega) y la opción «Exclusivamente fuera de Google Play»
 * (ver docs/entrega8/README.md).
 */
object FabricaAppCheck {
    const val esDepuracion = false
    const val nombre = "Play Integrity"

    fun crear(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()

    @Suppress("UNUSED_PARAMETER")
    fun tokenDepuracion(context: Context): String? = null
}
