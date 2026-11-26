package com.example.trabajodea10.data

import kotlinx.serialization.Serializable

enum class Rol(val etiqueta: String) {
    ADMIN("Administradora"),
    EMPRENDEDORA("Emprendedora"),
    VISITANTE("Visitante")
}

enum class EstadoFeria(val etiqueta: String) {
    EN_CURSO("En curso"),
    PROXIMA("Próxima"),
    BORRADOR("Borrador"),
    FINALIZADA("Finalizada")
}

enum class EstadoAsistencia(val etiqueta: String) {
    PRESENTE("Presente"),
    PENDIENTE("Pendiente"),
    RECHAZADA("Rechazada")
}

enum class EstadoCuenta(val etiqueta: String) {
    ACTIVA("Activa"),
    SUSPENDIDA("Suspendida"),
    INVITADA("Invitada")
}

enum class EstadoInvitacion(val etiqueta: String) {
    CONFIRMADA("Confirmada"),
    PENDIENTE("Pendiente"),
    RECHAZADA("Rechazada")
}

enum class MedioPago(val etiqueta: String) {
    EFECTIVO("Efectivo"),
    TRANSFERENCIA("Transferencia"),
    DEBITO("Débito"),
    QR("QR")
}

enum class Categoria(val etiqueta: String) {
    TEXTIL("Textil"),
    CUERO("Cuero"),
    JOYERIA("Joyería"),
    COSMETICA("Cosmética"),
    GOURMET("Gourmet"),
    DECORACION("Decoración")
}

@Serializable
data class Participacion(
    val emprendedoraId: String,
    val asistencia: EstadoAsistencia = EstadoAsistencia.PENDIENTE,
    val invitacion: EstadoInvitacion = EstadoInvitacion.CONFIRMADA
)

@Serializable
data class Feria(
    val id: String,
    val nombre: String,
    val ubicacion: String,
    val fechaInicio: String,
    val fechaFin: String,
    val horario: String = "9 a 18 hs",
    val cupos: Int,
    val precioEntrada: Double,
    val estado: EstadoFeria,
    val participaciones: List<Participacion> = emptyList(),
    val organizadora: String = "Red Trama",
    val fotoUrl: String? = null,
    val estiloMapa: String = "Satelital"
)

@Serializable
data class Asociacion(
    val id: String,
    val nombre: String,
    val localidad: String,
    val color: Long
)

@Serializable
data class Emprendedora(
    val id: String,
    val nombre: String,
    val email: String,
    val telefono: String,
    val asociacionId: String?,
    val estado: EstadoCuenta,
    val color: Long,
    val seguidores: Int = 0
)

/** Cómo se vende un producto (RF-PRO-06). */
enum class Modalidad(val etiqueta: String, val explicacion: String) {
    REGULAR("Regular", "Se vende de inmediato mientras queden unidades."),
    A_PEDIDO("A pedido", "Lo haces por encargo: no se agota."),
    PIEZA_UNICA("Pieza única", "Una sola unidad en total. Al venderse desaparece para siempre.")
}

/** Qué pasa cuando no quedan unidades vendibles entre las opciones (RF-PRO-08). */
enum class AlAgotarse(val etiqueta: String) {
    MOSTRAR_AGOTADO("Mostrar «Se acabó»"),
    OCULTAR("Ocultar de la tienda"),
    PASAR_A_PEDIDO("Pasar a «A pedido»")
}

/** Lo que ve el público (mismo contrato que la web y el servidor). */
enum class EstadoPublico(val etiqueta: String) {
    DISPONIBLE("Disponible"), AGOTADO("Se acabó"), A_PEDIDO("A pedido"), OCULTO("No se muestra")
}

/** Estado de una venta respecto del servidor. */
enum class EstadoSync { PENDIENTE, CONFIRMADA, CONFLICTO }

/**
 * Opción de un producto (talla, color, sabor) con su propio precio, foto y cantidad.
 * precio = null usa el precio del producto. stockBase es la cantidad que tenía
 * el servidor la última vez (solo en el celular): permite enviar diferencias
 * sin pisar ventas hechas en otro dispositivo.
 */
@Serializable
data class Variante(
    val id: String,
    val nombre: String,
    val stock: Int,
    val precio: Double? = null,
    val fotoUrl: String? = null,
    val stockBase: Int? = null
)

