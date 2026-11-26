package com.example.trabajodea10.data.asistente

import com.example.trabajodea10.data.Producto

/**
 * Encuentra los productos de la tienda con sesión a partir de lo que dijo la persona
 * («los chalecos azules», «el gorro de lana»).
 *
 * Regla de seguridad: **nunca elige «el más parecido» si hay dudas**. Solo devuelve [Resultado.Unico]
 * cuando un único producto contiene todas las palabras dichas. En cualquier otro caso devuelve las
 * coincidencias razonables para que la persona escoja (con nombre, foto, precio y cuántos quedan).
 */
object ResolutorProductos {

    sealed class Resultado {
        data class Unico(val producto: Producto) : Resultado()
        data class Varios(val opciones: List<Producto>) : Resultado()
        data object Ninguno : Resultado()
    }

    private val VACIAS = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "mi", "mis", "producto", "productos",
        "que", "con", "para", "y", "a", "en", "al", "lo", "esos", "esas", "estos", "estas", "ese", "esa", "este", "esta",
        "todos", "todas", "todo", "toda", "otro", "otra", "mas", "cada", "uno", "unidades", "unidad",
        "hoy", "ayer", "recien", "ahora", "tarde", "temprano", "anoche", "nuevo", "nueva", "nuevos", "nuevas"
    )

    /** Palabras con significado (sin artículos ni números), normalizadas sin tildes. */
    fun palabras(t: String): List<String> =
        NumerosEs.normalizar(t).replace(Regex("[^a-z0-9ñ ]"), " ").split(" ")
            .filter { it.length >= 2 && it !in VACIAS && !it.all(Char::isDigit) }

    /** «chalecos» = «chaleco», «collares» = «collar», «luces» = «luz»: singular y plural cuentan igual. */
    fun mismaPalabra(a: String, b: String): Boolean {
        if (a == b) return true
        val (corta, larga) = if (a.length <= b.length) a to b else b to a
        return larga == corta + "s" || larga == corta + "es" ||
            (corta.endsWith("z") && larga == corta.dropLast(1) + "ces")
    }

    /** Fracción de las palabras dichas que aparecen en el nombre (1 cada una) o en opciones y descripción (0,5). */
    fun puntaje(dicho: List<String>, p: Producto): Double {
        if (dicho.isEmpty()) return 0.0
        val nombre = palabras(p.nombre)
        val extra = palabras(p.variantes.joinToString(" ") { it.nombre } + " " + p.descripcion)
        var s = 0.0
        dicho.forEach { w ->
            when {
                nombre.any { mismaPalabra(it, w) } -> s += 1.0
                extra.any { mismaPalabra(it, w) } -> s += 0.5
            }
        }
        return s / dicho.size
    }

    fun buscar(texto: String, productos: List<Producto>): Resultado {
        val dicho = palabras(texto)
        if (dicho.isEmpty()) return Resultado.Ninguno
        val candidatos = productos.filter { it.nombre.isNotBlank() }
            .map { it to puntaje(dicho, it) }
            .filter { it.second >= 0.5 }
            .sortedByDescending { it.second }
        if (candidatos.isEmpty()) return Resultado.Ninguno
        val mejor = candidatos.first().second
        val empatados = candidatos.filter { it.second >= mejor - 1e-9 }
        // Único solo si es el único con TODAS las palabras dichas en su nombre.
        if (empatados.size == 1 && mejor >= 1.0 && palabras(empatados[0].first.nombre).isNotEmpty() &&
            dicho.all { w -> palabras(empatados[0].first.nombre).any { mismaPalabra(it, w) } }) {
            return Resultado.Unico(empatados[0].first)
        }
        // Si hay productos con todas las palabras dichas, se muestran solo esos (los parecidos a medias sobran).
        val opciones = if (mejor >= 1.0) empatados else candidatos
        return Resultado.Varios(opciones.take(6).map { it.first })
    }

    private val ORDINALES = listOf(
        "primero|primera|1" to 0, "segundo|segunda|2" to 1, "tercero|tercera|tercer|3" to 2,
        "cuarto|cuarta|4" to 3, "quinto|quinta|5" to 4, "sexto|sexta|6" to 5
    )

    /**
     * La persona responde a una lista de opciones: «el primero», «los de lana», «el azul», «el de cinco mil».
     * Devuelve las opciones que siguen calzando (una sola = elegida). Lista vacía = no se entendió.
     */
    fun refinar(respuesta: String, opciones: List<Producto>): List<Producto> {
        val t = NumerosEs.normalizar(respuesta)
        val d = NumerosEs.aDigitos(respuesta)
        // «el primero», «la segunda» (solo si la frase es corta: «el primero que vendí ayer» no es una elección).
        if (t.split(" ").size <= 4) {
            for ((patron, i) in ORDINALES) {
                if (Regex("\\b(el |la |los |las )?($patron)\\b").containsMatchIn(t) && !Regex("\\d{3,}").containsMatchIn(d)) {
                    return listOfNotNull(opciones.getOrNull(i))
                }
            }
        }
        // «el de cinco mil»: por precio.
        InterpreteAlerce.valorSuelto(respuesta.replace(Regex("(?i)^(el|la|los|las) de "), ""))?.let { (monto, esMonto) ->
            if (esMonto) opciones.filter { it.precio.toLong() == monto }.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val dicho = palabras(respuesta)
        if (dicho.isEmpty()) return emptyList()
        val puntajes = opciones.map { it to puntaje(dicho, it) }.filter { it.second > 0 }
        if (puntajes.isEmpty()) return emptyList()
        val mejor = puntajes.maxOf { it.second }
        return puntajes.filter { it.second >= mejor - 1e-9 }.map { it.first }
    }

    /**
     * Opción (talla, color, sabor) que nombró la persona: «talla M», «la roja». Cuenta cada palabra,
     * también las de una letra («M», «S»), y solo elige si una sola opción calza completa.
     */
    fun elegirOpcion(respuesta: String, variantes: List<com.example.trabajodea10.data.Variante>): com.example.trabajodea10.data.Variante? {
        fun tokens(t: String) = NumerosEs.normalizar(t).replace(Regex("[^a-z0-9ñ ]"), " ").split(" ").filter { it.isNotEmpty() && it !in VACIAS }
        val dicho = tokens(respuesta)
        if (dicho.isEmpty()) return null
        val completas = variantes.filter { v -> tokens(v.nombre).let { w -> w.isNotEmpty() && w.all { x -> dicho.any { y -> mismaPalabra(x, y) } } } }
        if (completas.size == 1) return completas.first()
        // «la M» (sin decir «talla»): la palabra que distingue a una sola opción.
        val distintivas = variantes.filter { v -> tokens(v.nombre).any { x -> dicho.any { y -> mismaPalabra(x, y) } && variantes.count { o -> tokens(o.nombre).any { mismaPalabra(it, x) } } == 1 } }
        return distintivas.singleOrNull()
    }

    /** ¿Dijo el plural sin precisar («las bufandas», «los chalecos»)? Puede referirse a todas. */
    fun hablaDeVarios(texto: String): Boolean =
        Regex("\\b(los|las|mis|todos|todas|esos|esas|estos|estas) [a-z]+s\\b").containsMatchIn(NumerosEs.normalizar(texto))
}
