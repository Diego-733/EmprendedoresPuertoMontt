package com.example.trabajodea10.data.asistente

import com.example.trabajodea10.data.asistente.InterpreteGeneral.Intencion
import com.example.trabajodea10.data.asistente.SesionAsistente.Accion
import com.example.trabajodea10.data.asistente.SesionAsistente.Opcion
import com.example.trabajodea10.data.asistente.SesionAsistente.Respuesta
import com.example.trabajodea10.data.asistente.TemasAyuda.Destino

/** Lo que el asistente general puede consultar: solo datos públicos o, en administración, conteos del panel. */
interface ContextoGeneral {
    data class Feria(val id: String, val nombre: String, val cuando: String, val lugar: String)
    data class TiendaPublica(val id: String, val nombre: String, val rubro: String, val sector: String)
    data class ResumenAdmin(val activas: Int, val pendientes: Int, val desactivadas: Int, val sinAutorizacion: Int)

    fun ferias(): List<Feria>
    fun tiendas(): List<TiendaPublica>
    fun conSesion(): Boolean
    fun resumenAdmin(): ResumenAdmin? = null
}

/**
 * Conversación del asistente para visitantes, compradores y administración (Asistente 2.0).
 * Responde, consulta datos reales autorizados y lleva a la pantalla correcta. No ejecuta acciones
 * sensibles: lo administrativo importante se explica y se abre la pantalla con sus confirmaciones.
 * Nunca dice códigos de activación ni contraseñas.
 */
class AsistenteGeneral(private val rol: InterpreteGeneral.Rol, private val ctx: ContextoGeneral) {

    private fun ir(destino: Destino) = SesionAsistente.ACCION_IR + destino.name

    fun saludo(nombre: String?): Respuesta = Respuesta(
        (if (nombre.isNullOrBlank()) "Hola. " else "Hola, $nombre. ") + when (rol) {
            InterpreteGeneral.Rol.ADMIN -> "Soy el asistente de Alerce. Te ayudo a encontrar cuentas, pendientes, ferias y herramientas del panel."
            else -> "Soy el asistente de Alerce. Dime qué buscas: un producto, una tienda, una feria o ayuda con la app."
        }, sugerencias()
    )

    private fun sugerencias(): List<Opcion> = when (rol) {
        InterpreteGeneral.Rol.ADMIN -> listOf(
            Opcion("¿Hay autorizaciones pendientes?", "¿Hay autorizaciones pendientes?"),
            Opcion("¿Cómo autorizo a una emprendedora?", "¿Cómo autorizo a una emprendedora?"),
            Opcion("Abrir ferias", "abre las ferias"), Opcion("Bitácora", "muéstrame la bitácora"))
        else -> listOf(
            Opcion("Busco algo para regalar", "Busco algo para regalar"),
            Opcion("¿Qué ferias hay?", "¿Qué ferias hay próximamente?"),
            Opcion("¿Cómo compro?", "¿Cómo compro un producto?"), Opcion("Quiero vender en Alerce", "Quiero vender en Alerce"))
    }

