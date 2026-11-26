package com.example.trabajodea10.data.asistente

import com.example.trabajodea10.data.MedioPago

/**
 * Lee lo que se dijo de una venta: «vendí una bufanda y dos gorros a cinco mil cada uno, en efectivo,
 * en la feria». Solo lee: no busca productos (eso lo hace [ResolutorProductos]) ni registra nada.
 *
 * Nunca inventa: una cantidad que no se dijo queda en null y se pregunta.
 */
object LectorVentas {

    data class LineaDicha(
        val cantidad: Int?,
        val producto: String,
        /** Precio dicho para esta línea (por unidad salvo [precioEsTotal]). */
        val precio: Long? = null,
        val precioEsTotal: Boolean = false
    )

    data class VentaDicha(
        val lineas: List<LineaDicha>,
        /** «por veinte mil en total» sin decir de qué línea: total de toda la venta. */
        val totalDicho: Long? = null,
        val medio: MedioPago? = null,
        /** Dijo un medio que Alerce no tiene («tarjeta de crédito»): se dice cuáles hay. */
        val medioNoDisponible: String? = null,
        /** «tarjeta» a secas: puede ser débito o crédito; se pregunta. */
        val medioDudoso: Boolean = false,
        val enFeria: Boolean = false
    )

    /** Medio de pago dicho, solo entre los que existen en Alerce (Efectivo, Transferencia, Débito, QR). */
    fun medio(frase: String): Triple<MedioPago?, String?, Boolean> {
        val t = NumerosEs.normalizar(frase)
        return when {
            Regex("\\b(credito|cheque|cuotas|paypal|bitcoin|cripto)\\b").containsMatchIn(t) ->
                Triple(null, Regex("\\b(credito|cheque|cuotas|paypal|bitcoin|cripto)\\b").find(t)!!.value, false)
            Regex("\\b(efectivo|en efectivo|al contado|plata en mano|billete|billetes|cash)\\b").containsMatchIn(t) -> Triple(MedioPago.EFECTIVO, null, false)
            Regex("\\b(transferencia|transfirio|transfirieron|me transfirio|me transfirieron|deposito|depositaron)\\b").containsMatchIn(t) -> Triple(MedioPago.TRANSFERENCIA, null, false)
            Regex("\\b(debito|redcompra|tarjeta de debito)\\b").containsMatchIn(t) -> Triple(MedioPago.DEBITO, null, false)
            Regex("\\b(qr|codigo qr|con el codigo)\\b").containsMatchIn(t) -> Triple(MedioPago.QR, null, false)
            Regex("\\btarjeta\\b").containsMatchIn(t) -> Triple(null, null, true)
            else -> Triple(null, null, false)
        }
    }

    /** ¿Dijo que fue en una feria? («los vendí en la feria», «fue en la feria navideña»). */
    fun enFeria(frase: String): Boolean = Regex("\\ben (la|una) feria\\b|\\bferia\\b").containsMatchIn(NumerosEs.normalizar(frase))

    private val VERBO_VENTA = Regex(
        "^.*?\\b(registra que vendi|anota que vendi|anotar que vendi|registrar que vendi|hice una venta de|venta de|vendi|vendimos|se vendieron|se vendio|me compraron|compraron)\\b"
    )
    private val COLA = Regex(
        "\\b(en efectivo|al contado|con transferencia|por transferencia|con debito|con tarjeta( de debito| de credito)?|con qr|con codigo qr|" +
            "pago en [a-z]+|pagaron con [a-z]+|me pagaron con [a-z]+|fue en efectivo|en (la|una) feria( [a-z]+)*|hoy|ayer|recien|esta manana|en la tarde)\\b"
    )
    private val TOTAL = Regex("\\b(?:por|en|a) (\\d+)(?: pesos)? (?:en total|por todo|todo junto|en todo)\\b|\\b(?:en total|el total fue de|total de) (\\d+)\\b")
    private val PRECIO_LINEA = Regex("\\b(?:a|en|por) (\\d+)(?: (pesos|lucas?|luquitas?))?(?: (cada uno|cada una|c/u|la unidad|cada))?(?: (en total|por todos|por todas|los dos|las dos))?\\b")

