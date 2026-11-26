package com.example.trabajodea10.ui.comprador

import androidx.compose.foundation.border
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PantallaDirectorio(nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val columnas = columnasGrilla()
    // Qué se está mirando: productos (buscar algo concreto), emprendimientos (conocer tiendas) o asociaciones.
    var vista by rememberSaveable { mutableStateOf(VISTA_PRODUCTOS) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var tipo by rememberSaveable { mutableStateOf("") }
    var sector by rememberSaveable { mutableStateOf("") }
    var asociacion by rememberSaveable { mutableStateOf("") }
    var verFiltros by rememberSaveable { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SesionManager.cargarTiendas()
        VitrinaStore.cargar()
    }

    val tiendas = SesionManager.tiendas.toList()
    val asociaciones = VitrinaStore.asociaciones.filter { it.activa }
    val filtradas = ReglasVitrina.filtrarTiendas(tiendas, busqueda, tipo, sector, asociacion, VitrinaStore.pertenencias) { TramaStore.productosDeTienda(it) }
    // Productos públicos (nunca los ocultos) de las tiendas que pasan los filtros; con búsqueda, los que calzan.
    // Usa los mismos datos públicos que ya trae la vitrina: no hay consultas nuevas al servidor.
    val tiendasParaProductos = if (busqueda.isBlank()) ReglasVitrina.filtrarTiendas(tiendas, "", tipo, sector, asociacion, VitrinaStore.pertenencias) { TramaStore.productosDeTienda(it) } else filtradas
    val productos = tiendasParaProductos.flatMap { t ->
        TramaStore.productosDeTienda(t.id)
            .filter { it.nombre.isNotBlank() && ReglasCatalogo.estadoPublico(it) != EstadoPublico.OCULTO && (busqueda.isBlank() || ReglasVitrina.coincideProducto(busqueda, it)) }
            .map { it to t }
    }.sortedBy { if (ReglasCatalogo.estadoPublico(it.first) == EstadoPublico.AGOTADO) 1 else 0 }   // lo que se puede comprar, primero
    // Buscar por voz con el dictado del celular: lo dictado queda en el buscador y se puede corregir.
    val contextoVoz = LocalContext.current
    val hayDictado = remember { com.example.trabajodea10.ui.componentes.dictadoDisponible(contextoVoz) }
    var estadoVoz by rememberSaveable { mutableStateOf("") }
    val dictarBusqueda = com.example.trabajodea10.ui.componentes.rememberDictadoPorVoz(
        indicacion = "Di lo que buscas",
        onSinTexto = { estadoVoz = "Dictado cancelado. Tu búsqueda sigue igual." }
    ) { texto -> busqueda = texto; estadoVoz = "Buscando «$texto». Puedes corregirlo en el buscador." }
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
    val nombreAsociacion = { id: String -> if (id == ReglasVitrina.SIN_ASOCIACION) "Sin asociación" else asociaciones.firstOrNull { it.id == id }?.nombre ?: id }
    val filtrosActivos = listOf(tipo, sector, asociacion).count { it.isNotBlank() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Rediseño v2: cabecera de marca con el buscador adentro (antes, título suelto y buscador aparte).
        item {
            com.example.trabajodea10.ui.componentes.CabeceraMarca(
                sobretitulo = "Hecho en Alerce",
                titulo = "Vitrina de Alerce",
                subtitulo = if (SesionManager.mensajeError != null) "Sin conexión: se muestran los datos guardados" else "Lo que hacen y venden las emprendedoras de Alerce",
                margen = 0.dp,
                acciones = {
                    com.example.trabajodea10.ui.componentes.AccionCabecera(Icons.Filled.Refresh, "Actualizar la vitrina") {
                        ambito.launch { SesionManager.cargarTiendas(); VitrinaStore.cargar(); avisar("Vitrina actualizada") }
                    }
                }
            ) {
                CampoBusqueda(
                    valor = busqueda, onValorChange = { busqueda = it; estadoVoz = "" },
                    placeholder = "Busca torta, chal, barbería…",
                    onDictar = if (hayDictado) dictarBusqueda else null
                )
                if (estadoVoz.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(estadoVoz, style = MaterialTheme.typography.bodyMedium, color = com.example.trabajodea10.ui.theme.SobreBosqueSuave,
                        modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                }
            }
        }
        // Una sola línea de controles: qué mirar y «Filtros». Los tres filtros van en un panel (antes ocupaban ~200 dp).
        item {
            com.example.trabajodea10.ui.componentes.ContenedorChips(modifier = Modifier.fillMaxWidth(), espacioHorizontal = 8.dp, espacioVertical = 8.dp) {
                ChipFiltro(texto = "Productos (${productos.size})", seleccionado = vista == VISTA_PRODUCTOS, onClick = { vista = VISTA_PRODUCTOS })
                ChipFiltro(texto = "Emprendimientos (${filtradas.size})", seleccionado = vista == VISTA_TIENDAS, onClick = { vista = VISTA_TIENDAS })
                ChipFiltro(texto = "Asociaciones (${asocFiltradas.size})", seleccionado = vista == VISTA_ASOCIACIONES, onClick = { vista = VISTA_ASOCIACIONES })
                OutlinedButton(
                    onClick = { verFiltros = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.5.dp, if (filtrosActivos > 0) NegroApple else GrisBorde)
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = NegroApple, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (filtrosActivos > 0) "Filtros ($filtrosActivos)" else "Filtros", color = NegroApple, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // Filtros aplicados: se ven y se quitan con un toque.
        if (filtrosActivos > 0) {
            item {
                com.example.trabajodea10.ui.componentes.ContenedorChips(modifier = Modifier.fillMaxWidth(), espacioHorizontal = 8.dp, espacioVertical = 8.dp) {
                    if (tipo.isNotBlank()) EtiquetaFiltroActivo("Tipo: $tipo") { tipo = "" }
                    if (sector.isNotBlank()) EtiquetaFiltroActivo("Sector: $sector") { sector = "" }
                    if (asociacion.isNotBlank()) EtiquetaFiltroActivo(nombreAsociacion(asociacion)) { asociacion = "" }
                    TextButton(onClick = { tipo = ""; sector = ""; asociacion = "" }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Limpiar filtros", fontWeight = FontWeight.Bold, color = RojoAlerce)
                    }
                }
            }
        }
        when (vista) {
            VISTA_PRODUCTOS -> {
                if (productos.isEmpty()) {
                    item {
                        Vacio(
                            titulo = if (SesionManager.cargando) "Cargando…" else "Sin productos",
                            mensaje = if (busqueda.isNotBlank() || filtrosActivos > 0) "No hay productos que coincidan. Prueba con otra palabra o quita filtros." else "Todavía no hay productos publicados."
                        )
                    }
                }
                items(productos.chunked(columnas), key = { fila -> "pf_" + fila.joinToString("_") { it.first.id } }) { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        fila.forEach { (p, t) ->
                            TarjetaProductoGrilla(p, t, Modifier.weight(1f), ancha = columnas == 1) { nav.irA(Ruta.ProductoPublico(p.id)) }
                        }
                        if (fila.size < columnas) Spacer(Modifier.weight(1f))
                    }
                }
            }
            VISTA_TIENDAS -> {
                // Al buscar algo, primero los productos que calzan (hasta 4) y un acceso a verlos todos.
                if (busqueda.isNotBlank() && productos.isNotEmpty()) {
                    item { Text("Productos que coinciden", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NegroApple, modifier = Modifier.semantics { heading() }) }
                    items(productos.take(4).chunked(columnas), key = { fila -> "pb_" + fila.joinToString("_") { it.first.id } }) { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            fila.forEach { (p, t) -> TarjetaProductoGrilla(p, t, Modifier.weight(1f), ancha = columnas == 1) { nav.irA(Ruta.ProductoPublico(p.id)) } }
                            if (fila.size < columnas) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (productos.size > 4) item {
                        TextButton(onClick = { vista = VISTA_PRODUCTOS }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Ver los ${productos.size} productos", fontWeight = FontWeight.Bold, color = NegroApple)
                        }
                    }
                    item { Text("Emprendimientos", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NegroApple, modifier = Modifier.semantics { heading() }) }
                }
                if (filtradas.isEmpty()) {
                    item {
                        Vacio(
                            titulo = if (SesionManager.cargando) "Cargando…" else "Sin resultados",
                            mensaje = "No hay emprendimientos que coincidan con la búsqueda o los filtros."
                        )
                    }
                }
                items(filtradas, key = { "t_" + it.id }) { tienda ->
                    val cuantos = TramaStore.productosDeTienda(tienda.id).count { ReglasCatalogo.estadoPublico(it) != EstadoPublico.OCULTO && it.nombre.isNotBlank() }
                    TarjetaTiendaDirectorio(tienda, VitrinaStore.asociacion(tienda.asociacionId)?.nombre, cuantos) {
                        nav.irA(Ruta.TiendaPublica(tienda.id))
                    }
                }
            }
            else -> {
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
        }
        item {
            TextButton(onClick = { nav.irA(Ruta.VisMarketplaceClasico) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Ver vitrina clásica (agenda de horas y foro)", color = TextoSecundario)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    // Panel de filtros: los mismos tres filtros de siempre, sin quitar opciones.
    if (verFiltros) {
        val estadoPanel = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { verFiltros = false }, sheetState = estadoPanel, containerColor = BlancoPuro) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Filtros", fontSize = 20.sp, fontWeight = FontWeight.Black, color = NegroApple, modifier = Modifier.semantics { heading() })
                SelectorFiltro("Tipo de emprendimiento", tipos.map { it to it }, tipo) { tipo = it }
                SelectorFiltro("Sector", sectores.map { it to it }, sector) { sector = it }
                SelectorFiltro(
                    "Asociación",
                    asociaciones.map { it.id to it.nombre } + (ReglasVitrina.SIN_ASOCIACION to "Sin asociación"),
                    asociacion
                ) { asociacion = it }
                val total = when (vista) { VISTA_PRODUCTOS -> productos.size; VISTA_ASOCIACIONES -> asocFiltradas.size; else -> filtradas.size }
                BotonPrimario(texto = "Ver $total ${if (total == 1) "resultado" else "resultados"}", onClick = { verFiltros = false })
                if (filtrosActivos > 0) BotonSecundario(texto = "Limpiar filtros", onClick = { tipo = ""; sector = ""; asociacion = "" })
            }
        }
    }
}

private const val VISTA_PRODUCTOS = "productos"
private const val VISTA_TIENDAS = "tiendas"
private const val VISTA_ASOCIACIONES = "asociaciones"

/** Filtro aplicado: texto visible y una «✕» con nombre accesible para quitarlo. */
@Composable
private fun EtiquetaFiltroActivo(texto: String, onQuitar: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp), color = GrisChip, border = BorderStroke(1.dp, NegroApple),
        modifier = Modifier.heightIn(min = 48.dp).clickable(onClickLabel = "Quitar el filtro $texto", onClick = onQuitar)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texto, fontWeight = FontWeight.SemiBold, color = NegroApple, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Close, contentDescription = "Quitar", tint = NegroApple, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Producto en la grilla de la vitrina: la foto manda (cuadrada), después el precio, el nombre y quién lo
 * vende. El estado («Se acabó», «A pedido») va escrito, no solo con color.
 */
/** Dos columnas de productos; con la letra del celular al 150 % o más, una sola para que los nombres se lean enteros. */
@Composable
private fun columnasGrilla(): Int = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) 1 else 2

@Composable
private fun TarjetaProductoGrilla(p: Producto, tienda: Tienda, modifier: Modifier, mostrarTienda: Boolean = true, ancha: Boolean = false, onClick: () -> Unit) {
    // A una columna (letra grande) la foto es más baja y el nombre tiene más líneas, para que no se corte.
    val proporcion = if (ancha) 1.6f else 1f
    val opcion = p.variantes.firstOrNull { ReglasCatalogo.opcionVendible(p, it) } ?: p.variantes.firstOrNull()
    val estado = ReglasCatalogo.estadoPublico(p)
    val precio = ReglasVitrina.textoPrecio(tienda, ReglasCatalogo.precioDe(p, opcion))
    Surface(
        shape = RoundedCornerShape(14.dp), color = BlancoPuro, border = BorderStroke(1.dp, GrisBorde),
        modifier = modifier.clickable(onClickLabel = "Ver ${p.nombre}", onClick = onClick)
            .semantics(mergeDescendants = true) {}
    ) {
        Column {
            Box {
                val foto = ReglasCatalogo.fotoDe(p, opcion)
                if (foto != null) FotoProducto(foto, p.colorFoto, p.nombre,
                    Modifier.fillMaxWidth().aspectRatio(proporcion), forma = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                else Box(
                    // Sin foto: un fondo tranquilo que no compite con los productos que sí tienen foto.
                    Modifier.fillMaxWidth().aspectRatio(if (ancha) 3f else 1f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).background(GrisChip),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = TextoSecundario, modifier = Modifier.size(28.dp))
                        Text("Sin foto", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                    }
                }
                if (estado != EstadoPublico.DISPONIBLE) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (estado == EstadoPublico.AGOTADO) NegroApple else NaranjaContenedor,
                        modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
                    ) {
                        Text(estado.etiqueta, color = if (estado == EstadoPublico.AGOTADO) BlancoPuro else NegroApple,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(precio, fontWeight = FontWeight.Black, fontSize = 17.sp, color = if (tienda.mostrarPrecios) RojoAlerce else NegroApple)
                Text(p.nombre, fontWeight = FontWeight.SemiBold, maxLines = if (ancha) 4 else 2, overflow = TextOverflow.Ellipsis, color = NegroApple)
                if (mostrarTienda) Text(tienda.nombre, style = MaterialTheme.typography.bodySmall, color = TextoSecundario, maxLines = if (ancha) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (p.variantes.size > 1) Text("${p.variantes.size} opciones", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
        }
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
private fun TarjetaTiendaDirectorio(tienda: Tienda, asociacion: String?, cuantos: Int, onClick: () -> Unit) {
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
                Text(if (cuantos == 1) "1 producto" else "$cuantos productos", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = NegroApple)
            }
            // Toda la tarjeta abre la tienda; la flecha solo lo indica (TalkBack lee el nombre de la tienda).
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = NegroApple)
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
    val columnas = columnasGrilla()

    LaunchedEffect(tiendaId) {
        if (SesionManager.tiendas.none { it.id == tiendaId }) SesionManager.cargarTiendas()
        if (VitrinaStore.asociaciones.isEmpty()) VitrinaStore.cargar()
    }

    Column(Modifier.fillMaxSize()) {
        if (tienda == null) {
            BarraSuperior(titulo = "Tienda", onVolver = { nav.volver() })
            Vacio(titulo = "Tienda no encontrada", mensaje = "Puede que ya no esté publicada.")
            return@Column
        }
        val productos = TramaStore.productosDeTienda(tienda.id)
        val asociacion = VitrinaStore.asociacionesDe(tienda).firstOrNull()
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rediseño v2: la tienda se presenta en una cabecera de marca (logo, nombre, rubro y bio).
            item {
                com.example.trabajodea10.ui.componentes.CabeceraMarca(
                    sobretitulo = listOf(tienda.rubro, tienda.sector).filter { it.isNotBlank() }.joinToString(" · "),
                    titulo = tienda.nombre,
                    margen = 0.dp,
                    onVolver = { nav.volver() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FotoProducto(tienda.logoUrl, tienda.colorLogo, tienda.nombre,
                            Modifier.size(64.dp).border(3.dp, androidx.compose.ui.graphics.Color.White, RoundedCornerShape(32.dp)),
                            RoundedCornerShape(32.dp), 24.sp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            if (tienda.bio.isNotBlank()) tienda.bio else "Emprendimiento de Alerce",
                            color = com.example.trabajodea10.ui.theme.SobreBosqueSuave, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
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
            // Productos en grilla de dos columnas: la foto y el precio mandan (antes, lista de filas con foto pequeña).
            items(productos.chunked(columnas), key = { fila -> fila.joinToString("_") { it.id } }) { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    fila.forEach { p -> TarjetaProductoGrilla(p, tienda, Modifier.weight(1f), mostrarTienda = false, ancha = columnas == 1) { nav.irA(Ruta.ProductoPublico(p.id)) } }
                    if (fila.size < columnas) Spacer(Modifier.weight(1f))
                }
            }
            // Después de los productos: calificaciones reales, compartir y QR, punto de retiro (nada se quitó).
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
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (mostrarQR) DialogoQR(titulo = tienda.nombre, url = urlTienda(tienda.id)) { mostrarQR = false }
        contacto?.let { (productoId, referencia) ->
            DialogoContacto(tienda, productoId, referencia, onCerrar = { contacto = null }, avisar = avisar)
        }
    }
}

/** Botones de contacto según la configuración de la emprendedora (A06). */
@Composable
private fun BotonesContacto(tienda: Tienda, productoId: String?, referencia: String, onFormulario: (String?, String) -> Unit) {
    val contexto = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // El rótulo dice a quién le llega y por qué canal (nombre público de la tienda); el mensaje lleva el producto.
        if (tienda.ofreceWhatsApp) {
            BotonPrimario(
                texto = com.example.trabajodea10.data.ContactoWhatsApp.etiquetaWhatsApp(tienda.nombre),
                icono = { Icon(Icons.Filled.Chat, contentDescription = null) },
                onClick = {
                    val texto = if (referencia.isBlank()) "Hola ${tienda.nombre}, vi tu tienda en Alerce Emprende y quisiera consultar."
                    else "Hola ${tienda.nombre}, me interesa «$referencia» que vi en Alerce Emprende."
                    abrirWhatsApp(contexto, tienda.whatsapp, texto)
                }
            )
        }
        if (tienda.avisoContacto.isNotEmpty()) {
            Text(tienda.avisoContacto, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
        }
        if (tienda.ofreceFormulario) {
            BotonSecundario(
                texto = com.example.trabajodea10.data.ContactoWhatsApp.etiquetaMensaje(tienda.nombre),
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
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val foto = ReglasCatalogo.fotoDe(p, opcion)
                if (foto != null) FotoProducto(
                    fotoUrl = foto, colorRespaldo = p.colorFoto, inicial = p.nombre,
                    modifier = Modifier.fillMaxWidth().height(260.dp), completa = true
                ) else Box(
                    // Sin foto: el mismo aviso tranquilo de la grilla, más bajo, para que el nombre y el precio suban.
                    Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(16.dp)).background(GrisChip).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = TextoSecundario)
                        Text("Este producto aún no tiene foto", color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Text(p.nombre, fontWeight = FontWeight.Black, fontSize = 22.sp)
                Text(
                    ReglasVitrina.textoPrecio(tienda, precio),
                    fontWeight = FontWeight.Black, fontSize = 22.sp,
                    color = if (tienda?.mostrarPrecios != false) RojoAlerce else NegroApple
                )
                Text(estado.etiqueta + (opcion?.let { if (p.manejaStock && estado == EstadoPublico.DISPONIBLE) " · quedan ${it.stock}" else "" } ?: ""),
                    color = if (estado == EstadoPublico.DISPONIBLE) com.example.trabajodea10.ui.theme.VerdeOk else TextoSecundario,
                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                // Quién lo vende, a un toque (antes había que bajar hasta «Ver tienda»).
                // Fila tocable alineada a la izquierda: con letra grande el texto baja de línea sin cortarse.
                if (tienda != null) Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(onClickLabel = "Ver la tienda", role = androidx.compose.ui.semantics.Role.Button) { nav.irA(Ruta.TiendaPublica(tienda.id)) },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Vendido por ${tienda.nombre} ›", fontWeight = FontWeight.Bold, color = NegroApple)
                }
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
                // El contacto va fijo abajo (siempre a la vista); aquí, calificaciones, compartir y la tienda.
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
        // Contactar a la emprendedora: la acción principal, fija abajo y sin tapar contenido (la lista termina antes).
        // Usa los mismos botones de siempre, según el contacto que eligió cada emprendedora (WhatsApp, mensaje o ambos).
        if (tienda != null) Surface(color = BlancoPuro, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                BotonesContacto(tienda, p.id, referencia) { prod, ref -> contacto = prod to ref }
            }
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
                TarjetaTiendaDirectorio(t, null, TramaStore.productosDeTienda(t.id).count { ReglasCatalogo.estadoPublico(it) != EstadoPublico.OCULTO && it.nombre.isNotBlank() }) { nav.irA(Ruta.TiendaPublica(t.id)) }
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
