package com.example.trabajodea10.ui.asistente

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.example.trabajodea10.data.FeriasStore
import com.example.trabajodea10.data.ItemVenta
import com.example.trabajodea10.data.Modalidad
import com.example.trabajodea10.data.ReglasCatalogo
import com.example.trabajodea10.data.ReglasFerias
import com.example.trabajodea10.data.ReglasVitrina
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.asistente.SesionAsistente.Accion
import com.example.trabajodea10.data.asistente.SesionAsistente.TipoCambio
import com.example.trabajodea10.data.asistente.SugerenciaVenta
import com.example.trabajodea10.data.remoto.EstadoConexion
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ejecuta las acciones **ya confirmadas** por la persona, siempre con las funciones de siempre
 * (las mismas de «Mis productos», «Venta» y «Mis ferias»), y devuelve el resultado real.
 *
 * - Nunca escribe en la base de datos ni llama al servidor por su cuenta: TramaStore guarda en el
 *   celular y el Sincronizador sube (cantidades con stockBase, ventas con registrar_venta).
 * - Revisa que cada producto sea de la tienda con sesión y que nada haya cambiado entremedio.
 * - Nunca dice «guardado en el servidor» si no lo sabe: dice «quedó en tu celular y se está subiendo».
 */
object EjecutorAsistente {

    /** Lo que necesita para trabajar. [responder] agrega el resultado a la conversación. */
    class Entorno(
        val contexto: Context,
        val nav: ControladorNavegacion,
        val ambito: CoroutineScope,
        val responder: (String) -> Unit
    )

    private fun miProducto(id: String) = TramaStore.productoPorId(id)?.takeIf { it.tiendaId == TramaStore.tienda.id }

    /** Dónde quedó de verdad (la misma regla que el aviso del formulario de productos). */
    private fun estadoGuardado(): String = when {
        !com.example.trabajodea10.data.remoto.Sincronizador.ventasConServidor -> "Quedó guardado solo en este celular (la app no está conectada al servidor)."
        EstadoConexion.conectado -> "Quedó en tu celular y se está subiendo; en «Mis productos» verás si quedó «Guardado»."
        else -> "No hay señal: quedó guardado en tu celular y se sube solo cuando vuelva."
    }

    fun ejecutar(a: Accion, e: Entorno) {
        when (a) {
            is Accion.PrepararVenta -> prepararVenta(a, e, reemplazarCarrito = false, preguntarSiHayCarrito = true)
            is Accion.CambiarInventario -> e.responder(cambiarInventario(a))
            is Accion.CambiarVisibilidad -> e.responder(cambiarVisibilidad(a))
            is Accion.AbrirEdicion -> abrirEdicion(a, e)
            is Accion.ResponderFeria -> responderFeria(a, e)
            is Accion.ComoLlegar -> comoLlegar(a, e)
            else -> Unit   // navegación, formulario y guía los maneja la pantalla
        }
    }

    /**
     * Llena el carrito de «Venta» con los productos confirmados y abre «Venta». No registra la venta:
     * se cobra y confirma ahí, con registrar_venta (atómica e idempotente) como siempre.
     * Si ya había productos en el carrito, pregunta antes de mezclar o reemplazar.
     */
    fun prepararVenta(a: Accion.PrepararVenta, e: Entorno, reemplazarCarrito: Boolean, preguntarSiHayCarrito: Boolean) {
        if (preguntarSiHayCarrito && TramaStore.carritoVenta.isNotEmpty()) {
            AsistenteMemoria.carritoPendiente = a
            return
        }
        val items = a.lineas.mapNotNull { l ->
            val p = miProducto(l.productoId) ?: return@mapNotNull null
            val v = p.variantes.firstOrNull { it.id == l.varianteId } ?: return@mapNotNull null
            ItemVenta(
                productoId = p.id, varianteId = v.id, nombre = p.nombre, detalle = v.nombre,
                precio = ReglasCatalogo.precioDe(p, v), cantidad = l.cantidad,
                fotoUrl = ReglasCatalogo.fotoDe(p, v), encargo = p.modalidad == Modalidad.A_PEDIDO
            )
        }
        if (items.size != a.lineas.size) {
            e.responder("Algún producto ya no está en tu catálogo, así que no preparé la venta. Revísala en «Venta».")
            return
        }
        // Se vuelve a validar con lo que hay ahora (pudo venderse algo entremedio).
        val conCarrito = if (reemplazarCarrito) items else TramaStore.carritoVenta.toList() + items
        TramaStore.validarVenta(conCarrito)?.let { problema ->
            e.responder("No preparé la venta: $problema Revisa las cantidades y vuelve a intentarlo.")
            return
        }
        if (reemplazarCarrito) TramaStore.carritoVenta.clear()
        items.forEach { TramaStore.agregarAlCarrito(it) }
        SugerenciaVenta.preparar(a.medio, if (a.enFeria) feriaDeHoy() else null)
        e.responder("Listo en «Venta». Revisa y toca «Continuar al pago»: se anota cuando confirmes el pago. " +
            if (com.example.trabajodea10.data.remoto.Sincronizador.ventasConServidor) "Si no hay señal, queda anotada en el celular y se sube sola." else "Queda solo en este celular (la app no está conectada al servidor).")
        e.nav.irA(Ruta.EmpVenta)
    }

