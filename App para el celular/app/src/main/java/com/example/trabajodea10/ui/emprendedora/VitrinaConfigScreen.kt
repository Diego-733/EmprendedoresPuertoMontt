package com.example.trabajodea10.ui.emprendedora

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trabajodea10.data.ContactoRecibido
import com.example.trabajodea10.data.ModoContacto
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.remoto.ClienteSupabase
import com.example.trabajodea10.data.remoto.VitrinaRepositorio
import com.example.trabajodea10.ui.comprador.CENTRO_PUERTO_MONTT
import com.example.trabajodea10.ui.comprador.MapaOsm
import com.example.trabajodea10.ui.comprador.PuntoMapa
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.FilaSwitch
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.TituloSeccion
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.publico.abrirWhatsApp
import com.example.trabajodea10.ui.theme.GrisBorde
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.RojoAlerce
import com.example.trabajodea10.ui.theme.TextoSecundario
import com.example.trabajodea10.ui.theme.VerdeContenedor
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

/*
 * EMPRENDEDORA (Entrega 6, checklist A16–A18):
 * - Mostrar u ocultar precios y elegir cómo la contactan (RF-CFG-01, RF-CFG-02).
 * - Punto de venta o retiro opcional marcado en un mapa (RF-MAP-04).
 * - Bandeja con los mensajes que llegaron por el formulario de la vitrina (RF-CFG-03).
 */

