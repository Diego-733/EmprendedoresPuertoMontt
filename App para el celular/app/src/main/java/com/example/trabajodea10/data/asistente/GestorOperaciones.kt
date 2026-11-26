package com.example.trabajodea10.data.asistente

import com.example.trabajodea10.data.ItemVenta
import com.example.trabajodea10.data.MedioPago
import com.example.trabajodea10.data.Modalidad
import com.example.trabajodea10.data.Producto
import com.example.trabajodea10.data.asistente.InterpreteAlerce.Intencion
import com.example.trabajodea10.data.asistente.SesionAsistente.Accion
import com.example.trabajodea10.data.asistente.SesionAsistente.CambioInventario
import com.example.trabajodea10.data.asistente.SesionAsistente.Confirmacion
import com.example.trabajodea10.data.asistente.SesionAsistente.LineaPreparada
import com.example.trabajodea10.data.asistente.SesionAsistente.Opcion
import com.example.trabajodea10.data.asistente.SesionAsistente.Respuesta
import com.example.trabajodea10.data.asistente.SesionAsistente.TipoCambio
import com.example.trabajodea10.data.asistente.TemasAyuda.Destino

/**
 * Operaciones sobre datos que ya existen (ventas, cantidades, quién ve un producto, edición y
 * ferias) y consultas. Lógica pura: lee con [ContextoAsistente] y propone [Accion]es tipadas.
 *
 * Reglas:
 *  - Nada se ejecuta sin una tarjeta [Confirmacion] que la persona confirma. Cada confirmación tiene
 *    un número y vale **una sola vez** (un doble toque o un «sí» repetido no ejecuta dos veces).
 *  - Los productos salen solo de la tienda con sesión. Si hay dudas se muestran opciones; nunca se
 *    elige por parecido.
 *  - No se inventan precios, medios de pago ni descuentos: la venta usa el precio del catálogo,
 *    como la pantalla «Venta». Si se dijo otro precio, se avisa.
 *  - Lo que falta se pregunta.
 */
class GestorOperaciones(private val ctx: ContextoAsistente) {

    private class LineaVenta(
        var dicho: String,
        var cantidad: Int?,
        var producto: Producto? = null,
        var varianteId: String? = null,
        var opciones: List<Producto> = emptyList(),
        val precioDicho: Long? = null,
        val precioEsTotal: Boolean = false
    )

    private sealed class Flujo
    private class FlujoVenta(
        val lineas: MutableList<LineaVenta>, var medio: MedioPago?, var enFeria: Boolean,
        var totalDicho: Long?, var avisoMedio: String?
    ) : Flujo()
    private class FlujoInventario(
        val tipo: TipoCambio, val sumar: Boolean, var dicho: String?, var cantidad: Int?,
        var opciones: List<Producto> = emptyList(), var elegidos: List<Producto> = emptyList(),
        var varianteId: String? = null, val permiteVarios: Boolean = false
    ) : Flujo()
    private class FlujoVisibilidad(val visible: Boolean, var dicho: String?, var opciones: List<Producto> = emptyList(), var elegidos: List<Producto> = emptyList()) : Flujo()
    private class FlujoEdicion(var dicho: String?, val paso: Int, var precio: Long?, val esPrecio: Boolean, var opciones: List<Producto> = emptyList(), var producto: Producto? = null) : Flujo()
    private class FlujoFeria(val participo: Boolean, var opciones: List<ContextoAsistente.ResumenFeria>) : Flujo()

    private var flujo: Flujo? = null
    private var pendiente: Pair<Confirmacion, Accion>? = null
    private var siguienteId = 1

    /** Último producto del que se habló: «este producto», «el mismo». */
    var ultimoProducto: Producto? = null
        private set

    val activo: Boolean get() = flujo != null || pendiente != null

    /** Qué hay a medio hacer, para recordárselo a la persona si pregunta otra cosa entremedio. */
    fun enCurso(): String? = when (flujo) {
        is FlujoVenta -> "Tu venta sigue anotada aquí, sin registrar"
        is FlujoInventario, is FlujoVisibilidad -> "El cambio sigue preparado aquí, sin hacer"
        is FlujoEdicion -> "El cambio del producto sigue preparado aquí, sin guardar"
        is FlujoFeria -> "Tu respuesta a la feria sigue preparada aquí, sin enviar"
        null -> if (pendiente != null) "Lo que preparamos sigue esperando tu confirmación" else null
    }

    fun cancelar() { flujo = null; pendiente = null }

    // ======================================================================= entrada

    /** Una intención nueva que corresponde a una operación o consulta. null = no es de aquí. */
    fun iniciar(r: InterpreteAlerce.Resultado, frase: String): Respuesta? = when (r.intencion) {
        Intencion.REGISTRAR_VENTA -> iniciarVenta(frase)
        Intencion.MARCAR_SE_ACABO -> iniciarInventario(TipoCambio.SE_ACABO, false, r, frase)
        Intencion.CAMBIAR_CANTIDAD -> iniciarInventario(TipoCambio.FIJAR, false, r, frase)
        Intencion.SUMAR_CANTIDAD -> iniciarInventario(TipoCambio.FIJAR, true, r, frase)
        Intencion.DISPONIBLE_OTRA_VEZ -> iniciarInventario(TipoCambio.DISPONIBLE, false, r, frase)
        Intencion.OCULTAR_PRODUCTO -> iniciarVisibilidad(false, r, frase)
        Intencion.MOSTRAR_PRODUCTO -> iniciarVisibilidad(true, r, frase)
        Intencion.CAMBIAR_PRECIO -> iniciarEdicion(r, frase, paso = 1, esPrecio = true)
        Intencion.EDITAR_PRODUCTO -> iniciarEdicion(r, frase, paso = if (Regex("\\b(foto|fotos|imagen)\\b").containsMatchIn(NumerosEs.normalizar(frase))) 0 else 1, esPrecio = false)
        Intencion.PARTICIPAR_FERIA -> iniciarFeria(true)
        Intencion.NO_PARTICIPAR_FERIA -> iniciarFeria(false)
        Intencion.CONSULTAR_VENTAS_PRODUCTO -> consultarVentasProducto(r.datos.producto)
        Intencion.CONSULTAR_PRODUCTOS -> consultarProductos()
        Intencion.CONSULTAR_AGOTADOS -> consultarAgotados()
        Intencion.CONSULTAR_INVITACIONES -> consultarInvitaciones()
        Intencion.CONSULTAR_UBICACION_FERIA -> consultarUbicacion(comoLlegar = false)
        Intencion.COMO_LLEGAR_FERIA -> consultarUbicacion(comoLlegar = true)
        Intencion.CONSULTAR_TIENDA -> consultarTienda()
        else -> null
    }

