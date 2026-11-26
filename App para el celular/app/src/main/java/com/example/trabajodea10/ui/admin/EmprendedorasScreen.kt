package com.example.trabajodea10.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.semantics.semantics
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
import com.example.trabajodea10.ui.componentes.AccionCabecera
import com.example.trabajodea10.ui.componentes.AvatarIniciales
import com.example.trabajodea10.ui.componentes.AvisoAccion
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.CabeceraMarca
import com.example.trabajodea10.ui.componentes.CampoBusqueda
import com.example.trabajodea10.ui.componentes.ChipFiltro
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
 * Emprendedoras (rediseño v2). Antes mostraba cuentas de EJEMPLO («@trama.app») y cambiaba su estado
 * solo en el celular. Ahora:
 *  - la lista viene del servidor (listar_emprendedoras: tiendas activas, desactivadas, sin tienda y
 *    correos autorizados que aún no se registran);
 *  - «Autorizar correo» escribe en cuentas_autorizadas, como siempre (RN-01);
 *  - activar o desactivar una tienda usa cambiar_estado_emprendedora, la misma función del panel web,
 *    con confirmación. El servidor decide si se puede (es_admin()).
 */

private fun tonoDe(e: EstadoCuentaAdmin) = when (e) {
    EstadoCuentaAdmin.ACTIVA -> Tono.OK
    EstadoCuentaAdmin.DESACTIVADA -> Tono.CORTEZA
    EstadoCuentaAdmin.PENDIENTE_REGISTRO -> Tono.AMBAR
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
    var busqueda by rememberSaveable { mutableStateOf("") }
    var filtro by remember { mutableStateOf(AdminStore.filtroAlAbrir) }
    var mostrarAlta by remember { mutableStateOf(false) }
    var seleccionada by remember { mutableStateOf<CuentaEmprendedora?>(null) }
    LaunchedEffect(Unit) {
        AdminStore.filtroAlAbrir = null
        if (!AdminStore.cargadoAlgunaVez) AdminStore.cargar()
    }
    val resumen = ReglasAdmin.resumen(AdminStore.cuentas)
    val lista = ReglasAdmin.filtrar(AdminStore.cuentas, busqueda, filtro)

    Column(Modifier.fillMaxSize().background(FondoLimpio)) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CabeceraMarca(
                    sobretitulo = "Personas",
                    titulo = "Emprendedoras",
                    subtitulo = "${resumen.total} cuentas · ${resumen.activas} tiendas activas",
                    acciones = {
                        AccionCabecera(Icons.Filled.Refresh, "Actualizar la lista") { ambito.launch { AdminStore.cargar() } }
                        AccionCabecera(Icons.Filled.PersonAdd, "Autorizar un correo") { mostrarAlta = true }
                    }
                ) {
                    CampoBusqueda(valor = busqueda, onValorChange = { busqueda = it }, placeholder = "Nombre, correo o tienda…")
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChipFiltro("Todas (${resumen.total})", filtro == null, { filtro = null })
                    ChipFiltro("Activas (${resumen.activas})", filtro == EstadoCuentaAdmin.ACTIVA, { filtro = EstadoCuentaAdmin.ACTIVA })
                    ChipFiltro("Por registrarse (${resumen.pendientes})", filtro == EstadoCuentaAdmin.PENDIENTE_REGISTRO, { filtro = EstadoCuentaAdmin.PENDIENTE_REGISTRO })
                    ChipFiltro("Desactivadas (${resumen.desactivadas})", filtro == EstadoCuentaAdmin.DESACTIVADA, { filtro = EstadoCuentaAdmin.DESACTIVADA })
                    ChipFiltro("Sin tienda (${resumen.sinTienda})", filtro == EstadoCuentaAdmin.SIN_TIENDA, { filtro = EstadoCuentaAdmin.SIN_TIENDA })
                }
            }
            if (AdminStore.sonEjemplo) item {
                AvisoAccion("Datos de ejemplo: esta versión no está conectada al servidor.", Tono.AMBAR, Modifier.padding(horizontal = 16.dp))
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
        onAutorizar = { email, nombre, listo ->
            ambito.launch {
                AdminStore.autorizar(email, nombre)
                    .onSuccess { avisar(it); mostrarAlta = false }
                    .onFailure { listo(it.message ?: "No se pudo autorizar. Revisa la conexión.") }
            }
        }
    )

    seleccionada?.let { c ->
        HojaCuenta(
            cuenta = c,
            onCerrar = { seleccionada = null },
            onVerTienda = { id -> seleccionada = null; nav.irA(Ruta.TiendaPublica(id)) },
            onCambiarEstado = { activa ->
                ambito.launch {
                    AdminStore.cambiarEstado(c, activa)
                        .onSuccess { avisar(if (activa) "La tienda de ${c.nombre} quedó activa." else "La tienda de ${c.nombre} quedó desactivada.") }
                        .onFailure { avisar(it.message ?: "No se pudo cambiar el estado.") }
                    seleccionada = null
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaAutorizar(onCerrar: () -> Unit, onAutorizar: (String, String, (String) -> Unit) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Autorizar un correo", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text("Cuando esa persona se registre con este correo, el servidor la dejará como emprendedora (RN-01).",
                color = TextoSecundario, fontSize = 15.sp)
            OutlinedTextField(nombre, { nombre = it; error = null }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it; error = null }, label = { Text("Correo") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            error?.let { AvisoAccion(it, Tono.CORTEZA) }
            BotonPrimario(texto = if (enviando) "Autorizando…" else "Autorizar correo", enabled = !enviando, onClick = {
                enviando = true
                onAutorizar(email, nombre) { e -> error = e; enviando = false }
            })
            BotonSecundario(texto = "Cancelar", onClick = onCerrar)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaCuenta(cuenta: CuentaEmprendedora, onCerrar: () -> Unit, onVerTienda: (String) -> Unit, onCambiarEstado: (Boolean) -> Unit) {
    var confirmar by remember { mutableStateOf<Boolean?>(null) }
    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            if (cuenta.estado == EstadoCuentaAdmin.PENDIENTE_REGISTRO)
                AvisoAccion("El correo está autorizado; falta que la persona cree su cuenta con él.", Tono.AMBAR)
            cuenta.tiendaId?.let { id -> BotonPrimario(texto = "Ver su tienda pública", onClick = { onVerTienda(id) }) }
            if (cuenta.usuarioId != null && cuenta.estado == EstadoCuentaAdmin.ACTIVA)
                BotonSecundario(texto = "Desactivar la tienda", onClick = { confirmar = false })
            if (cuenta.usuarioId != null && cuenta.estado == EstadoCuentaAdmin.DESACTIVADA)
                BotonSecundario(texto = "Volver a activar la tienda", onClick = { confirmar = true })
        }
    }
    confirmar?.let { activa ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(if (activa) "¿Activar la tienda?" else "¿Desactivar la tienda?", fontWeight = FontWeight.Bold) },
            text = {
                Text(if (activa) "La tienda de ${cuenta.nombre} vuelve a verse en la vitrina y ella puede volver a hacer cambios en su tienda."
                    else "La tienda de ${cuenta.nombre} deja de verse en la vitrina y ella no podrá hacer cambios en su tienda hasta que la actives. No se borra nada.")
            },
            confirmButton = { TextButton(onClick = { confirmar = null; onCambiarEstado(activa) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (activa) "Activar" else "Desactivar", fontWeight = FontWeight.Bold) } },
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
