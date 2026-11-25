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
    /** Sin WhatsApp utilizable y sin mensajes por la vitrina: una sola frase, sin destino de reemplazo. */
    const val SIN_CONTACTO = "Por ahora esta tienda no recibe mensajes desde la vitrina"
    const val AYUDA = "Escribe tu número con +56, por ejemplo +56 9 XXXX XXXX. Si lo dejas vacío, tu tienda no muestra el botón de WhatsApp."
    const val INVALIDO = "Revisa el número: escribe +56 9 y los 8 dígitos de tu celular."
    const val DE_EJEMPLO = "Ese número es de ejemplo. Escribe el WhatsApp real de tu tienda o déjalo vacío."

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

    /**
     * Números de ejemplo de los datos de prueba (supabase/02_datos_prueba.sql, MockData, SesionManager
     * y la demo web). Si una tienda real los conserva, abrir WhatsApp le escribiría a un desconocido:
     * se tratan como «sin WhatsApp». Misma lista que NUMEROS_DEMOSTRACION en js/catalogo.js.
     */
    private val NUMEROS_DEMOSTRACION = setOf("56987654321", "56991234567", "56976543210", "56965432109", "56912345678", "56988887777")
    private val SERIE_EJEMPLO = Regex("^96123(0000|45[01]\\d|4520)$")

    fun esDemostracion(texto: String?): Boolean {
        val d = normalizar(texto) ?: return false
        if (d in NUMEROS_DEMOSTRACION) return true
        val ocho = d.takeLast(8)
        if (ocho.all { it == ocho[0] } || ocho == "12345678" || ocho == "87654321") return true
        return SERIE_EJEMPLO.matches(d.substring(2))
    }

    /** Número para abrir WhatsApp: válido y que no sea de los datos de ejemplo. Si no, null. */
    fun numeroContacto(texto: String?): String? = normalizar(texto)?.takeUnless { esDemostracion(it) }

    /** Formato para guardar y mostrar: "+56 9 1234 5678" (celular) o "+56 65 212 3456" (fijo). */
    fun formatear(texto: String?): String? {
        val d = normalizar(texto) ?: return null
        return if (d[2] == '9') "+56 9 ${d.substring(3, 7)} ${d.substring(7)}"
        else "+56 ${d.substring(2, 4)} ${d.substring(4, 7)} ${d.substring(7)}"
    }

    /** Enlace wa.me con el mensaje ya escrito, o null si el número no sirve o es de ejemplo. */
    fun enlace(numero: String?, mensaje: String): String? {
        val d = numeroContacto(numero) ?: return null
        return "https://wa.me/$d?text=" + java.net.URLEncoder.encode(mensaje, "UTF-8").replace("+", "%20")
    }

    /** Rótulos que dicen, antes de tocar, a quién le llega y por qué canal. */
    fun etiquetaWhatsApp(nombre: String) = "Enviar WhatsApp a $nombre"
    fun etiquetaMensaje(nombre: String) = "Enviar mensaje a $nombre"
}