@Composable
fun PantallaConfigVitrina(nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    val actual = TramaStore.tienda
    var mostrarPrecios by remember { mutableStateOf(actual.mostrarPrecios) }
    var modo by remember { mutableStateOf(actual.contactoModo) }
    var lat by remember { mutableStateOf(actual.puntoLat) }
    var lng by remember { mutableStateOf(actual.puntoLng) }
    var nombrePunto by remember { mutableStateOf(actual.puntoNombre ?: "") }
    var guardando by remember { mutableStateOf(false) }
    val sinNumero = actual.whatsapp.count { it.isDigit() } < 8

    Column(Modifier.fillMaxSize()) {
        BarraSuperior(titulo = "Mi vitrina", subtitulo = "Precios, contacto y punto de retiro", onVolver = { nav.volver() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Tarjeta {
                Column(Modifier.padding(14.dp)) {
                    FilaSwitch(
                        titulo = "Mostrar mis precios",
                        descripcion = if (mostrarPrecios) "Las personas ven el precio de cada producto." else "Las personas verán «Consultar precio».",
                        checked = mostrarPrecios,
                        onCheckedChange = { mostrarPrecios = it }
                    )
                }
            }
            Tarjeta {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("¿Cómo te contactan?", fontWeight = FontWeight.Bold)
                    ModoContacto.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = modo == m, onClick = { modo = m }, role = Role.RadioButton),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = modo == m, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(m.etiqueta)
                        }
                    }
                    if (modo.usaWhatsApp && sinNumero) {
                        Text("Tu tienda no tiene un número de WhatsApp válido: agrégalo en «Tienda» para que aparezca el botón.",
                            color = RojoAlerce, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Tarjeta {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Punto de venta o retiro (opcional)", fontWeight = FontWeight.Bold)
                    Text("Toca el mapa para marcar tu local o el lugar donde entregas.", color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
                    val la = lat
                    val ln = lng
                    MapaOsm(
                        puntos = if (la != null && ln != null) listOf(PuntoMapa("punto", la, ln, nombrePunto.ifBlank { "Mi punto" })) else emptyList(),
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        centro = if (la != null && ln != null) GeoPoint(la, ln) else CENTRO_PUERTO_MONTT,
                        zoom = if (la != null) 16.0 else 12.0,
                        onTocarMapa = { a, b ->
                            lat = Math.round(a * 1e6) / 1e6
                            lng = Math.round(b * 1e6) / 1e6
                        }
                    )
                    OutlinedTextField(
                        value = nombrePunto, onValueChange = { nombrePunto = it.take(80) },
                        label = { Text("Nombre del lugar (ej: Mi local en Alerce)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (la != null && ln != null) {
                        Text("Marcado: $la, $ln", color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { lat = null; lng = null }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, tint = RojoAlerce)
                            Spacer(Modifier.width(6.dp))
                            Text("Quitar punto de retiro", color = RojoAlerce)
                        }
                    }
                }
            }
            BotonSecundario(
                texto = "Ver mi tienda como visitante",
                icono = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                onClick = { nav.irA(Ruta.TiendaPublica(TramaStore.tienda.id)) }
            )
            Spacer(Modifier.height(8.dp))
        }
        Surface(shadowElevation = 6.dp) {
            BotonPrimario(
                texto = if (guardando) "Guardando…" else "Guardar",
                enabled = !guardando,
                modifier = Modifier.padding(16.dp),
                onClick = {
                    guardando = true
                    val conPunto = lat != null && lng != null
                    val nueva = TramaStore.tienda.copy(
                        mostrarPrecios = mostrarPrecios,
                        contactoModo = modo,
                        puntoLat = if (conPunto) lat else null,
                        puntoLng = if (conPunto) lng else null,
                        puntoNombre = if (conPunto) nombrePunto.trim().ifBlank { null } else null
                    )
                    ambito.launch {
                        val enServidor = SesionManager.guardarTienda(nueva)
                        guardando = false
                        avisar(if (enServidor) "Listo: los visitantes ya ven el cambio." else "Guardado en el celular. Se subirá cuando haya conexión.")
                    }
                }
            )
        }
    }
}

@Composable
fun PantallaBandejaContactos(nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    val contexto = LocalContext.current
    val mensajes = remember { mutableStateListOf<ContactoRecibido>() }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val tiendaId = TramaStore.tienda.id

    suspend fun cargar() {
        cargando = true
        if (ClienteSupabase.sesion == null) {
            error = "Inicia sesión con tu cuenta para ver tus mensajes."
            cargando = false
            return
        }
        runCatching { VitrinaRepositorio.contactos(tiendaId) }
            .onSuccess { mensajes.clear(); mensajes.addAll(it); error = null }
            .onFailure { error = "No se pudieron cargar: ${it.message}" }
        cargando = false
    }
    LaunchedEffect(tiendaId) { cargar() }

    Column(Modifier.fillMaxSize()) {
        BarraSuperior(
            titulo = "Mensajes recibidos",
            subtitulo = "${mensajes.count { !it.leido }} sin leer",
            onVolver = { nav.volver() },
            acciones = { IconButton(onClick = { ambito.launch { cargar() } }) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") } }
        )
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            error?.let { e -> item { Text(e, color = RojoAlerce) } }
            if (!cargando && mensajes.isEmpty() && error == null) {
                item { Vacio(titulo = "Aún no tienes mensajes", mensaje = "Cuando alguien te escriba desde tu vitrina, aparecerá aquí.") }
            }
            if (cargando && mensajes.isEmpty()) item { Text("Cargando…", color = TextoSecundario) }
            items(mensajes, key = { it.id }) { m ->
                val producto = m.productoId?.let { TramaStore.productoPorId(it) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (m.leido) MaterialTheme.colorScheme.surface else VerdeContenedor,
                    border = BorderStroke(1.dp, GrisBorde)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.nombre, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(m.creadoEn.take(16).replace("T", " "), color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
                        }
                        if (m.medio.isNotBlank()) Text("Responder a: ${m.medio}", color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
                        if (producto != null) Text("Sobre: ${producto.nombre}", color = NegroApple, style = MaterialTheme.typography.bodySmall)
                        Text(m.mensaje)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val digitos = m.medio.count { it.isDigit() }
                            if (digitos >= 8) {
                                TextButton(onClick = { abrirWhatsApp(contexto, m.medio, "Hola ${m.nombre}, te respondo desde ${TramaStore.tienda.nombre}.") }) {
                                    Icon(Icons.Filled.Chat, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("WhatsApp")
                                }
                            } else if (m.medio.contains("@")) {
                                TextButton(onClick = {
                                    runCatching {
                                        contexto.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + m.medio.trim()))
                                            .putExtra(Intent.EXTRA_SUBJECT, "Respuesta de ${TramaStore.tienda.nombre}")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                }) { Icon(Icons.Filled.Email, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Correo") }
                            }
                            if (!m.leido) {
                                TextButton(onClick = {
                                    ambito.launch {
                                        runCatching { VitrinaRepositorio.marcarLeido(m.id) }
                                            .onSuccess {
                                                val i = mensajes.indexOfFirst { it.id == m.id }
                                                if (i >= 0) mensajes[i] = m.copy(leido = true)
                                            }
                                            .onFailure { avisar("No se pudo marcar: ${it.message}") }
                                    }
                                }) { Text("Marcar leído") }
                            }
                        }
                    }
                }
            }
        }
    }
}
