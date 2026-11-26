package com.example.trabajodea10.data.asistente

/**
 * «Enséñame»: cómo se hace cada tarea en Alerce, con los mismos nombres que tienen los botones
 * de la app. Cada tema dice adónde llevar a la persona y si existe la guía interactiva.
 * Textos cortos, en español de Chile, pensados también para leerse en voz alta.
 */
object TemasAyuda {

    enum class Destino { MIS_PRODUCTOS, NUEVO_PRODUCTO, VENTA, FERIAS, TIENDA, COMPARTIR, MENSAJES, HISTORIAL, INICIO, AJUSTES, AYUDA,
        // Asistente general (2.0)
        VITRINA, MAPA_FERIAS, AYUDA_PUBLICA, PERFIL, ADMIN_INICIO, ADMIN_CUENTAS, ADMIN_FERIAS, ADMIN_GESTION,
        ADMIN_ASOCIACIONES, ADMIN_TIPOS, ADMIN_EQUIPO, ADMIN_BITACORA }

    enum class Tema(
        val titulo: String,
        val pasos: List<String>,
        val destino: Destino,
        val etiquetaDestino: String,
        /** true: además se puede seguir «Guíame paso a paso» dentro del formulario. */
        val conGuia: Boolean = false,
        /** true: el asistente también puede ayudar a hacerlo (preparar los datos). */
        val puedoAyudar: Boolean = false
    ) {
        AGREGAR_PRODUCTO(
            "Agregar un producto",
            listOf(
                "Ve a «Mis productos» y toca «Añadir producto».",
                "Primero la foto: «Tomar foto» o «Elegir de la galería». Si no tienes una, puedes seguir sin foto.",
                "Escribe el nombre y el precio. Puedes dictarlos con el micrófono.",
                "Si viene en tallas o colores, agrégalos. Si no, toca «Siguiente».",
                "Di cuántos tienes y elige «Solo yo lo veo» o «Lo ven todos».",
                "Toca «Guardar en Mis productos». Antes de eso no se guarda nada."
            ),
            Destino.NUEVO_PRODUCTO, "Abrir «Añadir producto»", conGuia = true, puedoAyudar = true
        ),
        AGREGAR_FOTO(
            "Agregar una foto",
            listOf(
                "En el primer paso de «Añadir producto» toca «Tomar foto» o «Elegir de la galería».",
                "Busca un lugar con buena luz y pon el producto al centro, sobre un fondo liso.",
                "Si no diste permiso a la cámara, usa «Elegir de la galería».",
                "Para cambiar la foto de un producto que ya tienes, ábrelo desde «Mis productos»."
            ),
            Destino.NUEVO_PRODUCTO, "Abrir «Añadir producto»", conGuia = true
        ),
        REGISTRAR_VENTA(
            "Registrar una venta",
            listOf(
                "Toca «Venta» en la barra de abajo.",
                "Toca el producto que vendiste, elige la cantidad (y la opción, si tiene) y toca «Agregar».",
                "Revisa las cantidades y toca «Continuar al pago».",
                "Elige cómo te pagaron y toca «Confirmar y generar PDF». Las cantidades se descuentan solas."
            ),
            Destino.VENTA, "Ir a «Venta»"
        ),
        CAMBIAR_PRECIO(
            "Cambiar el precio de un producto",
            listOf(
                "Ve a «Mis productos» y toca el producto.",
                "Toca «Siguiente» hasta el paso «Datos» y cambia el «Precio».",
                "Sigue hasta el final y toca «Guardar en Mis productos»."
            ),
            Destino.MIS_PRODUCTOS, "Ir a «Mis productos»"
        ),
        CAMBIAR_CANTIDAD(
            "Cambiar cuántos tengo o marcar «Se acabó»",
            listOf(
                "Ve a «Mis productos».",
                "En el producto, usa «Cuántos tengo» con los botones menos y más.",
                "Si se terminó, toca «Se acabó». Si fue un error, toca «Deshacer».",
                "Debajo de cada producto verás si quedó «Guardado» o «Pendiente»."
            ),
            Destino.MIS_PRODUCTOS, "Ir a «Mis productos»"
        ),
        OCULTAR_PRODUCTO(
            "Ocultar o mostrar un producto",
            listOf(
                "Ve a «Mis productos».",
                "En el producto, usa el interruptor «Lo ven todos» / «Solo yo lo veo».",
                "«Solo yo lo veo» lo saca de tu tienda sin borrarlo."
            ),
            Destino.MIS_PRODUCTOS, "Ir a «Mis productos»"
        ),
        COMPARTIR_TIENDA(
            "Compartir tu tienda",
            listOf(
                "Abre el código QR de tu tienda.",
                "Toca «Compartir imagen del QR» para enviarlo por WhatsApp, o «Guardar en el celular» para imprimirlo.",
                "Quien lo escanee ve tus productos y tu contacto."
            ),
            Destino.COMPARTIR, "Abrir el QR de mi tienda"
        ),
        PARTICIPAR_FERIA(
            "Participar en una feria",
            listOf(
                "Toca «Ferias» en la barra de abajo.",
                "Las ferias llegan por invitación de la administración.",
                "En cada invitación toca «Participo» o «No participo»."
            ),
            Destino.FERIAS, "Ir a «Ferias»"
        ),
        CAMBIAR_WHATSAPP(
            "Cambiar tu número de WhatsApp",
            listOf(
                "Toca «Tienda» en la barra de abajo.",
                "Busca «WhatsApp de tu tienda» y escribe tu número, por ejemplo +56 9 1234 5678.",
                "Toca «Guardar cambios»."
            ),
            Destino.TIENDA, "Ir a «Tienda»"
        ),
        VER_MENSAJES(
            "Ver tus mensajes",
            listOf(
                "Abre «Mensajes recibidos».",
                "Ahí están los mensajes que te enviaron desde tu tienda."
            ),
            Destino.MENSAJES, "Abrir «Mensajes recibidos»"
        ),
        BORRAR_PRODUCTO(
            "Borrar un producto",
            listOf(
                "Ve a «Mis productos» y toca el producto.",
                "Arriba a la derecha toca «Más acciones» (los tres puntos).",
                "Elige «Borrar producto» y confirma. Esto no se puede deshacer.",
                "Si solo quieres que no se vea, mejor usa «Solo yo lo veo»: así no se borra."
            ),
            Destino.MIS_PRODUCTOS, "Ir a «Mis productos»"
        ),
        TEXTO_Y_VOZ(
            "Agrandar la letra o escuchar en voz alta",
            listOf(
                "En «Inicio», toca el engranaje de arriba a la derecha («Ajustes y mi cuenta»).",
                "En «Accesibilidad», usa «Tamaño del texto» para agrandar la letra.",
                "En «Lectura en voz alta» puedes hacer que se lea solo y elegir la velocidad.",
                "En la guía y en cada respuesta del asistente, toca «🔊 Escuchar» para oírla otra vez."
            ),
            Destino.AJUSTES, "Abrir «Ajustes»"
        ),
        SABER_SI_SE_GUARDO(
            "Saber si un producto se guardó",
            listOf(
                "En «Mis productos», debajo de cada producto dice cómo quedó.",
                "«Guardado»: ya está en el servidor y tus clientas lo ven.",
                "«Pendiente»: no había señal; quedó en tu celular y se sube solo cuando vuelva.",
                "«Subiendo…»: se está enviando en este momento.",
                "Con las ventas pasa lo mismo: en «Historial de ventas» ves cuántos cambios faltan por subir. Se suben solos cuando hay conexión."
            ),
            Destino.MIS_PRODUCTOS, "Ir a «Mis productos»"
        ),
        CAMBIAR_MIS_DATOS(
            "Cambiar los datos de tu tienda",
            listOf(
                "Toca «Tienda» en la barra de abajo.",
                "Cambia «Nombre de la Tienda», «Descripción para tus clientes» o «WhatsApp de tu tienda».",
                "Toca «Guardar cambios». Sin señal, se guarda en el celular y se sube después."
            ),
            Destino.TIENDA, "Ir a «Tienda»"
        ),
        RECUPERAR_ACCESO(
            "Recuperar el acceso a tu cuenta",
            listOf(
                "En la pantalla de ingreso toca «¿Olvidaste tu contraseña?».",
                "Escribe tu correo y abre el enlace que te llega.",
                "Elige una contraseña nueva de al menos 6 caracteres.",
                "Si activaste la huella, también puedes entrar con ella."
            ),
            Destino.AJUSTES, "Abrir «Ajustes»"
        ),
        MODO_SENCILLO(
            "Usar el modo sencillo",
            listOf(
                "En «Ajustes», en «Accesibilidad», activa «Modo sencillo».",
                "Inicio muestra solo botones grandes: hablar con el asistente, agregar un producto, registrar una venta, ver productos, ferias, compartir la tienda y ayuda.",
                "Todo lo demás sigue en la barra de abajo y en «Ver el inicio completo».",
                "Para volver al inicio de siempre, apágalo en «Ajustes». Cada cuenta guarda su preferencia."
            ),
            Destino.AJUSTES, "Abrir «Ajustes»"
        );

        /** Texto para leer en voz alta: título y pasos numerados en palabras simples. */
        fun paraLeer(): String = titulo + ". " + pasos.mapIndexed { i, p -> "Paso ${i + 1}: $p" }.joinToString(" ")
    }

