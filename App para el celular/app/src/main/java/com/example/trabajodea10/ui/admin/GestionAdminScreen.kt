package com.example.trabajodea10.ui.admin

import com.example.trabajodea10.ui.componentes.FilaSwitch
import com.example.trabajodea10.ui.componentes.ContenedorChips
import com.example.trabajodea10.ui.componentes.ChipFiltro
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.data.TipoAdmin
import com.example.trabajodea10.data.AsociacionPublica
import kotlinx.coroutines.launch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trabajodea10.data.AdminStore
import com.example.trabajodea10.data.NavegacionAnonima
import com.example.trabajodea10.data.ReglasAdmin
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.VitrinaStore
import com.example.trabajodea10.ui.componentes.AvisoAccion
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.CabeceraMarca
import com.example.trabajodea10.ui.componentes.EncabezadoSeccion
import com.example.trabajodea10.ui.componentes.EstadoCargando
import com.example.trabajodea10.ui.componentes.FilaGestion
import com.example.trabajodea10.ui.componentes.GrupoFilas
import com.example.trabajodea10.ui.componentes.IconoTono
import com.example.trabajodea10.ui.componentes.PildoraEstado
import com.example.trabajodea10.ui.componentes.Tono
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.FondoLimpio
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.TextoSecundario

/** Dirección del panel web de administración (la misma que ya usaba «Ferias»). */
internal const val URL_PANEL_WEB = "https://diego-733.github.io/EmprendedoresPuertoMontt/"

internal fun abrirPanelWeb(contexto: android.content.Context) {
    runCatching {
        contexto.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, URL_PANEL_WEB.toUri()))
    }
}

/*
 * «Gestión» (rediseño v2): todas las áreas de administración agrupadas por tema, con cifras reales.
 * En el celular se CONSULTA; crear, editar y borrar asociaciones, tipos y administradores se hace en
 * el panel web (como hasta ahora). Nada de esto cambia permisos: el servidor sigue exigiendo es_admin().
 */
@Composable
fun PantallaAdminGestion(nav: ControladorNavegacion) {
    val contexto = LocalContext.current
    var navegacion by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!AdminStore.cargadoAlgunaVez) AdminStore.cargar()
        VitrinaStore.cargar()
    }
    val resumen = ReglasAdmin.resumen(AdminStore.cuentas)
    Column(Modifier.fillMaxSize().background(FondoLimpio).verticalScroll(rememberScrollState())) {
        CabeceraMarca(
            sobretitulo = "Administración",
            titulo = "Gestión",
            subtitulo = "Todas las áreas de Alerce en un solo lugar"
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (AdminStore.sonEjemplo) AvisoAccion("Datos de ejemplo: esta versión no está conectada al servidor.", Tono.AMBAR)

            EncabezadoSeccion("Personas", sobretitulo = "Cuentas")
            GrupoFilas {
                FilaGestion("Emprendedoras", Icons.Filled.People, Tono.MUSGO, { nav.seleccionarTab(Ruta.AdminEmprendedoras) },
                    detalle = "${resumen.total} cuentas · ${resumen.activas} activas",
                    insignia = resumen.pendientes.takeIf { it > 0 }?.let { "$it por registrarse" })
                Separador()
                FilaGestion("Administradores", Icons.Filled.SupervisorAccount, Tono.NEUTRO, { nav.irA(Ruta.AdminEquipo) },
                    detalle = "${AdminStore.admins.size} con acceso a este panel")
            }

            EncabezadoSeccion("Vitrina y ferias", sobretitulo = "Contenido")
            GrupoFilas {
                FilaGestion("Ferias", Icons.Filled.CalendarMonth, Tono.AZUL, { nav.seleccionarTab(Ruta.AdminFerias) },
                    detalle = "${VitrinaStore.ferias.size} en el servidor")
                Separador()
                FilaGestion("Asociaciones", Icons.Filled.Groups, Tono.AMBAR, { nav.irA(Ruta.AdminAsociaciones) },
                    detalle = "${VitrinaStore.asociaciones.size} registradas")
                Separador()
                FilaGestion("Tipos de producto", Icons.Filled.Category, Tono.CORTEZA, { nav.irA(Ruta.AdminTipos) },
                    detalle = "${AdminStore.tipos.count { it.activo }} activos de ${AdminStore.tipos.size}")
            }

            EncabezadoSeccion("Control", sobretitulo = "Seguridad y registro")
            GrupoFilas {
                FilaGestion("Bitácora", Icons.Filled.History, Tono.OK, { nav.irA(Ruta.AdminBitacora) },
                    detalle = "Quién cambió qué, y cuándo")
                Separador()
                FilaGestion("Asistente del panel", Icons.Filled.Search, Tono.MUSGO, { nav.irA(Ruta.Asistente) },
                    detalle = "Pregunta por cuentas, pendientes o ferias")
                Separador()
                // Configuración de la app sin cuenta: se lee aquí y se cambia en la web (W12).
                FilaGestion("App sin cuenta", Icons.Filled.PublicOff, Tono.NEUTRO, { navegacion = true },
                    detalle = "Hoy: " + when (VitrinaStore.navegacion) {
                        NavegacionAnonima.PERMITIR -> "se puede comprar sin cuenta"
                        NavegacionAnonima.INVITAR -> "se invita a entrar con Google"
                        NavegacionAnonima.OBLIGAR -> "hay que entrar con Google"
                    })
            }

            EncabezadoSeccion("Herramientas", sobretitulo = "Cuenta y diagnóstico")
            GrupoFilas {
                FilaGestion("Ajustes y mi cuenta", Icons.Filled.Settings, Tono.NEUTRO, { nav.irA(Ruta.Ajustes) },
                    detalle = "Accesibilidad, voz, privacidad y cerrar sesión")
                Separador()
                FilaGestion("Diagnóstico técnico", Icons.Filled.VerifiedUser, Tono.MUSGO, { nav.irA(Ruta.Ajustes) },
                    detalle = "Protección de la app (App Check), al final de Ajustes")
                Separador()
                FilaGestion("Abrir el panel web", Icons.AutoMirrored.Filled.OpenInNew, Tono.AZUL, { abrirPanelWeb(contexto) },
                    detalle = "Imagen y mapa de las ferias, y avisos manuales a las tiendas")
            }
            mensaje?.let { AvisoAccion(it, Tono.OK) }
            AvisoAccion(
                "Todo lo que cambias aquí se guarda en el servidor, con las mismas reglas que el panel web. Si el servidor lo rechaza, verás el motivo.",
                Tono.AZUL, icono = Icons.Filled.Info
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    if (navegacion) HojaNavegacionAnonima(onCerrar = { navegacion = false }, onResultado = { mensaje = it })
}

@Composable
private fun Separador() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 74.dp, end = 16.dp))

