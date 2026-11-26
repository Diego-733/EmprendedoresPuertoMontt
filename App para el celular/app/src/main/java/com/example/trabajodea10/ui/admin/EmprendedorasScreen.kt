package com.example.trabajodea10.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.trabajodea10.data.AdminStore
import com.example.trabajodea10.data.CuentaEmprendedora
import com.example.trabajodea10.data.EstadoCuentaAdmin
import com.example.trabajodea10.data.ReglasAdmin
import com.example.trabajodea10.data.VitrinaStore
import com.example.trabajodea10.data.remoto.CodigoActivacion
import com.example.trabajodea10.ui.componentes.AccionCabecera
import com.example.trabajodea10.ui.componentes.AvatarIniciales
import com.example.trabajodea10.ui.componentes.AvisoAccion
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.CabeceraMarca
import com.example.trabajodea10.ui.componentes.CampoBusqueda
import com.example.trabajodea10.ui.componentes.ChipFiltro
import com.example.trabajodea10.ui.componentes.ContenedorChips
import com.example.trabajodea10.ui.componentes.EstadoCargando
import com.example.trabajodea10.ui.componentes.PildoraEstado
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.Tono
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.FondoLimpio
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.TextoSecundario

/*
 * Emprendedoras: gestión real con las funciones del servidor.
 *  · Autorizar → autorizar_emprendedora (11): devuelve un CÓDIGO DE ACTIVACIÓN que se muestra una vez.
 *  · Pendientes → generar código nuevo o revocar (solo si aún no se usó).
 *  · Emprendedoras antiguas sin autorización registrada → regularizar (deja constancia, no cambia nada más).
 *  · Editar nombre y nombre de la tienda (editar_emprendedora, 10), asociaciones (tienda_asociaciones),
 *    activar o desactivar la tienda (cambiar_estado_emprendedora, 10: efecto real en el servidor).
 * Toda acción pide confirmación cuando cambia algo importante y muestra el resultado real.
 */

internal fun tonoDe(e: EstadoCuentaAdmin) = when (e) {
    EstadoCuentaAdmin.ACTIVA -> Tono.OK
    EstadoCuentaAdmin.DESACTIVADA -> Tono.CORTEZA
    EstadoCuentaAdmin.PENDIENTE_REGISTRO, EstadoCuentaAdmin.PENDIENTE_ACTIVACION -> Tono.AMBAR
    EstadoCuentaAdmin.SIN_TIENDA -> Tono.AZUL
}