    /**
     * Respuesta mientras hay una operación a medio hacer. null = la frase es otra cosa (una pregunta,
     * ir a otra pantalla): la sesión la responde y lo preparado se conserva.
     */
    fun responder(texto: String): Respuesta? {
        val n = NumerosEs.normalizar(texto)
        if (InterpreteAlerce.esCancelacion(texto) || Regex("^(cancela|cancelar)( todo)?\\b|\\bcancela todo\\b").containsMatchIn(n)) {
            cancelar()
            return Respuesta("Listo, no hice nada. ¿Te ayudo con otra cosa?", SesionAsistente.SUGERENCIAS)
        }
        // Confirmación (botón o voz). Solo vale para la tarjeta que está a la vista.
        Regex("^@confirmar:(\\d+)$").find(texto)?.let { return confirmar(it.groupValues[1].toInt()) }
        if (pendiente != null && Regex("^(si|confirmo|confirmar|dale|ya|ok|listo|hazlo|correcto|ir a cobrar|abrir)\\b").containsMatchIn(n) &&
            InterpreteAlerce.esAfirmacion(texto)) {
            return confirmar(pendiente!!.first.id)
        }
        return when (val f = flujo) {
            is FlujoVenta -> responderVenta(f, texto)
            is FlujoInventario -> responderInventario(f, texto)
            is FlujoVisibilidad -> responderVisibilidad(f, texto)
            is FlujoEdicion -> responderEdicion(f, texto)
            is FlujoFeria -> responderFeria(f, texto)
            null -> null
        }
    }

    /** «seguir»: vuelve a mostrar lo que se estaba preparando. */
    fun reanudar(): Respuesta? = when (val f = flujo) {
        is FlujoVenta -> avanzarVenta(f, "")
        is FlujoInventario -> avanzarInventario(f, "")
        is FlujoVisibilidad -> avanzarVisibilidad(f, "")
        is FlujoEdicion -> avanzarEdicion(f, "")
        is FlujoFeria -> avanzarFeria(f, "")
        null -> pendiente?.let { (c, _) -> Respuesta("Esto sigue esperando tu confirmación.", listOf(Opcion(c.confirmar, "@confirmar:${c.id}"), CANCELAR), confirmacion = c) }
    }

    private fun confirmar(id: Int): Respuesta {
        val p = pendiente
        if (p == null || p.first.id != id) {
            return Respuesta("No hay nada esperando confirmación (puede que ya lo hayas confirmado). ¿Te ayudo con otra cosa?", SesionAsistente.SUGERENCIAS)
        }
        pendiente = null
        flujo = null
        return Respuesta(textoAlConfirmar(p.second), accion = p.second)
    }

    private fun textoAlConfirmar(a: Accion): String = when (a) {
        is Accion.PrepararVenta -> "Te llevo a «Venta» con los productos. Revisa y toca «Continuar al pago»: la venta se anota cuando confirmes el pago."
        is Accion.AbrirEdicion -> "Abro el producto. Revisa y toca «Guardar en Mis productos» para que quede guardado."
        else -> "Hecho en este celular. Te cuento cómo quedó:"
    }

    private fun preparar(titulo: String, lineas: List<String>, confirmar: String, accion: Accion, nota: String?, texto: String, extra: List<Opcion> = emptyList()): Respuesta {
        val c = Confirmacion(siguienteId++, titulo, lineas, confirmar, nota)
        pendiente = c to accion
        return Respuesta(texto, listOf(Opcion(confirmar, "@confirmar:${c.id}")) + extra + CANCELAR, confirmacion = c)
    }

    // ======================================================================= ventas

    private fun iniciarVenta(frase: String): Respuesta {
        val v = LectorVentas.leer(frase)
        val f = FlujoVenta(
            v.lineas.map { LineaVenta(it.producto, it.cantidad, precioDicho = it.precio, precioEsTotal = it.precioEsTotal) }.toMutableList(),
            v.medio, v.enFeria, v.totalDicho, avisoMedio(v.medioNoDisponible, v.medioDudoso)
        )
        // «Vendí tres» sin decir qué: si se acababa de hablar de un producto, se propone ese (no se da por hecho).
        flujo = f
        return avanzarVenta(f, "")
    }

    private fun avisoMedio(noDisponible: String?, dudoso: Boolean): String? = when {
        noDisponible != null -> "En Alerce se puede anotar efectivo, transferencia, débito o QR; «$noDisponible» no está. Lo eliges al cobrar."
        dudoso -> "Con «tarjeta» no sé si fue débito: lo eliges al cobrar."
        else -> null
    }

    private fun opcionProducto(p: Producto, entrada: String = "@producto:${p.id}"): Opcion {
        val cuantos = when {
            !p.manejaStock || p.modalidad == Modalidad.A_PEDIDO -> "a pedido"
            p.agotado -> "se acabó"
            else -> "quedan ${p.stockTotal}"
        }
        return Opcion("${p.nombre} · ${NumerosEs.pesos(p.precio.toLong())} · $cuantos", entrada, p.id)
    }

