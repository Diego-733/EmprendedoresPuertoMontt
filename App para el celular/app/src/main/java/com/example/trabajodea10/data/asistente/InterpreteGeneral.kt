package com.example.trabajodea10.data.asistente

/**
 * Asistente 2.0 para quienes NO son emprendedoras: visitantes, compradores y administración.
 * Mismo enfoque que InterpreteAlerce (puntaje por señales sobre el texto ya pasado por el Canonizador),
 * pero con intenciones de navegación, búsqueda, consulta y ayuda. Nunca ejecuta acciones sensibles:
 * en administración, autorizar, generar códigos, revocar, desactivar o crear administradores solo se
 * explican y se abre la pantalla; los códigos y contraseñas nunca se leen ni se dicen.
 */
object InterpreteGeneral {

    enum class Rol { VISITANTE, COMPRADOR, ADMIN }

    enum class Intencion {
        // Comunes
        SALUDO, AYUDA, AJUSTES, DESCONOCIDA,
        // Visitante y comprador
        BUSCAR, EXPLORAR_CATEGORIAS, VER_TIENDAS, VER_ASOCIACIONES, LIMPIAR_FILTROS, FERIAS_PROXIMAS, MAPA_FERIAS,
        UBICACION_TIENDA, CONTACTAR, COMO_COMPRAR, COMPARTIR, TIENDAS_SEGUIDAS, SEGUIR_TIENDA, CALIFICAR, AVISOS, PERFIL,
        INICIAR_SESION, RECUPERAR_ACCESO, QUIERO_VENDER, NO_PERMITIDO,
        // Administración
        ADMIN_CUENTAS, ADMIN_PENDIENTES, ADMIN_ESTADOS, ADMIN_AUTORIZAR, ADMIN_FERIAS, ADMIN_CREAR_FERIA, ADMIN_ASISTENCIA,
        ADMIN_ASOCIACIONES, ADMIN_TIPOS, ADMIN_EQUIPO, ADMIN_BITACORA, ADMIN_CONFIGURACION, ADMIN_DIAGNOSTICO, ADMIN_ACCION_SENSIBLE
    }

    /** [termino]: lo que se busca. [nombre]: persona o tienda mencionada. */
    data class Resultado(val intencion: Intencion, val termino: String? = null, val nombre: String? = null)

    private class Regla(val intencion: Intencion, val peso: Int, val senal: Regex, val roles: Set<Rol>)

    private val PUBLICO = setOf(Rol.VISITANTE, Rol.COMPRADOR)
    private val ADMIN = setOf(Rol.ADMIN)
    private val TODOS = setOf(Rol.VISITANTE, Rol.COMPRADOR, Rol.ADMIN)
    private fun re(p: String) = Regex(p)

