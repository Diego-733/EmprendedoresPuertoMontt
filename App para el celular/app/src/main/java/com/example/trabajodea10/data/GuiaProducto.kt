package com.example.trabajodea10.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.Normalizer

/**
 * «Guíame paso a paso» (misma lógica que js/guiaProducto.js en la web).
 *
 * Entiende unas pocas frases escritas o dictadas y lleva al paso del alta de producto que
 * corresponde. Si la frase es ambigua, ofrece dos opciones. Reglas locales: sin IA de pago,
 * sin servidor y sin guardar audio. La guía va encima del formulario de siempre: no cambia
 * sus reglas ni su guardado, y salir de ella no borra nada.
 */
object GuiaProducto {

    enum class Intencion(val titulo: String, val paso: Int?) {
        PRODUCTO("Subir un producto nuevo", 0),
        FOTO("Agregar una foto", 0),
        DATOS("Poner el nombre y el precio", 1),
        VISIBILIDAD("Elegir quién ve el producto", 4),
        VENTA("Registrar una venta", null)
    }

    sealed class Respuesta {
        data class Ir(val intencion: Intencion) : Respuesta()
        data class Elegir(val opciones: List<Intencion>) : Respuesta()
    }

    fun normalizar(t: String?): String =
        Normalizer.normalize((t ?: "").lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[¿?¡!.,;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    // Orden = prioridad cuando la frase calza con varias.
    private val REGLAS = listOf(
        Intencion.FOTO to Regex("\\b(foto|fotos|imagen|imagenes|camara|fotografia)\\b"),
        Intencion.VISIBILIDAD to Regex("(quien lo ve|quienes lo ven|solo yo|lo ven todos|ocult|escond|que no lo vea|privad)"),
        Intencion.DATOS to Regex("\\b(precio|cobrar|cuanto cobro|nombre del producto|cambiar el nombre)\\b"),
        Intencion.VENTA to Regex("\\b(vendi|registrar (una )?venta|anotar (una )?venta|registro de venta)\\b"),
        Intencion.PRODUCTO to Regex("\\b(subir|agregar|anadir|publicar|poner|cargar|crear|nuevo)\\b.*\\b(producto|productos|algo|articulo|cosa)\\b|\\bvender (algo|un|una|mis|productos?|cosas)\\b|\\bproducto nuevo\\b|\\bprimer producto\\b")
    )

    /** Interpreta una frase; solo ofrece lo que esta cuenta puede usar. Nunca más de dos opciones. */
    fun interpretar(frase: String, disponibles: Set<Intencion> = Intencion.entries.toSet()): Respuesta {
        val t = normalizar(frase)
        var hallados = REGLAS.filter { (id, re) -> re.containsMatchIn(t) && id in disponibles }.map { it.first }
        // «quiero subir un producto con foto»: es subir un producto (empieza igual, en la foto).
        if (Intencion.PRODUCTO in hallados && Intencion.FOTO in hallados) hallados = listOf(Intencion.PRODUCTO)
        if (hallados.size == 1) return Respuesta.Ir(hallados[0])
        if (hallados.size > 1) return Respuesta.Elegir(hallados.take(2))
        // «vender» a secas: ¿subir algo para vender o anotar una venta?
        val base = if (Regex("\\bvend").containsMatchIn(t)) listOf(Intencion.PRODUCTO, Intencion.VENTA) else listOf(Intencion.PRODUCTO, Intencion.FOTO)
        val opciones = base.filter { it in disponibles }
        if (opciones.size == 1) return Respuesta.Ir(opciones[0])
        return Respuesta.Elegir(opciones.ifEmpty { listOf(Intencion.PRODUCTO, Intencion.FOTO).filter { it in disponibles } })
    }

    /** Ayuda de cada paso del alta (Foto, Datos, Opciones, Disponibilidad, Revisión). Mismos nombres que los botones. */
    data class AyudaPaso(val indicacion: String, val ejemplo: String?, val ayuda: String)

    val AYUDAS = listOf(
        AyudaPaso(
            "Toca «Tomar foto» o «Elegir de la galería». Si ahora no tienes una, toca «Siguiente» y sigue sin foto.",
            null,
            "Si no das permiso a la cámara, usa «Elegir de la galería». La foto se puede cambiar después."
        ),
        AyudaPaso(
            "Escribe el «Nombre del producto» y el «Precio». Toca el micrófono para dictar el nombre y corrígelo si hace falta.",
            "Nombre: «Chal de lana gris». Precio: 15000 (sin puntos).",
            "La descripción y el tipo son opcionales. Cuando esté listo, toca «Siguiente»."
        ),
        AyudaPaso(
            "Si viene en tallas, colores o sabores, agrégalos aquí. Si no, toca «Siguiente».",
            "Un chal en «Gris» y «Azul», cada uno con cuántos tienes.",
            "Las opciones se pueden agregar después desde Mis productos."
        ),
        AyudaPaso(
            "Indica cuántos tienes y cómo lo vendes. Si no sabes, déjalo como está y toca «Siguiente».",
            "Tienes 3 chales: escribe 3. Si lo haces por encargo, elige «A pedido».",
            "Puedes cambiar las cantidades cuando quieras desde Mis productos."
        ),
        AyudaPaso(
            "Elige «Solo yo lo veo» o «Lo ven todos» y toca «Guardar en Mis productos».",
            "«Solo yo lo veo»: queda guardado sin mostrarse. «Lo ven todos»: aparece en tu tienda.",
            "Nada se guarda hasta que tocas «Guardar en Mis productos». Después puedes cambiar quién lo ve."
        )
    )

    /**
     * Lo mismo que [AYUDAS], dicho como lo diría una persona (para «🔊 Escuchar» y la lectura
     * automática). Usa los nombres reales de los botones para que se puedan encontrar.
     */
    val LOCUCIONES = listOf(
        "Primero, vamos a agregar una foto. Busca un lugar con buena luz y deja el producto al centro. " +
            "Toca Tomar foto, o Elegir de la galería. Si ahora no tienes una, toca Siguiente y sigue sin foto.",
        "Ahora escribe o dime cómo se llama tu producto y cuánto cuesta. " +
            "Puedes tocar el micrófono para dictarlo. Cuando esté listo, toca Siguiente.",
        "Si tu producto viene en tallas, colores o sabores, agrégalos aquí. Si no, toca Siguiente.",
        "Ahora indica cuántos tienes. Si no lo sabes, déjalo como está y toca Siguiente. Lo puedes cambiar después.",
        "Último paso. Elige si lo ven todos o solo tú, y toca Guardar en Mis productos. Antes de eso no se guarda nada."
    )

    /** Texto para leer en voz alta en un paso; [resumen] (si viene) se agrega al final. */
    fun locucion(paso: Int, resumen: String? = null): String =
        listOfNotNull(LOCUCIONES.getOrNull(paso), resumen?.takeIf { it.isNotBlank() }).joinToString(" ")

    // ---------- Estado de la guía en esta sesión (no se guarda en el servidor ni en el celular)
    var activa by mutableStateOf(false)
        private set
    /** Abrir el ejemplo de foto al llegar (la persona preguntó «¿cómo agrego una foto?»). */
    var abrirEjemploFoto by mutableStateOf(false)

    fun iniciar(intencion: Intencion = Intencion.PRODUCTO) {
        activa = true
        abrirEjemploFoto = intencion == Intencion.FOTO
    }

    fun salir() {
        activa = false
        abrirEjemploFoto = false
    }
}