    private fun avanzarVenta(f: FlujoVenta, prefijo: String): Respuesta {
        pendiente = null
        if (f.lineas.isEmpty()) return Respuesta(prefijo + "¿Qué vendiste? Por ejemplo: «dos gorros» o «una bufanda y dos gorros».", listOf(CANCELAR))
        val catalogo = ctx.productos()
        for (l in f.lineas) {
            if (l.producto != null) continue
            // «vendí dos productos»: no nombra cuál; se pregunta en vez de buscar un producto llamado «productos».
            if (ResolutorProductos.palabras(l.dicho).isEmpty()) l.dicho = ""
            if (l.dicho.isBlank()) {
                return Respuesta(prefijo + "¿Qué producto vendiste?" + (ultimoProducto?.let { " ¿Fue «${it.nombre}»?" } ?: ""),
                    listOfNotNull(ultimoProducto?.let { opcionProducto(it) }) + listOf(VER_PRODUCTOS, CANCELAR))
            }
            if (l.opciones.isEmpty()) {
                when (val r = ResolutorProductos.buscar(l.dicho, catalogo)) {
                    is ResolutorProductos.Resultado.Unico -> { l.producto = r.producto; continue }
                    is ResolutorProductos.Resultado.Varios -> l.opciones = r.opciones
                    ResolutorProductos.Resultado.Ninguno -> return Respuesta(
                        prefijo + "No encontré «${l.dicho}» en tus productos. Dime el nombre como está en «Mis productos», o elige uno.",
                        catalogo.filter { it.nombre.isNotBlank() && it.vendidaEn == null }.take(4).map { opcionProducto(it) } + listOf(VER_PRODUCTOS, CANCELAR)
                    )
                }
            }
            return Respuesta(prefijo + "Encontré más de uno parecido a «${l.dicho}». ¿Cuál vendiste?", l.opciones.map { opcionProducto(it) } + CANCELAR)
        }
        // Opción (talla, color…) cuando el producto tiene varias.
        for (l in f.lineas) {
            val p = l.producto!!
            if (l.varianteId == null) {
                if (p.variantes.size == 1) l.varianteId = p.variantes.first().id
                else return Respuesta(prefijo + "¿Qué opción de «${p.nombre}»?", p.variantes.map { v ->
                    val q = if (p.manejaStock && p.modalidad != Modalidad.A_PEDIDO) " (quedan ${v.stock})" else ""
                    Opcion(v.nombre + q, "@opcion:${v.id}")
                } + CANCELAR)
            }
        }
        for (l in f.lineas) {
            if (l.cantidad == null) return Respuesta(prefijo + "¿Cuántos «${l.producto!!.nombre}» vendiste?", listOf(Opcion("1", "1"), Opcion("2", "2"), Opcion("3", "3"), CANCELAR))
        }
        // Todo listo: se valida igual que en «Venta» y se muestra el resumen.
        val items = f.lineas.map { l ->
            val p = l.producto!!
            ItemVenta(p.id, l.varianteId!!, p.nombre, p.variantes.first { it.id == l.varianteId }.nombre,
                ctx.precioDe(p, l.varianteId), l.cantidad!!, null, encargo = p.modalidad == Modalidad.A_PEDIDO)
        }
        ctx.validarVenta(items)?.let { problema ->
            return Respuesta(prefijo + "No se puede anotar así: $problema Dime la cantidad correcta (por ejemplo «eran dos») o cancela.", listOf(CANCELAR))
        }
        ultimoProducto = f.lineas.last().producto
        val lineas = items.map { "${it.cantidad} × ${it.nombre}" + (if (it.detalle != "Única") " (${it.detalle})" else "") +
            " · ${NumerosEs.pesos(it.precio.toLong())} c/u = ${NumerosEs.pesos((it.precio * it.cantidad).toLong())}" }
        val total = items.sumOf { it.precio * it.cantidad }.toLong()
        val notas = mutableListOf<String>()
        f.lineas.zip(items).forEach { (l, it) ->
            val dicho = l.precioDicho ?: return@forEach
            val unidad = if (l.precioEsTotal) dicho / it.cantidad.coerceAtLeast(1) else dicho
            if (l.precioEsTotal && dicho % it.cantidad.coerceAtLeast(1) != 0L) notas += "Dijiste ${NumerosEs.pesos(dicho)} en total por «${it.nombre}»."
            else if (unidad != it.precio.toLong()) notas += "Dijiste ${NumerosEs.pesos(unidad)} por cada «${it.nombre}», pero en tu catálogo cuesta ${NumerosEs.pesos(it.precio.toLong())}."
        }
        f.totalDicho?.let { if (it != total) notas += "Dijiste ${NumerosEs.pesos(it)} en total; con los precios del catálogo son ${NumerosEs.pesos(total)}." }
        if (notas.isNotEmpty()) notas += "La venta se anota con el precio del catálogo (así funciona «Venta»). Si el precio cambió, cámbialo primero en «Mis productos»."
        f.avisoMedio?.let { notas += it }
        val resumen = lineas + listOf(
            "Total: ${NumerosEs.pesos(total)}",
            "Pago: " + (f.medio?.etiqueta ?: "lo eliges al cobrar"),
        ) + listOfNotNull(if (f.enFeria) "Lugar: en la feria (se elige al cobrar)" else null)
        return preparar(
            "Revisa la venta (aún no se anota)", resumen, "Ir a cobrar",
            Accion.PrepararVenta(f.lineas.map { LineaPreparada(it.producto!!.id, it.varianteId!!, it.cantidad!!) }, f.medio, f.enFeria),
            (notas + "Se anota cuando confirmes el pago en «Venta»; las cantidades se descuentan solas.").joinToString(" "),
            prefijo + "Así queda la venta. Si algo está mal, dímelo (por ejemplo «eran cuatro» o «fue en efectivo»)."
        )
    }

