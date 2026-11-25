package com.example.trabajodea10.ui.comprador

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.trabajodea10.data.EstadoPublico
import com.example.trabajodea10.data.FeriaPublica
import com.example.trabajodea10.data.Producto
import com.example.trabajodea10.data.ReglasCatalogo
import com.example.trabajodea10.data.ReglasVitrina
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.Tienda
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.VitrinaStore
import com.example.trabajodea10.data.remoto.ConfigSupabase
import com.example.trabajodea10.data.remoto.VitrinaRepositorio
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.CampoBusqueda
import com.example.trabajodea10.ui.componentes.ChipFiltro
import com.example.trabajodea10.ui.componentes.FotoProducto
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.TituloSeccion
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.publico.abrirWhatsApp
import com.example.trabajodea10.ui.publico.urlAsociacion
import com.example.trabajodea10.ui.publico.urlFeria
import com.example.trabajodea10.ui.publico.urlProducto
import com.example.trabajodea10.ui.publico.urlTienda
import com.example.trabajodea10.ui.theme.BlancoPuro
import com.example.trabajodea10.ui.theme.GrisBorde
import com.example.trabajodea10.ui.theme.GrisChip
import com.example.trabajodea10.ui.theme.NaranjaContenedor
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.RojoAlerce
import com.example.trabajodea10.ui.theme.TextoSecundario
import kotlinx.coroutines.launch

/*
 * MODO COMPRADOR (Entrega 6, checklist A01–A07 y A09–A12).
 * Directorio de emprendedoras y asociaciones con búsqueda y filtros, tienda,
 * producto con variantes, asociación, contacto según la configuración de cada
 * emprendedora, compartir y QR. Funciona sin sesión.
 */

// ============================================================ Directorio (A01, A02)

