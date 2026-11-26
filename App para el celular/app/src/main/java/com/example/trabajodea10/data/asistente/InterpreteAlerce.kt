package com.example.trabajodea10.data.asistente

/**
 * Intérprete local del Asistente Alerce: reglas en el celular, sin internet, sin costo y sin IA
 * generativa. Entiende frases habituales de Chile y saca los datos que la persona dijo, sin
 * inventar los que no dijo. Si la frase admite dos lecturas, devuelve [Intencion.AMBIGUA] para
 * que el asistente pregunte.
 *
 * No ejecuta nada: solo propone una [Intencion] y sus [Datos]. Quien decide qué se hace es
 * [SesionAsistente] y, para cambiar datos, siempre la persona con una confirmación.
 *
 * Mecanismos generales (no frases fijas):
 *  - números dichos o escritos de varias formas ([NumerosEs]);
 *  - autocorrecciones dentro de una frase («a 35 mil, no, a 40 mil»): manda lo último dicho;
 *  - dos precios distintos sin corrección: no se elige uno, se pregunta ([Datos.preciosOidos]);
 *  - verbos con o sin tilde y con o sin artículo («agrégame un collar», «agrega gorro de lana»).
 */
object InterpreteAlerce {

    /**
     * Qué clase de pedido es. Separa lo que solo se pregunta (CONSULTA), lo que se quiere aprender
     * (AYUDA), lo que cambia datos (ACCION: siempre con revisión y confirmación), ir a una pantalla
     * (NAVEGACION) y la conversación misma (saludos, negativas, «otra cosa»).
     */
    enum class Clase { CONSULTA, AYUDA, ACCION, NAVEGACION, CONVERSACION }

    enum class Intencion(val clase: Clase) {
        CREAR_PRODUCTO(Clase.ACCION),
        REGISTRAR_VENTA(Clase.ACCION),
        MARCAR_SE_ACABO(Clase.ACCION),
        CAMBIAR_CANTIDAD(Clase.ACCION),
        SUMAR_CANTIDAD(Clase.ACCION),
        DISPONIBLE_OTRA_VEZ(Clase.ACCION),
        CAMBIAR_PRECIO(Clase.ACCION),
        EDITAR_PRODUCTO(Clase.ACCION),
        OCULTAR_PRODUCTO(Clase.ACCION),
        MOSTRAR_PRODUCTO(Clase.ACCION),
        BORRAR_PRODUCTO(Clase.ACCION),
        PARTICIPAR_FERIA(Clase.ACCION),
        NO_PARTICIPAR_FERIA(Clase.ACCION),
        VER_PRODUCTOS(Clase.NAVEGACION),
        CONSULTAR_VENTAS_MES(Clase.CONSULTA),
        CONSULTAR_VENTAS_PRODUCTO(Clase.CONSULTA),
        CONSULTAR_PRODUCTOS(Clase.CONSULTA),
        CONSULTAR_AGOTADOS(Clase.CONSULTA),
        CONSULTAR_PROXIMA_FERIA(Clase.CONSULTA),
        CONSULTAR_INVITACIONES(Clase.CONSULTA),
        CONSULTAR_UBICACION_FERIA(Clase.CONSULTA),
        COMO_LLEGAR_FERIA(Clase.CONSULTA),
        CONSULTAR_TIENDA(Clase.CONSULTA),
        VER_FERIAS(Clase.NAVEGACION),
        COMPARTIR_TIENDA(Clase.NAVEGACION),
        CAMBIAR_WHATSAPP(Clase.NAVEGACION),
        VER_MENSAJES(Clase.NAVEGACION),
        ABRIR_AJUSTES(Clase.NAVEGACION),
        ABRIR_AYUDA(Clase.AYUDA),
        SABER_SI_SE_GUARDO(Clase.AYUDA),
        ENSENAME(Clase.AYUDA),
        SALUDO(Clase.CONVERSACION),
        /** «No quiero registrar una venta»: se responde que no se hace nada. */
        RECHAZAR(Clase.CONVERSACION),
        /** «Quiero hacer otra cosa», «cambiemos de tema»: se deja lo que se estaba haciendo. */
        OTRA_COSA(Clase.CONVERSACION),
        AMBIGUA(Clase.CONVERSACION),
        DESCONOCIDA(Clase.CONVERSACION)
    }

    /** Lo que la persona dijo. null = no lo dijo (nunca se completa con suposiciones). */
    data class Datos(
        val nombre: String? = null,
        val descripcion: String? = null,
        val precio: Long? = null,
        /** true si el monto se dijo como total («en total», «por todos»): no es el precio de cada uno. */
        val precioEsTotal: Boolean = false,
        val cantidad: Int? = null,
        val producto: String? = null,
        /** Precios distintos oídos en la misma frase sin corregirse («un gorro a 5000 y una bufanda a 8000»). */
        val preciosOidos: List<Long> = emptyList()
    ) {
        val variosPrecios: Boolean get() = preciosOidos.size > 1
    }

    data class Resultado(
        val intencion: Intencion,
        val datos: Datos = Datos(),
        /** Para AMBIGUA: las lecturas posibles. Para ENSENAME: el tema. */
        val opciones: List<Intencion> = emptyList(),
        val tema: TemasAyuda.Tema? = null
    )

    private fun re(p: String) = Regex(p)

    /** Una palabra con o sin tilde («agrega» también calza «agrégame», «anadir» calza «añadir»). */
    private fun tolerante(palabra: String) = palabra.map { c ->
        when (c) { 'a' -> "[aá]"; 'e' -> "[eé]"; 'i' -> "[ií]"; 'o' -> "[oó]"; 'u' -> "[uúü]"; 'n' -> "[nñ]"; else -> c.toString() }
    }.joinToString("")

    private val VERBOS_CREAR = listOf(
        "agrega", "agregar", "agregue", "agrego", "anade", "anadir", "sube", "subir", "subo", "pon", "poner", "pongo",
        "publica", "publicar", "publico", "crea", "crear", "ingresa", "ingresar", "carga", "cargar"
    )
    /** Verbos de crear sobre el texto original (con tildes): «agrégame», «añade», «súbeme». */
    private val VERBOS_CREAR_ORIGINAL = VERBOS_CREAR.joinToString("|") { tolerante(it) }