/** Fila de una lista de gestión con estado y una acción al tocar. */
@Composable
private fun FilaConsulta(
    titulo: String, detalle: String?, estado: String?, tono: Tono, icono: androidx.compose.ui.graphics.vector.ImageVector,
    tonoEstado: Tono = Tono.OK, onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Editar $titulo", onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconoTono(icono, tono, tamano = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NegroApple)
            if (!detalle.isNullOrBlank()) Text(detalle, fontSize = 14.sp, color = TextoSecundario)
        }
        if (estado != null) { Spacer(Modifier.width(8.dp)); PildoraEstado(estado, tonoEstado) }
    }
}

@Composable
private fun PantallaGestion(
    titulo: String, subtitulo: String, nav: ControladorNavegacion, accion: String? = null, onAccion: (() -> Unit)? = null,
    contenido: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(FondoLimpio)) {
        BarraSuperior(titulo = titulo, subtitulo = subtitulo, onVolver = { nav.volver() })
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (AdminStore.sonEjemplo) item { AvisoAccion("Datos de ejemplo: esta versión no está conectada al servidor. Las acciones no se guardan.", Tono.AMBAR) }
            if (accion != null && onAccion != null) item { BotonPrimario(texto = accion, onClick = onAccion) }
            contenido()
        }
    }
}