private val COLORES = listOf(0xFF1F5C46, 0xFF8C3B22, 0xFF2E4E7A, 0xFF6B4500, 0xFF5B3F7A, 0xFF1E6B3A)
private fun colorDe(texto: String): Long = COLORES[(texto.hashCode() and 0x7fffffff) % COLORES.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAdminEmprendedoras(
    nav: ControladorNavegacion,
    avisar: (String) -> Unit
) {
    val ambito = rememberCoroutineScope()
    var busqueda by rememberSaveable { mutableStateOf(AdminStore.busquedaAlAbrir.orEmpty()) }
    var filtro by remember { mutableStateOf(AdminStore.filtroAlAbrir) }
    AdminStore.busquedaAlAbrir = null
    var soloSinAutorizacion by remember { mutableStateOf(false) }
    var mostrarAlta by remember { mutableStateOf(false) }
    var seleccionada by remember { mutableStateOf<CuentaEmprendedora?>(null) }
    var codigoMostrado by remember { mutableStateOf<Pair<String, CodigoActivacion>?>(null) }
    LaunchedEffect(Unit) {
        AdminStore.filtroAlAbrir = null
        if (!AdminStore.cargadoAlgunaVez) AdminStore.cargar()
        VitrinaStore.cargar()
    }
    val resumen = ReglasAdmin.resumen(AdminStore.cuentas)
    val lista = ReglasAdmin.filtrar(AdminStore.cuentas, busqueda, filtro).filter { !soloSinAutorizacion || it.autorizada == false }

    Column(Modifier.fillMaxSize().background(FondoLimpio)) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CabeceraMarca(
                    sobretitulo = "Personas",
                    titulo = "Emprendedoras",
                    subtitulo = "${resumen.total} cuentas · ${resumen.activas} tiendas activas · ${resumen.pendientes} pendientes",
                    acciones = {
                        AccionCabecera(Icons.Filled.Refresh, "Actualizar la lista") { ambito.launch { AdminStore.cargar() } }
                        AccionCabecera(Icons.Filled.PersonAdd, "Autorizar una emprendedora") { mostrarAlta = true }
                    }
                ) {
                    CampoBusqueda(valor = busqueda, onValorChange = { busqueda = it }, placeholder = "Nombre, correo o tienda…")
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipFiltro("Todas (${resumen.total})", filtro == null && !soloSinAutorizacion, { filtro = null; soloSinAutorizacion = false })
                    ChipFiltro("Activas (${resumen.activas})", filtro == EstadoCuentaAdmin.ACTIVA, { filtro = EstadoCuentaAdmin.ACTIVA; soloSinAutorizacion = false })
                    ChipFiltro("Pendientes (${resumen.pendientes})", filtro == EstadoCuentaAdmin.PENDIENTE_REGISTRO, { filtro = EstadoCuentaAdmin.PENDIENTE_REGISTRO; soloSinAutorizacion = false })
                    ChipFiltro("Desactivadas (${resumen.desactivadas})", filtro == EstadoCuentaAdmin.DESACTIVADA, { filtro = EstadoCuentaAdmin.DESACTIVADA; soloSinAutorizacion = false })
                    if (resumen.sinTienda > 0) ChipFiltro("Sin tienda (${resumen.sinTienda})", filtro == EstadoCuentaAdmin.SIN_TIENDA, { filtro = EstadoCuentaAdmin.SIN_TIENDA; soloSinAutorizacion = false })
                    if (resumen.sinAutorizacion > 0) ChipFiltro("Sin autorización (${resumen.sinAutorizacion})", soloSinAutorizacion, { filtro = null; soloSinAutorizacion = true })
                }
            }
            if (AdminStore.sonEjemplo) item {
                AvisoAccion("Datos de ejemplo: esta versión no está conectada al servidor. Las acciones no se guardan.", Tono.AMBAR, Modifier.padding(horizontal = 16.dp))
            }
            if (!AdminStore.sonEjemplo && !AdminStore.registroControlado) item {
                AvisoAccion("El servidor todavía no tiene el registro controlado (migración 11): no se pueden generar códigos de activación desde aquí.",
                    Tono.AZUL, Modifier.padding(horizontal = 16.dp))
            }
            if (resumen.sinAutorizacion > 0 && !soloSinAutorizacion) item {
                AvisoAccion("${resumen.sinAutorizacion} emprendedora(s) se registraron por la vía abierta anterior, sin autorización registrada. Revísalas y decide.",
                    Tono.AZUL, Modifier.padding(horizontal = 16.dp), accion = "Ver", onAccion = { filtro = null; soloSinAutorizacion = true })
            }
            AdminStore.error?.let { e ->
                item { AvisoAccion(e, Tono.CORTEZA, Modifier.padding(horizontal = 16.dp), accion = "Reintentar", onAccion = { ambito.launch { AdminStore.cargar() } }) }
            }
            if (AdminStore.cargando && !AdminStore.cargadoAlgunaVez) item { EstadoCargando("Trayendo las cuentas…") }
            if (AdminStore.cargadoAlgunaVez && lista.isEmpty()) item {
                Vacio(titulo = "No hay cuentas que coincidan", mensaje = "Prueba con otra palabra o con «Todas».")
            }
            items(lista, key = { it.email + (it.usuarioId ?: "") }) { c ->
                Tarjeta(onClick = { seleccionada = c }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarIniciales(c.nombre, colorDe(c.email), tamano = 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nombre, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = NegroApple, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(c.tiendaNombre ?: c.email, fontSize = 14.sp, color = TextoSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (c.pendiente) Text(
                                if (c.codigoVigente) "Código vigente" + (c.codigoExpira?.let { " hasta " + FechasAdmin.momento(it).substringBefore(" ·") } ?: "")
                                else "Sin código vigente: genera uno nuevo",
                                fontSize = 13.sp, color = TextoSecundario)
                            if (c.autorizada == false) Text("Sin autorización registrada (vía abierta anterior)", fontSize = 13.sp, color = TextoSecundario)
                        }
                        Spacer(Modifier.width(8.dp))
                        PildoraEstado(c.estado.etiqueta, tonoDe(c.estado))
                    }
                }
            }
        }
    }

    if (mostrarAlta) HojaAutorizar(
        onCerrar = { mostrarAlta = false },
        onAutorizar = { email, nombre, asociacion, listo ->
            ambito.launch {
                AdminStore.autorizar(email, nombre, asociacion)
                    .onSuccess { codigoMostrado = nombre to it; mostrarAlta = false }
                    .onFailure { listo(it.message ?: "No se pudo autorizar.") }
            }
        }
    )

    codigoMostrado?.let { (nombre, codigo) -> DialogoCodigo(nombre, codigo) { codigoMostrado = null } }

    seleccionada?.let { c ->
        HojaCuenta(
            cuenta = c,
            onCerrar = { seleccionada = null },
            onVerTienda = { id -> seleccionada = null; nav.irA(Ruta.TiendaPublica(id)) },
            onResultado = { mensaje -> avisar(mensaje) },
            onCodigo = { codigo -> seleccionada = null; codigoMostrado = c.nombre to codigo }
        )
    }
}