    // ---------- Palabras clave (sobre texto normalizado: minúsculas, sin tildes) ----------
    private val PREGUNTA_COMO = re("\\b(como|ensename|ensenar|explicame|explica|que hago|ayudame a entender|no se como|donde (se )?(cambio|veo|pongo|agrego|borro|elimino))\\b")
    private val VERBO_CREAR = re("\\b(${VERBOS_CREAR.joinToString("|")}|agregame|subeme|ponme|nuevo producto|producto nuevo)\\b")
    private val OBJETO_CREAR = re("\\b(producto|productos|algo|cosa|cosas|articulo|articulos)\\b|\\ben mi tienda\\b|\\bnuevo\\b")
    private val FOTO = re("\\b(foto|fotos|fotografia|imagen|imagenes)\\b")
    private val VENDER = re("\\bvender\\b|\\bquiero vender\\b|\\bvendo\\b")
    private val VENDI = re("\\b(vendi|vendimos|se vendio|se vendieron|me compraron|hice una venta|registrar (una )?venta|anotar (una )?venta)\\b")
    // «cuanto e vendido»: el dictado a veces escribe «e» o «a» por «he».
    private val CONSULTA_VENTAS = re("\\b(cuanto (vendi|(he |e |a )?vendido|llevo vendido|llevo|gane|(he |e |a )?ganado)|mis ventas|ventas del mes|vendi este mes|cuanto vendimos)\\b")
    private val SE_ACABO = re("\\b(se (me )?acab(o|aron)|ya no (me )?(tengo|quedan|queda)|no me quedan|agotad[oa]s?|sin stock)\\b")
    private val ME_QUEDAN = re("\\b(me quedan|me queda|quedan|queda|ahora tengo|tengo solo|solo tengo)\\b")
    private val SUMAR = re("\\b(me llegaron|llegaron|me trajeron|hice|termine|sume|agregue) (\\d+)\\b")
    private val CAMBIO_PRECIO = re("\\b(cambiar|cambia|cambiale|cambio|subir|sube|subele|bajar|baja|bajale|nuevo|otro|actualizar)\\b.{0,12}\\bprecio\\b" +
        // «el precio del chaleco es (era, queda en) veinte mil»: dice el precio nuevo de un producto que ya existe.
        "|^el precio (de|del|de la|de los|de las|de mis|de mi) [a-z ]{2,40}? (es|era|sera|queda en|quedo en|ahora es|debe ser) ")
    private val OCULTAR = re("\\b(ocultar|oculta|esconder|esconde|que no se vea|no mostrar|dejar de mostrar|solo yo lo vea)\\b")
    private val BORRAR = re("\\b(borrar|borra|borrame|eliminar|elimina|eliminame)\\b")
    private val VER_PRODUCTOS = re("\\b(mis productos|ver (mis )?productos|muestrame (mis )?productos|ir a productos|mi catalogo|lo que vendo)\\b")
    private val PROXIMA_FERIA = re("\\b(proxima feria|(cuando|que dia|donde) (es|sera|queda) (mi|la) (proxima )?feria|que feria (tengo|viene))\\b")
    private val FERIAS = re("\\b(feria|ferias|participar|inscribirme|invitacion|invitaciones)\\b")
    private val COMPARTIR = re("\\b(compartir|comparte|qr|codigo qr|enlace|link)\\b")
    private val CONTACTO = re("\\b(whatsapp|wasap|guasap|telefono|numero|celular)\\b")
    private val CAMBIAR = re("\\b(cambiar|cambia|cambio|actualizar|poner otro|nuevo)\\b")
    private val MENSAJES = re("\\b(mensajes|me escribieron|consultas|me escribio|bandeja)\\b")
    private val GUARDO = re("\\b(se guardo|quedo guardado|se subio|llego al servidor|esta guardado|pendiente|sin senal)\\b")
    private val SALUDO = re("^(hola|buenas|buenos dias|buenas tardes|buenas noches|alo)\\b")
    private val GENERICOS = setOf("producto", "productos", "algo", "cosa", "cosas", "articulo", "articulos", "nuevo")
    /** Palabras que nunca son el nombre de un producto («quiero poner una foto» no crea el producto «Foto»). */
    private val NO_ES_NOMBRE = re("^(producto|productos|algo|cosa|cosas|foto|fotos|imagen|precio|venta|ventas|tienda|feria|ferias|luca|lucas|pesos)\\b")

