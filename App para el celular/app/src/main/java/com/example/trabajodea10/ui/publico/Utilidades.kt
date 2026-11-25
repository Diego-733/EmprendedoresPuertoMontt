package com.example.trabajodea10.ui.publico

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Vitrina pública que se comparte por WhatsApp y se codifica en el QR.
 */
const val URL_VITRINA = "https://diego-733.github.io/EmprendedoresPuertoMontt/"

/** Enlace que abre directamente la ficha de una tienda en la vitrina web (también va en su QR). */
fun urlTienda(tiendaId: String): String = URL_VITRINA + "?tienda=" + android.net.Uri.encode(tiendaId)

/** Enlaces públicos de la Entrega 6 (RF-COM-01/03): abren su página web sin iniciar sesión. */
fun urlProducto(productoId: String): String = URL_VITRINA + "?producto=" + android.net.Uri.encode(productoId)
fun urlAsociacion(asociacionId: String): String = URL_VITRINA + "?asociacion=" + android.net.Uri.encode(asociacionId)
fun urlFeria(feriaId: String): String = URL_VITRINA + "?feria=" + android.net.Uri.encode(feriaId)

/**
 * Acciones externas (mapas, teléfono, WhatsApp) pensadas para botones grandes.
 * Se usan desde las pantallas públicas (Visitante) para que las personas mayores
 * puedan llamar o escribir con un solo toque, sin escribir números.
 */

/** Abre la app de mapas del teléfono con la dirección buscada. */
fun abrirMapa(contexto: Context, direccion: String) {
    val uri = Uri.parse("geo:0,0?q=" + Uri.encode(direccion))
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { contexto.startActivity(intent) }
}

/** Abre el marcador con el número listo para llamar (la persona solo toca "llamar"). */
fun llamarTelefono(contexto: Context, numero: String) {
    val numeroLimpio = numero.replace(Regex("[^+0-9]"), "")
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numeroLimpio"))
    runCatching { contexto.startActivity(intent) }
}

/**
 * Abre WhatsApp con un mensaje ya escrito hacia el número de ESA tienda. Si el número falta o
 * no es válido, no abre nada y devuelve false (la pantalla muestra «Esta tienda aún no tiene…»).
 */
fun abrirWhatsApp(contexto: Context, numero: String, mensaje: String): Boolean {
    val numeroLimpio = com.example.trabajodea10.data.ContactoWhatsApp.normalizar(numero) ?: return false
    val uri = Uri.parse("https://wa.me/$numeroLimpio?text=" + Uri.encode(mensaje))
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { contexto.startActivity(intent) }.isSuccess
}

/**
 * Contacto por WhatsApp con UNA tienda: usa el número que configuró su dueña y respeta su
 * preferencia (WhatsApp, formulario o ambos). Si no hay número válido, avisa en vez de abrir
 * un chat con otro destinatario.
 */
fun contactarTiendaPorWhatsApp(contexto: Context, tienda: com.example.trabajodea10.data.Tienda, mensaje: String): Boolean {
    if (tienda.ofreceWhatsApp && abrirWhatsApp(contexto, tienda.whatsapp, mensaje)) return true
    val aviso = when {
        tienda.faltaWhatsApp && tienda.ofreceFormulario -> com.example.trabajodea10.data.ContactoWhatsApp.SIN_WHATSAPP + ". Usa «Enviar un mensaje» en su ficha."
        tienda.faltaWhatsApp -> com.example.trabajodea10.data.ContactoWhatsApp.SIN_WHATSAPP + "."
        else -> "Esta tienda recibe consultas por mensaje: usa «Enviar un mensaje» en su ficha."
    }
    android.widget.Toast.makeText(contexto, aviso, android.widget.Toast.LENGTH_LONG).show()
    return false
}

/** Comparte un texto directamente en WhatsApp; si no está instalado, abre el selector. */
fun compartirPorWhatsApp(contexto: Context, mensaje: String) {
    val enviar = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, mensaje)
        setPackage("com.whatsapp")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val abrioWhatsApp = runCatching { contexto.startActivity(enviar) }.isSuccess
    if (!abrioWhatsApp) {
        val alternativo = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mensaje)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            contexto.startActivity(Intent.createChooser(alternativo, "Compartir tienda"))
        }
    }
}