@Serializable
data class Producto(
    val id: String,
    val tiendaId: String = "emp1",
    val nombre: String,
    val categoria: Categoria,
    val precio: Double,
    val publicado: Boolean,
    val destacado: Boolean,
    val colorFoto: Long,
    val variantes: List<Variante>,
    val fotoUrl: String? = null,
    val descripcion: String = "",
    val tipo: String? = null,
    val atributos: Map<String, String> = emptyMap(),
    val fotos: List<String> = emptyList(),
    val modalidad: Modalidad = Modalidad.REGULAR,
    val manejaStock: Boolean = true,
    val alAgotarse: AlAgotarse = AlAgotarse.MOSTRAR_AGOTADO,
    val disponible: Boolean = true,
    val vendidaEn: String? = null
) {
    val stockTotal: Int get() = ReglasCatalogo.unidades(this)
    /** Sin unidades vendibles (solo si lleva la cuenta y no es a pedido). */
    val agotado: Boolean
        get() = manejaStock && modalidad != Modalidad.A_PEDIDO && (vendidaEn != null || stockTotal == 0)
    val stockBajo: Boolean get() = manejaStock && modalidad == Modalidad.REGULAR && stockTotal in 1..3
}

@Serializable
data class ItemVenta(
    val productoId: String,
    val varianteId: String,
    val nombre: String,
    val detalle: String,
    val precio: Double,
    val cantidad: Int,
    val fotoUrl: String? = null,
    /** Encargo: se anota la venta pero no descuenta unidades (a pedido). */
    val encargo: Boolean = false
)

@Serializable
data class Venta(
    val id: String,
    val fecha: String,
    val items: List<ItemVenta>,
    val medioPago: MedioPago,
    val feriaId: String?,
    val total: Double,
    val lugarTipo: String? = null,
    val lugarNombre: String? = null,
    /** false: historial desactivado, solo se guardó el registro técnico mínimo. */
    val enHistorial: Boolean = true,
    val estadoSync: EstadoSync = EstadoSync.CONFIRMADA,
    val mensajeConflicto: String? = null
) {
    val lugarLegible: String
        get() = when {
            lugarTipo == "OTRO" && !lugarNombre.isNullOrBlank() -> lugarNombre
            else -> ""
        }
}

/** Valores con los que nace un producto nuevo (RF-PRO-11). */
@Serializable
data class ValoresProducto(
    val categoria: Categoria = Categoria.TEXTIL,
    val modalidad: Modalidad = Modalidad.REGULAR,
    val manejaStock: Boolean = true,
    val alAgotarse: AlAgotarse = AlAgotarse.MOSTRAR_AGOTADO,
    val publicado: Boolean = false,
    val disponible: Boolean = true
)

/** Campo específico de un tipo de producto (lo define la administración en tipos_producto). */
@Serializable
data class CampoTipo(
    val clave: String,
    val etiqueta: String,
    val tipo: String = "texto",
    val opciones: List<String> = emptyList(),
    val obligatorio: Boolean = false
)

@Serializable
data class TipoProducto(
    val id: String,
    val categoria: String,
    val nombre: String,
    val campos: List<CampoTipo> = emptyList()
)

/** Feria registrada en la plataforma (tabla ferias del servidor). */
@Serializable
data class FeriaPlataforma(val id: String, val nombre: String)

/** Producto que se está creando o editando; se guarda en el celular a cada cambio. */
@Serializable
data class BorradorProducto(
    val producto: Producto,
    val esNuevo: Boolean,
    val paso: Int = 0
)

@Serializable
data class NecesidadesTienda(
    val tieneStock: Boolean = true,
    val tieneAgendamiento: Boolean = false,
    val tieneVentaPorWhatsApp: Boolean = true,
    val tieneProductosAPedido: Boolean = false,
    val participaEnFerias: Boolean = true,
    val tieneForoComunidad: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "tieneStock" to tieneStock,
        "tieneAgendamiento" to tieneAgendamiento,
        "tieneVentaPorWhatsApp" to tieneVentaPorWhatsApp,
        "tieneProductosAPedido" to tieneProductosAPedido,
        "participaEnFerias" to participaEnFerias,
        "tieneForoComunidad" to tieneForoComunidad
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): NecesidadesTienda {
            return NecesidadesTienda(
                tieneStock = (map["tieneStock"] as? Boolean) ?: true,
                tieneAgendamiento = (map["tieneAgendamiento"] as? Boolean) ?: false,
                tieneVentaPorWhatsApp = (map["tieneVentaPorWhatsApp"] as? Boolean) ?: true,
                tieneProductosAPedido = (map["tieneProductosAPedido"] as? Boolean) ?: false,
                participaEnFerias = (map["participaEnFerias"] as? Boolean) ?: true,
                tieneForoComunidad = (map["tieneForoComunidad"] as? Boolean) ?: false
            )
        }
    }
}