    // Señales nuevas (consultas, ferias, tienda, edición y conversación).
    private val CONSULTA_VENTAS_PRODUCTO = re("\\bcuant[oa]s? ([a-z ]{2,30}?) (vendi|(he |e |a )?vendido|vendimos|se vendieron|llevo vendid[oa]s)\\b")
    private val CONSULTA_PRODUCTOS = re("\\b((que|cuales|cuantos) productos (tengo|hay|me quedan)|productos disponibles|que (tengo|me queda) disponible)\\b")
    private val CONSULTA_AGOTADOS = re("\\b((que|cuales) (productos )?(se (me )?(acabaron|agotaron|acabo)|estan agotados)|productos agotados|que se (me )?acabo)\\b")
    private val CONSULTA_INVITACIONES = re("\\b((tengo|hay) (alguna )?invitaci(on|ones)( pendientes?)?|invitaciones pendientes|me invitaron|me han invitado)\\b")
    private val UBICACION_FERIA = re("\\bdonde (queda|es|sera|se hace|esta)\\b.*\\bferia\\b|\\b(direccion|ubicacion|lugar) de (la|mi) (proxima )?feria\\b")
    private val COMO_LLEGAR = re("\\b(como (llego|llegar|voy|me voy)|mapa)\\b.*\\bferia\\b|\\bcomo llego\\b")
    private val PARTICIPAR = re("\\b(quiero participar|si participo|participo|voy a ir|confirmo (mi )?(participacion|asistencia)|acepto la invitacion|quiero ir a la feria)\\b")
    private val PARTICIPAR_GENERAL = re("\\b(en una feria|en alguna feria|en ferias)\\b")
    private val NO_PARTICIPAR = re("\\b(no podre (ir|asistir|participar)|no voy a (ir|poder ir|participar|asistir)|no participo|no puedo (ir|asistir|participar)|rechazo la invitacion|no voy a la feria)\\b")
    private val CONSULTA_TIENDA = re("\\b((datos|informacion|info) de (mi )?tienda|(cual es|cual) (el )?(link|enlace|nombre|numero|whatsapp) de mi tienda|como se ve mi tienda|que dice mi tienda)\\b")
    private val AJUSTES = re("\\b(ajustes|configuracion|mi cuenta|cerrar sesion|modo sencillo|tamano de (la )?letra)\\b")
    private val AYUDA = re("^(ayuda|necesito ayuda|ayudame|centro de ayuda)$|\\b(centro de ayuda|necesito ayuda|no entiendo nada|estoy perdida|no se usar)\\b")
    private val EDITAR = re("\\b(cambiar|cambia|cambiale|editar|edita|modificar|modifica|corregir|corrige|actualizar)\\b.{0,12}\\b(descripcion|nombre|foto|fotos|opciones|tallas|colores)\\b" +
        "|\\b(agregar|agregale|poner|ponle|subir|cambiar) (otra |una |la )?(foto|fotos|imagen) (al|a la|a los|a las|del|de la|de los|de las|de mis|de mi|a mis|a mi)\\b")
    private val MOSTRAR = re("\\b(volver a mostrar|vuelve a mostrar|mostrar (de nuevo|otra vez)|que se (vea|vean) (de nuevo|otra vez)?|publicar de nuevo|que lo vean todos|que los vean todos)\\b")
    private val DISPONIBLE_DE_NUEVO = re("\\b(volver a tener disponible|disponibles? (otra vez|de nuevo)|de nuevo disponibles?|volver a vender|vuelva a estar disponible|ya (lo |los |la |las )?tengo de nuevo)\\b")
    private val TENGO_DISPONIBLES = re("\\btengo (\\d+) [a-z ]{2,30}? ?disponibles?\\b|\\b(\\d+) disponibles?\\b")
    private val TENGO_MAS = re("\\b(tengo|hay) (\\d+) mas\\b|\\b(\\d+) mas\\b")
    private val RECHAZO = re("^(no quiero|no voy a|no necesito|ya no quiero|no deseo|no) (registrar|anotar|agregar|subir|vender|cambiar|ocultar|borrar|hacer|crear|publicar|participar)\\b")
    private val OTRA_COSA = re("\\b(quiero hacer otra cosa|otra cosa|cambiemos de tema|olvida eso|dejemos eso)\\b")

    /**
     * Una regla: si la señal aparece (y se cumple la condición), la intención suma [peso].
     * El peso también ordena: la señal más específica gana (por ejemplo «¿cuántos gorros vendí?»
     * es una consulta aunque contenga «vendí»).
     */
    private class Regla(val intencion: Intencion, val peso: Int, val senal: Regex, val condicion: (Datos, String) -> Boolean = { _, _ -> true })

    private val REGLAS: List<Regla> = listOf(
        Regla(Intencion.CONSULTAR_VENTAS_PRODUCTO, 30, CONSULTA_VENTAS_PRODUCTO) { _, t ->
            CONSULTA_VENTAS_PRODUCTO.find(t)?.groupValues?.get(1)?.let { it.trim() !in setOf("", "me", "he", "lo", "se") && !it.startsWith("este mes") } == true
        },
        Regla(Intencion.CONSULTAR_VENTAS_MES, 29, CONSULTA_VENTAS),
        Regla(Intencion.CONSULTAR_AGOTADOS, 28, CONSULTA_AGOTADOS) { _, t -> esPregunta(t) || t.startsWith("productos agotados") },
        Regla(Intencion.CONSULTAR_PRODUCTOS, 27, CONSULTA_PRODUCTOS),
        Regla(Intencion.CONSULTAR_INVITACIONES, 26, CONSULTA_INVITACIONES),
        Regla(Intencion.CONSULTAR_UBICACION_FERIA, 25, UBICACION_FERIA),
        Regla(Intencion.CONSULTAR_PROXIMA_FERIA, 24, PROXIMA_FERIA),
        Regla(Intencion.CONSULTAR_TIENDA, 23, CONSULTA_TIENDA),
        Regla(Intencion.SABER_SI_SE_GUARDO, 22, GUARDO),
        Regla(Intencion.NO_PARTICIPAR_FERIA, 21, NO_PARTICIPAR),
        Regla(Intencion.REGISTRAR_VENTA, 20, VENDI),
        Regla(Intencion.MARCAR_SE_ACABO, 19, SE_ACABO),
        Regla(Intencion.CAMBIAR_PRECIO, 18, CAMBIO_PRECIO),
        Regla(Intencion.EDITAR_PRODUCTO, 17, EDITAR),
        Regla(Intencion.OCULTAR_PRODUCTO, 16, OCULTAR),
        Regla(Intencion.MOSTRAR_PRODUCTO, 16, MOSTRAR),
        Regla(Intencion.BORRAR_PRODUCTO, 15, BORRAR),
        Regla(Intencion.DISPONIBLE_OTRA_VEZ, 15, DISPONIBLE_DE_NUEVO),
        Regla(Intencion.SUMAR_CANTIDAD, 14, SUMAR),
        Regla(Intencion.SUMAR_CANTIDAD, 14, TENGO_MAS) { _, t -> !VERBO_CREAR.containsMatchIn(t) },
        Regla(Intencion.PARTICIPAR_FERIA, 13, PARTICIPAR) { _, t -> !PARTICIPAR_GENERAL.containsMatchIn(t) },
        Regla(Intencion.CREAR_PRODUCTO, 12, VERBO_CREAR) { d, t -> OBJETO_CREAR.containsMatchIn(t) || d.nombre != null || d.precio != null },
        Regla(Intencion.AMBIGUA, 11, VENDER),
        Regla(Intencion.CAMBIAR_CANTIDAD, 10, ME_QUEDAN) { d, _ -> d.cantidad != null },
        Regla(Intencion.CAMBIAR_CANTIDAD, 10, TENGO_DISPONIBLES) { _, t -> !VERBO_CREAR.containsMatchIn(t) },
        Regla(Intencion.VER_PRODUCTOS, 9, VER_PRODUCTOS),
        Regla(Intencion.CAMBIAR_WHATSAPP, 8, CONTACTO) { _, t -> CAMBIAR.containsMatchIn(t) },
        Regla(Intencion.COMPARTIR_TIENDA, 7, COMPARTIR),
        Regla(Intencion.VER_MENSAJES, 6, MENSAJES),
        Regla(Intencion.ABRIR_AJUSTES, 5, AJUSTES),
        Regla(Intencion.VER_FERIAS, 4, FERIAS),
        Regla(Intencion.ABRIR_AYUDA, 3, AYUDA),
        Regla(Intencion.ENSENAME, 2, PREGUNTA_COMO),
        Regla(Intencion.SALUDO, 1, SALUDO),
    )

