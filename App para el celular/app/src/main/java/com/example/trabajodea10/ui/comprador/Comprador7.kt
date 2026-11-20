package com.example.trabajodea10.ui.comprador

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trabajodea10.avisos.AvisosCelular
import com.example.trabajodea10.data.FeriasStore
import com.example.trabajodea10.data.ParticipantePublico
import com.example.trabajodea10.data.ReglasVitrina
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.Tienda
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.VitrinaStore
import com.example.trabajodea10.data.remoto.ClienteSupabase
import com.example.trabajodea10.data.remoto.FeriasRepositorio
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.FotoProducto
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.TituloSeccion
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.TextoSecundario
import kotlinx.coroutines.launch

/*
 * Entrega 7 en el modo comprador: seguir (con la acción pendiente al iniciar sesión),
 * consentimiento explícito para avisos, participantes de una feria con muestra de su
 * catálogo público y calificaciones. Las reglas las vuelve a comprobar el servidor.
 */

/** «Seguir» / «Siguiendo» de una tienda o asociación (RF-CMP-06, RF-ACC-08). */
@Composable
fun BotonSeguir(tipo: String, id: String, nombre: String, nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    var pedirSesion by remember { mutableStateOf(false) }
    var ofrecerAvisos by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    val siguiendo = FeriasStore.siguiendo(tipo, id)

    val contenido = if (siguiendo) "Dejar de seguir a $nombre" else "Seguir a $nombre"
    OutlinedButton(
        onClick = {
            if (ClienteSupabase.sesion == null) { pedirSesion = true; return@OutlinedButton }
            ocupado = true
            ambito.launch {
                val error = if (siguiendo) FeriasStore.dejarDeSeguir(tipo, id) else FeriasStore.seguir(tipo, id)
                ocupado = false
                when {
                    error != null -> avisar(error)
                    siguiendo -> avisar("Dejaste de seguir a $nombre. No recibirás sus avisos.")
                    else -> { avisar("Ahora sigues a $nombre."); if (FeriasStore.consentimiento != true) ofrecerAvisos = true }
                }
            }
        },
        enabled = !ocupado,
        modifier = Modifier.heightIn(min = 48.dp).semantics {
            contentDescription = contenido
            stateDescription = if (siguiendo) "Siguiendo" else "Sin seguir"
        }
    ) {
        Icon(if (siguiendo) Icons.Filled.Check else Icons.Filled.PersonAdd, contentDescription = null)
        Text(if (siguiendo) "  Siguiendo" else "  Seguir", fontWeight = FontWeight.Bold)
    }

    if (pedirSesion) {
        AlertDialog(
            onDismissRequest = { pedirSesion = false },
            title = { Text("Inicia sesión para seguir") },
            text = { Text("Para seguir a $nombre necesitas una cuenta. Al volver quedarás siguiéndola, sin tener que tocar de nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    pedirSesion = false
                    FeriasStore.guardarPendiente(tipo, id, nombre)
                    nav.irAIngreso()
                }) { Text("Iniciar sesión") }
            },
            dismissButton = { TextButton(onClick = { pedirSesion = false }) { Text("Ahora no") } }
        )
    }
    if (ofrecerAvisos) DialogoConsentimiento(nombre, onCerrar = { ofrecerAvisos = false }, avisar = avisar)
}