/** Cómo la contactan desde la vitrina (RF-CFG-02, RF-CMP-05). */
@Serializable
enum class ModoContacto(val etiqueta: String) {
    WHATSAPP("Solo WhatsApp"),
    FORMULARIO("Solo formulario"),
    AMBOS("WhatsApp y formulario");

    val usaWhatsApp: Boolean get() = this == WHATSAPP || this == AMBOS
    val usaFormulario: Boolean get() = this == FORMULARIO || this == AMBOS

    companion object {
        fun desde(valor: String?): ModoContacto =
            entries.firstOrNull { it.name == valor?.uppercase() } ?: AMBOS
    }
}

/** Qué hace la app cuando se abre sin sesión (RF-ACC-06, la elige el administrador en la web). */
enum class NavegacionAnonima { PERMITIR, INVITAR, OBLIGAR;
    companion object {
        fun desde(valor: String?): NavegacionAnonima =
            entries.firstOrNull { it.name == valor?.uppercase() } ?: PERMITIR
    }
}

/** Asociación pública (RF-ASO-03). */
data class AsociacionPublica(
    val id: String,
    val nombre: String,
    val sector: String = "",
    val localidad: String = "",
    val descripcion: String = "",
    val activa: Boolean = true
)

/** Feria publicada en el servidor, con su ubicación (RF-FER-01, RF-MAP-02). */
data class FeriaPublica(
    val id: String,
    val nombre: String,
    val descripcion: String = "",
    val imagenUrl: String? = null,
    val lugar: String = "",
    val fechaInicio: String? = null,   // AAAA-MM-DD
    val fechaFin: String? = null,
    val horario: String = "",
    val organizador: String = "",
    val estado: String = "PROXIMA",
    val lat: Double? = null,
    val lng: Double? = null
) {
    val tieneUbicacion: Boolean get() = lat != null && lng != null
}

/** Mensaje que llegó por el formulario de la vitrina (RF-CFG-03). */
data class ContactoRecibido(
    val id: String,
    val nombre: String,
    val medio: String,
    val mensaje: String,
    val productoId: String?,
    val leido: Boolean,
    val creadoEn: String
)