    /**
     * Puntajes de todas las intenciones que tienen alguna señal en la frase (para pruebas y para un
     * futuro motor que quiera combinar). Una pregunta resta a las acciones: «¿vendí tres gorros?» no
     * es pedir que se anote una venta.
     */
    fun puntuar(frase: String): List<Pair<Intencion, Int>> {
        val t = NumerosEs.normalizar(frase)
        if (t.isBlank()) return emptyList()
        val d = extraerDatos(frase)
        val td = NumerosEs.aDigitos(frase)
        val pregunta = esPregunta(frase)
        val puntos = mutableMapOf<Intencion, Int>()
        for (r in REGLAS) {
            val texto = if (r.senal === SUMAR || r.senal === TENGO_MAS || r.senal === TENGO_DISPONIBLES) td else t
            if (!r.senal.containsMatchIn(texto) || !r.condicion(d, texto)) continue
            var p = r.peso
            if (pregunta && r.intencion.clase == Clase.ACCION && r.intencion != Intencion.PARTICIPAR_FERIA) p -= 15
            if (pregunta && r.intencion.clase == Clase.CONSULTA) p += 5
            puntos[r.intencion] = maxOf(puntos[r.intencion] ?: 0, p)
        }
        return puntos.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** Interpreta una frase completa. */
    fun interpretar(frase: String): Resultado {
        val t = NumerosEs.normalizar(frase)
        if (t.isBlank()) return Resultado(Intencion.DESCONOCIDA)
        val datos = extraerDatos(frase)

        // 1. Lo que nunca debe confundirse con una acción: «no quiero registrar…», «otra cosa», «¿cómo llego?».
        if (NO_PARTICIPAR.containsMatchIn(t)) return Resultado(Intencion.NO_PARTICIPAR_FERIA, datos)
        if (RECHAZO.containsMatchIn(t)) return Resultado(Intencion.RECHAZAR, datos)
        if (OTRA_COSA.containsMatchIn(t)) return Resultado(Intencion.OTRA_COSA, datos)
        if (COMO_LLEGAR.containsMatchIn(t)) return Resultado(Intencion.COMO_LLEGAR_FERIA, datos)
        // 2. «¿Cómo…?», «enséñame…» con un tema conocido: aprender (Enséñame), aunque nombre una acción.
        if (PREGUNTA_COMO.containsMatchIn(t) && !CONSULTA_VENTAS_PRODUCTO.containsMatchIn(t)) {
            TemasAyuda.temaDe(t)?.let { return Resultado(Intencion.ENSENAME, datos, tema = it) }
        }
        // 3. «Quiero poner una foto» (sin decir a qué producto): es aprender a poner fotos, no un producto «Foto».
        if (FOTO.containsMatchIn(t) && VERBO_CREAR.containsMatchIn(t) && datos.nombre == null && !EDITAR.containsMatchIn(t)) {
            return Resultado(Intencion.ENSENAME, datos, tema = TemasAyuda.Tema.AGREGAR_FOTO)
        }
        // 4. El resto, por puntaje.
        val ganadora = puntuar(frase).firstOrNull()?.first ?: return Resultado(Intencion.DESCONOCIDA, datos)
        val producto = datos.producto ?: productoMencionado(t)
        return when (ganadora) {
            Intencion.AMBIGUA -> Resultado(Intencion.AMBIGUA, datos.copy(producto = productoMencionado(t)), listOf(Intencion.CREAR_PRODUCTO, Intencion.REGISTRAR_VENTA))
            Intencion.SUMAR_CANTIDAD -> {
                val td = NumerosEs.aDigitos(frase)
                val n = SUMAR.find(td)?.groupValues?.get(2) ?: TENGO_MAS.find(td)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() && it.all(Char::isDigit) }
                Resultado(Intencion.SUMAR_CANTIDAD, datos.copy(cantidad = n?.toIntOrNull(), producto = producto))
            }
            Intencion.CAMBIAR_CANTIDAD -> {
                val td = NumerosEs.aDigitos(frase)
                val n = datos.cantidad ?: TENGO_DISPONIBLES.find(td)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
                Resultado(Intencion.CAMBIAR_CANTIDAD, datos.copy(cantidad = n, producto = producto))
            }
            Intencion.CONSULTAR_VENTAS_PRODUCTO -> Resultado(ganadora, datos.copy(producto = CONSULTA_VENTAS_PRODUCTO.find(t)?.groupValues?.get(1)?.trim()))
            Intencion.REGISTRAR_VENTA, Intencion.MARCAR_SE_ACABO, Intencion.CAMBIAR_PRECIO, Intencion.OCULTAR_PRODUCTO,
            Intencion.MOSTRAR_PRODUCTO, Intencion.BORRAR_PRODUCTO, Intencion.DISPONIBLE_OTRA_VEZ, Intencion.EDITAR_PRODUCTO ->
                Resultado(ganadora, datos.copy(producto = producto))
            Intencion.SALUDO -> Resultado(Intencion.SALUDO)
            else -> Resultado(ganadora, datos)
        }
    }