    private fun responderVenta(f: FlujoVenta, texto: String): Respuesta? {
        val n = NumerosEs.normalizar(texto)
        val actual = f.lineas.firstOrNull { it.producto == null } ?: f.lineas.firstOrNull { it.varianteId == null && (it.producto?.variantes?.size ?: 0) > 1 }
            ?: f.lineas.firstOrNull { it.cantidad == null }
        // Elección por botón.
        Regex("^@producto:(.+)$").find(texto)?.let { m ->
            val p = ctx.productos().firstOrNull { it.id == m.groupValues[1] } ?: return avanzarVenta(f, "Ese producto ya no está. ")
            (f.lineas.firstOrNull { it.producto == null } ?: return avanzarVenta(f, "")).apply { producto = p; opciones = emptyList(); if (dicho.isBlank()) dicho = p.nombre }
            return avanzarVenta(f, "")
        }
        Regex("^@opcion:(.+)$").find(texto)?.let { m ->
            f.lineas.firstOrNull { it.varianteId == null && it.producto?.variantes?.any { v -> v.id == m.groupValues[1] } == true }?.varianteId = m.groupValues[1]
            return avanzarVenta(f, "")
        }
        // Medio de pago y lugar («fue en efectivo», «en la feria»).
        val (medio, noDisp, dudoso) = LectorVentas.medio(texto)
        var cambio = false
        if (medio != null) { f.medio = medio; f.avisoMedio = null; cambio = true }
        else if (noDisp != null || dudoso) { f.avisoMedio = avisoMedio(noDisp, dudoso); cambio = true }
        if (LectorVentas.enFeria(texto)) { f.enFeria = true; cambio = true }
        // Más productos en la misma venta («y también una bufanda»).
        if (Regex("^(y |tambien |ademas |agrega |suma )").containsMatchIn(n) || Regex("\\b(tambien vendi|ademas vendi)\\b").containsMatchIn(n)) {
            val extra = LectorVentas.leer(texto.replace(Regex("(?i)^(y |también |tambien |además |ademas |agrega |suma )"), "vendí "))
            if (extra.lineas.any { it.producto.isNotBlank() }) {
                extra.lineas.filter { it.producto.isNotBlank() }.forEach { f.lineas += LineaVenta(it.producto, it.cantidad, precioDicho = it.precio, precioEsTotal = it.precioEsTotal) }
                return avanzarVenta(f, "Lo agregué. ")
            }
        }
        // Respuesta a «¿cuál?» con palabras: «los de lana», «el azul», «el primero».
        if (actual != null && actual.producto == null && actual.opciones.isNotEmpty()) {
            val quedan = ResolutorProductos.refinar(texto, actual.opciones)
            if (quedan.size == 1) { actual.producto = quedan.first(); actual.opciones = emptyList(); return avanzarVenta(f, "") }
            if (quedan.size > 1) { actual.opciones = quedan; return avanzarVenta(f, "") }
        }
        if (actual != null && actual.producto == null && actual.dicho.isBlank() && !InterpreteAlerce.esPregunta(texto) && medio == null) {
            actual.dicho = texto.trim(); return avanzarVenta(f, "")
        }
        // Respuesta a «¿qué opción?» con palabras («talla M», «el rojo»).
        if (actual != null && actual.producto != null && actual.varianteId == null) {
            val p = actual.producto!!
            ResolutorProductos.elegirOpcion(texto, p.variantes)?.let { actual.varianteId = it.id; return avanzarVenta(f, "") }
        }
        // Cantidad: respuesta directa o corrección («no, eran cuatro», «no vendí dos, vendí tres»).
        if (actual != null && actual.producto != null && actual.cantidad == null) {
            InterpreteAlerce.cantidadSuelta(texto)?.let { actual.cantidad = it; return avanzarVenta(f, "") }
        }
        LectorVentas.correccionCantidad(texto)?.let { (cantidad, palabras) ->
            val linea = if (palabras != null) f.lineas.firstOrNull { l -> l.producto?.let { ResolutorProductos.puntaje(ResolutorProductos.palabras(palabras), it) >= 0.5 } == true }
                else f.lineas.singleOrNull() ?: f.lineas.lastOrNull { it.cantidad != null }
            if (linea != null) { linea.cantidad = cantidad; return avanzarVenta(f, "Corregí la cantidad. ") }
        }
        if (cambio) return avanzarVenta(f, "Anotado. ")
        return null
    }

    // ======================================================================= cantidades y disponibilidad

    private fun esReferencia(texto: String) =
        Regex("\\b(este|ese|esta|esa) (producto|mismo|articulo)\\b|\\bel mismo\\b|\\beste producto\\b|^(este|ese|esto|eso)$").containsMatchIn(NumerosEs.normalizar(texto))

    private fun iniciarInventario(tipo: TipoCambio, sumar: Boolean, r: InterpreteAlerce.Resultado, frase: String): Respuesta {
        val f = FlujoInventario(tipo, sumar, r.datos.producto, r.datos.cantidad,
            permiteVarios = tipo != TipoCambio.FIJAR && ResolutorProductos.hablaDeVarios(frase))
        if (esReferencia(frase)) ultimoProducto?.let { f.elegidos = listOf(it) }
        flujo = f
        return avanzarInventario(f, "")
    }

