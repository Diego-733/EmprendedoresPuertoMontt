package com.example.trabajodea10.ui.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.remoto.ClienteSupabase
import com.example.trabajodea10.data.remoto.ConfigSupabase

/**
 * Abre el ingreso con Google en el navegador (RF-ACC-06, RN-07). Lo usan la pantalla de
 * entrada y el botón «Seguir» (RF-ACC-08). Al volver, MainActivity entrega el enlace
 * com.company.ultrapp://login-callback a SesionManager.procesarRetornoOAuth.
 *
 * Antes de abrir el navegador se consulta si el servidor tiene Google activo: si no,
 * se informa aquí en vez de dejar a la persona en una página de error.
 * Devuelve null si abrió el navegador, o el mensaje para mostrar.
 */
suspend fun abrirIngresoGoogle(contexto: Context): String? {
    if (!ConfigSupabase.configurado) return "La app no tiene configurada la conexión con el servidor."
    val activo = runCatching { ClienteSupabase.googleHabilitado() }.getOrNull()
        ?: return "Sin conexión con el servidor. Revisa tu internet e inténtalo otra vez."
    if (!activo) return "El ingreso con Google aún no está habilitado en el servidor."
    SesionManager.errorOAuth = null
    val intento = Intent(Intent.ACTION_VIEW, Uri.parse(SesionManager.urlIngresoGoogle())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { contexto.startActivity(intento); null }.getOrElse { "No hay un navegador para ingresar." }
}
