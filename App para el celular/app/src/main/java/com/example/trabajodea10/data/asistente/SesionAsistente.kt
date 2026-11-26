package com.example.trabajodea10.data.asistente

import com.example.trabajodea10.data.asistente.InterpreteAlerce.Intencion
import com.example.trabajodea10.data.asistente.TemasAyuda.Destino
import com.example.trabajodea10.data.asistente.TemasAyuda.Tema

/**
 * Conversación del Asistente Alerce (lógica pura, sin pantallas, probada en el computador).
 *
 * Reglas de seguridad:
 *  - Solo usa un conjunto cerrado de [Accion]es con datos tipados; no hay texto libre que se ejecute.
 *  - Nunca guarda ni cambia datos por sí sola. Para crear un producto prepara los datos y abre el
 *    formulario de siempre (mismas validaciones y guardado): la persona revisa y toca «Guardar».
 *  - Las consultas leen solo lo de esta cuenta a través de [ContextoAsistente] (que usa los
 *    repositorios y el estado local de siempre).
 *  - No inventa: si un dato falta, lo pregunta; si una frase es ambigua, ofrece opciones; si una
 *    respuesta no sirve como dato (una pregunta, un «no sé»), no la anota.
 */
class SesionAsistente(
    private val contexto: ContextoAsistente,
    /** Motor de interpretación (hoy, reglas locales). Ver MotorInterpretacion.kt. */
    private val motor: MotorInterpretacion = MotorLocal
) {

    /** Producto que se está preparando por conversación (aún no existe en Mis productos). */
    data class ProductoAsistido(
        val nombre: String? = null,
        val descripcion: String? = null,
        val precio: Long? = null,
        val cantidad: Int? = null,
        /** La persona dijo «después» a la cantidad: el formulario usará su valor de siempre. */
        val cantidadOmitida: Boolean = false
    ) {
        val faltaObligatorio: Campo? get() = when {
            nombre.isNullOrBlank() -> Campo.NOMBRE
            precio == null || precio <= 0 -> Campo.PRECIO
            else -> null
        }

        fun resumen(): List<Pair<String, String>> = listOf(
            "Nombre" to (nombre?.trim()?.ifBlank { null } ?: "falta"),
            "Descripción" to (descripcion?.trim()?.ifBlank { null } ?: "sin descripción"),
            "Precio" to (precio?.let { NumerosEs.pesos(it) } ?: "falta"),
            "Cuántos tengo" to (cantidad?.toString() ?: if (cantidadOmitida) "lo eliges en el formulario" else "sin indicar")
        )

        fun resumenParaLeer(): String = resumen().joinToString(". ") { (k, v) -> "$k: $v" } + "."
    }

    enum class Campo { NOMBRE, PRECIO, CANTIDAD, DESCRIPCION }

    /**
     * Conjunto cerrado de acciones con datos tipados. Las que cambian datos solo salen de la sesión
     * después de que la persona toca «Confirmar» (o lo dice) en la tarjeta de confirmación, y cada
     * confirmación vale una sola vez. Las ejecuta EjecutorAsistente con las funciones de siempre.
     */
    sealed class Accion {
        /** Ir a una pantalla (no cambia datos). */
        data class Navegar(val destino: Destino) : Accion()
        /** Abrir «Añadir producto» con estos datos para revisarlos, agregar la foto y guardar. */
        data class AbrirFormularioProducto(val datos: ProductoAsistido) : Accion()
        /** Abrir «Añadir producto» con «Guíame paso a paso» encendida. */
        data class IniciarGuia(val foto: Boolean) : Accion()
        /** Llenar el carrito de «Venta» (no registra la venta: se cobra y confirma en «Venta» como siempre). */
        data class PrepararVenta(val lineas: List<LineaPreparada>, val medio: com.example.trabajodea10.data.MedioPago?, val enFeria: Boolean) : Accion()
        /** «Se acabó», «me quedan…», «disponible otra vez»: con las funciones de «Mis productos». */
        data class CambiarInventario(val cambios: List<CambioInventario>) : Accion()
        /** «Lo ven todos» / «Solo yo lo veo». */
        data class CambiarVisibilidad(val productoIds: List<String>, val visible: Boolean) : Accion()
        /** Abrir el formulario de un producto existente con el cambio puesto (se guarda en el formulario). */
        data class AbrirEdicion(val productoId: String, val precio: Long?, val paso: Int) : Accion()
        /** «Participo» / «No participo»: la misma operación que los botones de «Mis ferias». */
        data class ResponderFeria(val feriaId: String, val nombre: String, val participo: Boolean) : Accion()
        /** Abrir el mapa del celular con la ubicación de la feria. */
        data class ComoLlegar(val lat: Double, val lng: Double, val nombre: String) : Accion()
        /** Asistente general: buscar en la vitrina pública (solo lectura). */
        data class Buscar(val termino: String?, val verTiendas: Boolean, val verAsociaciones: Boolean = false, val limpiar: Boolean = false) : Accion()
        /** Asistente general: abrir la ficha pública de una tienda. */
        data class AbrirTienda(val tiendaId: String) : Accion()
        /** Administración: abrir «Cuentas» filtrada (no cambia nada). */
        data class BuscarCuenta(val texto: String?, val soloPendientes: Boolean) : Accion()
    }

    data class LineaPreparada(val productoId: String, val varianteId: String, val cantidad: Int)

    /** [conocida]: unidades que había al preparar el cambio (para no pisar una venta anotada entremedio). */
    data class CambioInventario(val productoId: String, val varianteId: String?, val tipo: TipoCambio, val cantidad: Int? = null, val conocida: Int? = null)
    enum class TipoCambio { SE_ACABO, FIJAR, DISPONIBLE }

    /** Botón de respuesta rápida: al tocarlo se envía [entrada] como si la persona lo hubiera dicho. [productoId]: para mostrar su foto. */
    data class Opcion(val etiqueta: String, val entrada: String, val productoId: String? = null)

    /** Tarjeta «Revisa antes de confirmar»: qué se hará, con qué datos y el resultado esperado. */
    data class Confirmacion(val id: Int, val titulo: String, val lineas: List<String>, val confirmar: String, val nota: String? = null) {
        val paraLeer: String get() = (listOf(titulo) + lineas + listOfNotNull(nota)).joinToString(". ")
    }

    data class Respuesta(
        val texto: String,
        val opciones: List<Opcion> = emptyList(),
        val accion: Accion? = null,
        /** Pasos para mostrar en lista (Enséñame). */
        val pasos: List<String> = emptyList(),
        val confirmacion: Confirmacion? = null
    )

    var producto: ProductoAsistido? = null
        private set
    private var esperando: Campo? = null
    /** Último dato preguntado o corregido: «no, cuatro» se entiende como ese dato. */
    private var ultimoCampo: Campo? = null
    private var preguntoCantidad = false
    /** Lecturas posibles de la última frase ambigua (para «la primera» / «la segunda»). */
    private var elegir: List<Intencion> = emptyList()
    private var datosAmbiguos: InterpreteAlerce.Datos? = null
    /** Otro producto dicho a mitad de uno: se pregunta antes de dejar de lado el actual. */
    private var nuevoPendiente: InterpreteAlerce.Datos? = null

    fun saludo(nombre: String?): Respuesta = Respuesta(
        (if (nombre.isNullOrBlank()) "Hola. " else "Hola, $nombre. ") +
            "Soy tu asistente de Alerce. Puedo enseñarte a usar la app o ayudarte a hacerlo. Dime qué necesitas, con tu voz o escribiendo.",
        SUGERENCIAS
    )

    /** Ventas, cantidades, quién ve un producto, edición, ferias y consultas (ver GestorOperaciones). */
    private val operaciones = GestorOperaciones(contexto)

    /** Responde a lo que la persona escribió o dictó. */
    fun responder(entrada: String): Respuesta {
        val texto = entrada.trim()
        if (texto.isEmpty()) return Respuesta("No escuché nada. Puedes escribirlo o tocar una de las opciones.", SUGERENCIAS)
        val n = NumerosEs.normalizar(texto)

        // «Quiero hacer otra cosa», «mejor cancela todo», «no quiero registrar una venta»: se deja todo de lado.
        val primera = if (texto.startsWith("@")) null else motor.interpretar(texto)
        if (primera?.intencion == Intencion.OTRA_COSA || (primera?.intencion == Intencion.RECHAZAR && producto == null) ||
            Regex("\\bcancela(r)? todo\\b").containsMatchIn(n)) {
            val habia = producto != null || operaciones.activo
            cancelarTodo()
            return Respuesta(
                (if (habia) "Listo, dejé eso de lado: no se guardó ni se cambió nada. " else if (primera?.intencion == Intencion.RECHAZAR) "Está bien, no hago nada. " else "") +
                    "¿Qué necesitas?", SUGERENCIAS)
        }

        // 1. ¿Estamos preparando un producto? Primero respuestas, correcciones, «listo» o «cancelar».
        producto?.let { p -> return responderEnCreacion(p, texto) }

        // 2. Una operación a medio hacer (venta, cantidades, feria…): «seguir», respuestas y correcciones.
        if (operaciones.activo) {
            if (Regex("^(seguir|sigamos|sigue|volver|continuar|continua)$").containsMatchIn(n)) operaciones.reanudar()?.let { return it }
            operaciones.responder(texto)?.let { return it }
        }
        Regex("^@mapa:(.+)$").find(texto)?.let { m ->
            val a = operaciones.mapaDeFeria(m.groupValues[1]) ?: return Respuesta("Esa feria no tiene un punto en el mapa.", SUGERENCIAS)
            return Respuesta("Abro el mapa de tu celular.", accion = a)
        }
        if (texto.startsWith("@")) return Respuesta("Esa opción ya no está vigente. ¿Te ayudo con otra cosa?", SUGERENCIAS)

        // 3. «Cancelar» sin nada a medio hacer: se dice tal cual.
        if (InterpreteAlerce.esCancelacion(texto)) {
            elegir = emptyList(); datosAmbiguos = null
            return Respuesta("Está bien. No había nada a medio hacer. ¿Te ayudo con otra cosa?", SUGERENCIAS)
        }

        // 4. ¿Respondía a una pregunta de dos opciones?
        if (elegir.isNotEmpty()) {
            val elegida = when {
                Regex("\\b(subir|agregar|tienda|producto|nuevo|primera|primero)\\b").containsMatchIn(n) -> elegir.getOrNull(0)
                Regex("\\b(venta|vendi|anotar|registrar|segunda|segundo)\\b").containsMatchIn(n) -> elegir.getOrNull(1)
                else -> null
            }
            var datos = datosAmbiguos ?: InterpreteAlerce.Datos()
            elegir = emptyList(); datosAmbiguos = null
            // «Quiero vender unos gorros» → «subirlo»: el nombre es lo que dijo («Gorros»), no uno inventado.
            if (elegida == Intencion.CREAR_PRODUCTO && datos.nombre == null && datos.producto != null) {
                datos = datos.copy(nombre = InterpreteAlerce.capitalizar(datos.producto!!))
            }
            if (elegida == Intencion.REGISTRAR_VENTA) {
                return operaciones.iniciar(InterpreteAlerce.Resultado(Intencion.REGISTRAR_VENTA, datos), "vendí " + (datos.producto ?: ""))!!
            }
            if (elegida != null) return ejecutar(InterpreteAlerce.Resultado(elegida, datos))
        }

        val r = ValidadorResultado.validar(primera ?: motor.interpretar(texto))
        // Algo a medio hacer y llega otra cosa: si es otra operación, lo anterior se deja (y se dice);
        // si es una pregunta o ir a otra pantalla, lo preparado se conserva.
        val enCurso = operaciones.enCurso()
        val resp = ejecutar(r, Canonizador.canonizar(texto))
        return when {
            enCurso == null -> resp
            r.intencion.clase == InterpreteAlerce.Clase.ACCION -> resp.copy(texto = "Dejé de lado lo anterior (no se hizo nada). " + resp.texto)
            else -> resp.copy(texto = resp.texto + " $enCurso: di «seguir» para volver.")
        }
    }

    /** Deja de lado todo lo que estaba a medio hacer (nada estaba guardado). */
    fun cancelarTodo() {
        producto = null; esperando = null; nuevoPendiente = null
        elegir = emptyList(); datosAmbiguos = null
        operaciones.cancelar()
    }

    private fun ejecutar(r: InterpreteAlerce.Resultado, frase: String = ""): Respuesta {
        operaciones.iniciar(r, frase)?.let { return it }
        return when (r.intencion) {
            Intencion.CREAR_PRODUCTO -> empezarProducto(r.datos)
            Intencion.ENSENAME -> r.tema?.let { ensenar(it) } ?: Respuesta(
                "Te puedo enseñar a hacer cualquiera de estas cosas. ¿Cuál necesitas?",
                listOf(
                    Opcion("Agregar un producto", "¿Cómo agrego un producto?"),
                    Opcion("Registrar una venta", "¿Cómo registro una venta?"),
                    Opcion("Compartir mi tienda", "¿Cómo comparto mi tienda?"),
                    Opcion("Participar en una feria", "¿Cómo participo en una feria?"),
                    Opcion("Agrandar la letra o escuchar", "¿Cómo agrando la letra?"),
                    Opcion("Centro de ayuda", ACCION_IR + Destino.AYUDA.name)
                )
            )
            Intencion.AMBIGUA -> {
                elegir = r.opciones; datosAmbiguos = r.datos
                val que = r.datos.producto?.let { "«$it»" } ?: "eso"
                Respuesta(
                    "¿Qué quieres hacer con $que?",
                    listOf(Opcion("Subirlo a mi tienda", "Subirlo a mi tienda"), Opcion("Anotar una venta que hice", "Anotar una venta"))
                )
            }
            Intencion.BORRAR_PRODUCTO -> irAProducto(r.datos.producto,
                "Yo no borro productos. Para borrar %s: en «Mis productos» tócalo, abre «Más acciones» (los tres puntos) y elige «Borrar producto». Te pedirá confirmar. Si solo quieres que no se vea, usa «Solo yo lo veo».")
            Intencion.VER_PRODUCTOS -> Respuesta("Te muestro tus productos.", accion = Accion.Navegar(Destino.MIS_PRODUCTOS))
            Intencion.VER_FERIAS -> Respuesta("Te llevo a «Ferias». Ahí respondes las invitaciones con «Participo» o «No participo».", accion = Accion.Navegar(Destino.FERIAS))
            Intencion.COMPARTIR_TIENDA -> Respuesta("Te abro el código QR de tu tienda. Puedes tocar «Compartir imagen del QR» para enviarlo por WhatsApp.", accion = Accion.Navegar(Destino.COMPARTIR))
            Intencion.CAMBIAR_WHATSAPP -> Respuesta("Te llevo a «Tienda». Busca «WhatsApp de tu tienda», escribe tu número y toca «Guardar cambios».", accion = Accion.Navegar(Destino.TIENDA))
            Intencion.VER_MENSAJES -> Respuesta("Te abro «Mensajes recibidos».", accion = Accion.Navegar(Destino.MENSAJES))
            Intencion.ABRIR_AJUSTES -> Respuesta("Te abro «Ajustes»: ahí están el tamaño de letra, la lectura en voz alta, el modo sencillo y tu cuenta.", accion = Accion.Navegar(Destino.AJUSTES))
            Intencion.ABRIR_AYUDA -> Respuesta("Te abro el centro de ayuda: está ordenado por tareas.", accion = Accion.Navegar(Destino.AYUDA))
            Intencion.CONSULTAR_VENTAS_MES -> consultarVentas()
            Intencion.CONSULTAR_PROXIMA_FERIA -> consultarFeria()
            Intencion.SABER_SI_SE_GUARDO -> ensenar(Tema.SABER_SI_SE_GUARDO)
            Intencion.SALUDO -> Respuesta("Hola. ¿En qué te ayudo?", SUGERENCIAS)
            Intencion.VER_HISTORIAL -> Respuesta("Te abro el historial: ahí están tus ventas y sus comprobantes en PDF.", accion = Accion.Navegar(Destino.HISTORIAL))
            Intencion.CONSULTAR_SEGUIDORES -> Respuesta("Alerce todavía no muestra cuántas personas siguen tu tienda. Sí puedes ver tus ventas y tus mensajes recibidos.",
                listOf(Opcion("Ver mis ventas del mes", "¿Cuánto vendí este mes?"), Opcion("Mensajes recibidos", ACCION_IR + Destino.MENSAJES.name)))
            Intencion.CORREGIR -> Respuesta("No hay nada a medio hacer para corregir. Si una venta quedó mal anotada, revísala en el historial.",
                listOf(Opcion("Ver el historial", ACCION_IR + Destino.HISTORIAL.name)) + SUGERENCIAS)
            Intencion.RECHAZAR, Intencion.OTRA_COSA -> { cancelarTodo(); Respuesta("Está bien, no hago nada. ¿Qué necesitas?", SUGERENCIAS) }
            else -> Respuesta(
                "No entendí bien, o es algo que todavía no sé hacer. Puedo ayudarte a agregar un producto, anotar una venta, cambiar cuántos tienes, mostrarte tus productos o tus ferias, o compartir tu tienda.",
                SUGERENCIAS
            )
        }
    }

    // ---------- Enséñame ----------
    private fun ensenar(tema: Tema): Respuesta {
        val opciones = mutableListOf<Opcion>()
        if (tema.puedoAyudar) opciones += Opcion("Ayúdame a hacerlo", "Quiero agregar un producto")
        if (tema.conGuia) opciones += Opcion("Guíame en la pantalla", if (tema == Tema.AGREGAR_FOTO) ACCION_GUIA_FOTO else ACCION_GUIA)
        else opciones += Opcion(tema.etiquetaDestino, ACCION_IR + tema.destino.name)
        return Respuesta("Así se hace: ${tema.titulo.lowercase()}.", opciones, pasos = tema.pasos)
    }

    private fun irAProducto(producto: String?, plantilla: String): Respuesta {
        val nombre = producto?.let { "«$it»" } ?: "el producto"
        return Respuesta(plantilla.format(nombre), listOf(Opcion("Ir a «Mis productos»", ACCION_IR + Destino.MIS_PRODUCTOS.name)))
    }

    // ---------- Consultas (solo datos de esta cuenta) ----------
    private fun consultarVentas(): Respuesta {
        val v = contexto.ventasDelMes()
        val texto = if (v.cantidad == 0) "Este mes aún no tienes ventas registradas."
        else "Este mes llevas ${NumerosEs.pesos(v.total)} en ${v.cantidad} ${if (v.cantidad == 1) "venta" else "ventas"}." +
            (if (v.pendientes > 0) " ${v.pendientes} ${if (v.pendientes == 1) "de ellas aún se está subiendo" else "de ellas aún se están subiendo"} al servidor." else "")
        return Respuesta(texto, listOf(Opcion("Ver el historial", ACCION_IR + Destino.HISTORIAL.name)))
    }

    private fun consultarFeria(): Respuesta {
        val f = contexto.proximaFeria()
        val pendientes = contexto.invitacionesPorResponder()
        val texto = when {
            f != null -> "Tu próxima feria es «${f.nombre}», ${f.cuando}" + (f.lugar?.let { ", en $it" } ?: "") + "." +
                (if (pendientes > 0) " Además tienes $pendientes ${if (pendientes == 1) "invitación" else "invitaciones"} por responder." else "")
            pendientes > 0 -> "No tienes ferias confirmadas, pero tienes $pendientes ${if (pendientes == 1) "invitación" else "invitaciones"} por responder."
            else -> "No tienes ferias próximas. Cuando la administración te invite, la verás en «Ferias»."
        }
        return Respuesta(texto, listOf(Opcion("Ir a «Ferias»", ACCION_IR + Destino.FERIAS.name)))
    }

    // ---------- Ayúdame a hacerlo: producto por conversación ----------
    private fun empezarProducto(d: InterpreteAlerce.Datos, prefijo: String = ""): Respuesta {
        nuevoPendiente = null
        producto = ProductoAsistido(nombre = d.nombre, descripcion = d.descripcion,
            precio = d.precio?.takeIf { !d.precioEsTotal && !d.variosPrecios }, cantidad = d.cantidad)
        preguntoCantidad = false
        esperando = null
        if (d.variosPrecios) return preguntarEntrePrecios(d, prefijo)
        if (d.precio != null && d.precioEsTotal) {
            esperando = Campo.PRECIO; ultimoCampo = Campo.PRECIO
            return Respuesta(prefijo + "Dijiste ${NumerosEs.pesos(d.precio)} en total. ¿Cuánto cuesta cada uno?", OPCIONES_CREACION)
        }
        return siguientePregunta(prefijo + if (d.nombre != null || d.precio != null || d.cantidad != null) "Anoté lo que me dijiste. " else "Bien, preparemos tu producto. ")
    }

    /** Dos precios distintos en la misma frase: no se elige uno, se pregunta. */
    private fun preguntarEntrePrecios(d: InterpreteAlerce.Datos, prefijo: String = ""): Respuesta {
        esperando = Campo.PRECIO; ultimoCampo = Campo.PRECIO
        val que = (producto?.nombre ?: d.nombre)?.let { "«$it»" } ?: "este producto"
        return Respuesta(
            prefijo + "Escuché más de un precio (${d.preciosOidos.joinToString(" y ") { NumerosEs.pesos(it) }}). " +
                "Vamos de a un producto: ¿cuánto cuesta $que?",
            d.preciosOidos.map { Opcion(NumerosEs.pesos(it), "$it pesos") } + OPCION_CANCELAR
        )
    }

    private fun responderEnCreacion(p: ProductoAsistido, texto: String): Respuesta {
        if (InterpreteAlerce.esCancelacion(texto)) {
            producto = null; esperando = null; nuevoPendiente = null
            return Respuesta("Listo, no anoté nada. ¿Te ayudo con otra cosa?", SUGERENCIAS)
        }
        val n = NumerosEs.normalizar(texto)
        val actualNombre = p.nombre?.let { "«$it»" } ?: "el producto anterior"

        // 0. ¿Respondía si empezamos otro producto?
        nuevoPendiente?.let { nuevo ->
            nuevoPendiente = null
            if (Regex("\\b(nuevo|otro|otra|empezar|empecemos)\\b").containsMatchIn(n) || InterpreteAlerce.esAfirmacion(texto)) {
                return empezarProducto(nuevo, "Dejé de lado $actualNombre. ")
            }
            if (Regex("\\b(no|seguir|sigue|sigamos|anterior|mantener|quedarme)\\b").containsMatchIn(n)) {
                return siguientePregunta("Sigo con $actualNombre. ")
            }
        }

        val otra = ValidadorResultado.validar(motor.interpretar(texto))
        val cambioDeTema = esCambioDeTema(texto, otra)

        // 1. Respuesta directa a la pregunta que hicimos (solo si sirve como ese dato).
        when (esperando) {
            Campo.NOMBRE -> if (!cambioDeTema && !InterpreteAlerce.esAfirmacion(texto) &&
                InterpreteAlerce.correccion(texto)?.campo.let { it == null || it == InterpreteAlerce.Campo.NOMBRE }) {
                // «gorro de lana a 5000»: el nombre es lo de antes del precio; el precio también se anota.
                val cabeza = InterpreteAlerce.antesDeLosDatos(texto)
                if (InterpreteAlerce.pareceNombre(cabeza)) {
                    val (nom, desc) = InterpreteAlerce.nombreSuelto(cabeza) ?: return repreguntar(Campo.NOMBRE, texto)
                    val d = InterpreteAlerce.extraerDatos(texto)
                    producto = p.copy(
                        nombre = nom, descripcion = p.descripcion ?: desc,
                        precio = d.precio?.takeIf { !d.precioEsTotal && !d.variosPrecios } ?: p.precio,
                        cantidad = d.cantidad ?: p.cantidad
                    )
                    ultimoCampo = Campo.NOMBRE
                    return siguientePregunta("Anoté «$nom». ")
                }
            }
            Campo.PRECIO -> if (!cambioDeTema) InterpreteAlerce.montoSuelto(texto)?.let { m ->
                // «veinte» a secas puede ser $20 o $20.000: se pregunta en vez de suponer.
                if (m < 100 && !Regex("\\b(pesos?|lucas?|luquitas?|mil)\\b").containsMatchIn(n)) return preguntarEscala(m)
                producto = p.copy(precio = m)
                ultimoCampo = Campo.PRECIO
                return siguientePregunta("Anoté ${NumerosEs.pesos(m)}. ")
            }
            Campo.CANTIDAD -> {
                if (InterpreteAlerce.esDespues(texto)) {
                    producto = p.copy(cantidadOmitida = true)
                    return siguientePregunta("Está bien, lo eliges después en el formulario. ")
                }
                // «5000» o «8 lucas» respondiendo cuántos tiene es casi seguro un precio: lo ve la corrección.
                val pareceMonto = InterpreteAlerce.valorSuelto(texto)?.second == true
                if (!cambioDeTema && !pareceMonto) InterpreteAlerce.cantidadSuelta(texto)?.let { c ->
                    producto = p.copy(cantidad = c, cantidadOmitida = false)
                    ultimoCampo = Campo.CANTIDAD
                    return siguientePregunta("Anoté $c. ")
                }
            }
            Campo.DESCRIPCION -> if (!cambioDeTema && !InterpreteAlerce.esPregunta(texto) && !InterpreteAlerce.esAfirmacion(texto) &&
                !InterpreteAlerce.esDespues(texto) && InterpreteAlerce.correccion(texto) == null) {
                producto = p.copy(descripcion = InterpreteAlerce.capitalizar(texto.trim().trim('.')))
                ultimoCampo = Campo.DESCRIPCION
                return siguientePregunta("Anoté la descripción. ")
            }
            null -> Unit
        }

        // 2. Volvió a pedir crear: si es otro producto se pregunta; si es el mismo, se completan los datos.
        if (otra.intencion == Intencion.CREAR_PRODUCTO) {
            val d = otra.datos
            val esOtro = Regex("\\b(otro|otra|tambien|ademas)\\b").containsMatchIn(n) ||
                (d.nombre != null && p.nombre != null && NumerosEs.normalizar(d.nombre) != NumerosEs.normalizar(p.nombre) && InterpreteAlerce.empiezaConCrear(texto))
            if (esOtro && p.nombre != null) {
                nuevoPendiente = d
                val nuevo = d.nombre?.let { "«$it»" } ?: "un producto nuevo"
                return Respuesta(
                    "Tienes anotado $actualNombre y aún no se guarda. ¿Lo dejo de lado y empezamos con $nuevo? " +
                        "Si quieres los dos, termina primero $actualNombre y después me dices el otro.",
                    listOf(Opcion("Sí, empezar con $nuevo", "sí, empezar el nuevo"), Opcion("No, seguir con $actualNombre", "no, seguir con el anterior"))
                )
            }
            if (d.variosPrecios) return preguntarEntrePrecios(d)
            val cambios = mutableListOf<String>()
            var q = p
            d.nombre?.let { q = q.copy(nombre = it, descripcion = d.descripcion ?: q.descripcion); cambios += "«$it»" }
            d.precio?.takeIf { !d.precioEsTotal }?.let { q = q.copy(precio = it); cambios += NumerosEs.pesos(it) }
            d.cantidad?.let { q = q.copy(cantidad = it, cantidadOmitida = false); cambios += "$it disponibles" }
            if (cambios.isNotEmpty()) {
                producto = q
                return siguientePregunta("Anoté ${cambios.joinToString(", ")}. ")
            }
        }

        // 3. Correcciones: «no, el precio es doce mil», «tengo cinco», «cambia el nombre a…», «quiero una foto».
        val c = InterpreteAlerce.correccion(texto)
        if (c != null) {
            val actual = producto ?: p
            val datos = InterpreteAlerce.extraerDatos(texto)
            return when (c.campo) {
                InterpreteAlerce.Campo.FOTO -> if (actual.faltaObligatorio == null)
                    Respuesta("La foto se agrega en el primer paso del formulario. Toca «Continuar» y después «Tomar foto» o «Elegir de la galería».", OPCIONES_CREACION)
                else siguientePregunta("La foto la agregamos en el formulario, al continuar. ")
                InterpreteAlerce.Campo.NOMBRE -> { producto = actual.copy(nombre = c.valorTexto); ultimoCampo = Campo.NOMBRE; siguientePregunta("Cambié el nombre a «${c.valorTexto}». ") }
                InterpreteAlerce.Campo.DESCRIPCION -> { producto = actual.copy(descripcion = c.valorTexto); ultimoCampo = Campo.DESCRIPCION; siguientePregunta("Anoté la descripción. ") }
                InterpreteAlerce.Campo.PRECIO -> {
                    ultimoCampo = Campo.PRECIO
                    if (datos.precioEsTotal) { esperando = Campo.PRECIO; Respuesta("Dijiste ${NumerosEs.pesos(c.valorNumero!!)} en total. ¿Cuánto cuesta cada uno?", OPCIONES_CREACION) }
                    else {
                        // Si en la misma frase dijo cuántos tiene, también se anota.
                        producto = actual.copy(precio = c.valorNumero, cantidad = datos.cantidad?.takeIf { it.toLong() != c.valorNumero } ?: actual.cantidad)
                        siguientePregunta("Cambié el precio a ${NumerosEs.pesos(c.valorNumero!!)}. ")
                    }
                }
                InterpreteAlerce.Campo.CANTIDAD -> {
                    producto = actual.copy(cantidad = c.valorNumero!!.toInt(), cantidadOmitida = false); ultimoCampo = Campo.CANTIDAD
                    siguientePregunta("Anoté ${c.valorNumero} disponibles. ")
                }
            }
        }
        // 4. Nombra un dato sin decir el valor nuevo («cambia el precio»): se pregunta ese dato.
        if (!cambioDeTema) InterpreteAlerce.campoSinValor(texto)?.let { campo ->
            val propio = when (campo) {
                InterpreteAlerce.Campo.NOMBRE -> Campo.NOMBRE
                InterpreteAlerce.Campo.PRECIO -> Campo.PRECIO
                InterpreteAlerce.Campo.CANTIDAD -> Campo.CANTIDAD
                InterpreteAlerce.Campo.DESCRIPCION -> Campo.DESCRIPCION
                InterpreteAlerce.Campo.FOTO -> null
            }
            if (propio != null) {
                esperando = propio; ultimoCampo = propio
                return Respuesta(PREGUNTA_CORREGIR.getValue(propio), OPCIONES_CREACION)
            }
        }
        // 5. Un valor solo («no, quince mil», «perdón, cuatro»): se entiende según el último dato.
        InterpreteAlerce.valorSuelto(texto)?.let { (v, esMonto) ->
            val actual = producto ?: p
            return when {
                esMonto -> { producto = actual.copy(precio = v); ultimoCampo = Campo.PRECIO; siguientePregunta("Cambié el precio a ${NumerosEs.pesos(v)}. ") }
                ultimoCampo == Campo.CANTIDAD -> { producto = actual.copy(cantidad = v.toInt(), cantidadOmitida = false); siguientePregunta("Anoté $v disponibles. ") }
                ultimoCampo == Campo.PRECIO && v < 100 -> preguntarEscala(v)
                ultimoCampo == Campo.PRECIO -> { producto = actual.copy(precio = v); siguientePregunta("Cambié el precio a ${NumerosEs.pesos(v)}. ") }
                else -> Respuesta(
                    "¿$v es el precio o cuántos tienes?",
                    listOf(Opcion("Tengo $v", "tengo $v"), Opcion("Cuesta ${NumerosEs.pesos(v * 1000)}", "${v * 1000} pesos")) + OPCION_CANCELAR
                )
            }
        }
        if (InterpreteAlerce.esAfirmacion(texto)) return continuar(producto ?: p)
        // 6. Otra cosa en medio (una pregunta, ir a otra pantalla): se responde sin perder el producto.
        if (cambioDeTema && otra.intencion.clase == InterpreteAlerce.Clase.ACCION && otra.intencion != Intencion.BORRAR_PRODUCTO) {
            // Otra operación (anotar una venta, «se acabó»…) en medio del producto: una cosa a la vez.
            return Respuesta("Primero terminemos ${p.nombre?.let { "«$it»" } ?: "tu producto"}: toca «Continuar» o «Cancelar». Después me dices lo otro.", OPCIONES_CREACION)
        }
        if (cambioDeTema) {
            val resp = ejecutar(otra, texto)
            return resp.copy(texto = resp.texto + " Tu producto ${p.nombre?.let { "«$it» " } ?: ""}sigue anotado aquí, sin guardar: vuelve cuando quieras y di «continuar».")
        }
        return repreguntar(esperando, texto)
    }

    /** ¿Pide otra cosa? Consultas y «¿cómo…?» siempre; ir a una pantalla solo si lo pide claro («muéstrame…», «¿dónde…?»). */
    private fun esCambioDeTema(texto: String, r: InterpreteAlerce.Resultado): Boolean = when (r.intencion) {
        Intencion.CONSULTAR_VENTAS_MES, Intencion.CONSULTAR_PROXIMA_FERIA, Intencion.SABER_SI_SE_GUARDO, Intencion.ENSENAME,
        Intencion.CONSULTAR_VENTAS_PRODUCTO, Intencion.CONSULTAR_PRODUCTOS, Intencion.CONSULTAR_AGOTADOS, Intencion.CONSULTAR_INVITACIONES,
        Intencion.CONSULTAR_UBICACION_FERIA, Intencion.CONSULTAR_TIENDA, Intencion.ABRIR_AYUDA,
        Intencion.REGISTRAR_VENTA, Intencion.MARCAR_SE_ACABO, Intencion.OCULTAR_PRODUCTO, Intencion.MOSTRAR_PRODUCTO, Intencion.BORRAR_PRODUCTO,
        Intencion.SUMAR_CANTIDAD, Intencion.DISPONIBLE_OTRA_VEZ, Intencion.PARTICIPAR_FERIA, Intencion.NO_PARTICIPAR_FERIA -> true
        Intencion.VER_PRODUCTOS, Intencion.VER_FERIAS, Intencion.COMPARTIR_TIENDA, Intencion.CAMBIAR_WHATSAPP, Intencion.VER_MENSAJES ->
            InterpreteAlerce.esPregunta(texto) || PIDE_IR.containsMatchIn(NumerosEs.normalizar(texto))
        else -> false
    }

    /** «$20 o $20.000»: un número chico sin unidad no se adivina. */
    private fun preguntarEscala(m: Long): Respuesta {
        esperando = Campo.PRECIO; ultimoCampo = Campo.PRECIO
        return Respuesta("¿Son ${NumerosEs.pesos(m)} o ${NumerosEs.pesos(m * 1000)}?",
            listOf(Opcion(NumerosEs.pesos(m), "$m pesos"), Opcion(NumerosEs.pesos(m * 1000), "${m * 1000} pesos")))
    }

    /** No sirvió como respuesta: se vuelve a preguntar lo mismo, diciendo qué se espera. */
    private fun repreguntar(campo: Campo?, texto: String): Respuesta {
        val despues = InterpreteAlerce.esDespues(texto)
        return when (campo) {
            Campo.NOMBRE -> Respuesta(
                if (despues) "Necesito al menos un nombre simple, por ejemplo «gorro de lana». Si prefieres dejarlo para después, toca «Cancelar»."
                else "No lo anoté como nombre. ¿Cómo se llama el producto? Por ejemplo: «chaleco de lana».",
                OPCIONES_CREACION
            )
            Campo.PRECIO -> Respuesta(
                if (despues) "Sin precio no se puede guardar. Dímelo como número, o toca «Cancelar» y lo dejamos para después."
                else "No entendí el precio. Dímelo como número, por ejemplo «doce mil» o 12000.",
                OPCIONES_CREACION
            )
            Campo.CANTIDAD -> Respuesta("¿Cuántos tienes para vender? Di un número, por ejemplo «cuatro», o «después».", OPCIONES_CREACION + Opcion("Después", "después"))
            Campo.DESCRIPCION -> Respuesta("¿Cómo lo describirías? Por ejemplo: «tejido a mano, talla única».", OPCIONES_CREACION)
            null -> Respuesta(
                "No entendí qué cambiar. Puedes decir, por ejemplo: «el precio es doce mil», «tengo cinco» o «cambia el nombre a chaleco artesanal». O toca «Continuar».",
                OPCIONES_CREACION
            )
        }
    }

    /** Pregunta lo que falta (obligatorio primero, luego cuántos tiene) o muestra el resumen. */
    private fun siguientePregunta(prefijo: String): Respuesta {
        val p = producto ?: return Respuesta(prefijo.trim(), SUGERENCIAS)
        when (p.faltaObligatorio) {
            Campo.NOMBRE -> { esperando = Campo.NOMBRE; ultimoCampo = Campo.NOMBRE; return Respuesta(prefijo + "¿Cómo se llama el producto? Por ejemplo: «chaleco de lana».", OPCIONES_CREACION) }
            Campo.PRECIO -> { esperando = Campo.PRECIO; ultimoCampo = Campo.PRECIO; return Respuesta(prefijo + "¿Cuánto cuesta cada uno? Por ejemplo: «quince mil».", OPCIONES_CREACION) }
            else -> Unit
        }
        if (p.cantidad == null && !p.cantidadOmitida && !preguntoCantidad) {
            preguntoCantidad = true
            esperando = Campo.CANTIDAD; ultimoCampo = Campo.CANTIDAD
            return Respuesta(prefijo + "¿Cuántos tienes para vender? Si no sabes, di «después».", OPCIONES_CREACION + Opcion("Después", "después"))
        }
        esperando = null
        return Respuesta(
            prefijo + "Revisa los datos. Si algo está mal, dímelo (por ejemplo «el precio es doce mil») o corrígelo abajo. " +
                "Cuando esté bien, toca «Continuar»: abriré el formulario para agregar la foto y guardarlo.",
            OPCIONES_CREACION
        )
    }

    /** «Continuar»: abre el formulario de siempre con los datos. Aún no se guarda nada. */
    private fun continuar(p: ProductoAsistido): Respuesta {
        p.faltaObligatorio?.let { return siguientePregunta("Antes falta un dato. ") }
        producto = null; esperando = null; nuevoPendiente = null
        return Respuesta(
            "Abro «Añadir producto» con tus datos. Primero viene la foto; después revisa todo y toca «Guardar en Mis productos». Nada se guarda antes de eso.",
            listOf(Opcion("Volver a «Añadir producto»", ACCION_IR + Destino.NUEVO_PRODUCTO.name)),
            accion = Accion.AbrirFormularioProducto(p)
        )
    }

    /** Cambios hechos a mano en la tarjeta del resumen. Se guarda tal cual se escribe (los espacios incluidos). */
    fun editarAMano(nombre: String?, descripcion: String?, precio: Long?, cantidad: Int?) {
        val p = producto ?: return
        producto = p.copy(
            nombre = nombre?.takeIf { it.isNotBlank() },
            descripcion = descripcion?.takeIf { it.isNotBlank() },
            precio = precio?.takeIf { it > 0 },
            cantidad = cantidad?.takeIf { it >= 0 },
            cantidadOmitida = cantidad == null && p.cantidadOmitida
        )
    }

    fun cancelarProducto() { producto = null; esperando = null; nuevoPendiente = null }

    /** Vuelve a dejar anotado un producto (por ejemplo, si no se quiso reemplazar otro borrador). */
    fun retomar(p: ProductoAsistido) { producto = p; esperando = null; preguntoCantidad = true }

    companion object {
        /** Entradas especiales de los botones (no se muestran como texto de la persona). */
        const val ACCION_IR = "@ir:"
        const val ACCION_GUIA = "@guia"
        const val ACCION_GUIA_FOTO = "@guia-foto"

        private val PIDE_IR = Regex("\\b(muestrame|muestra|mostrar|ver|ve a|ir a|anda a|abre|abrir|llevame|lleva|vamos a|mejor|quiero ver|necesito ver)\\b")

        private val PREGUNTA_CORREGIR = mapOf(
            Campo.NOMBRE to "¿Cuál es el nombre correcto?",
            Campo.PRECIO to "¿Cuál es el precio correcto de cada uno?",
            Campo.CANTIDAD to "¿Cuántos tienes para vender?",
            Campo.DESCRIPCION to "¿Cómo lo describirías? Por ejemplo: «tejido a mano, talla única»."
        )

        val SUGERENCIAS = listOf(
            Opcion("Agregar un producto", "Quiero agregar un producto"),
            Opcion("¿Cómo registro una venta?", "¿Cómo registro una venta?"),
            Opcion("¿Cuánto vendí este mes?", "¿Cuánto vendí este mes?"),
            Opcion("Mi próxima feria", "¿Cuándo es mi próxima feria?"),
            Opcion("Compartir mi tienda", "Quiero compartir mi tienda"),
            Opcion("Ver mis productos", "Muéstrame mis productos")
        )
        val OPCION_CANCELAR = Opcion("Cancelar", "cancelar")
        val OPCIONES_CREACION = listOf(Opcion("Continuar", "continuar"), OPCION_CANCELAR)
    }
}