    /** ¿La frase empieza pidiendo crear algo? («quiero agregar…», «sube…», «y también agrega…»). */
    fun empiezaConCrear(frase: String): Boolean =
        Regex("^(?:(?:oye|ya|y|ahora|tambien|ademas|quiero|necesito|me gustaria|puedes) )*(?:${VERBOS_CREAR.joinToString("|")}|agregame|subeme|ponme)\\b")
            .containsMatchIn(NumerosEs.normalizar(frase))

    // ---------- Extracción de datos ----------

    private val APROX = "(?:como |unos |unas |mas o menos |alrededor de |cerca de |aproximadamente |aprox )?"
    private val CANTIDAD_PATRONES = listOf(
        re("\\btengo $APROX(\\d+|un|una|uno)\\b"),
        re("\\b(\\d+) (disponibles?|unidades?|en stock|para vender)\\b"),
        re("\\b(?:me )?quedan? $APROX(\\d+|un|una|uno)\\b"),
        re("\\bhay $APROX(\\d+)\\b"),
        re("\\bson $APROX(\\d+)\\b(?! (pesos|lucas?|mil))"),
        re("\\bcantidad (?:es |de )?(\\d+)\\b")
    )
    private val UNIDAD_MONTO = "(pesos|peso|lucas|luca|luquitas|luquita|gambas|gamba)"
    private val PRECIO_PATRONES = listOf(
        re("\\b(?:cuesta|cuestan|vale|valen|precio(?: es| de)?|cobro|cobrar|cobra|lo vendo a|los vendo a|las vendo a|sale|salen|a|en|por)\\s+\\$?(\\d+)(?:\\s+$UNIDAD_MONTO)?"),
        re("\\$\\s?(\\d+)(?:\\s+$UNIDAD_MONTO)?"),
        re("\\b(\\d+)\\s+$UNIDAD_MONTO\\b")
    )
    private val TOTAL = re("\\b(en total|por todos|por todas|por todo|todo junto|el total|los \\d+ en|las \\d+ en)\\b")

    /** Palabras de números (para cortar el nombre antes de «a quince mil», «a ciento veinte lucas»). */
    private val NUM_PAL = "(?:un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|" +
        "dieci\\p{L}+|veinte|veinti\\p{L}+|treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa|cien|ciento|" +
        "\\p{L}*cient[oa]s|quinient[oa]s|mil|y|media)"

    /**
     * Autocorrección dentro de una frase: «no» seguido de un dato, «perdón», «mejor dicho», «o sea»…
     * Solo separa: cada parte se lee igual que una frase, y lo último dicho reemplaza a lo anterior.
     */
    private val MARCA_CORRECCION = Regex(
        "(?:^|[\\s,;.]+)(?:no(?=[\\s,]+(?:a|en|por|son|es|eran|era|tengo|tiene|quedan|me|cuesta|vale|\\$|\\d|$NUM_PAL)(?:\\s|$))" +
            "|perd[oó]n|mejor dicho|o sea|en realidad|me equivoqu[eé])[\\s,;.]*"
    )

    /** Saca los datos de la frase. Un número usado como precio no se vuelve a usar como cantidad. */
    fun extraerDatos(frase: String): Datos {
        val partes = frase.lowercase().split(MARCA_CORRECCION).filter { it.isNotBlank() }
        if (partes.size <= 1) return extraerSimple(frase)
        // La primera parte trae el nombre completo; las siguientes corrigen lo que nombren.
        var d = extraerSimple(partes.first())
        var ultimo: Char? = when { d.precio != null -> 'p'; d.cantidad != null -> 'c'; else -> null }
        for (parte in partes.drop(1)) {
            val s = extraerSimple(parte)
            val suelto = numeroSuelto(parte)
            d = d.copy(
                nombre = s.nombre ?: d.nombre,
                descripcion = if (s.nombre != null) s.descripcion else d.descripcion,
                precio = s.precio ?: d.precio,
                precioEsTotal = if (s.precio != null) s.precioEsTotal else d.precioEsTotal,
                cantidad = s.cantidad ?: d.cantidad,
                preciosOidos = s.preciosOidos
            )
            when {
                s.precio != null -> ultimo = 'p'
                s.cantidad != null -> ultimo = 'c'
                suelto != null && ultimo == 'c' -> d = d.copy(cantidad = suelto.toInt())
                // «a 35 mil, no, 40»: ¿$40 o $40.000? No se adivina: se pregunta.
                // «quince lucas, no, dieciocho»: si lo anterior estaba en miles y el número nuevo calza en esa
                // escala (entre un tercio y el triple), se entiende en miles. Si no, no se adivina: se pregunta.
                suelto != null && ultimo == 'p' -> {
                    val previo = d.precio
                    d = when {
                        suelto >= 100 -> d.copy(precio = suelto)
                        previo != null && previo >= 1000 && previo % 1000 == 0L && suelto * 1000 in (previo / 3)..(previo * 3) -> d.copy(precio = suelto * 1000)
                        else -> d.copy(preciosOidos = listOfNotNull(previo, suelto * 1000).distinct())
                    }
                }
            }
        }
        return d
    }

    /** «40», «a 40», «son 4»: un número solo, sin unidad (para autocorrecciones). */
    private fun numeroSuelto(parte: String): Long? =
        Regex("^(?:a |en |son |es |eran |era |tengo |quedan |me quedan )?(\\d+)$").find(NumerosEs.aDigitos(parte).trim())?.groupValues?.get(1)?.toLongOrNull()