    private fun avanzarInventario(f: FlujoInventario, prefijo: String): Respuesta {
        pendiente = null
        val catalogo = ctx.productos()
        if (f.elegidos.isEmpty()) {
            val dicho = f.dicho
            if (dicho.isNullOrBlank()) return Respuesta(prefijo + "¿De qué producto?", listOf(VER_PRODUCTOS, CANCELAR))
            if (f.opciones.isEmpty()) {
                when (val r = ResolutorProductos.buscar(dicho, catalogo)) {
                    is ResolutorProductos.Resultado.Unico -> f.elegidos = listOf(r.producto)
                    is ResolutorProductos.Resultado.Varios -> f.opciones = r.opciones
                    ResolutorProductos.Resultado.Ninguno -> return Respuesta(
                        prefijo + "No encontré «$dicho» en tus productos. Dime el nombre como está en «Mis productos».", listOf(VER_PRODUCTOS, CANCELAR))
                }
            }
            if (f.elegidos.isEmpty()) {
                val todos = if (f.permiteVarios && f.opciones.size > 1) listOf(Opcion("Todos (${f.opciones.size})", "@todos")) else emptyList()
                return Respuesta(prefijo + "¿Cuál? Encontré más de uno parecido a «$dicho».", f.opciones.map { opcionProducto(it) } + todos + CANCELAR)
            }
        }
        f.elegidos.lastOrNull()?.let { ultimoProducto = it }
        return when (f.tipo) {
            TipoCambio.SE_ACABO -> {
                val lineas = f.elegidos.map { p -> if (cuentaUnidades(p)) "${p.nombre}: quedan ${p.stockTotal} → 0 («Se acabó»)" else "${p.nombre}: queda sin unidades («Se acabó»)" }
                preparar("Revisa: «Se acabó»", lineas, "Confirmar: se acabó",
                    Accion.CambiarInventario(f.elegidos.map { CambioInventario(it.id, null, TipoCambio.SE_ACABO, conocida = it.stockTotal) }),
                    "Si te equivocas, en «Mis productos» está «Deshacer» y «Cuántos tengo».",
                    prefijo + "¿Lo marco así? Si fue porque lo vendiste, mejor anota la venta: así queda en tu historial.",
                    extra = if (f.elegidos.size == 1) listOf(Opcion("Mejor anotar una venta", "vendí ${f.elegidos[0].nombre}")) else emptyList())
            }
            TipoCambio.DISPONIBLE -> {
                val p = f.elegidos.first()
                when {
                    f.elegidos.size == 1 && !p.disponible -> preparar("Revisa: disponible otra vez", listOf("${p.nombre}: no disponible → disponible"), "Confirmar",
                        Accion.CambiarInventario(listOf(CambioInventario(p.id, null, TipoCambio.DISPONIBLE))), null, prefijo + "¿Lo dejo disponible?")
                    f.elegidos.size == 1 && p.agotado && cuentaUnidades(p) -> {
                        flujo = FlujoInventario(TipoCambio.FIJAR, false, p.nombre, null, elegidos = listOf(p)).also { it.varianteId = p.variantes.singleOrNull()?.id }
                        avanzarInventario(flujo as FlujoInventario, prefijo + "«${p.nombre}» está en 0. ")
                    }
                    f.elegidos.size > 1 -> {
                        val noDisp = f.elegidos.filter { !it.disponible }
                        if (noDisp.isEmpty()) { flujo = null; Respuesta(prefijo + "Esos productos ya están disponibles. Si alguno está en 0, dime cuántos tienes.", listOf(VER_PRODUCTOS)) }
                        else preparar("Revisa: disponibles otra vez", noDisp.map { "${it.nombre}: no disponible → disponible" }, "Confirmar",
                            Accion.CambiarInventario(noDisp.map { CambioInventario(it.id, null, TipoCambio.DISPONIBLE) }), null, prefijo + "¿Los dejo disponibles?")
                    }
                    else -> { flujo = null; Respuesta(prefijo + "«${p.nombre}» ya está disponible.", listOf(VER_PRODUCTOS)) }
                }
            }
            TipoCambio.FIJAR -> {
                val p = f.elegidos.first()
                if (!cuentaUnidades(p)) {
                    flujo = null
                    return Respuesta(prefijo + "«${p.nombre}» no lleva la cuenta de unidades (se vende a pedido o sin contar). Si quieres contarlas, cámbialo en el formulario del producto.", listOf(VER_PRODUCTOS))
                }
                if (f.varianteId == null) {
                    if (p.variantes.size == 1) f.varianteId = p.variantes.first().id
                    else return Respuesta(prefijo + "¿De qué opción de «${p.nombre}»?", p.variantes.map { Opcion("${it.nombre} (quedan ${it.stock})", "@opcion:${it.id}") } + CANCELAR)
                }
                val v = p.variantes.first { it.id == f.varianteId }
                val cantidad = f.cantidad ?: return Respuesta(prefijo + (if (f.sumar) "¿Cuántos llegaron de «${p.nombre}»?" else "¿Cuántos «${p.nombre}» tienes ahora?"), listOf(CANCELAR))
                val nuevo = if (f.sumar) v.stock + cantidad else cantidad
                if (nuevo > 9999) return Respuesta(prefijo + "Son demasiadas unidades (máximo 9999). ¿Cuántos tienes?", listOf(CANCELAR))
                val nombre = p.nombre + if (p.variantes.size > 1) " (${v.nombre})" else ""
                preparar("Revisa: cuántos tienes", listOf(if (f.sumar) "$nombre: ${v.stock} + $cantidad = $nuevo" else "$nombre: ${v.stock} → $nuevo"), "Confirmar",
                    Accion.CambiarInventario(listOf(CambioInventario(p.id, v.id, TipoCambio.FIJAR, nuevo, conocida = v.stock))),
                    "Si mientras tanto se anota una venta en otro celular, el servidor la respeta: solo aplica la diferencia.",
                    prefijo + if (f.sumar) "¿Lo dejo así? (sumar no es una venta)" else "¿Lo dejo así?")
            }
        }
    }

    private fun cuentaUnidades(p: Producto) = p.manejaStock && p.modalidad != Modalidad.A_PEDIDO

    private fun responderInventario(f: FlujoInventario, texto: String): Respuesta? {
        if (texto == "@todos" && f.opciones.isNotEmpty()) { f.elegidos = f.opciones; return avanzarInventario(f, "") }
        Regex("^@producto:(.+)$").find(texto)?.let { m -> ctx.productos().firstOrNull { it.id == m.groupValues[1] }?.let { f.elegidos = listOf(it) }; return avanzarInventario(f, "") }
        Regex("^@opcion:(.+)$").find(texto)?.let { m -> f.varianteId = m.groupValues[1]; return avanzarInventario(f, "") }
        if (f.elegidos.isEmpty() && f.opciones.isNotEmpty()) {
            if (f.permiteVarios && Regex("\\b(todos|todas|los dos|las dos|ambos)\\b").containsMatchIn(NumerosEs.normalizar(texto))) { f.elegidos = f.opciones; return avanzarInventario(f, "") }
            val quedan = ResolutorProductos.refinar(texto, f.opciones)
            if (quedan.size == 1) { f.elegidos = quedan; return avanzarInventario(f, "") }
            if (quedan.size > 1) { f.opciones = quedan; return avanzarInventario(f, "") }
        }
        if (f.elegidos.isEmpty() && f.dicho.isNullOrBlank() && !InterpreteAlerce.esPregunta(texto)) { f.dicho = texto.trim(); return avanzarInventario(f, "") }
        if (f.tipo == TipoCambio.FIJAR && f.elegidos.isNotEmpty()) {
            val p = f.elegidos.first()
            if (f.varianteId == null && p.variantes.size > 1) {
                ResolutorProductos.elegirOpcion(texto, p.variantes)?.let { f.varianteId = it.id; return avanzarInventario(f, "") }
            }
            // Cantidad o corrección («no, cinco»).
            val c = InterpreteAlerce.cantidadSuelta(texto) ?: InterpreteAlerce.valorSuelto(texto)?.takeIf { !it.second }?.first?.toInt()
            if (c != null) { f.cantidad = c; return avanzarInventario(f, if (pendiente != null) "Corregí la cantidad. " else "") }
        }
        return null
    }

