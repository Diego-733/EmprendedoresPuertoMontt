package com.example.trabajodea10.ui.asistente

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.trabajodea10.data.EstadoInvitacion7
import com.example.trabajodea10.data.EstadoSync
import com.example.trabajodea10.data.AdminStore
import com.example.trabajodea10.data.FeriasStore
import com.example.trabajodea10.data.GuiaProducto
import com.example.trabajodea10.data.ItemVenta
import com.example.trabajodea10.data.MiFeria
import com.example.trabajodea10.data.Producto
import com.example.trabajodea10.data.ReglasCatalogo
import com.example.trabajodea10.data.ReglasFerias
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.asistente.ContextoAsistente
import com.example.trabajodea10.data.asistente.NumerosEs
import com.example.trabajodea10.data.asistente.SesionAsistente
import com.example.trabajodea10.data.asistente.SesionAsistente.Accion
import com.example.trabajodea10.data.asistente.SesionAsistente.Confirmacion
import com.example.trabajodea10.data.asistente.SesionAsistente.Opcion
import com.example.trabajodea10.data.asistente.SesionAsistente.ProductoAsistido
import com.example.trabajodea10.data.asistente.SugerenciaVenta
import com.example.trabajodea10.data.asistente.TemasAyuda.Destino
import com.example.trabajodea10.data.mesActualIso
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.BotonMicrofono
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.ChipFiltro
import com.example.trabajodea10.ui.componentes.ContenedorChips
import com.example.trabajodea10.ui.componentes.FotoProducto
import com.example.trabajodea10.ui.componentes.dictadoDisponible
import com.example.trabajodea10.ui.componentes.rememberDictadoPorVoz
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.publico.urlTienda
import com.example.trabajodea10.voz.BotonEscuchar
import com.example.trabajodea10.voz.LectorVoz
import java.util.Calendar

/**
 * Lo que el asistente puede consultar: SOLO el estado local de la cuenta con sesión abierta
 * (las mismas listas que muestran Inicio, Mis productos, Historial y Ferias). No consulta la base
 * de datos, no ejecuta SQL ni inventa: si una venta aún no se sube, lo dice.
 */
class ContextoAsistenteApp : ContextoAsistente {

    override fun ventasDelMes(): ContextoAsistente.ResumenVentas {
        val mes = mesActualIso()
        val delMes = TramaStore.ventas.filter { it.fecha.startsWith(mes) }
        return ContextoAsistente.ResumenVentas(
            total = delMes.sumOf { it.total }.toLong(),
            cantidad = delMes.size,
            pendientes = delMes.count { it.estadoSync == EstadoSync.PENDIENTE }
        )
    }

    private fun resumen(f: MiFeria, hoy: String): ContextoAsistente.ResumenFeria {
        val cuando = when {
            ReglasFerias.ocurreHoy(f, hoy) -> "hoy"
            f.fechaInicio != null -> fechaLegible(f.fechaInicio)
            else -> "con fecha por confirmar"
        } + (f.horario.takeIf { it.isNotBlank() }?.let { ", de $it" } ?: "")
        return ContextoAsistente.ResumenFeria(f.nombre, cuando, f.ubicacion.takeIf { it.isNotBlank() }, f.feriaId, f.lat, f.lng)
    }

    override fun proximaFeria(): ContextoAsistente.ResumenFeria? {
        val hoy = ReglasFerias.fechaSantiago()
        // La misma «próxima feria» que muestra Inicio (calendario sin rechazadas, canceladas ni pasadas).
        val f = ReglasFerias.calendario(FeriasStore.misFerias.toList(), hoy)
            .flatMap { it.second }.firstOrNull { it.estadoInvitacion.participa } ?: return null
        return resumen(f, hoy)
    }

    override fun invitacionesPorResponder(): Int = invitacionesPendientes().size