    private fun extraerSimple(frase: String): Datos {
        val d = NumerosEs.aDigitos(frase)
            // «luca y media», «2 lucas y media»: 1.500 y 2.500.
            .replace(Regex("\\b(?:(\\d+) )?(lucas?|luquitas?) y media\\b")) { m -> "${(m.groupValues[1].toLongOrNull() ?: 1) * 1000 + 500} pesos" }
            // «a luca» (sin número): mil pesos.
            .replace(Regex("(?<!\\d )\\b(luca|luquita)\\b"), "1 luca")
        var precio: Long? = null
        val candidatos = mutableListOf<Pair<IntRange, Long>>()
        for (p in PRECIO_PATRONES) {
            for (m in p.findAll(d)) {
                val grupoNumero = m.groups.drop(1).firstOrNull { it != null && it.value.all(Char::isDigit) } ?: continue
                val unidad = m.groups.drop(1).firstOrNull { it != null && !it.value.all(Char::isDigit) }?.value
                val numero = grupoNumero.value.toLongOrNull() ?: continue
                // «a 5» o «en 2» sin unidad ni miles no es un precio creíble («a 2 cuadras»): se ignora.
                val explicito = unidad != null || m.value.contains("$") || Regex("\\b(cuesta|cuestan|vale|valen|precio|cobr|vendo|sale)").containsMatchIn(m.value)
                if (!explicito && numero < 100) continue
                val monto = NumerosEs.montoConUnidad(numero, unidad)
                if (candidatos.none { it.first.first == grupoNumero.range.first }) candidatos += grupoNumero.range to monto
                if (precio == null) precio = monto
            }
        }
        val posicionesPrecio = candidatos.map { it.first.first }.toSet()
        var cantidad: Int? = null
        for (p in CANTIDAD_PATRONES) {
            val m = p.findAll(d).firstOrNull { mm -> mm.groups[1]?.range?.first !in posicionesPrecio } ?: continue
            val v = m.groupValues[1]
            cantidad = when (v) { "un", "una", "uno" -> 1; else -> v.toIntOrNull() }
            if (cantidad != null) break
        }
        val oidos = candidatos.sortedBy { it.first.first }.map { it.second }.distinct()
        val (nombre, descripcion) = nombreYDescripcion(frase)
        return Datos(
            nombre = nombre, descripcion = descripcion, precio = precio,
            precioEsTotal = precio != null && TOTAL.containsMatchIn(d), cantidad = cantidad,
            preciosOidos = if (oidos.size > 1) oidos else emptyList()
        )
    }

    // Lo que corta la frase del producto: «, », « que cuesta », « a 15000 », « a quince mil », « y tengo »…
    private val CORTE = "(?=,|\\.|;|:| que | y que | cuesta| cuestan| vale| valen| a \\$?\\d| en \\$?\\d| por \\$?\\d" +
        "| (?:a|en|por) (?:$NUM_PAL )*(?:lucas?|luquitas?|pesos|mil|gambas?)(?:\\s|$)" +
        "| y tengo| tengo| y me quedan| me quedan| precio| de precio| y son| son \\d| quiero| lo quiero| para vender| para subir" +
        "| cada un[oa]| y (?:un|una|unos|unas) |$)"
    private val ART = "(?:un|una|unos|unas|el|la|los|las|mis|mi)"
    private val PATRONES_PRODUCTO = listOf(
        Regex("(?:se llama|llamad[oa]|el nombre es|nombre:?|que se llame|producto:) (.+?)$CORTE"),
        Regex("\\b(?:es|son) (?:un|una|unos|unas) (.+?)$CORTE"),
        Regex("\\b(?:$VERBOS_CREAR_ORIGINAL)(?:me|le)? (?:$ART )?(?:(?:\\d+|$NUM_PAL) )?(.+?)$CORTE")
    )
    // Si no hubo verbo: «tengo 4 chalecos de lana para subir…», «a 15 lucas un gorro de lana quiero subir».
    private val PATRONES_PRODUCTO_RESPALDO = listOf(
        Regex("\\b(?:tengo|hay) (?:\\d+|$NUM_PAL) (.+?)$CORTE"),
        Regex("(?:^|\\s)(?:un|una|unos|unas) (.+?)$CORTE")
    )
    // Lo que describe pero no nombra: «chaleco de lana hecho a mano» → nombre «Chaleco de lana».
    private val COLA_DESCRIPTIVA = Regex(" (hech[oa]s? a mano|tejid[oa]s? a mano|artesanal(es)?|caser[oa]s?|para |con |ideal |de color |de tamaño |en tamaño )")

    /** Nombre y descripción desde la frase original (conserva tildes). Descripción solo si dijo algo más que el nombre. */
    fun nombreYDescripcion(frase: String): Pair<String?, String?> {
        val baja = frase.lowercase().replace(Regex("\\s+"), " ").trim()
        for (p in PATRONES_PRODUCTO + PATRONES_PRODUCTO_RESPALDO) {
            for (m in p.findAll(baja)) {
                val frag = m.groupValues[1].trim().trim(',', '.', ' ', ':')
                val sinArticulo = frag.replace(Regex("^((otro|otra|nuevo|nueva) )?(producto (nuevo )?(que es )?(de )?|un |una |unos |unas )?"), "").trim()
                val norm = NumerosEs.normalizar(sinArticulo)
                if (sinArticulo.isEmpty() || norm in GENERICOS) continue
                if (NO_ES_NOMBRE.containsMatchIn(norm)) continue
                // «subir a 20 mil»: lo que viene después del verbo es el precio, no un nombre.
                if (norm.firstOrNull()?.isDigit() == true || Regex("^(a|en|por|de|para|que|y|cada) ").containsMatchIn("$norm ")) continue
                val cola = COLA_DESCRIPTIVA.find(" $sinArticulo")
                val nombre = if (cola != null && cola.range.first > 0) sinArticulo.substring(0, cola.range.first - 1).trim() else sinArticulo
                if (nombre.isBlank()) continue
                val descripcion = if (nombre.length < sinArticulo.length) capitalizar(sinArticulo) else null
                return capitalizar(nombre.split(" ").take(8).joinToString(" ")) to descripcion
            }
        }
        return null to null
    }

    /** Lo que va antes del precio y la cantidad: «gorro de lana a 5000, tengo 3» → «gorro de lana». */
    fun antesDeLosDatos(frase: String): String {
        val original = frase.trim().replace(Regex("\\s+"), " ")
        val m = Regex("^(.+?)$CORTE").find(original.lowercase()) ?: return original
        return original.substring(0, m.groupValues[1].length).trim().trim(',', '.', ' ')
    }