// ------------------------------------------------------------------ Asociaciones

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAdminAsociaciones(nav: ControladorNavegacion) {
    val ambito = rememberCoroutineScope()
    val contexto = LocalContext.current
    LaunchedEffect(Unit) { VitrinaStore.cargar(); if (SesionManager.tiendas.isEmpty()) SesionManager.cargarTiendas() }
    var editando by remember { mutableStateOf<AsociacionPublica?>(null) }
    var nueva by remember { mutableStateOf(false) }
    val conteo = ReglasAdmin.integrantesPorAsociacion(VitrinaStore.pertenencias, SesionManager.tiendas.toList())
    PantallaGestion("Asociaciones", "${VitrinaStore.asociaciones.size} registradas", nav, accion = "Nueva asociación", onAccion = { nueva = true }) {
        if (VitrinaStore.asociaciones.isEmpty()) item { Vacio(titulo = "Sin asociaciones", mensaje = "Todavía no hay asociaciones en el servidor.") }
        else item {
            GrupoFilas {
                VitrinaStore.asociaciones.forEachIndexed { i, a ->
                    if (i > 0) Separador()
                    FilaConsulta(a.nombre,
                        listOf(a.sector, a.localidad).filter { it.isNotBlank() }.joinToString(" · ").let { if (it.isBlank()) "" else "$it · " } + "${conteo[a.id] ?: 0} tiendas",
                        if (a.activa) "Activa" else "Inactiva", Tono.AMBAR, Icons.Filled.Groups,
                        if (a.activa) Tono.OK else Tono.NEUTRO) { editando = a }
                }
            }
        }
        item { Text("Los integrantes de cada tienda se cambian desde «Emprendedoras» (ficha de la cuenta) o aquí, al editar la asociación.", color = TextoSecundario, fontSize = 14.sp) }
    }
    if (nueva || editando != null) {
        val a = editando
        var nombre by remember(a) { mutableStateOf(a?.nombre ?: "") }
        var sector by remember(a) { mutableStateOf(a?.sector ?: "") }
        var localidad by remember(a) { mutableStateOf(a?.localidad ?: "") }
        var descripcion by remember(a) { mutableStateOf(a?.descripcion ?: "") }
        var activa by remember(a) { mutableStateOf(a?.activa ?: true) }
        var error by remember(a) { mutableStateOf<String?>(null) }
        var trabajando by remember(a) { mutableStateOf(false) }
        val cerrar = { nueva = false; editando = null }
        ModalBottomSheet(onDismissRequest = cerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (a == null) "Nueva asociación" else "Editar asociación", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sector, { sector = it }, label = { Text("Sector") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(localidad, { localidad = it }, label = { Text("Localidad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                FilaSwitch("Activa", "Se muestra en la vitrina y en los filtros", activa, { activa = it })
                if (a != null && SesionManager.tiendas.isNotEmpty()) {
                    Text("Integrantes", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Toca una tienda para sumarla o quitarla (se guarda al instante).", color = TextoSecundario, fontSize = 14.sp)
                    var integrantes by remember(a) { mutableStateOf(VitrinaStore.pertenencias.filterValues { a.id in it }.keys) }
                    ContenedorChips(modifier = Modifier.fillMaxWidth(), espacioHorizontal = 8.dp, espacioVertical = 8.dp) {
                        SesionManager.tiendas.forEach { t ->
                            val integra = t.id in integrantes
                            ChipFiltro(t.nombre, integra, {
                                ambito.launch {
                                    AdminStore.cambiarAsociacion(t.id, a.id, !integra)
                                        .onSuccess { integrantes = if (integra) integrantes - t.id else integrantes + t.id }
                                        .onFailure { error = it.message }
                                }
                            })
                        }
                    }
                }
                error?.let { AvisoAccion(it, Tono.CORTEZA) }
                BotonPrimario(texto = if (trabajando) "Guardando…" else "Guardar en el servidor", enabled = !trabajando, onClick = {
                    trabajando = true; error = null
                    ambito.launch {
                        AdminStore.guardarAsociacion(a?.id, nombre, sector, localidad, descripcion, activa)
                            .onSuccess {
                                android.widget.Toast.makeText(contexto, "Asociación «${nombre.trim()}» guardada.", android.widget.Toast.LENGTH_SHORT).show()
                                cerrar()
                            }
                            .onFailure { error = it.message }
                        trabajando = false
                    }
                })
                BotonSecundario(texto = "Cancelar", onClick = cerrar)
            }
        }
    }
}

// ------------------------------------------------------------------ Tipos de producto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAdminTipos(nav: ControladorNavegacion) {
    val ambito = rememberCoroutineScope()
    val contexto = LocalContext.current
    LaunchedEffect(Unit) { if (!AdminStore.cargadoAlgunaVez) AdminStore.cargar() }
    var editando by remember { mutableStateOf<TipoAdmin?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf<TipoAdmin?>(null) }
    val porCategoria = AdminStore.tipos.groupBy { it.categoria.ifBlank { "Sin categoría" } }
    PantallaGestion("Tipos de producto", "${AdminStore.tipos.count { it.activo }} activos de ${AdminStore.tipos.size}", nav,
        accion = "Nuevo tipo de producto", onAccion = { nuevo = true }) {
        if (AdminStore.cargando && AdminStore.tipos.isEmpty()) item { EstadoCargando("Trayendo los tipos…") }
        porCategoria.forEach { (categoria, lista) ->
            item { EncabezadoSeccion(categoria) }
            item {
                GrupoFilas {
                    lista.forEachIndexed { i, t ->
                        if (i > 0) Separador()
                        FilaConsulta(t.nombre, if (AdminStore.sonEjemplo) null else "${t.productos} productos lo usan",
                            if (t.activo) "Activo" else "Inactivo", Tono.CORTEZA, Icons.Filled.Category, if (t.activo) Tono.OK else Tono.NEUTRO) { editando = t }
                    }
                }
            }
        }
    }
    if (nuevo || editando != null) {
        val t = editando
        var nombre by remember(t) { mutableStateOf(t?.nombre ?: "") }
        var categoria by remember(t) { mutableStateOf(t?.categoria ?: "") }
        var orden by remember(t) { mutableStateOf((t?.orden ?: (AdminStore.tipos.maxOfOrNull { it.orden } ?: 0) + 1).toString()) }
        var activo by remember(t) { mutableStateOf(t?.activo ?: true) }
        var error by remember(t) { mutableStateOf<String?>(null) }
        val cerrar = { nuevo = false; editando = null }
        ModalBottomSheet(onDismissRequest = cerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (t == null) "Nuevo tipo de producto" else "Editar tipo", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(categoria, { categoria = it }, label = { Text("Categoría *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(orden, { orden = it.filter(Char::isDigit).take(4) }, label = { Text("Orden en la lista") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                FilaSwitch("Activo", "Las emprendedoras lo pueden elegir al crear productos", activo, { activo = it })
                if (t != null && t.productos > 0) Text("${t.productos} productos lo usan: se puede desactivar, pero no borrar.", color = TextoSecundario, fontSize = 14.sp)
                error?.let { AvisoAccion(it, Tono.CORTEZA) }
                BotonPrimario(texto = "Guardar en el servidor", onClick = {
                    ambito.launch {
                        AdminStore.guardarTipo(t?.id, nombre, categoria, orden.toIntOrNull() ?: 0, activo)
                            .onSuccess { android.widget.Toast.makeText(contexto, "Tipo «${nombre.trim()}» guardado.", android.widget.Toast.LENGTH_SHORT).show(); cerrar() }
                            .onFailure { error = it.message }
                    }
                })
                if (t != null && t.productos == 0 && !AdminStore.sonEjemplo) BotonSecundario(texto = "Borrar este tipo", onClick = { borrar = t })
                BotonSecundario(texto = "Cancelar", onClick = cerrar)
            }
        }
    }
    borrar?.let { t ->
        AlertDialog(
            onDismissRequest = { borrar = null },
            title = { Text("¿Borrar «${t.nombre}»?", fontWeight = FontWeight.Bold) },
            text = { Text("Ningún producto lo usa. Esta acción no se puede deshacer (puedes crearlo de nuevo).") },
            confirmButton = {
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    borrar = null
                    ambito.launch {
                        AdminStore.eliminarTipo(t).onSuccess { editando = null; android.widget.Toast.makeText(contexto, "Tipo borrado.", android.widget.Toast.LENGTH_SHORT).show() }
                            .onFailure { android.widget.Toast.makeText(contexto, it.message, android.widget.Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Borrar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { borrar = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } }
        )
    }
}

// ------------------------------------------------------------------ Administradores

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAdminEquipo(nav: ControladorNavegacion) {
    val ambito = rememberCoroutineScope()
    val contexto = LocalContext.current
    LaunchedEffect(Unit) { if (!AdminStore.cargadoAlgunaVez) AdminStore.cargar() }
    var nuevo by remember { mutableStateOf(false) }
    var cambiar by remember { mutableStateOf<com.example.trabajodea10.data.CuentaAdmin?>(null) }
    PantallaGestion("Administradores", "${AdminStore.admins.count { it.estado == "ACTIVA" }} activos de ${AdminStore.admins.size}", nav,
        accion = "Nueva cuenta de administración", onAccion = { nuevo = true }) {
        item {
            AvisoAccion("El servidor impide desactivar tu propia cuenta y dejar la plataforma sin otra administración activa.", Tono.AZUL, icono = Icons.Filled.Info)
        }
        if (AdminStore.admins.isEmpty()) item { Vacio(titulo = "Sin datos", mensaje = "No se pudo leer la lista de administradores.") }
        else item {
            GrupoFilas {
                AdminStore.admins.forEachIndexed { i, a ->
                    if (i > 0) Separador()
                    FilaConsulta(
                        a.nombre.ifBlank { a.email } + if (a.esYo) " (tú)" else "", a.email,
                        when (a.estado) { "ACTIVA" -> "Activa"; "PENDIENTE_REGISTRO" -> "Falta que entre"; else -> "Desactivada" },
                        Tono.NEUTRO, Icons.Filled.SupervisorAccount,
                        when (a.estado) { "ACTIVA" -> Tono.OK; "PENDIENTE_REGISTRO" -> Tono.AMBAR; else -> Tono.CORTEZA },
                        onClick = if (a.esYo) null else ({ cambiar = a })
                    )
                }
            }
        }
    }
    if (nuevo) {
        var email by remember { mutableStateOf("") }
        var nombre by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        ModalBottomSheet(onDismissRequest = { nuevo = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nueva cuenta de administración", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text("Tendrá acceso a todo el panel (no a las ventas de las emprendedoras). Crea su cuenta con su propia contraseña.", color = TextoSecundario)
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                error?.let { AvisoAccion(it, Tono.CORTEZA) }
                BotonPrimario(texto = "Crear en el servidor", onClick = {
                    ambito.launch {
                        AdminStore.crearAdmin(email, nombre)
                            .onSuccess { android.widget.Toast.makeText(contexto, "Cuenta de administración creada.", android.widget.Toast.LENGTH_SHORT).show(); nuevo = false }
                            .onFailure { error = it.message }
                    }
                })
                BotonSecundario(texto = "Cancelar", onClick = { nuevo = false })
            }
        }
    }
    cambiar?.let { a ->
        val activar = a.estado != "ACTIVA" && a.estado != "PENDIENTE_REGISTRO"
        AlertDialog(
            onDismissRequest = { cambiar = null },
            title = { Text(if (activar) "¿Reactivar a ${a.nombre.ifBlank { a.email }}?" else "¿Desactivar a ${a.nombre.ifBlank { a.email }}?", fontWeight = FontWeight.Bold) },
            text = { Text(if (activar) "Vuelve a tener acceso al panel de administración." else "Pierde el acceso al panel de administración; su cuenta queda como compradora. No se borra nada.") },
            confirmButton = {
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    cambiar = null
                    ambito.launch {
                        AdminStore.cambiarEstadoAdmin(a, activar)
                            .onSuccess { android.widget.Toast.makeText(contexto, if (activar) "Cuenta reactivada." else "Cuenta desactivada.", android.widget.Toast.LENGTH_SHORT).show() }
                            .onFailure { android.widget.Toast.makeText(contexto, it.message, android.widget.Toast.LENGTH_LONG).show() }
                    }
                }) { Text(if (activar) "Reactivar" else "Desactivar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { cambiar = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } }
        )
    }
}

// ------------------------------------------------------------------ Bitácora y app sin cuenta

@Composable
fun PantallaAdminBitacora(nav: ControladorNavegacion) {
    LaunchedEffect(Unit) { AdminStore.cargar() }
    PantallaGestion("Bitácora", "Últimos ${AdminStore.bitacora.size} cambios registrados", nav) {
        if (AdminStore.bitacora.isEmpty()) item { Vacio(titulo = "Sin movimientos", mensaje = "Aún no hay cambios registrados en la bitácora.") }
        else items(AdminStore.bitacora.chunked(8)) { bloque ->
            GrupoFilas {
                bloque.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    FilaBitacora(e)
                }
            }
        }
    }
}

/** Navegación sin cuenta (W12): se lee y se cambia con la misma clave de configuración que la web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HojaNavegacionAnonima(onCerrar: () -> Unit, onResultado: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    var elegida by remember { mutableStateOf(VitrinaStore.navegacion) }
    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("App sin cuenta", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text("Qué ve quien abre la app sin iniciar sesión. Se aplica a todas las personas.", color = TextoSecundario)
            listOf(
                NavegacionAnonima.PERMITIR to "Se puede comprar sin cuenta",
                NavegacionAnonima.INVITAR to "Se invita a entrar con Google (con «Omitir»)",
                NavegacionAnonima.OBLIGAR to "Hay que entrar con Google"
            ).forEach { (valor, texto) -> ChipFiltro(texto, elegida == valor, { elegida = valor }, Modifier.fillMaxWidth()) }
            BotonPrimario(texto = "Guardar en el servidor", onClick = {
                ambito.launch {
                    AdminStore.guardarNavegacion(elegida).onSuccess { onResultado("Configuración guardada."); onCerrar() }.onFailure { onResultado(it.message ?: "No se pudo guardar.") }
                }
            })
            BotonSecundario(texto = "Cancelar", onClick = onCerrar)
        }
    }
}
