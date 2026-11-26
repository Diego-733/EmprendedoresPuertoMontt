package com.example.trabajodea10.data.asistente

import java.text.Normalizer

/**
 * Asistente 2.0: capa de comprensión flexible ANTES de las reglas (local, sin IA ni internet).
 *
 *  1. Corrección de voz y tipeo en palabras clave: misma «huella fonética» (b/v, s/z/c, ll/y, h muda,
 *     qu/k) o una sola letra de diferencia o trasposición («bendí» → vendí, «regsitrar» → registrar).
 *     Solo se corrigen palabras que no son de la lista y solo hacia palabras clave: los nombres de
 *     productos se dejan tal cual.
 *  2. Diminutivos («gorritos» → gorros, «regalito» → regalo) para encontrar el producto o la búsqueda.
 *  3. Formas habituales de decir lo mismo («acabo de vender», «anota que vendí», «salieron tres…»,
 *     «se llevó dos…», «tengo que cobrar») → la forma que entienden las reglas («vendí…»).
 * El resultado es texto: lo sigue leyendo el mismo intérprete, que decide con su puntaje y nunca ejecuta nada.
 */
object Canonizador {

    /** Palabras clave (sin tildes, minúsculas) hacia las que se puede corregir. */
    private val CLAVES = listOf(
        "vendi", "vender", "vendo", "venta", "ventas", "vendido", "vendidos", "vendimos", "registrar", "anotar", "agregar",
        "producto", "productos", "precio", "cantidad", "feria", "ferias", "compartir", "tienda", "tiendas", "ajustes",
        "mensajes", "quedan", "llegaron", "ocultar", "mostrar", "borrar", "buscar", "busco", "asociacion", "asociaciones",
        "invitacion", "invitaciones", "historial", "seguidores", "contrasena", "emprendedora", "emprendedoras", "autorizar",
        "bitacora", "administradores", "sesion", "perfil", "ayuda", "filtros", "mapa", "comprobante", "comprobantes",
        "participo", "cobrar", "calificacion", "avisos", "notificaciones", "pendientes", "asistencia", "diagnostico"
    )
    /** Palabras comunes que nunca se «corrigen» aunque se parezcan a una clave. */
    private val PROTEGIDAS = setOf(
        "vende", "venden", "venda", "vendan", "ventana", "tiendo", "precios", "busca", "buscan", "mapas", "ayudame", "ayudar",
        "feriado", "perfiles", "quedo", "queda", "quede", "llego", "llega", "mostra", "borra", "agrega", "anota", "sesiones"
    )
    private val CLAVES_HUELLA: Map<String, String> = CLAVES.associateBy { huella(it) }
    private val NO_DIMINUTIVO = setOf(
        "bonito", "bonita", "bonitos", "bonitas", "poquito", "poquita", "chiquito", "chiquita", "cerquita", "ahorita", "favorito",
        "favorita", "favoritos", "favoritas", "bendito", "bendita", "maldito", "exquisito", "infinito", "gratis", "mosquito",
        "cabrito", "bonita", "chiquitos", "chiquitas", "dinamita", "termita", "visita", "visitas", "cita", "citas", "pepita"
    )