    /** Palabra del producto en frases de venta o cambio: «se me acabaron los chalecos» → «chalecos». */
    fun productoMencionado(t: String): String? {
        val tokens = NumerosEs.aDigitos(t).split(" ").filter { it.isNotEmpty() }
        val determinantes = setOf("los", "las", "el", "la", "mis", "unos", "unas", "un", "una", "de", "del", "al", "esos", "esas", "estos", "estas")
        val noProducto = GENERICOS + determinantes + setOf("precio", "tienda", "mes", "feria", "venta", "ventas", "pesos", "lucas",
            "luca", "cada", "total", "mi", "tu", "su", "este", "esta", "nada", "todo", "todos", "stock", "numero", "cantidad",
            "algo", "cosas", "mas", "hoy", "ahora", "aqui", "por", "en", "a", "que", "otra", "otro", "foto", "fotos", "imagen",
            "descripcion", "nombre", "opciones", "disponible", "disponibles", "nuevo", "nueva", "tengo", "hay", "y")
        // Dónde termina el nombre: «los chalecos de lana a 15 mil» → «chalecos de lana».
        val fin = setOf("a", "en", "por", "que", "y", "con", "cada", "mas", "al", "para", "se", "me", "ya", "no", "pero", "o",
            "fue", "eran", "era", "es", "sera", "queda", "quedo", "son", "esta", "estan", "disponible", "disponibles", "total", "pesos", "lucas", "luca",
            "hoy", "ayer", "recien", "ahora", "temprano", "tarde", "anoche", "esta", "semana", "efectivo", "transferencia", "debito")
        // «quiero vender gorros», «borra el producto gorro»: el producto también puede venir justo después del verbo.
        val verbos = setOf("vender", "vendo", "vendi", "subir", "agregar", "agrega", "publicar", "ocultar", "esconder", "acabaron",
            "acabo", "borrar", "borra", "eliminar", "elimina", "producto", "productos", "llegaron", "trajeron", "mostrar")
        for (i in 0 until tokens.size - 1) {
            val det = tokens[i]
            val palabra = tokens[i + 1]
            val esDeterminante = det in determinantes || det in verbos || det.all(Char::isDigit)
            if (!esDeterminante || palabra in noProducto || palabra.length < 3 || palabra.any(Char::isDigit)) continue
            // Se conservan hasta tres palabras más («gorro azul», «chalecos de lana gris»), sin números ni conectores.
            val partes = mutableListOf(palabra)
            var j = i + 2
            while (j < tokens.size && partes.size < 4) {
                val w = tokens[j]
                if (w in fin || w.any(Char::isDigit) || w in GENERICOS) break
                if (w == "de" || w == "del") {
                    val sig = tokens.getOrNull(j + 1)
                    if (sig == null || sig in fin || sig in noProducto || sig.any(Char::isDigit)) break
                }
                partes += w; j++
            }
            return partes.joinToString(" ")
        }
        return null
    }

    fun capitalizar(s: String): String = s.trim().replaceFirstChar { it.uppercase() }

    // ---------- Respuestas cortas dentro de una conversación ----------
    private val SI = re("^(si|sip|ya|ok|okey|dale|listo|bueno|esta bien|confirmo|correcto|continuar|continua|sigue|seguir|guardar|guardalo|perfecto|eso)\\b")
    private val NEGACION = re("\\b(no|nunca|tampoco|nada)\\b")
    private val NO_CANCELAR = re(
        "^(?:(?:no|oye|ya|mejor|bueno|ah|ay) )?(?:cancelar|cancela|cancelalo|olvidalo|olvida|dejalo|dejemoslo|mejor no|no quiero|" +
            "ya no quiero|no importa|no gracias|ninguno|ninguna|borralo|borra todo|empezar de nuevo|no lo quiero)\\b" +
            "|^(?:para|basta|salir|chao|adios|detente|nada)$"
    )
    private val DESPUES = re("^(despues|no se|nose|mas tarde|luego|no lo se|no tengo idea|ninguno por ahora)\\b")

    /** «sí», «ya», «listo»… pero nunca si la frase niega algo («ya no quiero» no es un sí). */
    fun esAfirmacion(t: String): Boolean {
        val n = NumerosEs.normalizar(t)
        return SI.containsMatchIn(n) && !NEGACION.containsMatchIn(n)
    }
    fun esCancelacion(t: String) = NO_CANCELAR.containsMatchIn(NumerosEs.normalizar(t))
    fun esDespues(t: String) = DESPUES.containsMatchIn(NumerosEs.normalizar(t))

    /** ¿Es una pregunta? («¿…?» o empieza con qué, cuánto, cuándo, cómo, dónde…). */
    fun esPregunta(frase: String): Boolean =
        frase.contains('?') || frase.contains('¿') ||
            Regex("^(que|cual|cuales|cuanto|cuanta|cuantos|cuantas|cuando|como|donde|por que|para que|quien)\\b").containsMatchIn(NumerosEs.normalizar(frase))

    /**
     * ¿Sirve esta respuesta como nombre de un producto? No si es una pregunta, un «no sé», una
     * negación o solo un número: en esos casos el asistente vuelve a preguntar en vez de anotar
     * algo que la persona no quiso decir como nombre.
     */
    fun pareceNombre(frase: String): Boolean {
        val t = NumerosEs.normalizar(frase)
        if (t.isBlank() || esPregunta(frase) || esDespues(frase) || esCancelacion(frase)) return false
        if (Regex("^no\\b").containsMatchIn(t)) return false
        if (cantidadSuelta(frase) != null || montoSuelto(frase) != null) return false
        return !NO_ES_NOMBRE.containsMatchIn(t.replace(Regex("^(un|una|unos|unas|el|la|los|las) "), ""))
    }

    /** Corrección dentro del producto que se está preparando: ¿qué campo quiere cambiar? */
    enum class Campo { NOMBRE, DESCRIPCION, PRECIO, CANTIDAD, FOTO }

    data class Correccion(val campo: Campo, val valorTexto: String? = null, val valorNumero: Long? = null)