    // ======================================================================= quién lo ve

    private fun iniciarVisibilidad(visible: Boolean, r: InterpreteAlerce.Resultado, frase: String): Respuesta {
        val f = FlujoVisibilidad(visible, r.datos.producto)
        if (esReferencia(frase)) ultimoProducto?.let { f.elegidos = listOf(it) }
        flujo = f
        return avanzarVisibilidad(f, "")
    }

    private fun avanzarVisibilidad(f: FlujoVisibilidad, prefijo: String): Respuesta {
        pendiente = null
        val verbo = if (f.visible) "mostrar" else "ocultar"
        if (f.elegidos.isEmpty()) {
            val dicho = f.dicho
            if (dicho.isNullOrBlank()) return Respuesta(prefijo + "¿Qué producto quieres $verbo?", listOf(VER_PRODUCTOS, CANCELAR))
            if (f.opciones.isEmpty()) when (val r = ResolutorProductos.buscar(dicho, ctx.productos())) {
                is ResolutorProductos.Resultado.Unico -> f.elegidos = listOf(r.producto)
                is ResolutorProductos.Resultado.Varios -> f.opciones = r.opciones
                ResolutorProductos.Resultado.Ninguno -> return Respuesta(prefijo + "No encontré «$dicho» en tus productos.", listOf(VER_PRODUCTOS, CANCELAR))
            }
            if (f.elegidos.isEmpty()) {
                val todos = if (f.opciones.size > 1) listOf(Opcion("Todos (${f.opciones.size})", "@todos")) else emptyList()
                return Respuesta(prefijo + "¿Cuáles quieres $verbo?", f.opciones.map { opcionProducto(it) } + todos + CANCELAR)
            }
        }
        f.elegidos.lastOrNull()?.let { ultimoProducto = it }
        val cambian = f.elegidos.filter { it.publicado != f.visible }
        if (cambian.isEmpty()) {
            flujo = null
            return Respuesta(prefijo + if (f.visible) "Ya los ven todos." else "Ya estaban como «Solo yo lo veo».", listOf(VER_PRODUCTOS))
        }
        val antes = if (f.visible) "Solo yo lo veo" else "Lo ven todos"
        val despues = if (f.visible) "Lo ven todos" else "Solo yo lo veo"
        return preparar("Revisa: quién lo ve", cambian.map { "${it.nombre}: «$antes» → «$despues»" }, "Confirmar",
            Accion.CambiarVisibilidad(cambian.map { it.id }, f.visible),
            if (f.visible) null else "No se borra: solo deja de mostrarse en tu tienda.", prefijo + "¿Lo dejo así?")
    }

    private fun responderVisibilidad(f: FlujoVisibilidad, texto: String): Respuesta? {
        if (texto == "@todos" && f.opciones.isNotEmpty()) { f.elegidos = f.opciones; return avanzarVisibilidad(f, "") }
        Regex("^@producto:(.+)$").find(texto)?.let { m -> ctx.productos().firstOrNull { it.id == m.groupValues[1] }?.let { f.elegidos = listOf(it) }; return avanzarVisibilidad(f, "") }
        if (f.elegidos.isEmpty() && f.opciones.isNotEmpty()) {
            if (Regex("\\b(todos|todas|los dos|las dos|ambos)\\b").containsMatchIn(NumerosEs.normalizar(texto))) { f.elegidos = f.opciones; return avanzarVisibilidad(f, "") }
            val quedan = ResolutorProductos.refinar(texto, f.opciones)
            if (quedan.size == 1) { f.elegidos = quedan; return avanzarVisibilidad(f, "") }
            if (quedan.size > 1) { f.opciones = quedan; return avanzarVisibilidad(f, "") }
        }
        if (f.elegidos.isEmpty() && f.dicho.isNullOrBlank() && !InterpreteAlerce.esPregunta(texto)) { f.dicho = texto.trim(); return avanzarVisibilidad(f, "") }
        return null
    }

    // ======================================================================= editar un producto

    private fun iniciarEdicion(r: InterpreteAlerce.Resultado, frase: String, paso: Int, esPrecio: Boolean): Respuesta {
        val f = FlujoEdicion(r.datos.producto, paso, r.datos.precio?.takeIf { esPrecio && !r.datos.precioEsTotal }, esPrecio)
        if (esReferencia(frase)) ultimoProducto?.let { f.producto = it }
        flujo = f
        return avanzarEdicion(f, "")
    }

    private fun avanzarEdicion(f: FlujoEdicion, prefijo: String): Respuesta {
        pendiente = null
        if (f.producto == null) {
            val dicho = f.dicho
            if (dicho.isNullOrBlank()) return Respuesta(prefijo + "¿Qué producto quieres cambiar?", listOf(VER_PRODUCTOS, CANCELAR))
            if (f.opciones.isEmpty()) when (val r = ResolutorProductos.buscar(dicho, ctx.productos())) {
                is ResolutorProductos.Resultado.Unico -> f.producto = r.producto
                is ResolutorProductos.Resultado.Varios -> f.opciones = r.opciones
                ResolutorProductos.Resultado.Ninguno -> return Respuesta(prefijo + "No encontré «$dicho» en tus productos.", listOf(VER_PRODUCTOS, CANCELAR))
            }
            if (f.producto == null) return Respuesta(prefijo + "¿Cuál quieres cambiar?", f.opciones.map { opcionProducto(it) } + CANCELAR)
        }
        val p = f.producto!!
        ultimoProducto = p
        if (f.esPrecio && f.precio == null) return Respuesta(prefijo + "¿Cuál es el nuevo precio de «${p.nombre}»? Ahora cuesta ${NumerosEs.pesos(p.precio.toLong())}.", listOf(CANCELAR))
        val (titulo, linea, nota) = when {
            f.esPrecio -> Triple("Revisa el precio nuevo", "${p.nombre}: ${NumerosEs.pesos(p.precio.toLong())} → ${NumerosEs.pesos(f.precio!!)}",
                "Abriré el formulario con el precio nuevo. Se guarda cuando toques «Guardar en Mis productos».")
            f.paso == 0 -> Triple("Abrir para cambiar la foto", "${p.nombre}: paso «Foto»",
                "Cada producto tiene una foto principal y puede tener una foto por opción (talla o color). Se guarda al tocar «Guardar en Mis productos».")
            else -> Triple("Abrir para cambiar los datos", "${p.nombre}: paso «Datos» (nombre, descripción y precio)",
                "Escribe o dicta el cambio en el formulario. Se guarda al tocar «Guardar en Mis productos».")
        }
        return preparar(titulo, listOf(linea), "Abrir y revisar", Accion.AbrirEdicion(p.id, f.precio, f.paso), nota, prefijo + "¿Abro el producto?")
    }