    private val REGLAS = listOf(
        // ---- Administración (las sensibles pesan más: nunca se ejecutan, se explican) ----
        Regla(Intencion.ADMIN_ACCION_SENSIBLE, 40, re("\\b(desactiva|desactivar|desactivala|revoca|revocar|revocale|elimina|eliminar|borra|borrar|bloquea|bloquear)\\b" +
            "|\\b(crea|crear|agrega|agregar|nuevo|nueva|haz) (un |una |otro |otra )?(administrador|administradora|admin)\\b|\\b(administrador|administradora) nuev[oa]\\b" +
            "|\\b(genera|generar|dame|dime|leeme|lee|leer|muestrame|mandame) (el |la |un |una )?(codigo|contrasena|clave)\\b|\\b(contrasena|clave) de\\b|\\bleeme\\b.*\\b(contrasena|clave|codigo)\\b"), ADMIN),
        Regla(Intencion.ADMIN_PENDIENTES, 35, re("\\b(pendientes?|falta(n)? (por )?registrar|quien falta|quienes faltan|sin activar|por activar|por registrarse)\\b"), ADMIN),
        Regla(Intencion.ADMIN_ESTADOS, 33, re("\\bcuant[ao]s (emprendedoras|cuentas|tiendas|vendedoras)\\b"), ADMIN),
        Regla(Intencion.ADMIN_CREAR_FERIA, 32, re("\\b(crear|crea|nueva|agregar|agrega|armar|arma|publicar|publica) (una )?feria\\b|^nueva feria$"), ADMIN),
        Regla(Intencion.ADMIN_ASISTENCIA, 31, re("\\b(asistencia|asistio|asistieron|checklist|pase de lista|quien (fue|vino|llego))\\b"), ADMIN),
        Regla(Intencion.ADMIN_AUTORIZAR, 30, re("\\b(autoriz\\w*|dar de alta|habilitar|habilita|dar acceso|agregar (una |a una )?(emprendedora|vendedora)|nueva (emprendedora|vendedora)|codigo de activacion)\\b"), ADMIN),
        Regla(Intencion.ADMIN_DIAGNOSTICO, 28, re("\\b(diagnostico|app check|play integrity|proteccion de la app)\\b"), ADMIN),
        Regla(Intencion.ADMIN_CONFIGURACION, 27, re("\\b(configuracion|app sin cuenta|navegacion anonima|sin cuenta|configurar la app)\\b"), ADMIN),
        Regla(Intencion.ADMIN_BITACORA, 26, re("\\b(bitacora|registro de cambios|historial de cambios|quien cambio|actividad reciente)\\b"), ADMIN),
        Regla(Intencion.ADMIN_EQUIPO, 26, re("\\b(administradores|administradoras|administrador|equipo de administracion|quienes administran)\\b"), ADMIN),
        Regla(Intencion.ADMIN_ASOCIACIONES, 24, re("\\b(asociacion|asociaciones|integrantes)\\b"), ADMIN),
        Regla(Intencion.ADMIN_TIPOS, 24, re("\\b(tipos? de productos?|categorias de productos?)\\b"), ADMIN),
        Regla(Intencion.ADMIN_FERIAS, 22, re("\\b(ferias?|invitar|invitaciones?)\\b"), ADMIN),
        Regla(Intencion.ADMIN_CUENTAS, 20, re("\\b(emprendedoras?|cuentas?|vendedoras?|tiendas)\\b|^busca(r|me)? (a |la cuenta de |la tienda de )?\\p{L}+"), ADMIN),

        // ---- Visitante y comprador ----
        Regla(Intencion.QUIERO_VENDER, 31, re("\\b(quiero|como|me gustaria|puedo|podria) (vender|abrir (mi |una )?tienda|ser emprendedora|tener (mi |una )?tienda)\\b" +
            "|\\babr(o|ir) mi tienda\\b|\\bvender (mis productos|en alerce|aqui)\\b|\\bcomo vendo\\b"), PUBLICO),
        Regla(Intencion.NO_PERMITIDO, 30, re("\\b(registrar|anotar) (una )?venta\\b|\\bvendi\\b|\\b(agrega|agregar|sube|subir|crea|crear) (un )?producto\\b|\\bmis productos\\b" +
            "|\\bautoriz\\w*\\b|\\bbitacora\\b|\\b(crear|crea) (una )?feria\\b|\\badministra\\w*\\b"), PUBLICO),
        Regla(Intencion.TIENDAS_SEGUIDAS, 28, re("\\b(tiendas|negocios|emprendimientos) que sigo\\b|\\bque tiendas sigo\\b|\\bmis (tiendas )?favorit\\w*\\b|\\blo que sigo\\b|\\btiendas seguidas\\b"), PUBLICO),
        Regla(Intencion.RECUPERAR_ACCESO, 27, re("\\b(olvide|olvidaste|no recuerdo|no me acuerdo|recuperar|no puedo entrar|no puedo ingresar|perdi)\\b"), TODOS),
        Regla(Intencion.LIMPIAR_FILTROS, 26, re("\\b(quita|quitar|limpia|limpiar|borra|borrar|saca|sacar) (los |el |todos los )?filtros?\\b|\\bver todo\\b"), PUBLICO),
        Regla(Intencion.EXPLORAR_CATEGORIAS, 25, re("\\b(regalo|regalar|regalito|obsequio|algo bonito|que me recomiendas|no se que (comprar|buscar|regalar)|ideas)\\b"), PUBLICO),
        Regla(Intencion.UBICACION_TIENDA, 24, re("\\b(donde (queda|esta)|como (llego|llegar|voy)|direccion|ubicacion)\\b.{0,25}\\b(tienda|local|negocio|emprendimiento)\\b"), PUBLICO),
        Regla(Intencion.MAPA_FERIAS, 24, re("\\bmapa\\b"), PUBLICO),
        Regla(Intencion.CONTACTAR, 23, re("\\b(hablar con|contactar|contacto|escribirle|escribir a|llamar a|llamarla|llamarlo|whatsapp)\\b|\\bla persona que vende\\b|\\b(vendedora|vendedor)\\b"), PUBLICO),
        Regla(Intencion.FERIAS_PROXIMAS, 22, re("\\b(ferias?)\\b"), PUBLICO),
        Regla(Intencion.COMO_COMPRAR, 22, re("\\b(como|donde) (compro|comprar|pido|pedir|hago un pedido|encargo|encargar)\\b|\\b(quiero comprar|hacer un pedido|mi pedido|carrito)\\b"), PUBLICO),
        Regla(Intencion.CALIFICAR, 22, re("\\b(calific\\w*|estrellas|opinion|resena|evaluar|puntuar)\\b"), PUBLICO),
        Regla(Intencion.INICIAR_SESION, 22, re("\\b(iniciar sesion|inicio de sesion|entrar (a|con) (mi )?(cuenta|google)|ingresar|login)\\b"), PUBLICO),
        Regla(Intencion.VER_ASOCIACIONES, 21, re("\\b(asociacion|asociaciones)\\b"), PUBLICO),
        Regla(Intencion.COMPARTIR, 21, re("\\b(compart\\w*|enviar el (link|enlace)|mandar (la tienda|el producto))\\b"), PUBLICO),
        Regla(Intencion.VER_TIENDAS, 20, re("\\b(emprendimientos?|tiendas|negocios|locales|emprendedoras)\\b"), PUBLICO),
        Regla(Intencion.SEGUIR_TIENDA, 20, re("\\b(seguir (a |una |la |esta |esa )?(tienda|emprendimiento|emprendedora)|como sigo)\\b"), PUBLICO),
        Regla(Intencion.AVISOS, 20, re("\\b(avisos|notificaciones|alertas)\\b"), PUBLICO),
        Regla(Intencion.PERFIL, 18, re("\\b(perfil|mi cuenta)\\b"), PUBLICO),
        Regla(Intencion.BUSCAR, 10, re("^(busco|buscar|buscame|busca|muestrame|quiero ver|tienen|venden|hay|necesito|me interesa|donde (encuentro|compro|venden|hay))\\b"), PUBLICO),

        // ---- Comunes ----
        Regla(Intencion.AJUSTES, 6, re("\\b(tamano de (la )?letra|letra (mas )?(grande|chica)|ajustes|leer en voz alta|lectura en voz alta|contraste|modo sencillo)\\b"), TODOS),
        Regla(Intencion.AYUDA, 2, re("^(ayuda|necesito ayuda|ayudame)$|\\b(centro de ayuda|estoy perdid\\w|no se usar|no entiendo nada)\\b"), TODOS),
        Regla(Intencion.SALUDO, 1, re("^(hola|buenas|buenos dias|buenas tardes|buenas noches|alo)\\b"), TODOS)
    )