    fun correccion(frase: String): Correccion? {
        val t = NumerosEs.normalizar(frase)
        val baja = frase.lowercase().trim()
        if (Regex("\\b(foto|fotos|fotografia|imagen|camara)\\b").containsMatchIn(t)) return Correccion(Campo.FOTO)
        Regex("(?:cambia(?:r)? el nombre a|cambialo a|el nombre es|se llama|que se llame|ponle de nombre|nombre:?) (.+)").find(baja)?.let {
            val v = it.groupValues[1].trim().trim('.', ',', ' ')
            if (v.isNotBlank()) return Correccion(Campo.NOMBRE, capitalizar(v.replace(Regex("^(un |una |unos |unas |el |la )"), "")))
        }
        // «no, es una bufanda»: corrige el nombre.
        Regex("^(?:no[,.]?\\s+)?(?:es|era|son|eran) (?:un|una|unos|unas) (.+)").find(baja)?.let {
            val v = it.groupValues[1].trim().trim('.', ',', ' ')
            if (v.isNotBlank() && pareceNombre(v)) return Correccion(Campo.NOMBRE, capitalizar(v))
        }
        Regex("(?:la descripcion es|la descripción es|descripcion:?|descripción:?|describelo como|descríbelo como) (.+)").find(baja)?.let {
            val v = it.groupValues[1].trim().trim('.', ',', ' ')
            if (v.isNotBlank()) return Correccion(Campo.DESCRIPCION, capitalizar(v))
        }
        val datos = extraerDatos(frase)
        if (datos.precio != null && !datos.variosPrecios &&
            Regex("\\b(precio|cuesta|vale|cobr|pesos|lucas?|luca|luquitas?|mil|\\$)").containsMatchIn(NumerosEs.aDigitos(frase) + " " + t)) {
            return Correccion(Campo.PRECIO, valorNumero = datos.precio)
        }
        if (datos.cantidad != null) return Correccion(Campo.CANTIDAD, valorNumero = datos.cantidad.toLong())
        return null
    }

    /** «cambia el precio», «el nombre está mal»: nombra un dato sin decir el valor nuevo. */
    fun campoSinValor(frase: String): Campo? {
        val t = NumerosEs.normalizar(frase)
        return when {
            Regex("\\bprecio\\b").containsMatchIn(t) -> Campo.PRECIO
            Regex("\\bnombre\\b").containsMatchIn(t) -> Campo.NOMBRE
            Regex("\\b(cantidad|cuantos tengo|cuantos hay|cuantos son)\\b").containsMatchIn(t) -> Campo.CANTIDAD
            Regex("\\bdescripcion\\b").containsMatchIn(t) -> Campo.DESCRIPCION
            else -> null
        }
    }

    /**
     * «no, quince mil», «perdón, cuatro»: un valor solo, sin decir de qué campo es.
     * Devuelve el número y si se dijo como monto (con «mil», «lucas», «pesos», «$» o desde mil).
     */
    fun valorSuelto(frase: String): Pair<Long, Boolean>? {
        val t = NumerosEs.normalizar(frase).replace(Regex("^(?:(?:no|perdon|mejor|bueno|ya|eran|era|son|es) )+"), "")
        val d = NumerosEs.aDigitos(t).trim()
            .replace(Regex("\\b(?:(\\d+) )?(lucas?|luquitas?) y media\\b")) { m -> "${(m.groupValues[1].toLongOrNull() ?: 1) * 1000 + 500} pesos" }
        val m = Regex("^\\$?(\\d+)(?: $UNIDAD_MONTO)?$").find(d) ?: return null
        val n = m.groupValues[1].toLongOrNull() ?: return null
        val unidad = m.groups[2]?.value
        val esMonto = unidad != null || Regex("\\b(mil|\\$)").containsMatchIn(t) || n >= 1000
        return NumerosEs.montoConUnidad(n, unidad) to esMonto
    }

    /** Respuesta suelta a «¿Cuánto cuesta?»: «doce mil», «12000», «veinte lucas». */
    fun montoSuelto(frase: String): Long? {
        extraerDatos(frase).let { if (it.precio != null && !it.variosPrecios) return it.precio }
        val d = NumerosEs.aDigitos(frase)
        val m = Regex("^(?:son |es |cuesta |vale )?\\$?(\\d+)(?: $UNIDAD_MONTO)?$").find(d.trim()) ?: return null
        val unidad = m.groups[2]?.value
        return NumerosEs.montoConUnidad(m.groupValues[1].toLong(), unidad)
    }

    /** Respuesta suelta a «¿Cuántos tienes?»: «cuatro», «4», «tengo cinco», «unos 10 más o menos», «una docena». */
    fun cantidadSuelta(frase: String): Int? {
        extraerDatos(frase).cantidad?.let { return it }
        val d = NumerosEs.aDigitos(frase).trim()
            .replace(Regex("^(?:como|unos|unas|mas o menos|alrededor de|cerca de|aproximadamente|aprox) "), "")
            .replace(Regex(" (?:mas o menos|aproximadamente|aprox|o algo asi)$"), "")
        Regex("^(?:son |tengo |hay )?(\\d+)(?: unidades?| disponibles?)?$").find(d)?.let { return it.groupValues[1].toIntOrNull() }
        return when (d) { "un", "una", "uno", "solo uno", "solo una" -> 1; else -> null }
    }

    /** Respuesta suelta a «¿Cómo se llama?»: quita «se llama», «es un»… */
    fun nombreSuelto(frase: String): Pair<String, String?>? {
        val (n, desc) = nombreYDescripcion(frase)
        if (n != null) return n to desc
        val limpio = frase.trim().trim('.', ',', ' ').replace(Regex("^(?i)(se llama|el nombre es|es un|es una|son unos|son unas|unos|unas|un|una)\\s+"), "")
        if (limpio.isBlank()) return null
        val cola = COLA_DESCRIPTIVA.find(" " + limpio.lowercase())
        val nombre = if (cola != null && cola.range.first > 0) limpio.substring(0, cola.range.first - 1) else limpio
        return capitalizar(nombre) to (if (nombre.length < limpio.length) capitalizar(limpio) else null)
    }
}