    fun sinTildes(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Huella fonética del español de Chile para palabras clave. */
    fun huella(palabra: String): String {
        var p = sinTildes(palabra).replace("ñ", "n")
        p = p.replace("ch", "#").replace("ll", "y").replace("qu", "k").replace("h", "")
            .replace(Regex("c(?=[ei])"), "s").replace("c", "k").replace("z", "s").replace("v", "b").replace("w", "gu").replace("#", "ch")
        return p.replace(Regex("(.)\\1+"), "$1")
    }

    /** Distancia de Damerau-Levenshtein (con trasposición) acotada: devuelve > 1 apenas se pasa de 1. */
    fun distancia(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val costo = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + costo)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    /** Corrige una palabra hacia una clave, o null si no corresponde. */
    fun corregir(palabra: String): String? {
        val s = sinTildes(palabra)
        if (s.length < 4 || s in CLAVES || s in PROTEGIDAS) return null
        CLAVES_HUELLA[huella(s)]?.let { return it }
        if (s.length >= 6) CLAVES.firstOrNull { it.length >= 6 && distancia(s, it) == 1 }?.let { return it }
        return null
    }

    /** «gorritos» → «gorros», «regalito» → «regalo», «bolsitas» → «bolsas». null si no es diminutivo. */
    fun sinDiminutivo(palabra: String): String? {
        val s = palabra.lowercase()
        if (sinTildes(s) in NO_DIMINUTIVO || s.length < 6) return null
        // Verbos y palabras en -ito/-ita que no son diminutivos: «necesito», «invita», «evita», «solicito», «exquisito».
        if (Regex("^(neces|invit|evit|permit|repit|visit|admit|solicit|quit|grit|deposit|limit|habit|exit|merit|credit|debit|apetit|gratuit|infinit|bendit|maldit|exquisit|ermit|transit|circuit|palpit|imit|marchit)").containsMatchIn(sinTildes(s))) return null
        val m = Regex("^(\\p{L}{3,}?)it([oa]s?)$").find(s) ?: return null
        return m.groupValues[1] + m.groupValues[2]
    }

    private val NUM = "(\\d+|un|una|uno|un par de|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|quince|veinte)"
    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    /** Formas de decir lo mismo → la forma que entienden las reglas. Se aplican en orden, sobre el texto ya corregido. */
    private val FORMAS: List<Pair<Regex, String>> = listOf(
        // Ventas
        r("\\b(?:acabo de|reci[eé]n|ya) vend(?:er|[ií])\\b") to "vendí",
        r("\\ban[oó]ta(?:me|lo)?(?: que)? vend[ií]\\b") to "vendí",
        r("\\b(?:quiero |necesito |hay que )?dejar anotad[ao] (?:una |la )?venta\\b") to "anotar una venta",
        r("\\breg[ií]stra(?:me|nos)? (?:una |la )?venta(?: de)?\\b") to "registrar una venta de",
        r("\\ban[oó]ta(?:me)? (?:una |la )?venta(?: de)?:?") to "registrar una venta de",
        r("\\btengo que cobrar\\b") to "vendí",
        // «cobré un collar» sí; «aros de cobre» no (el metal).
        r("(?<!de )\\bcobr(?:é|amos|e(?= (?:un|una|el|la|los|las|\\d)))(?!\\p{L})") to "vendí",
        r("^\\s*¿?\\s*c[oó]mo (?:vendo|anoto|registro) (?:$NUM )?\\p{L}+") to "¿cómo registro una venta?",
        r("\\bcu[aá]nto llevo vendido\\b") to "cuánto vendí",
        r("\\b(?:salieron|sali[oó]|se fueron|se fue|se llev[oó]|se llevaron) $NUM\\b") to "vendí $1",
        // Cantidades
        r("\\b(?:repuse|repusimos|entraron|me entraron) $NUM\\b") to "me llegaron $1",
        r("\\b(?:ahora )?tengo (?:solo|solamente|nom[aá]s) $NUM\\b") to "me quedan $1",
        // Ayuda general
        r("^\\s*(?:necesito|quiero|me falta) (?:una )?ayuda\\s*[.!]?\\s*$") to "abre el centro de ayuda",
        // Ajustes de letra
        r("\\b(?:agranda|achica|aumenta|disminuye|cambia) (?:la )?letra\\b") to "tamaño de letra"
    ).map { (re, s) -> re to s }

    private val CREAR = Regex("\\b(subir|sube|subo|agregar|agrega|anadir|anade|crear|crea|nuevo producto|publicar)\\b")

    fun canonizar(frase: String): String {
        // 1. Corrección de voz y tipeo, palabra por palabra (conserva lo demás tal cual).
        var t = Regex("\\p{L}+").replace(frase) { m -> corregir(m.value) ?: m.value }
        // 2. Diminutivos (no al crear un producto: «Gorritos de lana» es el nombre que la persona eligió).
        if (!CREAR.containsMatchIn(sinTildes(t))) t = Regex("\\p{L}+").replace(t) { m -> sinDiminutivo(m.value) ?: m.value }
        // 3. Formas equivalentes.
        for ((re, s) in FORMAS) t = re.replace(t, s)
        return t.replace("vendí un par de", "vendí 2")
    }
}