    fun normalizar(frase: String): String =
        Canonizador.sinTildes(Canonizador.canonizar(frase)).replace(Regex("[^a-z0-9ñ ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val ARTICULOS = Regex("^(un|una|unos|unas|el|la|los|las|algo de|algun|alguna|algunos|algunas)\\s+")
    private val INICIO_BUSQUEDA = Regex("^(busco|buscar|buscame|busca|muestrame|quiero ver|tienen|venden|hay|necesito|me interesa|donde encuentro|donde compro|donde venden|donde hay|ver)\\s+")

    /** Lo que se busca, sin el verbo ni los artículos («muéstrame gorros de lana» → «gorros de lana»). */
    fun termino(t: String): String? {
        var s = t.replace(INICIO_BUSQUEDA, "")
        repeat(2) { s = s.replace(ARTICULOS, "") }
        s = s.replace(Regex("\\b(por favor|porfa|en la vitrina|aqui|aca)\\b"), "").trim()
        return s.takeIf { it.length >= 2 }
    }

    /** Lo que viene después de «de/del/en» en «emprendimientos de Alerce Norte» o «tiendas de artesanía». */
    fun complemento(t: String): String? =
        Regex("\\b(?:emprendimientos?|tiendas|negocios|locales|emprendedoras) (?:de la |de los |del |de |en )(.+)$").find(t)?.groupValues?.get(1)?.trim()

    fun clasificar(frase: String, rol: Rol): Resultado {
        val t = normalizar(frase)
        if (t.isBlank()) return Resultado(Intencion.DESCONOCIDA)
        val ganadora = REGLAS.filter { rol in it.roles && it.senal.containsMatchIn(t) }.maxByOrNull { it.peso }?.intencion
            ?: return Resultado(Intencion.DESCONOCIDA, termino = t.takeIf { rol != Rol.ADMIN && it.split(' ').size <= 6 })
        return when (ganadora) {
            Intencion.BUSCAR -> Resultado(ganadora, termino = termino(t))
            Intencion.VER_TIENDAS -> Resultado(ganadora, termino = complemento(t))
            Intencion.ADMIN_CUENTAS -> Resultado(ganadora, nombre = Regex("^busca(?:r|me)? (?:a |la cuenta de |la tienda de )?(.+)$").find(t)?.groupValues?.get(1)?.trim())
            Intencion.UBICACION_TIENDA, Intencion.CONTACTAR ->
                Resultado(ganadora, nombre = Regex("\\b(?:tienda|local|negocio) (?:de |del |de la )?(\\p{L}[\\p{L} ]{2,30})$").find(t)?.groupValues?.get(1)?.trim()
                    ?.takeUnless { it.startsWith("esta") || it.startsWith("ese") })
            else -> Resultado(ganadora)
        }
    }
}