    /**
     * Separa la venta en líneas: «una bufanda y dos gorros» → [1 bufanda] [2 gorros].
     * Si hay una autocorrección («no vendí dos, vendí tres», «no, eran cuatro»), vale lo último.
     */
    fun leer(frase: String): VentaDicha {
        val (medio, noDisponible, dudoso) = medio(frase)
        val feria = enFeria(frase)
        var t = NumerosEs.aDigitos(frase)
            .replace(Regex("\\b(?:(\\d+) )?(lucas?|luquitas?) y media\\b")) { m -> "${(m.groupValues[1].toLongOrNull() ?: 1) * 1000 + 500} pesos" }
        // Autocorrección: «no vendi 2 vendi 3» → «vendi 3»; se queda lo dicho después del último «no…».
        val ultimaCorreccion = Regex("\\bno (vendi|eran|era|fueron|fue|son)\\b").findAll(t).lastOrNull()
        if (ultimaCorreccion != null) {
            val despues = t.substring(ultimaCorreccion.range.last + 1)
            val reafirma = Regex("\\b(vendi|eran|era|fueron|fue|son)\\b").find(despues)
            if (reafirma != null) {
                // Lo nombrado antes (el producto) se conserva si después solo se dijo la cantidad.
                val antes = t.substring(0, ultimaCorreccion.range.first)
                t = antes + " vendi " + despues.substring(reafirma.range.last + 1)
            }
        }
        t = t.replace(VERBO_VENTA, " ")
        var total: Long? = null
        TOTAL.find(t)?.let { m ->
            total = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toLongOrNull()
            t = t.replace(m.value, " ")
        }
        t = t.replace(COLA, " ").replace(Regex("\\s+"), " ").trim()
        val trozos = t.split(Regex(",| y (?=(\\d+|un|una|uno|unos|unas|el|la|los|las)\\b)| mas (?=\\d)| ademas ")).map { it.trim() }.filter { it.isNotBlank() }
        val lineas = trozos.mapNotNull { leerLinea(it) }
        // Un total suelto con una sola línea es el total de esa línea.
        if (total != null && lineas.size == 1 && lineas[0].precio == null) {
            return VentaDicha(listOf(lineas[0].copy(precio = total, precioEsTotal = true)), null, medio, noDisponible, dudoso, feria)
        }
        return VentaDicha(lineas, total, medio, noDisponible, dudoso, feria)
    }

    private fun leerLinea(trozo: String): LineaDicha? {
        var s = " $trozo "
        var precio: Long? = null
        var esTotal = false
        PRECIO_LINEA.find(s)?.let { m ->
            val n = m.groupValues[1].toLongOrNull()
            val unidad = m.groupValues[2].ifEmpty { null }
            if (n != null && (n >= 100 || unidad != null)) {
                precio = NumerosEs.montoConUnidad(n, unidad)
                esTotal = m.groupValues[4].isNotEmpty()
                s = s.replace(m.value, " ")
            }
        }
        s = s.replace(Regex("\\b(cada uno|cada una|c/u|en total)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        val m = Regex("^(?:(\\d+|un|una|uno|unos|unas) )?(.*)$").find(s) ?: return null
        val cantidadTxt = m.groupValues[1]
        val cantidad = when {
            cantidadTxt.isEmpty() || cantidadTxt == "unos" || cantidadTxt == "unas" -> null
            cantidadTxt in setOf("un", "una", "uno") -> 1
            else -> cantidadTxt.toIntOrNull()
        }
        val producto = m.groupValues[2].replace(Regex("^(el|la|los|las|mis|de|del) "), "").trim()
        if (producto.isBlank() || producto.all { it.isDigit() || it == ' ' }) {
            return if (cantidad != null) LineaDicha(cantidad, "", precio, esTotal) else null
        }
        return LineaDicha(cantidad, producto, precio, esTotal)
    }

    /**
     * Corrección de una venta en curso: «no, eran cuatro gorros», «fueron 3», «vendí tres, no dos».
     * Devuelve (cantidad, palabras del producto o null) si la frase corrige una cantidad.
     */
    fun correccionCantidad(frase: String): Pair<Int, String?>? {
        // «No, eran cuatro»: ese «no» niega lo anterior, no al número que sigue; se quita.
        val sinNoInicial = frase.trim().replace(Regex("(?i)^no\\s*[,.;]\\s*"), "")
        val t = NumerosEs.aDigitos(sinNoInicial)
        if (!Regex("^(no|eran|era|fueron|fue|son|perdon|mejor|en realidad|vendi|vendimos)\\b").containsMatchIn(t) &&
            sinNoInicial.length == frase.trim().length) return null
        // Números con lo que viene después; un número precedido por «no» (o «no vendí») es el que se descarta.
        val numeros = Regex("\\b(\\d+)\\b(?: ([a-z ]+?))?(?=$| no\\b| \\d|,)").findAll(t).map { m ->
            val antes = t.substring(0, m.range.first).trim()
            val negado = Regex("\\bno( (vendi|vendimos|eran|era|fueron|fue|son))?$").containsMatchIn(antes)
            Triple(m.groupValues[1].toInt(), m.groupValues[2].trim(), negado)
        }.toList()
        if (numeros.isEmpty()) return null
        // Si todos están negados, solo «no eran 4» (sin coma: el dictado no las pone) se toma como corrección.
        val elegido = numeros.lastOrNull { !it.third }
            ?: numeros.singleOrNull()?.takeIf { Regex("^no (eran|fueron|son|era|fue) \\d+").containsMatchIn(t) }
            ?: return null
        if (elegido.first <= 0 || elegido.first > 9999) return null
        val prod = elegido.second.replace(Regex("\\b(cada|uno|una|pesos|lucas?|nomas|no mas|solamente|solo)\\b.*$"), "")
            .replace(Regex("\\b(vendi|vendimos|eran|era|fueron|fue|son|no|el|la|los|las)\\b"), " ")
            .replace(Regex("\\s+"), " ").trim().ifBlank { null }
        return elegido.first to prod
    }
}
