package com.example.trabajodea10.data

import java.text.Normalizer

/**
 * Búsqueda pública por palabras: la misma para lo escrito y lo dictado, y la misma regla que
 * Cat.coincideBusqueda en la web (js/catalogo.js). «Quiero una torta de cumpleaños» encuentra
 * «Torta de chocolate para cumpleaños»: importan las palabras, no el orden ni las tildes.
 */
object ReglasBusqueda {
    private val PALABRAS_VACIAS = setOf(
        "de", "del", "la", "las", "el", "los", "un", "una", "unos", "unas", "y", "o", "para", "con", "en", "por",
        "a", "al", "me", "mi", "quiero", "busco", "necesito", "comprar", "algo", "hay"
    )

    /** Sin tildes ni mayúsculas, con espacios simples. */
    fun normalizar(texto: String?): String =
        Normalizer.normalize((texto ?: "").lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Palabras que importan («una torta de cumpleaños» → torta, cumpleanos). */
    fun palabras(consulta: String?): List<String> {
        val todas = normalizar(consulta).replace(Regex("[^a-z0-9ñ ]"), " ").split(" ").filter { it.isNotBlank() }
        val utiles = todas.filter { it !in PALABRAS_VACIAS }
        return utiles.ifEmpty { todas }
    }

    /** true si TODAS las palabras útiles aparecen en alguno de los campos. Consulta vacía: true. */
    fun coincide(consulta: String?, campos: List<String?>): Boolean {
        val ps = palabras(consulta)
        if (ps.isEmpty()) return true
        val texto = " " + campos.joinToString(" ") { normalizar(it) } + " "
        return ps.all { w ->
            texto.contains(w) || (w.length > 3 && w.endsWith("s") && texto.contains(w.replace(Regex("e?s$"), "")))
        }
    }
}