    /** Las mismas invitaciones que muestran «Participo / No participo» en «Mis ferias». */
    override fun invitacionesPendientes(): List<ContextoAsistente.ResumenFeria> {
        val hoy = ReglasFerias.fechaSantiago()
        return FeriasStore.misFerias.filter {
            it.estadoInvitacion == EstadoInvitacion7.PENDIENTE && ReglasFerias.feriaActiva(it.estado, it.fechaInicio, it.fechaFin, hoy)
        }.map { resumen(it, hoy) }
    }

    override fun feriasConfirmadas(): List<ContextoAsistente.ResumenFeria> {
        val hoy = ReglasFerias.fechaSantiago()
        return ReglasFerias.calendario(FeriasStore.misFerias.toList(), hoy).flatMap { it.second }
            .filter { it.estadoInvitacion.participa }.map { resumen(it, hoy) }
    }

    override fun productos(): List<Producto> = TramaStore.productosDeMiTienda()

    override fun unidadesVendidasEsteMes(productoId: String): Int {
        val mes = mesActualIso()
        return TramaStore.ventas.filter { it.fecha.startsWith(mes) }.flatMap { it.items }.filter { it.productoId == productoId }.sumOf { it.cantidad }
    }

    override fun tienda(): ContextoAsistente.ResumenTienda {
        val t = TramaStore.tienda
        return ContextoAsistente.ResumenTienda(t.nombre, t.whatsapp.takeIf { it.isNotBlank() }, urlTienda(t.id), t.contactoModo.etiqueta)
    }

    override fun validarVenta(items: List<ItemVenta>): String? = TramaStore.validarVenta(TramaStore.carritoVenta.toList() + items)

    override fun precioDe(producto: Producto, varianteId: String?): Double =
        ReglasCatalogo.precioDe(producto, producto.variantes.firstOrNull { it.id == varianteId })

    private fun fechaLegible(iso: String): String {
        val p = iso.take(10).split("-").mapNotNull { it.toIntOrNull() }
        if (p.size != 3) return "el $iso"
        val c = Calendar.getInstance().apply { set(p[0], p[1] - 1, p[2]) }
        val dias = listOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")
        val meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
        return "el ${dias[c.get(Calendar.DAY_OF_WEEK) - 1]} ${p[2]} de ${meses[p[1] - 1]}"
    }
}

/**
 * La conversación vive mientras la app está abierta (no se guarda en el celular ni en el
 * servidor, y nunca se guarda audio). Si cambia la cuenta o la tienda, empieza de cero; al cerrar
 * sesión se borra.
 */
object AsistenteMemoria {
    data class Mensaje(
        val id: Int,
        val deAsistente: Boolean,
        val texto: String,
        val pasos: List<String> = emptyList(),
        val opciones: List<Opcion> = emptyList(),
        val confirmacion: Confirmacion? = null,
        /** false: ya se respondió (se confirmó, se canceló o se habló de otra cosa); la tarjeta queda solo como registro. */
        val confirmacionVigente: Boolean = true
    ) {
        val paraLeer: String get() = (listOf(texto) + pasos.mapIndexed { i, p -> "Paso ${i + 1}: $p" } + listOfNotNull(confirmacion?.paraLeer)).joinToString(" ")
    }

    private var cuenta: String? = null
    private var siguienteId = 0
    var sesion: SesionAsistente? = null
        private set
    val mensajes = mutableStateListOf<Mensaje>()
    /** Copia observable del producto que se está preparando (para la tarjeta editable). */
    var producto by mutableStateOf<ProductoAsistido?>(null)
        private set
    /** «Tienes un producto a medio hacer»: aquí y no en la pantalla, para que girar el celular no lo pierda. */
    var conflicto by mutableStateOf<Conflicto?>(null)
    /** Venta confirmada cuando «Venta» ya tenía productos: se pregunta si agregar o empezar de nuevo. */
    var carritoPendiente by mutableStateOf<Accion.PrepararVenta?>(null)
    /** «Enviando tu respuesta…»: lo que está pasando ahora (null = esperando lo que digas). */
    var estado by mutableStateOf<String?>(null)

    data class Conflicto(val producto: ProductoAsistido, val nombreBorrador: String, val borradorEsNuevo: Boolean)