    private val CLAVES: List<Pair<Tema, Regex>> = listOf(
        Tema.SABER_SI_SE_GUARDO to Regex("\\b(se guardo|guardado|guardo bien|se subio|pendiente|correctamente)\\b"),
        Tema.AGREGAR_FOTO to Regex("\\b(foto|fotos|fotografia|imagen|camara)\\b"),
        Tema.CAMBIAR_PRECIO to Regex("\\bprecio\\b"),
        Tema.CAMBIAR_WHATSAPP to Regex("\\b(whatsapp|wasap|telefono|numero|celular)\\b"),
        Tema.COMPARTIR_TIENDA to Regex("\\b(compart|qr|enlace|link|difundir)"),
        Tema.PARTICIPAR_FERIA to Regex("\\b(feria|ferias|participo|participar|invitacion)\\b"),
        Tema.REGISTRAR_VENTA to Regex("\\b(venta|ventas|vendi|vender lo que|cobrar|registro)\\b"),
        Tema.CAMBIAR_CANTIDAD to Regex("\\b(cuantos tengo|cantidad|se acabo|stock|quedan)\\b"),
        Tema.OCULTAR_PRODUCTO to Regex("\\b(ocult|escond|que no se vea|solo yo)"),
        Tema.VER_MENSAJES to Regex("\\b(mensaje|mensajes|consultas)\\b"),
        Tema.RECUPERAR_ACCESO to Regex("\\b(contrasena|clave|recuperar (el )?acceso|no puedo entrar|olvide)\\b"),
        Tema.MODO_SENCILLO to Regex("\\b(modo sencillo|mas sencillo|mas simple|mas facil)\\b"),
        Tema.CAMBIAR_MIS_DATOS to Regex("\\b(datos de (mi )?tienda|nombre de (mi )?tienda|descripcion de (mi )?tienda|mis datos)\\b"),
        Tema.TEXTO_Y_VOZ to Regex("\\b(letra|letras|agrand\\w*|tamano del texto|voz alta|escuch\\w*|lectura|leer|lea)\\b"),
        Tema.BORRAR_PRODUCTO to Regex("\\b(borr\\w*|elimin\\w*)"),
        Tema.AGREGAR_PRODUCTO to Regex("\\b(producto|productos|agrego|agregar|subo|subir|publico|publicar|poner|vender algo)\\b")
    )

    /** Tema de una pregunta «¿cómo…?» ya normalizada. null si no calza con ninguno. */
    fun temaDe(textoNormalizado: String): Tema? = CLAVES.firstOrNull { (_, re) -> re.containsMatchIn(textoNormalizado) }?.first
}
