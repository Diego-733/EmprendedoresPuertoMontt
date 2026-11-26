package com.example.trabajodea10.data.asistente

import com.example.trabajodea10.data.ItemVenta
import com.example.trabajodea10.data.MedioPago
import com.example.trabajodea10.data.Producto

/**
 * Arquitectura del Asistente Alerce (de la voz al resultado):
 *
 *  1. Entrada: voz (dictado del sistema) o texto ............ PantallaAsistente
 *  2. Normalización y números ................................ NumerosEs
 *  3. Intención (puntaje por señales) y datos dichos ........ MotorInterpretacion → InterpreteAlerce, LectorVentas
 *  4. Contexto de la conversación, ambigüedad y preguntas ... SesionAsistente, GestorOperaciones
 *  5. Productos reales de la tienda ......................... ResolutorProductos (+ ContextoAsistente)
 *  6. Acción tipada + validación + confirmación ............. SesionAsistente.Accion, Confirmacion
 *  7. Ejecución con las funciones de siempre ................ EjecutorAsistente (UI) → TramaStore / FeriasStore
 *  8. Resultado real (guardado, pendiente o rechazado) ...... EjecutorAsistente
 *
 * El motor de interpretación es intercambiable: hoy es [MotorLocal] (reglas, sin internet ni costo).
 * Un motor futuro (por ejemplo, IA en un servidor) solo podría devolver el mismo [InterpreteAlerce.Resultado]:
 * una intención de la lista cerrada y datos tipados. Nunca ejecuta nada ni decide confirmar: todo lo que
 * cambia datos sigue pasando por la confirmación de la persona y por las funciones de siempre.
 */
interface MotorInterpretacion {
    fun interpretar(frase: String): InterpreteAlerce.Resultado
}

/** Motor local por reglas (no es inteligencia artificial). */
object MotorLocal : MotorInterpretacion {
    /** Asistente 2.0: primero la capa flexible (voz, tipeo, diminutivos y formas equivalentes), luego las reglas. */
    override fun interpretar(frase: String): InterpreteAlerce.Resultado = InterpreteAlerce.interpretar(Canonizador.canonizar(frase))
}

/**
 * Validación de lo que entregue cualquier motor antes de usarlo: intenciones de la lista cerrada,
 * montos y cantidades dentro de los límites de la app. Un motor externo que devuelva algo fuera de
 * esto se trata como «no entendí».
 */
object ValidadorResultado {
    fun validar(r: InterpreteAlerce.Resultado): InterpreteAlerce.Resultado {
        val d = r.datos
        val precioMal = d.precio != null && (d.precio <= 0 || d.precio > 99_999_999)
        val cantidadMal = d.cantidad != null && (d.cantidad < 0 || d.cantidad > 9999)
        val nombreMal = (d.nombre?.length ?: 0) > 120 || (d.descripcion?.length ?: 0) > 1000
        return if (precioMal || cantidadMal || nombreMal) InterpreteAlerce.Resultado(InterpreteAlerce.Intencion.DESCONOCIDA) else r
    }
}

/**
 * Lo que el asistente puede consultar, siempre de la cuenta con la sesión abierta y solo leyendo.
 * Las funciones nuevas tienen valor por defecto para que una implementación parcial (pruebas) funcione.
 */
interface ContextoAsistente {
    data class ResumenVentas(val total: Long, val cantidad: Int, val pendientes: Int)
    data class ResumenFeria(
        val nombre: String, val cuando: String, val lugar: String?,
        val id: String = "", val lat: Double? = null, val lng: Double? = null
    )
    data class ResumenTienda(val nombre: String, val whatsapp: String?, val enlace: String?, val contacto: String?)

    fun ventasDelMes(): ResumenVentas
    fun proximaFeria(): ResumenFeria?
    fun invitacionesPorResponder(): Int

    /** Productos de la tienda con sesión (nunca de otra). */
    fun productos(): List<Producto> = emptyList()
    /** Unidades vendidas este mes de esos productos (ventas locales de la cuenta, incluidas las por subir). */
    fun unidadesVendidasEsteMes(productoId: String): Int = 0
    /** Invitaciones que se pueden responder hoy (las mismas que muestran «Participo / No participo»). */
    fun invitacionesPendientes(): List<ResumenFeria> = emptyList()
    /** Ferias en las que ya participa. */
    fun feriasConfirmadas(): List<ResumenFeria> = emptyList()
    fun tienda(): ResumenTienda? = null
    /** Mismas validaciones de «Venta» (cantidades, pieza única…). null = se puede. */
    fun validarVenta(items: List<ItemVenta>): String? = null
    fun precioDe(producto: Producto, varianteId: String?): Double =
        producto.variantes.firstOrNull { it.id == varianteId }?.precio ?: producto.precio
}

/**
 * Lo que el asistente dejó elegido para la pantalla de pago («fue en efectivo», «en la feria»).
 * Se lee una sola vez como valor inicial; la persona lo puede cambiar. Se borra al cerrar sesión.
 */
object SugerenciaVenta {
    private var medio: MedioPago? = null
    private var lugar: String? = null

    fun preparar(medio: MedioPago?, lugar: String?) { this.medio = medio; this.lugar = lugar }
    fun tomarMedio(): MedioPago? = medio.also { medio = null }
    fun tomarLugar(): String? = lugar.also { lugar = null }
    fun olvidar() { medio = null; lugar = null }
}