    /** La feria de hoy en la que participa (si es una sola y está en la lista de la plataforma). */
    private fun feriaDeHoy(): String? {
        val hoy = ReglasFerias.fechaSantiago()
        val ids = TramaStore.feriasPlataforma.map { it.id }.toSet()
        return FeriasStore.misFerias.filter { it.estadoInvitacion.participa && ReglasFerias.ocurreHoy(it, hoy) && it.feriaId in ids }
            .singleOrNull()?.feriaId
    }

    private fun cambiarInventario(a: Accion.CambiarInventario): String {
        val hechos = mutableListOf<String>()
        val avisos = mutableListOf<String>()
        a.cambios.forEach { c ->
            val p = miProducto(c.productoId) ?: run { avisos += "Un producto ya no está en tu catálogo."; return@forEach }
            when (c.tipo) {
                TipoCambio.SE_ACABO -> {
                    if (c.conocida != null && p.stockTotal != c.conocida) { avisos += "«${p.nombre}» cambió mientras tanto (ahora quedan ${p.stockTotal}); no lo toqué."; return@forEach }
                    TramaStore.marcarAgotado(p.id); hechos += "«${p.nombre}» quedó en 0 («Se acabó»)"
                }
                TipoCambio.FIJAR -> {
                    val v = p.variantes.firstOrNull { it.id == c.varianteId } ?: run { avisos += "La opción de «${p.nombre}» ya no existe."; return@forEach }
                    val cantidad = c.cantidad ?: return@forEach
                    if (c.conocida != null && v.stock != c.conocida) { avisos += "«${p.nombre}» cambió mientras tanto (ahora ${v.stock}); no lo toqué. Pídemelo de nuevo."; return@forEach }
                    TramaStore.cambiarCuantosQuedan(p.id, v.id, cantidad); hechos += "«${p.nombre}» quedó en $cantidad"
                }
                TipoCambio.DISPONIBLE -> { TramaStore.cambiarDisponibilidad(listOf(p.id), true); hechos += "«${p.nombre}» quedó disponible" }
            }
        }
        return listOfNotNull(
            hechos.takeIf { it.isNotEmpty() }?.joinToString("; ", postfix = ". ")?.replaceFirstChar { it.uppercase() }?.plus(estadoGuardado()),
            avisos.takeIf { it.isNotEmpty() }?.joinToString(" ")
        ).joinToString(" ").ifBlank { "No cambié nada." }
    }

    private fun cambiarVisibilidad(a: Accion.CambiarVisibilidad): String {
        val cambiados = a.productoIds.mapNotNull { miProducto(it) }.filter { it.publicado != a.visible }
        cambiados.forEach { TramaStore.togglePublicado(it.id) }
        if (cambiados.isEmpty()) return "No había nada que cambiar."
        val como = if (a.visible) "«Lo ven todos»" else "«Solo yo lo veo»"
        return "Listo: ${cambiados.joinToString(", ") { "«${it.nombre}»" }} ${if (cambiados.size == 1) "quedó" else "quedaron"} en $como. ${estadoGuardado()}"
    }

    private fun abrirEdicion(a: Accion.AbrirEdicion, e: Entorno) {
        val p = miProducto(a.productoId) ?: return e.responder("Ese producto ya no está en tu catálogo.")
        // Nunca se pisa otro producto a medio hacer sin preguntar: se avisa y no se abre.
        val otro = TramaStore.borrador?.takeIf { it.producto.id != p.id && (!it.esNuevo || !TramaStore.borradorVacio(it.producto)) }
        if (otro != null) {
            return e.responder("Tienes «${otro.producto.nombre.ifBlank { "un producto sin nombre" }}» a medio hacer en este celular. Termínalo o descártalo en «Mis productos» y vuelve a pedírmelo.")
        }
        val b = TramaStore.abrirEdicion(p.id) ?: return e.responder("No pude abrir el producto.")
        val producto = a.precio?.let { b.producto.copy(precio = it.toDouble()) } ?: b.producto
        TramaStore.actualizarBorrador(producto, a.paso)
        e.responder("Abrí «${p.nombre}». Se guarda cuando toques «Guardar en Mis productos»; si sales sin guardar, el cambio queda a medio hacer en este celular.")
        e.nav.irA(Ruta.ProductoDetalle(p.id))
    }

    /** La misma operación que «Participo» / «No participo» en «Mis ferias», con su resultado real. */
    private fun responderFeria(a: Accion.ResponderFeria, e: Entorno) {
        AsistenteMemoria.estado = "Enviando tu respuesta a la feria…"
        e.ambito.launch {
            val (r, mensaje) = FeriasStore.responder(a.feriaId, a.participo)
            AsistenteMemoria.estado = null
            e.responder(when (r) {
                FeriasStore.ResultadoRespuesta.CONFIRMADA_POR_SERVIDOR ->
                    if (a.participo) "Listo: confirmaste tu participación en «${a.nombre}»." else "Listo: respondiste que no participas en «${a.nombre}»."
                FeriasStore.ResultadoRespuesta.PENDIENTE_DE_CONEXION -> "Sin conexión: tu respuesta quedó guardada en el celular y se enviará al volver la señal. Todavía no está confirmada."
                FeriasStore.ResultadoRespuesta.RECHAZADA -> "No se pudo guardar: ${mensaje ?: "la feria no acepta la respuesta"}."
            })
        }
    }

    private fun comoLlegar(a: Accion.ComoLlegar, e: Entorno) {
        val intento = Intent(Intent.ACTION_VIEW, ReglasVitrina.uriComoLlegar(a.lat, a.lng, a.nombre).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { e.contexto.startActivity(intento) }.onFailure { e.responder("Este celular no tiene una app de mapas para abrir la ubicación.") }
    }
}
