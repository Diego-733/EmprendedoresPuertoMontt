package com.example.trabajodea10.data

/**
 * Regla única para el WhatsApp de una tienda (misma regla que js/catalogo.js en la web).
 * Acepta el formato chileno con o sin +56 y con espacios: "+56 9 1234 5678", "56912345678",
 * "9 1234 5678". También fijos chilenos de 11 dígitos con código 56 ("+56 65 212 3456").
 * Devuelve null si falta o no es un número chileno válido: en ese caso la app NO abre
 * WhatsApp y muestra «Esta tienda aún no tiene un WhatsApp de contacto».
 */
object ContactoWhatsApp {
    const val SIN_WHATSAPP = "Esta tienda aún no tiene un WhatsApp de contacto"
    const val AYUDA = "Escribe tu número con +56, por ejemplo +56 9 XXXX XXXX. Si lo dejas vacío, tu tienda no muestra el botón de WhatsApp."
    const val INVALIDO = "Revisa el número: escribe +56 9 y los 8 dígitos de tu celular."

    /** Dígitos para wa.me (ej. "56912345678") o null si no es válido. */
    fun normalizar(texto: String?): String? {
        var d = (texto ?: "").filter { it.isDigit() }
        if (d.startsWith("00")) d = d.drop(2)
        if (d.length == 9 && d.startsWith("9")) d = "56$d"
        if (d.length != 11 || !d.startsWith("56")) return null
        if (d[2] !in '2'..'9') return null
        return d
    }

    fun esValido(texto: String?): Boolean = normalizar(texto) != null

    /** Formato para guardar y mostrar: "+56 9 1234 5678" (celular) o "+56 65 212 3456" (fijo). */
    fun formatear(texto: String?): String? {
        val d = normalizar(texto) ?: return null
        return if (d[2] == '9') "+56 9 ${d.substring(3, 7)} ${d.substring(7)}"
        else "+56 ${d.substring(2, 4)} ${d.substring(4, 7)} ${d.substring(7)}"
    }

    /** Enlace wa.me con el mensaje ya escrito, o null si el número no sirve. */
    fun enlace(numero: String?, mensaje: String): String? {
        val d = normalizar(numero) ?: return null
        return "https://wa.me/$d?text=" + java.net.URLEncoder.encode(mensaje, "UTF-8").replace("+", "%20")
    }
}