    private fun responderEdicion(f: FlujoEdicion, texto: String): Respuesta? {
        Regex("^@producto:(.+)$").find(texto)?.let { m -> ctx.productos().firstOrNull { it.id == m.groupValues[1] }?.let { f.producto = it }; return avanzarEdicion(f, "") }
        if (f.producto == null && f.opciones.isNotEmpty()) {
            val quedan = ResolutorProductos.refinar(texto, f.opciones)
            if (quedan.size == 1) { f.producto = quedan.first(); return avanzarEdicion(f, "") }
            if (quedan.size > 1) { f.opciones = quedan; return avanzarEdicion(f, "") }
        }
        if (f.producto == null && f.dicho.isNullOrBlank() && !InterpreteAlerce.esPregunta(texto)) { f.dicho = texto.trim(); return avanzarEdicion(f, "") }
        if (f.esPrecio && f.producto != null) {
            // «veinte mil», «no, dieciocho lucas»; un número chico sin unidad se pregunta (no se adivina la escala).
            val v = InterpreteAlerce.valorSuelto(texto) ?: InterpreteAlerce.montoSuelto(texto)?.let { it to (it >= 1000) }
            if (v != null) {
                if (!v.second && v.first < 100) return Respuesta("¿Son ${NumerosEs.pesos(v.first)} o ${NumerosEs.pesos(v.first * 1000)}?",
                    listOf(Opcion(NumerosEs.pesos(v.first), "${v.first} pesos"), Opcion(NumerosEs.pesos(v.first * 1000), "${v.first * 1000} pesos"), CANCELAR))
                f.precio = v.first
                return avanzarEdicion(f, if (pendiente != null) "Corregí el precio. " else "")
            }
        }
        return null
    }

    // ======================================================================= ferias

    private fun iniciarFeria(participo: Boolean): Respuesta {
        val pendientes = ctx.invitacionesPendientes()
        if (pendientes.isEmpty()) {
            val confirmadas = ctx.feriasConfirmadas()
            return if (!participo && confirmadas.isNotEmpty())
                Respuesta("Ya confirmaste tu participación en «${confirmadas.first().nombre}». En la app no se puede cambiar una participación confirmada: avisa a la administración de la feria.",
                    listOf(Opcion("Ir a «Ferias»", SesionAsistente.ACCION_IR + Destino.FERIAS.name)))
            else Respuesta("No tienes invitaciones por responder. Las ferias llegan por invitación de la administración y aparecen en «Ferias».",
                listOf(Opcion("Ir a «Ferias»", SesionAsistente.ACCION_IR + Destino.FERIAS.name)))
        }
        val f = FlujoFeria(participo, pendientes)
        flujo = f
        return avanzarFeria(f, "")
    }

    private fun avanzarFeria(f: FlujoFeria, prefijo: String): Respuesta {
        pendiente = null
        if (f.opciones.size > 1) {
            return Respuesta(prefijo + "Tienes ${f.opciones.size} invitaciones. ¿Cuál?", f.opciones.map { Opcion("${it.nombre} · ${it.cuando}", "@feria:${it.id}") } + CANCELAR)
        }
        val feria = f.opciones.first()
        val decision = if (f.participo) "Participo" else "No participo"
        return preparar("Revisa tu respuesta a la feria", listOfNotNull("«${feria.nombre}»", feria.cuando.replaceFirstChar { it.uppercase() }, feria.lugar?.let { "Lugar: $it" }, "Tu respuesta: $decision"),
            "Confirmar: $decision", Accion.ResponderFeria(feria.id, feria.nombre, f.participo),
            "Es lo mismo que tocar «$decision» en «Mis ferias». Sin señal, queda guardada y se envía sola.",
            prefijo + "¿Envío esta respuesta?")
    }

    private fun responderFeria(f: FlujoFeria, texto: String): Respuesta? {
        Regex("^@feria:(.+)$").find(texto)?.let { m -> f.opciones.firstOrNull { it.id == m.groupValues[1] }?.let { f.opciones = listOf(it) }; return avanzarFeria(f, "") }
        if (f.opciones.size > 1) {
            val dicho = ResolutorProductos.palabras(texto)
            val quedan = f.opciones.filter { o -> dicho.isNotEmpty() && dicho.any { w -> ResolutorProductos.palabras(o.nombre).any { ResolutorProductos.mismaPalabra(it, w) } } }
            if (quedan.isNotEmpty()) { f.opciones = quedan; return avanzarFeria(f, "") }
        }
        return null
    }

    // ======================================================================= consultas

