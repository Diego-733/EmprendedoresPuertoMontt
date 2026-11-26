package com.example.trabajodea10.data.asistente

import java.text.Normalizer

/**
 * Números y montos tal como se dicen en Chile, sin servicios externos:
 * «quince mil», «doce mil quinientos», «veinte lucas», «una luca», «$15.000», «15 mil», «cuatro».
 *
 * Solo convierte; no decide si un número es precio o cantidad (eso lo hace [InterpreteAlerce]
 * mirando las palabras de alrededor).
 */
object NumerosEs {

    /** Minúsculas, sin tildes ni signos de pregunta; conserva dígitos, «$», puntos y comas. */
    fun normalizar(t: String?): String =
        Normalizer.normalize((t ?: "").lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[¿?¡!;:\"«»()]"), " ")
            .replace(Regex("[,.](?=\\s|$)"), " ")      // puntuación; los puntos de miles («15.000») se conservan
            .replace(Regex("\\s+"), " ")
            .trim()

    private val UNIDADES = mapOf(
        "cero" to 0, "uno" to 1, "un" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12,
        "trece" to 13, "catorce" to 14, "quince" to 15, "dieciseis" to 16, "diecisiete" to 17,
        "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20, "veintiuno" to 21, "veintiun" to 21,
        "veintiuna" to 21, "veintidos" to 22, "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25,
        "veintiseis" to 26, "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29
    )
    private val DECENAS = mapOf(
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
        "setenta" to 70, "ochenta" to 80, "noventa" to 90
    )
    private val CENTENAS = mapOf(
        "cien" to 100, "ciento" to 100, "doscientos" to 200, "doscientas" to 200, "trescientos" to 300,
        "trescientas" to 300, "cuatrocientos" to 400, "cuatrocientas" to 400, "quinientos" to 500,
        "quinientas" to 500, "seiscientos" to 600, "seiscientas" to 600, "setecientos" to 700,
        "setecientas" to 700, "ochocientos" to 800, "ochocientas" to 800, "novecientos" to 900, "novecientas" to 900
    )
    /** «un», «una» y «uno» solo cuentan como número junto a «mil», «millón», «luca» o «docena»: «un chaleco» no es «1 chaleco». */
    private val AMBIGUOS = setOf("un", "una", "uno")
    private val MULTIPLICADORES = setOf("mil", "millon", "millones", "luca", "lucas", "luquita", "luquitas", "docena", "docenas")
    private val DOCENA = setOf("docena", "docenas")

    private fun esPalabraNumero(p: String) =
        p in UNIDADES || p in DECENAS || p in CENTENAS || p == "mil" || p == "millon" || p == "millones"

    /**
     * Reemplaza las palabras de números por dígitos: «cuesta quince mil pesos» → «cuesta 15000 pesos».
     * Quita los puntos de miles («15.000» → «15000») y une «15 mil» → «15000».
     */
    fun aDigitos(texto: String): String {
        val tokens = normalizar(texto).split(" ").filter { it.isNotEmpty() }
        val salida = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val crudo = tokens[i]
            // Miles con punto o coma («15.000», «8,500»: el dictado a veces usa la coma); en pesos no hay decimales.
            var t = crudo.replace(Regex("^\\$"), "").let { if (Regex("^\\d{1,3}([.,]\\d{3})+$").matches(it)) it.replace(".", "").replace(",", "") else it }
            // «$15 000»: miles separados por espacio (solo con signo $, para no juntar dos números distintos).
            if (crudo.startsWith("$") && Regex("^\\d{1,3}$").matches(t)) {
                while (tokens.getOrNull(i + 1)?.let { Regex("^\\d{3}$").matches(it) } == true) { t += tokens[i + 1]; i++ }
            }
            val siguiente = tokens.getOrNull(i + 1)
            // «media docena» = 6.
            if (t == "media" && siguiente == "docena") { salida += "6"; i += 2; continue }
            // «15 mil» y «15 mil 500»: el número ya va en dígitos y «mil» lo multiplica.
            val previo = salida.lastOrNull()
            if (t in DOCENA && previo != null && previo.all { it.isDigit() }) {
                salida[salida.lastIndex] = (previo.toLong() * 12).toString(); i++; continue
            }
            if ((t == "mil" || t == "millones" || t == "millon") && previo != null && previo.all { it.isDigit() }) {
                salida[salida.lastIndex] = (previo.toLong() * (if (t == "mil") 1000 else 1_000_000)).toString()
                val resto = tokens.getOrNull(i + 1)
                if (t == "mil" && resto != null && Regex("^\\d{1,3}$").matches(resto)) {
                    salida[salida.lastIndex] = (salida.last().toLong() + resto.toLong()).toString(); i += 2
                } else i++
                continue
            }
            val ambiguoSuelto = t in AMBIGUOS && siguiente !in MULTIPLICADORES
            if (!esPalabraNumero(t) || ambiguoSuelto) { salida += t; i++; continue }
            var total = 0L
            var actual = 0L
            var j = i
            while (j < tokens.size) {
                val p = tokens[j]
                when {
                    p == "y" && j > i && tokens[j - 1] in DECENAS && tokens.getOrNull(j + 1)?.let { (UNIDADES[it] ?: 99) < 10 } == true -> { j++; continue }
                    p in UNIDADES && !(p in AMBIGUOS && tokens.getOrNull(j + 1) !in MULTIPLICADORES && j > i) -> actual += UNIDADES.getValue(p)
                    p in DOCENA -> actual = (if (actual == 0L) 1 else actual) * 12
                    p in DECENAS -> actual += DECENAS.getValue(p)
                    p in CENTENAS -> actual += CENTENAS.getValue(p)
                    p == "mil" -> { total += (if (actual == 0L) 1 else actual) * 1000; actual = 0 }
                    p == "millon" || p == "millones" -> { total = (total + (if (actual == 0L) 1 else actual)) * 1_000_000; actual = 0 }
                    else -> break
                }
                j++
            }
            salida += (total + actual).toString()
            i = j
        }
        return salida.joinToString(" ")
    }

    /** Monto en pesos de un número con su unidad: «20 lucas» → 20000; «3 gambas» → 300; «15000 pesos» → 15000. */
    fun montoConUnidad(numero: Long, unidad: String?): Long = when {
        unidad == null -> numero
        unidad.startsWith("luc") || unidad.startsWith("luq") -> numero * 1000   // luca, lucas, luquita, luquitas
        unidad.startsWith("gamba") -> numero * 100
        else -> numero
    }

    /** Pesos chilenos para leer y mostrar: 15000 → «$15.000». */
    fun pesos(valor: Long): String = "$" + String.format(java.util.Locale.ROOT, "%,d", valor).replace(',', '.')
}