/** Muestra el código UNA vez, con copiar y compartir (nunca contraseñas). */
@Composable
private fun DialogoCodigo(nombre: String, codigo: CodigoActivacion, onCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val mensaje = ReglasAdmin.mensajeCodigo(nombre, codigo)
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Código de activación", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Entrégalo a $nombre en persona o por un canal de confianza. Se muestra solo esta vez.")
                Text(codigo.codigo, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp, modifier = Modifier.semantics(mergeDescendants = true) {})
                Text("Correo: ${codigo.email}\nVence en 14 días. Sirve una sola vez y solo con ese correo.", color = TextoSecundario, fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                val intento = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, mensaje) }
                runCatching { contexto.startActivity(android.content.Intent.createChooser(intento, "Enviar el código")) }
            }) { Text("Compartir", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                val portapapeles = contexto.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                portapapeles.setPrimaryClip(android.content.ClipData.newPlainText("Código Alerce", mensaje))
                onCerrar()
            }) { Text("Copiar y cerrar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaAutorizar(onCerrar: () -> Unit, onAutorizar: (String, String, String?, (String) -> Unit) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var asociacion by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Autorizar una emprendedora", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text("Se genera un código de activación. Ella crea su cuenta con este correo y SU contraseña, y escribe el código. Tú nunca conoces su contraseña.",
                color = TextoSecundario, fontSize = 15.sp)
            OutlinedTextField(nombre, { nombre = it; error = null }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it; error = null }, label = { Text("Correo") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            if (VitrinaStore.asociaciones.isNotEmpty()) {
                Text("Asociación (opcional)", fontWeight = FontWeight.SemiBold)
                ContenedorChips(modifier = Modifier.fillMaxWidth(), espacioHorizontal = 8.dp, espacioVertical = 8.dp) {
                    ChipFiltro("Ninguna", asociacion == null, { asociacion = null })
                    VitrinaStore.asociaciones.forEach { a -> ChipFiltro(a.nombre, asociacion == a.id, { asociacion = a.id }) }
                }
            }
            error?.let { AvisoAccion(it, Tono.CORTEZA) }
            BotonPrimario(texto = if (enviando) "Autorizando…" else "Autorizar y generar código", enabled = !enviando, onClick = {
                enviando = true
                onAutorizar(email, nombre, asociacion) { e -> error = e; enviando = false }
            })
            BotonSecundario(texto = "Cancelar", onClick = onCerrar)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaCuenta(
    cuenta: CuentaEmprendedora,
    onCerrar: () -> Unit,
    onVerTienda: (String) -> Unit,
    onResultado: (String) -> Unit,
    onCodigo: (CodigoActivacion) -> Unit
) {
    val ambito = rememberCoroutineScope()
    var confirmar by remember { mutableStateOf<String?>(null) }   // ACTIVAR, DESACTIVAR, REVOCAR, CODIGO, REGULARIZAR
    var editando by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf(cuenta.nombre) }
    var tiendaNombre by remember { mutableStateOf(cuenta.tiendaNombre ?: "") }
    var trabajando by remember { mutableStateOf(false) }
    var asociacionesActuales by remember { mutableStateOf(cuenta.asociaciones.ifEmpty {
        cuenta.tiendaId?.let { VitrinaStore.pertenencias[it] } ?: emptyList() }) }

    fun ejecutar(bloque: suspend () -> Result<*>, ok: String) {
        trabajando = true
        ambito.launch {
            bloque().onSuccess { onResultado(ok); onCerrar() }.onFailure { onResultado(it.message ?: "No se pudo completar.") }
            trabajando = false
        }
    }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarIniciales(cuenta.nombre, colorDe(cuenta.email), tamano = 56)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(cuenta.nombre, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                    PildoraEstado(cuenta.estado.etiqueta, tonoDe(cuenta.estado))
                }
            }
            Dato("Correo", cuenta.email)
            Dato("Tienda", cuenta.tiendaNombre ?: "Aún sin tienda")
            cuenta.creadoEn?.takeIf { it.length >= 10 }?.let { Dato("Desde", FechasAdmin.momento(it).substringBefore(" ·")) }
            when (cuenta.estado) {
                EstadoCuentaAdmin.PENDIENTE_REGISTRO -> AvisoAccion("Autorizada. Falta que cree su cuenta con este correo y escriba el código.", Tono.AMBAR)
                EstadoCuentaAdmin.PENDIENTE_ACTIVACION -> AvisoAccion("Ya creó su cuenta: falta que escriba el código de activación.", Tono.AMBAR)
                EstadoCuentaAdmin.DESACTIVADA -> AvisoAccion("Desactivada: su tienda no se ve y no puede hacer cambios (lo bloquea el servidor). No se borró nada.", Tono.CORTEZA)
                else -> Unit
            }
            if (cuenta.cuentaAnterior) {
                AvisoAccion("La cuenta con este correo existía ANTES de autorizarla: podría ser de otra persona. Solo si confirmas en persona que es suya (por ejemplo, entra a esa cuenta frente a ti), permite que la active.", Tono.CORTEZA)
                BotonSecundario(texto = "Confirmé en persona que la cuenta es suya", enabled = !trabajando, onClick = { confirmar = "PERMITIR" })
            }
            if (cuenta.pendiente) {
                Text(if (cuenta.codigoVigente) "Hay un código vigente (${cuenta.intentos} intentos fallidos)." else "No hay un código vigente.", color = TextoSecundario)
                BotonPrimario(texto = "Generar código nuevo", enabled = !trabajando, onClick = { confirmar = "CODIGO" })
                BotonSecundario(texto = "Revocar la autorización", enabled = !trabajando, onClick = { confirmar = "REVOCAR" })
            }
            if (cuenta.autorizada == false && cuenta.usuarioId != null) {
                AvisoAccion("Se registró por la vía abierta anterior, sin autorización. Puedes dejar constancia de que la conservas (no cambia nada más) o desactivarla.", Tono.AZUL)
                BotonSecundario(texto = "Regularizar (dejar constancia)", enabled = !trabajando, onClick = { confirmar = "REGULARIZAR" })
            }
            cuenta.tiendaId?.let { id -> BotonPrimario(texto = "Ver su tienda pública", onClick = { onVerTienda(id) }) }

            if (cuenta.usuarioId != null && !cuenta.pendiente) {
                if (!editando) BotonSecundario(texto = "Editar nombre y tienda", onClick = { editando = true })
                else {
                    OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre de la emprendedora") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tiendaNombre, { tiendaNombre = it }, label = { Text("Nombre de la tienda") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    BotonPrimario(texto = "Guardar cambios", enabled = !trabajando, onClick = {
                        ejecutar({ AdminStore.editar(cuenta, nombre, tiendaNombre) }, "Datos de ${nombre.trim()} guardados en el servidor.")
                    })
                }
            }

            val tienda = cuenta.tiendaId
            if (tienda != null && VitrinaStore.asociaciones.isNotEmpty()) {
                Text("Asociaciones", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Toca para sumarla o quitarla. Se guarda en el servidor al instante.", color = TextoSecundario, fontSize = 14.sp)
                ContenedorChips(modifier = Modifier.fillMaxWidth(), espacioHorizontal = 8.dp, espacioVertical = 8.dp) {
                    VitrinaStore.asociaciones.forEach { a ->
                        val integra = a.id in asociacionesActuales
                        ChipFiltro(a.nombre, integra, {
                            trabajando = true
                            ambito.launch {
                                AdminStore.cambiarAsociacion(tienda, a.id, !integra)
                                    .onSuccess {
                                        asociacionesActuales = if (integra) asociacionesActuales - a.id else asociacionesActuales + a.id
                                        onResultado(if (integra) "Se quitó de ${a.nombre}." else "Se sumó a ${a.nombre}.")
                                    }
                                    .onFailure { onResultado(it.message ?: "No se pudo cambiar la asociación.") }
                                trabajando = false
                            }
                        })
                    }
                }
            }
            if (cuenta.usuarioId != null && cuenta.estado == EstadoCuentaAdmin.ACTIVA)
                BotonSecundario(texto = "Desactivar la tienda", enabled = !trabajando, onClick = { confirmar = "DESACTIVAR" })
            if (cuenta.usuarioId != null && cuenta.estado == EstadoCuentaAdmin.DESACTIVADA)
                BotonPrimario(texto = "Volver a activar la tienda", enabled = !trabajando, onClick = { confirmar = "ACTIVAR" })
        }
    }
    confirmar?.let { accion ->
        val (titulo, cuerpo, boton) = when (accion) {
            "ACTIVAR" -> Triple("¿Activar la tienda?", "La tienda de ${cuenta.nombre} vuelve a verse en la vitrina y ella puede volver a hacer cambios en su tienda.", "Activar")
            "DESACTIVAR" -> Triple("¿Desactivar la tienda?", "La tienda de ${cuenta.nombre} deja de verse en la vitrina y ella no podrá hacer cambios en su tienda hasta que la actives. No se borra nada.", "Desactivar")
            "REVOCAR" -> Triple("¿Revocar la autorización?", "El correo ${cuenta.email} deja de estar autorizado y su código deja de servir. Si ya creó su cuenta, queda como compradora.", "Revocar")
            "CODIGO" -> Triple("¿Generar un código nuevo?", "El código anterior deja de servir y se reinician los intentos. Vence en 14 días.", "Generar")
            "PERMITIR" -> Triple("¿Confirmaste en persona?", "La cuenta ${cuenta.email} existía antes de la autorización. Confirma solo si ${cuenta.nombre} entró a esa cuenta frente a ti. Después podrá activarla con su código.", "Confirmo")
            else -> Triple("¿Regularizar?", "Queda registrado que la administración conserva a ${cuenta.nombre} como emprendedora. No cambia su rol, su tienda ni sus datos.", "Regularizar")
        }
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(titulo, fontWeight = FontWeight.Bold) },
            text = { Text(cuerpo) },
            confirmButton = {
                TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    confirmar = null
                    when (accion) {
                        "ACTIVAR" -> ejecutar({ AdminStore.cambiarEstado(cuenta, true) }, "La tienda de ${cuenta.nombre} quedó activa.")
                        "DESACTIVAR" -> ejecutar({ AdminStore.cambiarEstado(cuenta, false) }, "La tienda de ${cuenta.nombre} quedó desactivada en el servidor.")
                        "REVOCAR" -> ejecutar({ AdminStore.revocar(cuenta) }, "Se revocó la autorización de ${cuenta.email}.")
                        "REGULARIZAR" -> ejecutar({ AdminStore.regularizar(cuenta) }, "Quedó registrada la autorización de ${cuenta.nombre}.")
                        "PERMITIR" -> ejecutar({ AdminStore.permitirCuentaExistente(cuenta) }, "Listo: ${cuenta.nombre} ya puede activar su cuenta con el código.")
                        "CODIGO" -> {
                            trabajando = true
                            ambito.launch {
                                AdminStore.regenerarCodigo(cuenta).onSuccess { onCodigo(it) }.onFailure { onResultado(it.message ?: "No se pudo generar.") }
                                trabajando = false
                            }
                        }
                    }
                }) { Text(boton, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(etiqueta, fontSize = 13.sp, color = TextoSecundario, fontWeight = FontWeight.SemiBold)
        Text(valor, fontSize = 17.sp, color = NegroApple)
    }
}