@Composable
fun PantallaDirectorio(nav: ControladorNavegacion, avisar: (String) -> Unit) {
    var verAsociaciones by rememberSaveable { mutableStateOf(false) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var tipo by rememberSaveable { mutableStateOf("") }
    var sector by rememberSaveable { mutableStateOf("") }
    var asociacion by rememberSaveable { mutableStateOf("") }
    val ambito = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SesionManager.cargarTiendas()
        VitrinaStore.cargar()
    }

    val tiendas = SesionManager.tiendas.toList()
    val asociaciones = VitrinaStore.asociaciones.filter { it.activa }
    val filtradas = ReglasVitrina.filtrarTiendas(tiendas, busqueda, tipo, sector, asociacion, VitrinaStore.pertenencias)
    val tipos = tiendas.map { it.rubro }.filter { it.isNotBlank() }.distinct().sorted()
    val sectores = tiendas.map { it.sector }.filter { it.isNotBlank() }.distinct().sorted()
    val asocFiltradas = asociaciones.filter { a ->
        val socias = tiendas.filter { it.asociacionId == a.id }
        (asociacion.isBlank() || asociacion == a.id) &&
            (sector.isBlank() || a.sector == sector || socias.any { it.sector == sector }) &&
            (tipo.isBlank() || socias.any { it.rubro == tipo }) &&
            (busqueda.isBlank() || a.nombre.contains(busqueda, true) || a.descripcion.contains(busqueda, true) ||
                socias.any { it.nombre.contains(busqueda, true) })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vitrina de Alerce", fontSize = 24.sp, fontWeight = FontWeight.Black, color = NegroApple)
                    Text(
                        if (SesionManager.mensajeError != null) "Sin conexión: se muestran datos guardados" else "Emprendedoras y asociaciones de Alerce",
                        style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                    )
                }
                IconButton(onClick = {
                    ambito.launch { SesionManager.cargarTiendas(); VitrinaStore.cargar(); avisar("Vitrina actualizada") }
                }) { Icon(Icons.Filled.Store, contentDescription = "Actualizar vitrina", tint = NegroApple) }
            }
        }
        item {
            CampoBusqueda(valor = busqueda, onValorChange = { busqueda = it }, placeholder = "Buscar por nombre, rubro o sector")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipFiltro(texto = "Emprendedoras (${filtradas.size})", seleccionado = !verAsociaciones, onClick = { verAsociaciones = false })
                ChipFiltro(texto = "Asociaciones (${asocFiltradas.size})", seleccionado = verAsociaciones, onClick = { verAsociaciones = true })
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorFiltro("Tipo de emprendimiento", tipos.map { it to it }, tipo) { tipo = it }
                SelectorFiltro("Sector", sectores.map { it to it }, sector) { sector = it }
                SelectorFiltro(
                    "Asociación",
                    asociaciones.map { it.id to it.nombre } + (ReglasVitrina.SIN_ASOCIACION to "Sin asociación"),
                    asociacion
                ) { asociacion = it }
                if (tipo.isNotBlank() || sector.isNotBlank() || asociacion.isNotBlank() || busqueda.isNotBlank()) {
                    TextButton(onClick = { tipo = ""; sector = ""; asociacion = ""; busqueda = "" }) {
                        Text("Limpiar filtros", fontWeight = FontWeight.Bold, color = RojoAlerce)
                    }
                }
            }
        }
        if (!verAsociaciones) {
            if (filtradas.isEmpty()) {
                item {
                    Vacio(
                        titulo = if (SesionManager.cargando) "Cargando…" else "Sin resultados",
                        mensaje = "No hay emprendedoras que coincidan con la búsqueda o los filtros."
                    )
                }
            }
            items(filtradas, key = { "t_" + it.id }) { tienda ->
                TarjetaTiendaDirectorio(tienda, VitrinaStore.asociacion(tienda.asociacionId)?.nombre) {
                    nav.irA(Ruta.TiendaPublica(tienda.id))
                }
            }
        } else {
            if (asocFiltradas.isEmpty()) {
                item { Vacio(titulo = if (VitrinaStore.cargando) "Cargando…" else "Sin asociaciones", mensaje = "No hay asociaciones que coincidan.") }
            }
            items(asocFiltradas, key = { "a_" + it.id }) { a ->
                val socias = tiendas.count { it.asociacionId == a.id }
                Tarjeta(onClick = { nav.irA(Ruta.AsociacionPublica(a.id)) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Groups, contentDescription = null, tint = NegroApple, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(listOf(a.sector, a.localidad).filter { it.isNotBlank() }.joinToString(" · ") + " · $socias integrantes",
                                style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                            if (a.descripcion.isNotBlank()) Text(a.descripcion, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            TextButton(onClick = { nav.irA(Ruta.VisMarketplaceClasico) }) {
                Text("Ver vitrina clásica (agenda de horas y foro)", color = TextoSecundario)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Selector desplegable con botón grande (48 dp). "" = todos. */
@Composable
private fun SelectorFiltro(etiqueta: String, opciones: List<Pair<String, String>>, seleccion: String, onSeleccion: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val textoActual = opciones.firstOrNull { it.first == seleccion }?.second ?: "Todos"
    Box {
        OutlinedButton(
            onClick = { abierto = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (seleccion.isNotBlank()) NegroApple else GrisBorde)
        ) {
            Column(Modifier.weight(1f)) {
                Text(etiqueta, fontSize = 11.sp, color = TextoSecundario)
                Text(textoActual, fontWeight = FontWeight.SemiBold, color = NegroApple, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = NegroApple)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = { onSeleccion(""); abierto = false })
            opciones.forEach { (valor, texto) ->
                DropdownMenuItem(text = { Text(texto) }, onClick = { onSeleccion(valor); abierto = false })
            }
        }
    }
}

@Composable
private fun TarjetaTiendaDirectorio(tienda: Tienda, asociacion: String?, onClick: () -> Unit) {
    Tarjeta(onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FotoProducto(
                fotoUrl = tienda.logoUrl, colorRespaldo = tienda.colorLogo, inicial = tienda.nombre,
                modifier = Modifier.size(56.dp), forma = RoundedCornerShape(28.dp), tamanoInicial = 22.sp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tienda.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(tienda.rubro, tienda.sector).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                if (asociacion != null) Text("🤝 $asociacion", style = MaterialTheme.typography.bodySmall, color = Color1E40AF)
            }
        }
    }
}

private val Color1E40AF = androidx.compose.ui.graphics.Color(0xFF1E40AF)

// ============================================================ Tienda (A03, A06, A07, A09, A10, A18)

@Composable
fun PantallaTiendaPublica(tiendaId: String, nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val contexto = LocalContext.current
    val tienda = SesionManager.tiendas.firstOrNull { it.id == tiendaId }
        ?: TramaStore.tienda.takeIf { it.id == tiendaId }
    var mostrarQR by remember { mutableStateOf(false) }
    var contacto by remember { mutableStateOf<Pair<String?, String>?>(null) }

    LaunchedEffect(tiendaId) {
        if (SesionManager.tiendas.none { it.id == tiendaId }) SesionManager.cargarTiendas()
        if (VitrinaStore.asociaciones.isEmpty()) VitrinaStore.cargar()
    }

    Column(Modifier.fillMaxSize()) {
        BarraSuperior(titulo = tienda?.nombre ?: "Tienda", onVolver = { nav.volver() })
        if (tienda == null) {
            Vacio(titulo = "Tienda no encontrada", mensaje = "Puede que ya no esté publicada.")
            return@Column
        }
        val productos = TramaStore.productosDeTienda(tienda.id)
        val asociacion = VitrinaStore.asociacionesDe(tienda).firstOrNull()
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FotoProducto(tienda.logoUrl, tienda.colorLogo, tienda.nombre, Modifier.size(64.dp), RoundedCornerShape(32.dp), 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tienda.nombre, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(listOf(tienda.rubro, tienda.sector).filter { it.isNotBlank() }.joinToString(" · "), color = TextoSecundario)
                    }
                }
                if (tienda.bio.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(tienda.bio, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (asociacion != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = androidx.compose.ui.graphics.Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFBFDBFE)),
                        modifier = Modifier.clickable { nav.irA(Ruta.AsociacionPublica(asociacion.id)) }
                    ) {
                        Text("🤝 Integrante de ${asociacion.nombre}", modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = Color1E40AF, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { BotonesContacto(tienda, null, "") { prod, ref -> contacto = prod to ref } }
            // Entrega 7: seguir la tienda y calificarla (si la emprendedora lo permite)
            item { BotonSeguir("TIENDA", tienda.id, tienda.nombre, nav, avisar) }
            item { SeccionCalificaciones(tienda, null, nav, avisar) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario(
                        texto = "Compartir", modifier = Modifier.weight(1f),
                        icono = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { compartirEnlace(contexto, "Mira la tienda ${tienda.nombre} en Alerce Emprende", urlTienda(tienda.id)) }
                    )
                    BotonSecundario(
                        texto = "Código QR", modifier = Modifier.weight(1f),
                        icono = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                        onClick = { mostrarQR = true }
                    )
                }
            }
            if (tienda.tienePuntoRetiro) {
                item {
                    val lat = tienda.puntoLat!!
                    val lng = tienda.puntoLng!!
                    val nombrePunto = tienda.puntoNombre ?: tienda.nombre
                    Tarjeta {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📍 Punto de venta o retiro: $nombrePunto", fontWeight = FontWeight.Bold)
                            MapaOsm(
                                puntos = listOf(PuntoMapa("punto", lat, lng, nombrePunto)),
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                zoom = 16.0
                            )
                            BotonPrimario(
                                texto = "Cómo llegar",
                                icono = { Icon(Icons.Filled.Directions, contentDescription = null) },
                                onClick = { abrirComoLlegar(contexto, lat, lng, nombrePunto) }
                            )
                        }
                    }
                }
            }
            item { TituloSeccion(texto = "Productos (${productos.size})") }
            if (!tienda.mostrarPrecios) {
                item {
                    Surface(color = NaranjaContenedor, shape = RoundedCornerShape(12.dp)) {
                        Text("Esta tienda muestra «Consultar precio»: escríbele para saber el valor.",
                            modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (productos.isEmpty()) {
                item { Vacio(titulo = "Sin productos a la vista", mensaje = "Esta tienda aún no publica productos.") }
            }
            items(productos, key = { it.id }) { p ->
                TarjetaProductoPublico(p, tienda) { nav.irA(Ruta.ProductoPublico(p.id)) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (mostrarQR) DialogoQR(titulo = tienda.nombre, url = urlTienda(tienda.id)) { mostrarQR = false }
        contacto?.let { (productoId, referencia) ->
            DialogoContacto(tienda, productoId, referencia, onCerrar = { contacto = null }, avisar = avisar)
        }
    }
}

@Composable
private fun TarjetaProductoPublico(p: Producto, tienda: Tienda, onClick: () -> Unit) {
    val opcion = p.variantes.firstOrNull { ReglasCatalogo.opcionVendible(p, it) } ?: p.variantes.firstOrNull()
    val estado = ReglasCatalogo.estadoPublico(p)
    Tarjeta(onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FotoProducto(ReglasCatalogo.fotoDe(p, opcion), p.colorFoto, p.nombre, Modifier.size(72.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.nombre, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    ReglasVitrina.textoPrecio(tienda, ReglasCatalogo.precioDe(p, opcion)),
                    fontWeight = FontWeight.Black, color = if (tienda.mostrarPrecios) RojoAlerce else NegroApple
                )
                if (p.variantes.size > 1) Text("${p.variantes.size} opciones", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                if (estado != EstadoPublico.DISPONIBLE) Text(estado.etiqueta, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
        }
    }
}

/** Botones de contacto según la configuración de la emprendedora (A06). */
@Composable
private fun BotonesContacto(tienda: Tienda, productoId: String?, referencia: String, onFormulario: (String?, String) -> Unit) {
    val contexto = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tienda.ofreceWhatsApp) {
            BotonPrimario(
                texto = "Escribir por WhatsApp",
                icono = { Icon(Icons.Filled.Chat, contentDescription = null) },
                onClick = {
                    val texto = if (referencia.isBlank()) "Hola ${tienda.nombre}, vi tu tienda en Alerce Emprende y quisiera consultar."
                    else "Hola ${tienda.nombre}, me interesa «$referencia» que vi en Alerce Emprende."
                    abrirWhatsApp(contexto, tienda.whatsapp, texto)
                }
            )
        }
        if (tienda.faltaWhatsApp) {
            Text(com.example.trabajodea10.data.ContactoWhatsApp.SIN_WHATSAPP, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
        }
        if (tienda.ofreceFormulario) {
            BotonSecundario(
                texto = "Enviar un mensaje",
                icono = { Icon(Icons.Filled.Email, contentDescription = null) },
                onClick = { onFormulario(productoId, referencia) }
            )
        }
    }
}

/** Formulario de contacto: el mensaje llega a la bandeja de la emprendedora (RF-CFG-03). */
@Composable
fun DialogoContacto(tienda: Tienda, productoId: String?, referencia: String, onCerrar: () -> Unit, avisar: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(SesionManager.nombreUsuarioActual) }
    var medio by remember { mutableStateOf(SesionManager.emailActual ?: "") }
    var mensaje by remember { mutableStateOf(if (referencia.isNotBlank()) "Hola, me interesa «$referencia». " else "") }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!enviando) onCerrar() },
        title = { Text("Escribir a ${tienda.nombre}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nombre, onValueChange = { nombre = it.take(80) }, label = { Text("Tu nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = medio, onValueChange = { medio = it.take(120) }, label = { Text("Correo o teléfono para responderte") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mensaje, onValueChange = { mensaje = it.take(1000) }, label = { Text("Mensaje") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = RojoAlerce, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !enviando, onClick = {
                when {
                    nombre.isBlank() -> error = "Escribe tu nombre."
                    mensaje.isBlank() -> error = "Escribe tu mensaje."
                    !ConfigSupabase.configurado -> error = "La app no está conectada al servidor."
                    else -> {
                        enviando = true; error = null
                        ambito.launch {
                            runCatching { VitrinaRepositorio.enviarContacto(tienda.id, nombre, medio, mensaje, productoId) }
                                .onSuccess { avisar("Mensaje enviado. ${tienda.nombre} lo verá en su app."); onCerrar() }
                                .onFailure { error = "No se pudo enviar: ${it.message}" }
                            enviando = false
                        }
                    }
                }
            }) { Text(if (enviando) "Enviando…" else "Enviar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCerrar, enabled = !enviando) { Text("Cancelar") } }
    )
}

// ============================================================ Producto (A04, A07, A09)

@Composable
fun PantallaProductoPublico(productoId: String, nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val contexto = LocalContext.current
    val p = TramaStore.productoPorId(productoId)
    val tienda = p?.let { prod -> SesionManager.tiendas.firstOrNull { it.id == prod.tiendaId } ?: TramaStore.tienda.takeIf { it.id == prod.tiendaId } }
    var varianteId by rememberSaveable { mutableStateOf<String?>(null) }
    var contacto by remember { mutableStateOf<Pair<String?, String>?>(null) }

    Column(Modifier.fillMaxSize()) {
        BarraSuperior(titulo = p?.nombre ?: "Producto", subtitulo = tienda?.nombre, onVolver = { nav.volver() })
        if (p == null) {
            Vacio(titulo = "Producto no encontrado", mensaje = "Puede que ya no esté publicado.")
            return@Column
        }
        val opcion = p.variantes.firstOrNull { it.id == varianteId }
            ?: p.variantes.firstOrNull { ReglasCatalogo.opcionVendible(p, it) } ?: p.variantes.firstOrNull()
        val precio = ReglasCatalogo.precioDe(p, opcion)
        val estado = ReglasCatalogo.estadoPublico(p)
        val referencia = p.nombre + if (opcion != null && p.variantes.size > 1) " — ${opcion.nombre}" else ""
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FotoProducto(
                    fotoUrl = ReglasCatalogo.fotoDe(p, opcion), colorRespaldo = p.colorFoto, inicial = p.nombre,
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                )
            }
            item {
                Text(p.nombre, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(
                    ReglasVitrina.textoPrecio(tienda, precio),
                    fontWeight = FontWeight.Black, fontSize = 22.sp,
                    color = if (tienda?.mostrarPrecios != false) RojoAlerce else NegroApple
                )
                Text(estado.etiqueta + (opcion?.let { if (p.manejaStock && estado == EstadoPublico.DISPONIBLE) " · quedan ${it.stock}" else "" } ?: ""),
                    color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
            }
            if (p.variantes.size > 1) {
                item {
                    Text("Elige una opción", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.variantes.forEach { v ->
                            ChipFiltro(texto = v.nombre, seleccionado = v.id == opcion?.id, onClick = { varianteId = v.id })
                        }
                    }
                }
            }
            if (p.descripcion.isNotBlank()) item { Text(p.descripcion, style = MaterialTheme.typography.bodyMedium) }
            if (tienda != null) {
                item { BotonesContacto(tienda, p.id, referencia) { prod, ref -> contacto = prod to ref } }
                item { SeccionCalificaciones(tienda, p.id, nav, avisar) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BotonSecundario(
                            texto = "Compartir", modifier = Modifier.weight(1f),
                            icono = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = { compartirEnlace(contexto, "Mira «${p.nombre}» de ${tienda.nombre}", urlProducto(p.id)) }
                        )
                        BotonSecundario(
                            texto = "Ver tienda", modifier = Modifier.weight(1f),
                            icono = { Icon(Icons.Filled.Store, contentDescription = null) },
                            onClick = { nav.irA(Ruta.TiendaPublica(tienda.id)) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (tienda != null) contacto?.let { (prod, ref) -> DialogoContacto(tienda, prod, ref, { contacto = null }, avisar) }
    }
}

// ============================================================ Asociación (A05, A09, A10)

@Composable
fun PantallaAsociacionPublica(asociacionId: String, nav: ControladorNavegacion, avisar: (String) -> Unit = {}) {
    val contexto = LocalContext.current
    var mostrarQR by remember { mutableStateOf(false) }
    LaunchedEffect(asociacionId) {
        if (VitrinaStore.asociacion(asociacionId) == null) VitrinaStore.cargar()
        if (SesionManager.tiendas.none { it.asociacionId == asociacionId }) SesionManager.cargarTiendas()
    }
    val a = VitrinaStore.asociacion(asociacionId)
    Column(Modifier.fillMaxSize()) {
        BarraSuperior(titulo = a?.nombre ?: "Asociación", onVolver = { nav.volver() })
        if (a == null) {
            Vacio(titulo = if (VitrinaStore.cargando) "Cargando…" else "Asociación no encontrada", mensaje = "Revisa tu conexión e intenta de nuevo.")
            return@Column
        }
        val socias = SesionManager.tiendas.filter { VitrinaStore.perteneceA(it, a.id) }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(listOf(a.sector, a.localidad).filter { it.isNotBlank() }.joinToString(" · "), color = TextoSecundario)
                if (a.descripcion.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(a.descripcion) }
            }
            item { BotonSeguir("ASOCIACION", a.id, a.nombre, nav, avisar) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario(
                        texto = "Compartir", modifier = Modifier.weight(1f),
                        icono = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { compartirEnlace(contexto, "Conoce la asociación ${a.nombre} en Alerce Emprende", urlAsociacion(a.id)) }
                    )
                    BotonSecundario(
                        texto = "Código QR", modifier = Modifier.weight(1f),
                        icono = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                        onClick = { mostrarQR = true }
                    )
                }
            }
            item { TituloSeccion(texto = "Integrantes (${socias.size})") }
            if (socias.isEmpty()) item { Vacio(titulo = "Sin integrantes", mensaje = "Esta asociación aún no tiene tiendas en la vitrina.") }
            items(socias, key = { it.id }) { t ->
                TarjetaTiendaDirectorio(t, null) { nav.irA(Ruta.TiendaPublica(t.id)) }
            }
        }
        if (mostrarQR) DialogoQR(titulo = a.nombre, url = urlAsociacion(a.id)) { mostrarQR = false }
    }
}

// ============================================================ Feria (A10, A11)

@Composable
fun PantallaFeriaPublica(feriaId: String, nav: ControladorNavegacion) {
    val contexto = LocalContext.current
    var mostrarQR by remember { mutableStateOf(false) }
    LaunchedEffect(feriaId) { if (VitrinaStore.feria(feriaId) == null) VitrinaStore.cargar() }
    val f = VitrinaStore.feria(feriaId)
    Column(Modifier.fillMaxSize()) {
        BarraSuperior(titulo = f?.nombre ?: "Feria", onVolver = { nav.volver() })
        if (f == null) {
            Vacio(titulo = if (VitrinaStore.cargando) "Cargando…" else "Feria no encontrada", mensaje = "Puede que ya no esté publicada.")
            return@Column
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (f.imagenUrl != null) {
                item {
                    AsyncImage(
                        model = f.imagenUrl, contentDescription = "Imagen de ${f.nombre}", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))
                    )
                }
            }
            if (f.estado == "CANCELADA") {
                item { Text("Esta feria fue cancelada. No se realizará.", color = RojoAlerce, fontWeight = FontWeight.Bold) }
            }
            item { DatosFeria(f) }
            if (f.tieneUbicacion) {
                item {
                    MapaOsm(
                        puntos = listOf(PuntoMapa(f.id, f.lat!!, f.lng!!, f.nombre, f.lugar)),
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        zoom = 16.0
                    )
                }
                item {
                    BotonPrimario(
                        texto = "Cómo llegar",
                        icono = { Icon(Icons.Filled.Directions, contentDescription = null) },
                        onClick = { abrirComoLlegar(contexto, f.lat!!, f.lng!!, f.nombre) }
                    )
                }
            } else {
                item { Text("Esta feria aún no tiene su ubicación marcada en el mapa.", color = TextoSecundario) }
            }
            // Entrega 7: participantes confirmadas con una muestra de su catálogo público (RF-FER-10)
            item { SeccionParticipantes(f.id, nav) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonSecundario(
                        texto = "Compartir", modifier = Modifier.weight(1f),
                        icono = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = { compartirEnlace(contexto, "Te invito a ${f.nombre}", urlFeria(f.id)) }
                    )
                    BotonSecundario(
                        texto = "Código QR", modifier = Modifier.weight(1f),
                        icono = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                        onClick = { mostrarQR = true }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (mostrarQR) DialogoQR(titulo = f.nombre, url = urlFeria(f.id)) { mostrarQR = false }
    }
}

@Composable
fun DatosFeria(f: FeriaPublica, distancia: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(ReglasVitrina.rangoFechas(f) + if (f.horario.isNotBlank()) " · ${f.horario}" else "", color = RojoAlerce, fontWeight = FontWeight.Bold)
        Text("📍 " + f.lugar.ifBlank { "Lugar por confirmar" } + (distancia?.let { " · a $it" } ?: ""), color = TextoSecundario)
        if (f.organizador.isNotBlank()) Text("Organiza: ${f.organizador}", color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
        if (f.descripcion.isNotBlank()) Text(f.descripcion, style = MaterialTheme.typography.bodyMedium)
    }
}