    /** [cuentaId] = cuenta con sesión + tienda: dos cuentas nunca comparten conversación ni producto anotado. */
    /** Visitante, comprador o administración: conversación de solo consulta y navegación (AsistenteGeneral). */
    var general: com.example.trabajodea10.data.asistente.AsistenteGeneral? = null
        private set

    fun asegurar(cuentaId: String) {
        val rolGeneral = ContextoGeneralApp.rolActual()
        val clave = "$cuentaId|${rolGeneral ?: "EMPRENDEDORA"}"
        if (sesion != null && cuenta == clave) return
        cuenta = clave
        val s = SesionAsistente(ContextoAsistenteApp())
        sesion = s
        general = rolGeneral?.let { com.example.trabajodea10.data.asistente.AsistenteGeneral(it, ContextoGeneralApp(it)) }
        mensajes.clear()
        producto = null
        conflicto = null
        carritoPendiente = null
        estado = null
        SugerenciaVenta.olvidar()
        agregar(general?.saludo(SesionManager.nombreSaludo) ?: s.saludo(SesionManager.nombreSaludo))
    }

    /** Al cerrar sesión: se olvida la conversación y lo preparado (en un celular compartido nadie más lo ve ni lo ejecuta). */
    fun olvidar() {
        cuenta = null
        sesion = null
        general = null
        mensajes.clear()
        producto = null
        conflicto = null
        carritoPendiente = null
        estado = null
        SugerenciaVenta.olvidar()
        LectorVoz.detener()
    }

    fun agregar(r: SesionAsistente.Respuesta): Mensaje {
        val m = Mensaje(siguienteId++, true, r.texto, r.pasos, r.opciones, r.confirmacion)
        // Las opciones y confirmaciones viejas dejan de valer: solo vale la última respuesta.
        for (i in mensajes.indices) {
            val v = mensajes[i]
            if (v.opciones.isNotEmpty() || (v.confirmacion != null && v.confirmacionVigente)) mensajes[i] = v.copy(opciones = emptyList(), confirmacionVigente = false)
        }
        mensajes.add(m)
        refrescarProducto()
        return m
    }

    fun agregarDePersona(texto: String) { mensajes.add(Mensaje(siguienteId++, false, texto)) }

    fun refrescarProducto() { producto = sesion?.producto }
}