@Serializable
data class Tienda(
    val id: String = "emp1",
    val nombre: String = "Mi Tienda",
    val rubro: String = "Artesanía y Comercio",
    val sector: String = "Alerce",
    val bio: String = "",
    // Sin número inventado: vacío hasta que la dueña escriba el suyo (antes venía "+56 9 1234 5678").
    val whatsapp: String = "",
    val instagram: String = "@tienda.alerce",
    val colorPortada: Long = 0xFF5C315B,
    val colorLogo: Long = 0xFF2E7D5B,
    val logoUrl: String? = null,
    val necesidades: NecesidadesTienda = NecesidadesTienda(),
    val historialActivo: Boolean = true,
    val valoresProducto: ValoresProducto = ValoresProducto(),
    // Entrega 6: vitrina y mapas
    val mostrarPrecios: Boolean = true,
    val contactoModo: ModoContacto = ModoContacto.AMBOS,
    val asociacionId: String? = null,
    val puntoLat: Double? = null,
    val puntoLng: Double? = null,
    val puntoNombre: String? = null,
    // Entrega 7: la emprendedora decide si recibe calificaciones (RF-CFG-05)
    val calificacionesActivas: Boolean = false
) {
    val tienePuntoRetiro: Boolean get() = puntoLat != null && puntoLng != null
    /** WhatsApp solo si lo eligió y dejó un número chileno válido que no sea de ejemplo (ContactoWhatsApp). */
    val ofreceWhatsApp: Boolean get() = contactoModo.usaWhatsApp && ContactoWhatsApp.numeroContacto(whatsapp) != null
    /** Eligió WhatsApp, pero el número falta, no es válido o es de ejemplo: se avisa en vez de abrir un chat equivocado. */
    val faltaWhatsApp: Boolean get() = contactoModo.usaWhatsApp && ContactoWhatsApp.numeroContacto(whatsapp) == null
    val ofreceFormulario: Boolean get() = contactoModo.usaFormulario
    /** Ni WhatsApp utilizable ni mensajes por la vitrina. */
    val sinCanalContacto: Boolean get() = !ofreceWhatsApp && !ofreceFormulario
    /** La frase que explica por qué no hay botón de WhatsApp ("" si lo hay). */
    val avisoContacto: String get() = when {
        sinCanalContacto -> ContactoWhatsApp.SIN_CONTACTO
        faltaWhatsApp -> ContactoWhatsApp.SIN_WHATSAPP
        else -> ""
    }

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "nombre" to nombre,
        "rubro" to rubro,
        "sector" to sector,
        "bio" to bio,
        "whatsapp" to whatsapp,
        "instagram" to instagram,
        "colorPortada" to colorPortada,
        "colorLogo" to colorLogo,
        "logoUrl" to (logoUrl ?: ""),
        "necesidades" to necesidades.toMap()
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): Tienda {
            val necMap = map["necesidades"] as? Map<String, Any?> ?: emptyMap()
            return Tienda(
                id = id,
                nombre = (map["nombre"] as? String) ?: "Comercio Local",
                rubro = (map["rubro"] as? String) ?: "Comercio",
                sector = (map["sector"] as? String) ?: "Alerce",
                bio = (map["bio"] as? String) ?: "",
                whatsapp = (map["whatsapp"] as? String) ?: "",   // sin número inventado
                instagram = (map["instagram"] as? String) ?: "",
                colorPortada = (map["colorPortada"] as? Number)?.toLong() ?: 0xFF5C315B,
                colorLogo = (map["colorLogo"] as? Number)?.toLong() ?: 0xFF2E7D5B,
                logoUrl = (map["logoUrl"] as? String)?.takeIf { it.isNotBlank() },
                necesidades = NecesidadesTienda.fromMap(necMap)
            )
        }
    }
}

@Serializable
data class ConfigSistema(
    val moneda: String = "CLP – Peso chileno",
    val zonaHoraria: String = "America/Santiago",
    val registroPublico: Boolean = true,
    val catalogoPublico: Boolean = true,
    val chatDirecto: Boolean = true,
    val feriasPublicas: Boolean = true
)

@Serializable
data class PreferenciasEmprendedora(
    val alertasStock: Boolean = true,
    val recordatoriosFeria: Boolean = true,
    val resumenVentas: Boolean = false,
    /** Inicio con botones grandes y pocas opciones (opcional y reversible; se guarda por cuenta). */
    val modoSencillo: Boolean = false
)

/**
 * Fotografía completa del estado de la app que se guarda en disco para que
 * todo siga igual cuando se cierra y se vuelve a abrir la aplicación.
 */
@Serializable
data class EstadoApp(
    val rol: Rol? = null,
    val emprendedoraActualId: String = "emp1",
    val asociaciones: List<Asociacion> = emptyList(),
    val emprendedoras: List<Emprendedora> = emptyList(),
    val ferias: List<Feria> = emptyList(),
    val productos: List<Producto> = emptyList(),
    val ventas: List<Venta> = emptyList(),
    val tienda: Tienda = MockData.tienda,
    val config: ConfigSistema = ConfigSistema(),
    val prefs: PreferenciasEmprendedora = PreferenciasEmprendedora(),
    val inscripcionesVisitante: List<String> = emptyList(),
    val escalaTexto: Float = 1f,
    val tiposProducto: List<TipoProducto> = emptyList(),
    val feriasPlataforma: List<FeriaPlataforma> = emptyList(),
    val borrador: BorradorProducto? = null
)

data class BloqueHorario(
    val id: String,
    val tiendaId: String,
    val fecha: String,             // Formato YYYY-MM-DD
    val hora: String,              // Formato HH:mm
    val duracion: String = "45 min",
    val reservado: Boolean = false,
    val clienteNombre: String? = null,
    val clienteTelefono: String? = null,
    val servicio: String? = null
)

data class RespuestaForo(
    val id: String,
    val autorNombre: String,
    val esEmprendedor: Boolean = false,
    val texto: String,
    val fecha: String
)

data class PublicacionForo(
    val id: String,
    val tiendaId: String,
    val autorNombre: String,
    val esEmprendedor: Boolean = false,
    val texto: String,
    val fecha: String,
    val respuestas: List<RespuestaForo> = emptyList()
)