/** Consentimiento explícito (RNF-PRI-02) y, en Android 13+, el permiso del sistema. */
@Composable
fun DialogoConsentimiento(nombre: String?, onCerrar: () -> Unit, avisar: (String) -> Unit) {
    val contexto = LocalContext.current
    val ambito = rememberCoroutineScope()
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        FeriasStore.permisoCambiado()
        if (!ok) avisar("Sin permiso del celular: los avisos quedarán en «Mis avisos».")
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("¿Quieres recibir avisos?") },
        text = {
            Text("Te avisaremos cuando ${nombre ?: "las tiendas y asociaciones que sigues"} confirme su participación en una feria. " +
                "Solo eso: no enviamos publicidad. Puedes darte de baja cuando quieras en Mi perfil → Avisos.")
        },
        confirmButton = {
            TextButton(onClick = {
                onCerrar()
                ambito.launch {
                    val error = FeriasStore.guardarConsentimiento(true)
                    if (error != null) { avisar(error); return@launch }
                    avisar("Listo: te avisaremos.")
                    if (Build.VERSION.SDK_INT >= 33 && !AvisosCelular.puedeNotificar(contexto)) permiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) { Text("Sí, avisarme") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Ahora no") } }
    )
}

/** Participantes confirmadas con una muestra de su catálogo público (RF-FER-10). */
@Composable
fun SeccionParticipantes(feriaId: String, nav: ControladorNavegacion) {
    var participantes by remember { mutableStateOf<List<ParticipantePublico>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(feriaId) {
        runCatching { FeriasRepositorio.participantes(feriaId) }
            .onSuccess { participantes = it; if (it.any { p -> TramaStore.productosDeTienda(p.tiendaId).isEmpty() }) SesionManager.cargarTiendas() }
            .onFailure { error = it.message ?: "Sin conexión" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TituloSeccion(texto = "Participan")
        when {
            error != null -> Text("No se pudieron cargar las participantes: $error", color = TextoSecundario)
            participantes == null -> Text("Cargando participantes…", color = TextoSecundario)
            participantes!!.isEmpty() -> Text("Aún no hay participantes confirmadas.", color = TextoSecundario)
        }
        participantes.orEmpty().forEach { p ->
            val tienda = SesionManager.tiendas.firstOrNull { it.id == p.tiendaId }
            val muestra = TramaStore.productosDeTienda(p.tiendaId).take(3)   // solo productos públicos
            Tarjeta(onClick = { nav.irA(Ruta.TiendaPublica(p.tiendaId)) }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.nombre, fontWeight = FontWeight.Bold)
                    Text(listOf(p.rubro, p.sector).filter { it.isNotBlank() }.joinToString(" · "), color = TextoSecundario,
                        style = MaterialTheme.typography.bodySmall)
                    if (muestra.isEmpty()) Text("Sin productos públicos por ahora.", color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        muestra.forEach { prod ->
                            Column(Modifier.weight(1f)) {
                                FotoProducto(prod.fotoUrl, prod.colorFoto, prod.nombre, Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 96.dp), RoundedCornerShape(10.dp), 18.sp)
                                Text(prod.nombre, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                Text(ReglasVitrina.textoPrecio(tienda, prod.precio), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        repeat(3 - muestra.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Calificaciones de una tienda o un producto (RF-CMP-09). Solo si la emprendedora las activó. */
@Composable
fun SeccionCalificaciones(tienda: Tienda, productoId: String?, nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    var resumen by remember { mutableStateOf<Pair<Double, Int>?>(null) }
    var dialogo by remember { mutableStateOf(false) }
    var refrescar by remember { mutableStateOf(0) }
    LaunchedEffect(tienda.id, refrescar) { resumen = runCatching { FeriasRepositorio.resumenCalificaciones(tienda.id) }.getOrNull() }
    if (!tienda.calificacionesActivas) {
        Text("Esta tienda no recibe calificaciones.", color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        resumen?.let { (prom, n) ->
            Text(if (n == 0) "Aún sin calificaciones" else "★ ${"%.1f".format(prom)} · $n calificación${if (n == 1) "" else "es"} de la tienda",
                fontWeight = FontWeight.SemiBold)
        }
        BotonSecundario(texto = if (productoId == null) "Calificar la tienda" else "Calificar este producto", onClick = {
            if (ClienteSupabase.sesion == null) { avisar("Inicia sesión para calificar."); nav.irAIngreso() } else dialogo = true
        })
    }
    if (dialogo) {
        var estrellas by remember { mutableStateOf(0) }
        var comentario by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogo = false },
            title = { Text(if (productoId == null) "Califica a ${tienda.nombre}" else "Califica este producto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        (1..5).forEach { n ->
                            IconButton(onClick = { estrellas = n }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics { contentDescription = "$n estrella${if (n == 1) "" else "s"}" }) {
                                Icon(if (n <= estrellas) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    OutlinedTextField(value = comentario, onValueChange = { comentario = it.take(500) }, label = { Text("Comentario (opcional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = estrellas > 0, onClick = {
                    dialogo = false
                    ambito.launch {
                        runCatching { FeriasRepositorio.calificar(tienda.id, productoId, estrellas, comentario) }
                            .onSuccess { avisar("¡Gracias! Tu calificación quedó guardada."); refrescar++ }
                            .onFailure { avisar(it.message ?: "No se pudo guardar la calificación.") }
                    }
                }) { Text("Enviar") }
            },
            dismissButton = { TextButton(onClick = { dialogo = false }) { Text("Cancelar") } }
        )
    }
}

/** Mi perfil → Avisos: consentimiento, lo que sigo y bandeja (RNF-PRI-02, RF-CMP-06). */
@Composable
fun SeccionAvisosCuenta(nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    var preguntar by remember { mutableStateOf(false) }
    if (ClienteSupabase.sesion == null) {
        TituloSeccion(texto = "Avisos")
        Text("Inicia sesión para seguir tiendas y recibir avisos de sus ferias.", color = TextoSecundario)
        return
    }
    LaunchedEffect(Unit) { FeriasStore.cargarAvisos() }
    TituloSeccion(texto = "Avisos de ferias")
    Tarjeta {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recibir avisos", fontWeight = FontWeight.SemiBold)
                    Text("Cuando alguien que sigues confirma su participación en una feria.", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
                Switch(checked = FeriasStore.consentimiento == true, onCheckedChange = { si ->
                    if (si) preguntar = true
                    else ambito.launch {
                        val e = FeriasStore.guardarConsentimiento(false)
                        avisar(e ?: "Te diste de baja: ya no recibirás avisos de ferias.")
                    }
                }, modifier = Modifier.semantics { contentDescription = "Recibir avisos de ferias" })
            }
        }
    }
    if (preguntar) DialogoConsentimiento(null, onCerrar = { preguntar = false }, avisar = avisar)

    TituloSeccion(texto = "Lo que sigues")
    if (FeriasStore.seguidos.isEmpty()) Text("Aún no sigues tiendas ni asociaciones.", color = TextoSecundario)
    FeriasStore.seguidos.sorted().forEach { clave ->
        val tipo = clave.substringBefore(":"); val id = clave.substringAfter(":")
        val nombre = if (tipo == "TIENDA") SesionManager.tiendas.firstOrNull { it.id == id }?.nombre ?: id else VitrinaStore.asociacion(id)?.nombre ?: id
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(nombre, fontWeight = FontWeight.SemiBold)
                Text(if (tipo == "TIENDA") "Tienda" else "Asociación", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
            TextButton(onClick = {
                ambito.launch { avisar(FeriasStore.dejarDeSeguir(tipo, id) ?: "Dejaste de seguir a $nombre.") }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dejar de seguir") }
        }
    }

    TituloSeccion(texto = "Mis avisos")
    val visibles = FeriasStore.avisos.filter { it.estado != "OMITIDO" }
    if (visibles.isEmpty()) Text("No tienes avisos.", color = TextoSecundario)
    visibles.take(10).forEach { a ->
        Tarjeta(onClick = {
            ambito.launch { runCatching { FeriasRepositorio.marcarAviso(a.id, leido = true, recibido = false) } }   // leído en la bandeja
            a.feriaId?.let { nav.irA(Ruta.FeriaPublica(it)) }
        }) {
            Column(Modifier.padding(12.dp)) {
                Text(a.titulo, fontWeight = if (a.leido) FontWeight.Normal else FontWeight.Bold)
                if (a.cuerpo.isNotBlank()) Text(a.cuerpo, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
        }
    }
}

/** Configuración de la emprendedora: avisos de contacto con horario y calificaciones (RF-CFG-04, RF-CFG-05). */
@Composable
fun SeccionAvisosYCalificacionesTienda(avisar: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    val tienda = TramaStore.tienda
    var activos by remember { mutableStateOf(false) }
    var desde by remember { mutableStateOf("09:00") }
    var hasta by remember { mutableStateOf("20:00") }
    var cargado by remember { mutableStateOf(false) }
    var calificaciones by remember(tienda.id) { mutableStateOf(tienda.calificacionesActivas) }
    LaunchedEffect(tienda.id) {
        runCatching { FeriasRepositorio.avisosContacto(tienda.id) }.onSuccess { (a, d, h) -> activos = a; desde = d; hasta = h; cargado = true }
            .onFailure { avisar("No se pudo leer tu horario de avisos: ${it.message}") }
    }

    TituloSeccion(texto = "Avisos de mensajes")
    Tarjeta {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Avisarme cuando llegue un mensaje", fontWeight = FontWeight.SemiBold)
                    Text("Fuera del horario, el mensaje queda en tu bandeja sin avisar.", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
                Switch(checked = activos, onCheckedChange = { activos = it }, modifier = Modifier.semantics { contentDescription = "Avisos de mensajes" })
            }
            if (activos) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SelectorHora("Desde", desde) { desde = it }
                    SelectorHora("Hasta", hasta) { hasta = it }
                }
                Text(if (desde == hasta) "Todo el día." else "Horario de Chile continental.", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
            BotonPrimario(texto = "Guardar avisos de mensajes", enabled = cargado, onClick = {
                ambito.launch {
                    runCatching { FeriasRepositorio.guardarAvisosContacto(activos, desde, hasta) }
                        .onSuccess { avisar(if (activos) "Guardado: te avisaremos de $desde a $hasta." else "Guardado: los mensajes llegarán a tu bandeja sin avisar.") }
                        .onFailure { avisar("No se guardó (necesitas conexión): ${it.message}") }
                }
            })
        }
    }

    TituloSeccion(texto = "Calificaciones")
    Tarjeta {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Permitir que me califiquen", fontWeight = FontWeight.SemiBold)
                Text("Quien compra podrá calificar tu tienda y tus productos. Si lo apagas, nadie puede calificar.", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
            }
            Switch(checked = calificaciones, onCheckedChange = { nuevo ->
                ambito.launch {
                    runCatching { FeriasRepositorio.cambiarCalificaciones(tienda.id, nuevo) }
                        .onSuccess {
                            calificaciones = nuevo
                            TramaStore.guardarTienda(TramaStore.tienda.copy(calificacionesActivas = nuevo))
                            avisar(if (nuevo) "Calificaciones activadas." else "Calificaciones desactivadas.")
                        }
                        .onFailure { avisar("No se cambió (necesitas conexión): ${it.message}") }
                }
            }, modifier = Modifier.semantics { contentDescription = "Permitir calificaciones" })
        }
    }
}

@Composable
private fun SelectorHora(etiqueta: String, valor: String, onCambio: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { abierto = true }, modifier = Modifier.heightIn(min = 48.dp).width(130.dp)) { Text("$etiqueta $valor") }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            (0..23).flatMap { h -> listOf("%02d:00".format(h), "%02d:30".format(h)) }.forEach { hora ->
                DropdownMenuItem(text = { Text(hora) }, onClick = { onCambio(hora); abierto = false })
            }
        }
    }
}