@Composable
fun PantallaAsistente(nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val contexto = LocalContext.current
    AsistenteMemoria.asegurar("${TramaStore.cuentaActual}|${TramaStore.tienda.id}")
    val sesion = AsistenteMemoria.sesion ?: return
    val mensajes = AsistenteMemoria.mensajes
    val lista = rememberLazyListState()
    val ambito = rememberCoroutineScope()
    var frase by rememberSaveable { mutableStateOf("") }
    val hayDictado = remember { dictadoDisponible(contexto) }

    // Al salir de esta pantalla, la lectura se detiene.
    DisposableEffect(Unit) { onDispose { if (LectorVoz.leyendo?.startsWith("asistente") == true) LectorVoz.detener() } }

    fun hablarSiCorresponde(m: AsistenteMemoria.Mensaje) {
        if (LectorVoz.debeLeerSolo(contexto)) LectorVoz.hablar("asistente-${m.id}", m.paraLeer)
    }

    fun decir(texto: String) { hablarSiCorresponde(AsistenteMemoria.agregar(SesionAsistente.Respuesta(texto))) }

    val entorno = EjecutorAsistente.Entorno(contexto, nav, ambito) { decir(it) }

    fun ir(destino: Destino) {
        when (destino) {
            Destino.MIS_PRODUCTOS -> nav.irA(Ruta.EmpCatalogo)
            Destino.VENTA -> nav.irA(Ruta.EmpVenta)
            Destino.FERIAS -> nav.irA(Ruta.EmpFerias)
            Destino.TIENDA -> nav.irA(Ruta.EmpTienda)
            Destino.COMPARTIR -> nav.irA(Ruta.EmpTiendaQR(TramaStore.tienda.id))
            Destino.MENSAJES -> nav.irA(Ruta.BandejaContactos)
            Destino.HISTORIAL -> nav.irA(Ruta.HistorialVentas)
            Destino.INICIO -> nav.seleccionarTab(Ruta.EmpInicio)
            Destino.NUEVO_PRODUCTO -> nav.irA(Ruta.ProductoDetalle(TramaStore.crearProductoBorrador()))
            Destino.AJUSTES -> nav.irA(Ruta.Ajustes)
            Destino.AYUDA -> nav.irA(Ruta.CentroAyuda)
            Destino.VITRINA -> nav.seleccionarTab(Ruta.VisProductos)
            Destino.MAPA_FERIAS -> nav.seleccionarTab(Ruta.VisInicio)
            Destino.AYUDA_PUBLICA -> nav.seleccionarTab(Ruta.VisAyuda)
            Destino.PERFIL -> nav.seleccionarTab(Ruta.Ajustes)
            Destino.ADMIN_INICIO -> nav.seleccionarTab(Ruta.AdminInicio)
            Destino.ADMIN_CUENTAS -> nav.seleccionarTab(Ruta.AdminEmprendedoras)
            Destino.ADMIN_FERIAS -> nav.seleccionarTab(Ruta.AdminFerias)
            Destino.ADMIN_GESTION -> nav.seleccionarTab(Ruta.AdminGestion)
            Destino.ADMIN_ASOCIACIONES -> nav.irA(Ruta.AdminAsociaciones)
            Destino.ADMIN_TIPOS -> nav.irA(Ruta.AdminTipos)
            Destino.ADMIN_EQUIPO -> nav.irA(Ruta.AdminEquipo)
            Destino.ADMIN_BITACORA -> nav.irA(Ruta.AdminBitacora)
        }
    }

    fun iniciarGuia(foto: Boolean) {
        // Igual que «🧭 Guíame paso a paso» de Mis productos: si había un producto a medio hacer, se retoma.
        val intencion = if (foto) GuiaProducto.Intencion.FOTO else GuiaProducto.Intencion.PRODUCTO
        val id = TramaStore.crearProductoBorrador()
        TramaStore.borrador?.let { TramaStore.actualizarBorrador(it.producto, intencion.paso ?: 0) }
        GuiaProducto.iniciar(intencion)
        nav.irA(Ruta.ProductoDetalle(id))
    }

    fun abrirFormulario(p: ProductoAsistido, reemplazar: Boolean) {
        when (val r = TramaStore.prepararProductoAsistido(p.nombre.orEmpty(), p.descripcion, p.precio, p.cantidad, reemplazar)) {
            is TramaStore.PreparacionAsistida.Listo -> {
                GuiaProducto.iniciar(GuiaProducto.Intencion.PRODUCTO)
                nav.irA(Ruta.ProductoDetalle(r.id))
            }
            is TramaStore.PreparacionAsistida.HayBorrador -> AsistenteMemoria.conflicto = AsistenteMemoria.Conflicto(p, r.nombre, r.esNuevo)
        }
    }

    fun enviar(entrada: String, mostrar: String = entrada) {
        val texto = entrada.trim()
        if (texto.isEmpty()) return
        frase = ""
        AsistenteMemoria.agregarDePersona(mostrar.trim())
        when {
            texto.startsWith(SesionAsistente.ACCION_IR) -> {
                val destino = runCatching { Destino.valueOf(texto.removePrefix(SesionAsistente.ACCION_IR)) }.getOrNull() ?: return
                ir(destino)
            }
            texto == SesionAsistente.ACCION_GUIA -> iniciarGuia(foto = false)
            texto == SesionAsistente.ACCION_GUIA_FOTO -> iniciarGuia(foto = true)
            else -> {
                val r = AsistenteMemoria.general?.responder(texto) ?: sesion.responder(texto)
                val m = AsistenteMemoria.agregar(r)
                when (val a = r.accion) {
                    is Accion.Navegar -> ir(a.destino)
                    is Accion.Buscar -> { PedidoVitrina.pendiente = a; nav.seleccionarTab(Ruta.VisProductos) }
                    is Accion.AbrirTienda -> nav.irA(Ruta.TiendaPublica(a.tiendaId))
                    is Accion.BuscarCuenta -> {
                        AdminStore.busquedaAlAbrir = a.texto
                        if (a.soloPendientes) AdminStore.filtroAlAbrir = com.example.trabajodea10.data.EstadoCuentaAdmin.PENDIENTE_ACTIVACION
                        nav.seleccionarTab(Ruta.AdminEmprendedoras)
                    }
                    is Accion.IniciarGuia -> iniciarGuia(a.foto)
                    is Accion.AbrirFormularioProducto -> abrirFormulario(a.datos, reemplazar = false)
                    null -> hablarSiCorresponde(m)
                    // Ventas, cantidades, quién lo ve, edición y ferias: ya confirmadas por la persona.
                    else -> EjecutorAsistente.ejecutar(a, entorno)
                }
            }
        }
    }

    val dictar = rememberDictadoPorVoz(
        indicacion = "Dime qué necesitas",
        onSinTexto = { avisar("No se escuchó nada. Puedes escribirlo o tocar una opción.") }
    ) { texto -> enviar(texto) }

    // Se lleva a la vista el último mensaje (la pregunta del asistente), no la tarjeta del producto:
    // así siempre se lee qué se preguntó; la tarjeta queda justo debajo.
    LaunchedEffect(mensajes.size) {
        if (mensajes.isNotEmpty()) lista.animateScrollToItem(mensajes.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        BarraSuperior(
            titulo = "Asistente Alerce",
            subtitulo = "Enséñame o ayúdame a hacerlo",
            onVolver = { nav.volver() },
            acciones = {
                val auto = LectorVoz.lecturaAutomatica
                IconButton(onClick = { LectorVoz.cambiarLecturaAutomatica(!auto) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (auto) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (auto) "Lectura automática encendida. Tocar para apagarla" else "Lectura automática apagada. Tocar para encenderla"
                    )
                }
            }
        )
        LazyColumn(
            state = lista,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(mensajes, key = { it.id }) { m ->
                if (m.deAsistente) BurbujaAsistente(m) { o -> enviar(o.entrada, o.etiqueta) }
                else BurbujaPersona(m.texto)
            }
            AsistenteMemoria.producto?.let { p ->
                item(key = "producto") {
                    TarjetaProductoAsistido(
                        p = p,
                        onCambio = { n, d, pr, c -> sesion.editarAMano(n, d, pr, c); AsistenteMemoria.refrescarProducto() },
                        onContinuar = { enviar("continuar", "Continuar") },
                        onCancelar = { enviar("cancelar", "Cancelar") }
                    )
                }
            }
        }
        // Entrada: escribir o dictar (el micrófono se abre solo al tocarlo).
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Qué está pasando: enviando algo, esperando una confirmación o esperando lo que digas.
                val ultima = mensajes.lastOrNull { it.deAsistente }
                val pendiente = ultima?.confirmacion?.takeIf { ultima.confirmacionVigente && ultima.opciones.isNotEmpty() }
                val estado = AsistenteMemoria.estado
                estado?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
                // Confirmar y Cancelar quedan fijos aquí (siempre a la vista, también con letra grande).
                pendiente?.let { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        BotonPrimario(texto = c.confirmar, onClick = { enviar("@confirmar:${c.id}", c.confirmar) })
                        BotonSecundario(texto = "Cancelar", onClick = { enviar("cancelar", "Cancelar") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = frase,
                        // Enter (teclado físico o el de la pantalla) envía, igual que el botón «Enviar».
                        onValueChange = { t -> if (t.contains('\n')) enviar(t.replace('\n', ' ')) else frase = t },
                        modifier = Modifier.weight(1f),
                        label = { Text("Escribe o dicta", maxLines = 1) },
                        placeholder = { Text("Ej: vendí dos gorros", maxLines = 2) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { enviar(frase) }),
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = if (hayDictado) ({ BotonMicrofono(onClick = dictar, descripcion = "Hablar con el asistente") }) else null
                    )
                    IconButton(onClick = { enviar(frase) }, enabled = frase.isNotBlank(), modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                    }
                }
                // Aviso breve al empezar (con letra grande no debe tapar la conversación).
                if (mensajes.size <= 1) Text(
                    if (hayDictado) "🎙 Escucha solo cuando tocas el micrófono. El dictado lo hace tu celular (puede usar internet). Alerce no guarda audio."
                    else "Este celular no tiene dictado por voz: puedes escribir o tocar las opciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    AsistenteMemoria.conflicto?.let { (p, nombre, esNuevo) ->
        AlertDialog(
            onDismissRequest = { AsistenteMemoria.conflicto = null; sesion.retomar(p); AsistenteMemoria.refrescarProducto() },
            title = { Text(if (esNuevo) "Tienes un producto a medio hacer" else "Tienes cambios sin guardar") },
            text = {
                Text(
                    if (esNuevo) "«$nombre» quedó sin terminar en este celular. ¿Lo reemplazo por «${p.nombre}»? Lo que tenía se borra."
                    else "Estabas cambiando «$nombre» y no guardaste. ¿Descarto esos cambios y abro «${p.nombre}»? «$nombre» sigue en «Mis productos» como estaba."
                )
            },
            confirmButton = {
                TextButton(onClick = { AsistenteMemoria.conflicto = null; abrirFormulario(p, reemplazar = true) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (esNuevo) "Sí, reemplazar" else "Sí, descartar cambios", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    AsistenteMemoria.conflicto = null
                    sesion.retomar(p)
                    AsistenteMemoria.agregar(SesionAsistente.Respuesta(
                        "No cambié nada. «$nombre» sigue en tu celular y tu producto nuevo sigue anotado aquí abajo. " +
                            "Puedes terminar «$nombre» en «Mis productos» y volver a tocar «Continuar».",
                        listOf(Opcion("Ir a «Mis productos»", SesionAsistente.ACCION_IR + Destino.MIS_PRODUCTOS.name))
                    ))
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("No, mantenerlo") }
            }
        )
    }

    AsistenteMemoria.carritoPendiente?.let { a ->
        val cuantos = TramaStore.carritoVenta.sumOf { it.cantidad }
        AlertDialog(
            onDismissRequest = { AsistenteMemoria.carritoPendiente = null; decir("No preparé la venta. Lo que había en «Venta» sigue igual.") },
            title = { Text("«Venta» ya tiene productos") },
            text = { Text("Hay $cuantos ${if (cuantos == 1) "unidad" else "unidades"} esperando en «Venta». ¿Agrego esta venta a esas o empiezo de nuevo?") },
            confirmButton = {
                TextButton(onClick = { AsistenteMemoria.carritoPendiente = null; EjecutorAsistente.prepararVenta(a, entorno, reemplazarCarrito = false, preguntarSiHayCarrito = false) },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Agregar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { AsistenteMemoria.carritoPendiente = null; decir("No preparé la venta. Lo que había en «Venta» sigue igual.") },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") }
                    TextButton(onClick = { AsistenteMemoria.carritoPendiente = null; EjecutorAsistente.prepararVenta(a, entorno, reemplazarCarrito = true, preguntarSiHayCarrito = false) },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Empezar de nuevo") }
                }
            }
        )
    }
}

@Composable
private fun BurbujaAsistente(m: AsistenteMemoria.Mensaje, onOpcion: (Opcion) -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(if (m.confirmacion != null || m.opciones.any { it.productoId != null }) 1f else 0.92f)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Solo el texto avisa a TalkBack (y no la burbuja entera): al quitar las opciones viejas no se repite el mensaje.
            Text(m.texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            m.pasos.forEachIndexed { i, p ->
                Row {
                    Text("${i + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 24.dp), color = MaterialTheme.colorScheme.primary)
                    Text(p, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            BotonEscuchar(id = "asistente-${m.id}", texto = m.paraLeer, detenerAlSalir = false)
            m.confirmacion?.let { c -> TarjetaConfirmacion(c, vigente = m.confirmacionVigente && m.opciones.isNotEmpty()) }
            // Productos: con foto, precio y cuántos quedan (no se elige por la persona sin mostrarlos).
            val productos = m.opciones.filter { it.productoId != null }
            productos.forEach { o -> FilaOpcionProducto(o) { onOpcion(o) } }
            val resto = m.opciones.filter { it.productoId == null && !(m.confirmacion != null && (it.entrada.startsWith("@confirmar:") || it.entrada == "cancelar")) }
            if (resto.isNotEmpty()) {
                ContenedorChips(espacioHorizontal = 8.dp, espacioVertical = 8.dp) {
                    resto.forEach { o -> ChipFiltro(texto = o.etiqueta, seleccionado = false, onClick = { onOpcion(o) }) }
                }
            }
        }
    }
}

/** Tarjeta «Revisa antes de confirmar»: lo que se hará y el resultado esperado, con botones grandes. */
@Composable
private fun TarjetaConfirmacion(c: Confirmacion, vigente: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, if (vigente) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            c.lineas.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
            c.nota?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // Los botones están fijos abajo, sobre el campo de texto (así no quedan fuera de la pantalla).
            Text(if (vigente) "Para hacerlo, toca «${c.confirmar}» abajo, o di «sí». Para no hacer nada, «Cancelar»." else "Ya no está esperando respuesta.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilaOpcionProducto(o: Opcion, onClick: () -> Unit) {
    val p = o.productoId?.let { TramaStore.productoPorId(it) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = o.etiqueta }
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (p != null) FotoProducto(fotoUrl = ReglasCatalogo.fotoDe(p, null), colorRespaldo = p.colorFoto, inicial = p.nombre.take(1), modifier = Modifier.size(48.dp), forma = RoundedCornerShape(10.dp))
            Text(o.etiqueta, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BurbujaPersona(texto: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 320.dp).semantics { contentDescription = "Tú: $texto" }
        ) {
            Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp))
        }
    }
}

/**
 * Resumen editable antes de abrir el formulario: lo dicho se puede corregir a mano.
 * Nada se guarda aquí; al «Continuar» se abre el formulario de siempre (con sus validaciones).
 */
@Composable
private fun TarjetaProductoAsistido(
    p: ProductoAsistido,
    onCambio: (String?, String?, Long?, Int?) -> Unit,
    onContinuar: () -> Unit,
    onCancelar: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tu producto (aún no se guarda)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
            OutlinedTextField(
                value = p.nombre.orEmpty(), onValueChange = { onCambio(it, p.descripcion, p.precio, p.cantidad) },
                label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = p.nombre.isNullOrBlank(),
                supportingText = if (p.nombre.isNullOrBlank()) ({ Text("Falta el nombre") }) else null
            )
            OutlinedTextField(
                value = p.descripcion.orEmpty(), onValueChange = { onCambio(p.nombre, it, p.precio, p.cantidad) },
                label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3
            )
            OutlinedTextField(
                value = p.precio?.toString().orEmpty(),
                onValueChange = { t -> onCambio(p.nombre, p.descripcion, t.filter(Char::isDigit).take(9).toLongOrNull(), p.cantidad) },
                label = { Text("Precio de cada uno (sin puntos)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = p.precio == null,
                supportingText = { Text(p.precio?.let { NumerosEs.pesos(it) } ?: "Falta el precio") }
            )
            OutlinedTextField(
                value = p.cantidad?.toString().orEmpty(),
                onValueChange = { t -> onCambio(p.nombre, p.descripcion, p.precio, t.filter(Char::isDigit).take(5).toIntOrNull()) },
                label = { Text("Cuántos tengo (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            BotonEscuchar(id = "asistente-resumen", texto = "Tu producto. " + p.resumenParaLeer(), etiqueta = "Escuchar resumen")
            BotonPrimario(texto = "Continuar: agregar foto y guardar", onClick = onContinuar, enabled = p.faltaObligatorio == null)
            BotonSecundario(texto = "Cancelar", onClick = onCancelar)
        }
    }
}