    fun responder(entrada: String): Respuesta {
        val texto = entrada.trim()
        if (texto.isEmpty()) return Respuesta("No escuché nada. Puedes escribirlo o tocar una opción.", sugerencias())
        if (texto.startsWith("@buscar:")) return buscar(texto.removePrefix("@buscar:"))
        val r = InterpreteGeneral.clasificar(texto, rol)
        return when (r.intencion) {
            Intencion.SALUDO -> saludo(null)
            Intencion.AYUDA -> Respuesta("Te abro la ayuda.", accion = Accion.Navegar(if (rol == InterpreteGeneral.Rol.ADMIN) Destino.ADMIN_GESTION else Destino.AYUDA_PUBLICA))
            Intencion.AJUSTES -> Respuesta("Te abro los ajustes: ahí están el tamaño de la letra, la lectura en voz alta y el contraste.", accion = Accion.Navegar(Destino.AJUSTES))

            Intencion.BUSCAR -> r.termino?.let { buscar(it) } ?: Respuesta("¿Qué quieres buscar? Por ejemplo: «gorros de lana» o «pan amasado».")
            Intencion.EXPLORAR_CATEGORIAS -> {
                val rubros = ctx.tiendas().map { it.rubro }.filter { it.isNotBlank() }.distinct().take(5)
                Respuesta("¿Qué le gusta a esa persona? Estos son los tipos de tienda que hay en la vitrina:" +
                    if (rubros.isEmpty()) " aún no hay tiendas cargadas." else "",
                    rubros.map { Opcion("Ver $it", "@buscar:$it") } + Opcion("Ver todos los productos", ir(Destino.VITRINA)))
            }
            Intencion.VER_TIENDAS -> Respuesta(
                r.termino?.let { "Te muestro los emprendimientos de «$it»." } ?: "Te muestro los emprendimientos de la vitrina.",
                accion = Accion.Buscar(termino = r.termino, verTiendas = true))
            Intencion.VER_ASOCIACIONES -> Respuesta("Te muestro las asociaciones.", accion = Accion.Buscar(termino = null, verTiendas = false, verAsociaciones = true))
            Intencion.LIMPIAR_FILTROS -> Respuesta("Listo, te muestro la vitrina sin filtros.", accion = Accion.Buscar(termino = "", verTiendas = false, limpiar = true))
            Intencion.FERIAS_PROXIMAS -> {
                val f = ctx.ferias().take(3)
                if (f.isEmpty()) Respuesta("Por ahora no hay ferias publicadas por venir.", listOf(Opcion("Ver el mapa de ferias", ir(Destino.MAPA_FERIAS))))
                else Respuesta("Estas son las próximas ferias: " + f.joinToString("; ") { "${it.nombre}, ${it.cuando}" + (if (it.lugar.isNotBlank()) ", en ${it.lugar}" else "") } + ".",
                    listOf(Opcion("Ver el mapa de ferias", ir(Destino.MAPA_FERIAS))))
            }
            Intencion.MAPA_FERIAS -> Respuesta("Te abro el mapa de ferias.", accion = Accion.Navegar(Destino.MAPA_FERIAS))
            Intencion.UBICACION_TIENDA, Intencion.CONTACTAR -> {
                val tienda = r.nombre?.let { n -> ctx.tiendas().firstOrNull { Canonizador.sinTildes(it.nombre).contains(Canonizador.sinTildes(n)) } }
                val como = if (r.intencion == Intencion.CONTACTAR)
                    "En la ficha de la tienda o del producto toca «WhatsApp» o «Llamar»: el mensaje le llega directo a esa emprendedora."
                else "En la ficha de la tienda está su punto de retiro o de venta, con «Cómo llegar» si lo tiene."
                if (tienda != null) Respuesta("$como Te abro «${tienda.nombre}».", accion = Accion.AbrirTienda(tienda.id))
                else Respuesta("$como ¿Qué tienda buscas? Dime su nombre o lo que vende.", listOf(Opcion("Ver emprendimientos", ir(Destino.VITRINA))))
            }
            Intencion.COMO_COMPRAR -> Respuesta("En Alerce le compras directo a cada emprendedora: abre el producto, revisa el precio y toca «WhatsApp» o «Llamar» para acordar el pedido y la entrega. La app no cobra pagos en línea.",
                listOf(Opcion("Ver productos", ir(Destino.VITRINA))))
            Intencion.COMPARTIR -> Respuesta("En la ficha de la tienda o del producto toca «Compartir»: puedes mandarlo por WhatsApp o mostrar su código QR.")
            Intencion.TIENDAS_SEGUIDAS, Intencion.AVISOS, Intencion.SEGUIR_TIENDA, Intencion.CALIFICAR -> conCuenta(r.intencion)
            Intencion.PERFIL, Intencion.INICIAR_SESION -> Respuesta(
                if (ctx.conSesion()) "Te abro «Mi perfil»." else "Te abro «Mi perfil»: ahí puedes entrar con tu cuenta o con Google.",
                accion = Accion.Navegar(Destino.PERFIL))
            Intencion.RECUPERAR_ACCESO -> Respuesta("Para recuperar tu acceso: en «Mi perfil» toca «Ingresar», escribe tu correo y toca «¿Olvidaste tu contraseña?». Te llega un correo con un enlace para crear una nueva. Si entras con Google, usa «Continuar con Google».",
                accion = Accion.Navegar(Destino.PERFIL))
            Intencion.QUIERO_VENDER -> Respuesta("Para vender en Alerce, la administración autoriza tu correo y te entrega un código de activación. Con ese código: crea tu cuenta con ese correo y tu propia contraseña, y en «Mi perfil» toca «Activar mi tienda con un código».",
                listOf(Opcion("Abrir «Mi perfil»", ir(Destino.PERFIL))))
            Intencion.NO_PERMITIDO -> Respuesta("Eso es solo para emprendedoras autorizadas o para la administración. Puedo ayudarte a buscar productos, tiendas o ferias.", sugerencias())

            // ---- Administración ----
            Intencion.ADMIN_CUENTAS -> Respuesta(
                r.nombre?.let { "Busco «$it» en las cuentas." } ?: "Te abro las cuentas de emprendedoras.",
                accion = Accion.BuscarCuenta(r.nombre, soloPendientes = false))
            Intencion.ADMIN_PENDIENTES -> {
                val res = ctx.resumenAdmin()
                Respuesta(res?.let { if (it.pendientes == 0) "No hay autorizaciones pendientes." else "Hay ${it.pendientes} autorización(es) pendiente(s). Te las muestro." }
                    ?: "Te muestro las cuentas pendientes.", accion = Accion.BuscarCuenta(null, soloPendientes = true))
            }
            Intencion.ADMIN_ESTADOS -> {
                val res = ctx.resumenAdmin()
                Respuesta(res?.let { "Tiendas activas: ${it.activas}. Desactivadas: ${it.desactivadas}. Pendientes: ${it.pendientes}. Sin autorización registrada: ${it.sinAutorizacion}." }
                    ?: "Aún no tengo los datos del panel. Ábrelo para cargarlos.", listOf(Opcion("Abrir «Cuentas»", ir(Destino.ADMIN_CUENTAS))))
            }
            Intencion.ADMIN_AUTORIZAR -> Respuesta("Para autorizar a una emprendedora: en «Cuentas» toca el botón con la persona y el «+», escribe su nombre y correo y toca «Autorizar y generar código». El código aparece una sola vez: entrégaselo en persona. Yo no genero ni leo códigos.",
                pasos = listOf("Abre «Cuentas».", "Toca «Autorizar una emprendedora» (arriba a la derecha).", "Escribe nombre y correo; si quieres, su asociación.", "Toca «Autorizar y generar código» y entrégalo en persona."),
                opciones = listOf(Opcion("Abrir «Cuentas»", ir(Destino.ADMIN_CUENTAS))))
            Intencion.ADMIN_FERIAS -> Respuesta("Te abro «Ferias». Toca una feria para editarla, publicarla, invitar tiendas o ver la asistencia.", accion = Accion.Navegar(Destino.ADMIN_FERIAS))
            Intencion.ADMIN_CREAR_FERIA -> Respuesta("En «Ferias» toca «Nueva feria»: se guarda como borrador y la publicas cuando esté lista.", accion = Accion.Navegar(Destino.ADMIN_FERIAS))
            Intencion.ADMIN_ASISTENCIA -> Respuesta("La asistencia se marca desde el día de la feria: en «Ferias» toca la feria y, en «Tiendas en la feria», elige «Asistió» o «No asistió».", accion = Accion.Navegar(Destino.ADMIN_FERIAS))
            Intencion.ADMIN_ASOCIACIONES -> Respuesta("Te abro «Asociaciones»: ahí creas, editas y cambias sus integrantes.", accion = Accion.Navegar(Destino.ADMIN_ASOCIACIONES))
            Intencion.ADMIN_TIPOS -> Respuesta("Te abro «Tipos de producto».", accion = Accion.Navegar(Destino.ADMIN_TIPOS))
            Intencion.ADMIN_EQUIPO -> Respuesta("Te abro «Administradores». Crear o desactivar cuentas de administración se hace ahí, con confirmación.", accion = Accion.Navegar(Destino.ADMIN_EQUIPO))
            Intencion.ADMIN_BITACORA -> Respuesta("Te abro la bitácora: muestra quién cambió qué y cuándo.", accion = Accion.Navegar(Destino.ADMIN_BITACORA))
            Intencion.ADMIN_CONFIGURACION -> Respuesta("En «Gestión» › «App sin cuenta» eliges qué ve quien abre la app sin iniciar sesión.", accion = Accion.Navegar(Destino.ADMIN_GESTION))
            Intencion.ADMIN_DIAGNOSTICO -> Respuesta("El diagnóstico técnico (App Check) está al final de «Ajustes».", accion = Accion.Navegar(Destino.AJUSTES))
            Intencion.ADMIN_ACCION_SENSIBLE -> Respuesta("Por seguridad, eso no lo hago por voz ni leo códigos o contraseñas. Hazlo tú en la pantalla correspondiente, donde se pide confirmar.",
                listOf(Opcion("Abrir «Cuentas»", ir(Destino.ADMIN_CUENTAS)), Opcion("Abrir «Administradores»", ir(Destino.ADMIN_EQUIPO))))

            Intencion.DESCONOCIDA -> {
                val busqueda = r.termino
                if (rol != InterpreteGeneral.Rol.ADMIN && busqueda != null)
                    Respuesta("No estoy seguro de lo que necesitas. ¿Quieres que busque «$busqueda» en la vitrina?", listOf(Opcion("Sí, buscar «$busqueda»", "@buscar:$busqueda")) + sugerencias())
                else Respuesta("No estoy seguro de lo que necesitas. Prueba con una de estas opciones o dilo con otras palabras.", sugerencias())
            }
        }
    }

    private fun buscar(termino: String): Respuesta =
        Respuesta("Busco «$termino» en la vitrina.", accion = Accion.Buscar(termino = termino, verTiendas = false))

    private fun conCuenta(i: Intencion): Respuesta {
        if (!ctx.conSesion()) return Respuesta("Para seguir tiendas, calificar y recibir avisos necesitas entrar con tu cuenta (o con Google).",
            listOf(Opcion("Abrir «Mi perfil»", ir(Destino.PERFIL))))
        return when (i) {
            Intencion.SEGUIR_TIENDA -> Respuesta("Abre la tienda y toca «Seguir»: te avisaremos de sus ferias.", listOf(Opcion("Ver emprendimientos", ir(Destino.VITRINA))))
            Intencion.CALIFICAR -> Respuesta("Abre la tienda y, en «Calificaciones», elige las estrellas. Solo si la emprendedora tiene las calificaciones activas.")
            else -> Respuesta("Te abro «Mi perfil»: ahí están las tiendas que sigues y tus avisos.", accion = Accion.Navegar(Destino.PERFIL))
        }
    }
}