    private fun consultarVentasProducto(dicho: String?): Respuesta {
        if (dicho.isNullOrBlank()) return Respuesta("¿De qué producto? Por ejemplo: «¿cuántos gorros vendí?».", listOf(Opcion("Ver el historial", SesionAsistente.ACCION_IR + Destino.HISTORIAL.name)))
        val encontrados = when (val r = ResolutorProductos.buscar(dicho, ctx.productos())) {
            is ResolutorProductos.Resultado.Unico -> listOf(r.producto)
            is ResolutorProductos.Resultado.Varios -> r.opciones.filter { ResolutorProductos.puntaje(ResolutorProductos.palabras(dicho), it) >= 1.0 }.ifEmpty { r.opciones }
            ResolutorProductos.Resultado.Ninguno -> emptyList()
        }
        if (encontrados.isEmpty()) return Respuesta("No encontré «$dicho» en tus productos.", listOf(VER_PRODUCTOS))
        val partes = encontrados.map { p -> ctx.unidadesVendidasEsteMes(p.id).let { n -> "$n «${p.nombre}»" } }
        encontrados.singleOrNull()?.let { ultimoProducto = it }
        return Respuesta("Este mes vendiste ${partes.joinToString(", ")}. (Cuento las ventas anotadas en este celular, también las que aún se están subiendo.)",
            listOf(Opcion("Ver el historial", SesionAsistente.ACCION_IR + Destino.HISTORIAL.name)))
    }

    private fun consultarProductos(): Respuesta {
        val lista = ctx.productos().filter { it.nombre.isNotBlank() }
        if (lista.isEmpty()) return Respuesta("Todavía no tienes productos. ¿Quieres agregar uno?", listOf(Opcion("Agregar un producto", "Quiero agregar un producto")))
        val agotados = lista.count { it.agotado }
        val ocultos = lista.count { !it.publicado }
        val texto = "Tienes ${lista.size} ${if (lista.size == 1) "producto" else "productos"}: ${lista.size - agotados} con unidades o a pedido" +
            (if (agotados > 0) ", $agotados se acabaron" else "") + (if (ocultos > 0) " y $ocultos solo los ves tú" else "") + "."
        return Respuesta(texto, listOf(VER_PRODUCTOS) + if (agotados > 0) listOf(Opcion("¿Cuáles se acabaron?", "¿qué productos se acabaron?")) else emptyList())
    }

    private fun consultarAgotados(): Respuesta {
        val agotados = ctx.productos().filter { it.nombre.isNotBlank() && it.agotado }
        if (agotados.isEmpty()) return Respuesta("Ningún producto se acabó.", listOf(VER_PRODUCTOS))
        return Respuesta("Se acabaron: ${agotados.take(8).joinToString(", ") { "«${it.nombre}»" }}" + (if (agotados.size > 8) " y ${agotados.size - 8} más." else ".") +
            " Para volver a tenerlos, dime por ejemplo «me quedan cinco gorros».", listOf(VER_PRODUCTOS))
    }

    private fun consultarInvitaciones(): Respuesta {
        val p = ctx.invitacionesPendientes()
        if (p.isEmpty()) return Respuesta("No tienes invitaciones por responder.", listOf(Opcion("Ir a «Ferias»", SesionAsistente.ACCION_IR + Destino.FERIAS.name)))
        return Respuesta("Tienes ${p.size} ${if (p.size == 1) "invitación" else "invitaciones"} por responder: " +
            p.joinToString("; ") { "«${it.nombre}», ${it.cuando}" + (it.lugar?.let { l -> ", en $l" } ?: "") } + ".",
            listOf(Opcion("Quiero participar", "quiero participar"), Opcion("No podré asistir", "no podré asistir"), Opcion("Ir a «Ferias»", SesionAsistente.ACCION_IR + Destino.FERIAS.name)))
    }

    private fun consultarUbicacion(comoLlegar: Boolean): Respuesta {
        val feria = ctx.proximaFeria() ?: ctx.invitacionesPendientes().singleOrNull()
            ?: return Respuesta("No tienes una próxima feria confirmada. Si tienes invitaciones, están en «Ferias».", listOf(Opcion("Ir a «Ferias»", SesionAsistente.ACCION_IR + Destino.FERIAS.name)))
        val lugar = feria.lugar?.let { "«${feria.nombre}» es en $it, ${feria.cuando}." } ?: "«${feria.nombre}» no tiene la dirección escrita."
        val mapa = if (feria.lat != null && feria.lng != null) listOf(Opcion("Abrir el mapa", "@mapa:${feria.id}")) else emptyList()
        if (comoLlegar && mapa.isEmpty()) return Respuesta("$lugar No tiene un punto en el mapa, así que no puedo abrir indicaciones.", listOf(Opcion("Ir a «Ferias»", SesionAsistente.ACCION_IR + Destino.FERIAS.name)))
        return Respuesta(lugar + if (mapa.isNotEmpty()) " Puedo abrir el mapa de tu celular con indicaciones." else "", mapa + Opcion("Ir a «Ferias»", SesionAsistente.ACCION_IR + Destino.FERIAS.name))
    }

    /** «Abrir el mapa» (botón): la ubicación sale de los datos de la feria, nunca de lo que se escribió. */
    fun mapaDeFeria(feriaId: String): Accion? {
        val f = (listOfNotNull(ctx.proximaFeria()) + ctx.invitacionesPendientes() + ctx.feriasConfirmadas()).firstOrNull { it.id == feriaId } ?: return null
        return if (f.lat != null && f.lng != null) Accion.ComoLlegar(f.lat, f.lng, f.nombre) else null
    }

    private fun consultarTienda(): Respuesta {
        val t = ctx.tienda() ?: return Respuesta("No encontré los datos de tu tienda.", listOf(Opcion("Ir a «Tienda»", SesionAsistente.ACCION_IR + Destino.TIENDA.name)))
        val partes = listOfNotNull(
            "Tu tienda se llama «${t.nombre}».",
            t.whatsapp?.takeIf { it.isNotBlank() }?.let { "WhatsApp: $it." } ?: "No tiene WhatsApp registrado.",
            t.contacto?.let { "Te contactan por: $it." },
            t.enlace?.let { "Enlace para compartir: $it" }
        )
        return Respuesta(partes.joinToString(" "), listOf(Opcion("Ir a «Tienda»", SesionAsistente.ACCION_IR + Destino.TIENDA.name), Opcion("Compartir mi tienda", SesionAsistente.ACCION_IR + Destino.COMPARTIR.name)))
    }

    companion object {
        private val CANCELAR = Opcion("Cancelar", "cancelar")
        private val VER_PRODUCTOS = Opcion("Ver mis productos", SesionAsistente.ACCION_IR + Destino.MIS_PRODUCTOS.name)
    }
}
