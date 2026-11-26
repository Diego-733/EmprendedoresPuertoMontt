package com.example.trabajodea10.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.trabajodea10.data.AdminStore
import com.example.trabajodea10.data.FeriaPublica
import com.example.trabajodea10.data.FilaChecklist
import com.example.trabajodea10.data.ReglasFerias
import com.example.trabajodea10.data.SesionManager
import com.example.trabajodea10.data.VitrinaStore
import com.example.trabajodea10.data.remoto.FormFeria
import com.example.trabajodea10.ui.componentes.AvisoAccion
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.ChipFiltro
import com.example.trabajodea10.ui.componentes.ContenedorChips
import com.example.trabajodea10.ui.componentes.EstadoCargando
import com.example.trabajodea10.ui.componentes.FilaSwitch
import com.example.trabajodea10.ui.componentes.PildoraEstado
import com.example.trabajodea10.ui.componentes.Tono
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.TextoSecundario

/*
 * Gestión de ferias en Android con las funciones reales del servidor (07):
 *  · Crear o editar: el mismo upsert de la web (la feria nueva queda en BORRADOR).
 *  · Publicar (publicar_feria) y cancelar con motivo (cancelar_feria: avisa a las participantes).
 *  · Invitar o agregar directamente tiendas y asociaciones (invitar_a_feria).
 *  · Checklist con el estado de cada tienda y la asistencia del día (checklist_feria, registrar_asistencia).
 * La foto y el punto en el mapa se agregan en el panel web (en el celular se conservan si ya existen).
 */

internal fun etiquetaEstadoFeria(estado: String) = when (estado) {
    "BORRADOR" -> "Borrador"; "CANCELADA" -> "Cancelada"; "FINALIZADA" -> "Finalizada"; "EN_CURSO" -> "En curso"; else -> "Publicada"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HojaFormFeria(feria: FeriaPublica?, onCerrar: () -> Unit, onResultado: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(feria?.nombre ?: "") }
    var descripcion by remember { mutableStateOf(feria?.descripcion ?: "") }
    var lugar by remember { mutableStateOf(feria?.lugar ?: "") }
    var inicio by remember { mutableStateOf(feria?.fechaInicio ?: "") }
    var fin by remember { mutableStateOf(feria?.fechaFin ?: "") }
    var horario by remember { mutableStateOf(feria?.horario ?: "") }
    var organizador by remember { mutableStateOf(feria?.organizador ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    val id = remember { feria?.id ?: ("feria_" + java.lang.Long.toString(System.currentTimeMillis(), 36)) }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (feria == null) "Nueva feria" else "Editar feria", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            if (feria == null) Text("Se guarda en el servidor como borrador. Revísala y toca «Publicar» cuando esté lista.", color = TextoSecundario)
            OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(lugar, { lugar = it }, label = { Text("Lugar *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(inicio, { inicio = it }, label = { Text("Fecha de inicio * (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fin, { fin = it }, label = { Text("Fecha de término (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(horario, { horario = it }, label = { Text("Horario * (ej.: 10:00 a 18:00)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(organizador, { organizador = it }, label = { Text("Organiza") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("La imagen y el punto en el mapa se agregan desde el panel web.", color = TextoSecundario, fontSize = 14.sp)
            error?.let { AvisoAccion(it, Tono.CORTEZA) }
            BotonPrimario(texto = if (guardando) "Guardando…" else "Guardar en el servidor", enabled = !guardando, onClick = {
                guardando = true; error = null
                val form = FormFeria(id, feria == null, nombre, descripcion, lugar, inicio.trim(), fin.trim(), horario, organizador, feria?.lat, feria?.lng)
                ambito.launch {
                    AdminStore.guardarFeria(form)
                        .onSuccess { onResultado(if (feria == null) "Feria «${nombre.trim()}» guardada como borrador." else "Cambios de «${nombre.trim()}» guardados."); onCerrar() }
                        .onFailure { error = it.message }
                    guardando = false
                }
            })
            BotonSecundario(texto = "Cancelar", onClick = onCerrar)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HojaGestionFeria(feria: FeriaPublica, nav: ControladorNavegacion, onCerrar: () -> Unit, onResultado: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    var editar by remember { mutableStateOf(false) }
    var invitar by remember { mutableStateOf(false) }
    var cancelar by remember { mutableStateOf(false) }
    var motivo by remember { mutableStateOf("") }
    var trabajando by remember { mutableStateOf(false) }
    var checklist by remember { mutableStateOf<List<FilaChecklist>?>(null) }
    var errorChecklist by remember { mutableStateOf<String?>(null) }
    val hoy = ReglasFerias.fechaSantiago()
    LaunchedEffect(feria.id) {
        if (feria.estado != "BORRADOR") AdminStore.checklist(feria.id).onSuccess { checklist = it }.onFailure { errorChecklist = it.message }
    }

    if (editar) { HojaFormFeria(feria, onCerrar = { editar = false; onCerrar() }, onResultado = onResultado); return }
    if (invitar) { HojaInvitar(feria, onCerrar = { invitar = false; onCerrar() }, onResultado = onResultado); return }

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(feria.nombre, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, modifier = Modifier.weight(1f))
                PildoraEstado(etiquetaEstadoFeria(feria.estado), when (feria.estado) { "BORRADOR" -> Tono.NEUTRO; "CANCELADA" -> Tono.CORTEZA; else -> Tono.OK })
            }
            Text(listOf(com.example.trabajodea10.data.ReglasVitrina.rangoFechas(feria), feria.horario, feria.lugar).filter { it.isNotBlank() }.joinToString(" · "), color = TextoSecundario)
            if (feria.estado == "BORRADOR") AvisoAccion("Borrador: todavía no la ve nadie fuera de la administración.", Tono.NEUTRO)
            BotonSecundario(texto = "Editar datos", enabled = !trabajando, onClick = { editar = true })
            if (feria.estado == "BORRADOR" || (feria.estado == "CANCELADA" && (feria.fechaFin ?: feria.fechaInicio ?: "") >= hoy)) {
                BotonPrimario(texto = if (feria.estado == "BORRADOR") "Publicar" else "Volver a publicar", enabled = !trabajando, onClick = {
                    trabajando = true
                    ambito.launch {
                        AdminStore.publicarFeria(feria.id).onSuccess { onResultado("«${feria.nombre}» quedó publicada."); onCerrar() }.onFailure { onResultado(it.message ?: "No se pudo publicar.") }
                        trabajando = false
                    }
                })
            }
            if (feria.estado != "BORRADOR" && feria.estado != "CANCELADA") {
                BotonPrimario(texto = "Invitar o agregar tiendas", enabled = !trabajando, onClick = { invitar = true })
                BotonSecundario(texto = "Cancelar la feria", enabled = !trabajando, onClick = { cancelar = true })
            }
            if (feria.estado != "BORRADOR") BotonSecundario(texto = "Ver la ficha pública", onClick = { onCerrar(); nav.irA(Ruta.FeriaPublica(feria.id)) })

            // Checklist y asistencia
            if (feria.estado != "BORRADOR") {
                Text("Tiendas en la feria", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                val enCurso = (feria.fechaInicio ?: "9999") <= hoy
                when {
                    errorChecklist != null -> AvisoAccion(errorChecklist!!, Tono.CORTEZA)
                    checklist == null -> EstadoCargando("Trayendo el checklist…")
                    checklist!!.isEmpty() -> Text("Aún no hay tiendas invitadas.", color = TextoSecundario)
                    else -> checklist!!.forEachIndexed { i, fila ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        FilaChecklistAdmin(fila, permitirAsistencia = enCurso && feria.estado != "CANCELADA") { valor ->
                            ambito.launch {
                                AdminStore.asistencia(feria.id, fila.tiendaId, valor)
                                    .onSuccess {
                                        checklist = checklist!!.map { if (it.tiendaId == fila.tiendaId) it.copy(asistencia = valor) else it }
                                        onResultado("Asistencia de ${fila.nombre} guardada.")
                                    }
                                    .onFailure { onResultado(it.message ?: "No se pudo guardar la asistencia.") }
                            }
                        }
                    }
                }
                if (!enCurso) Text("La asistencia se registra desde el día de la feria.", color = TextoSecundario, fontSize = 14.sp)
            }
        }
    }
    if (cancelar) AlertDialog(
        onDismissRequest = { cancelar = false },
        title = { Text("¿Cancelar «${feria.nombre}»?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se avisa a las tiendas invitadas o confirmadas. La feria no se borra.")
                OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo (se envía en el aviso)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                cancelar = false; trabajando = true
                ambito.launch {
                    AdminStore.cancelarFeria(feria.id, motivo).onSuccess { n -> onResultado("«${feria.nombre}» se canceló. Avisos enviados: $n."); onCerrar() }
                        .onFailure { onResultado(it.message ?: "No se pudo cancelar.") }
                    trabajando = false
                }
            }) { Text("Cancelar la feria", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { cancelar = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Volver") } }
    )
}

@Composable
private fun FilaChecklistAdmin(fila: FilaChecklist, permitirAsistencia: Boolean, onAsistencia: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Column(Modifier.weight(1f)) {
                Text(fila.nombre, fontWeight = FontWeight.Bold, color = NegroApple)
                Text(when (fila.invitacion) { "PENDIENTE" -> "Invitada, sin responder"; "CONFIRMADA" -> "Confirmó"; "RECHAZADA" -> "No participa"; "AGREGADA" -> "Agregada por la administración"; else -> fila.invitacion } +
                    (fila.viaAsociacion?.let { " · por su asociación" } ?: ""), fontSize = 14.sp, color = TextoSecundario)
            }
            Spacer(Modifier.width(8.dp))
            PildoraEstado(when (fila.asistencia) { "ASISTIO" -> "Asistió"; "NO_ASISTIO" -> "No asistió"; else -> "Sin registrar" },
                when (fila.asistencia) { "ASISTIO" -> Tono.OK; "NO_ASISTIO" -> Tono.CORTEZA; else -> Tono.NEUTRO })
        }
        if (permitirAsistencia && fila.invitacion in listOf("CONFIRMADA", "AGREGADA")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipFiltro("Asistió", fila.asistencia == "ASISTIO", { onAsistencia("ASISTIO") })
                ChipFiltro("No asistió", fila.asistencia == "NO_ASISTIO", { onAsistencia("NO_ASISTIO") })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaInvitar(feria: FeriaPublica, onCerrar: () -> Unit, onResultado: (String) -> Unit) {
    val ambito = rememberCoroutineScope()
    var tiendas by remember { mutableStateOf(setOf<String>()) }
    var asociaciones by remember { mutableStateOf(setOf<String>()) }
    var directo by remember { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (SesionManager.tiendas.isEmpty()) SesionManager.cargarTiendas() }
    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Invitar a «${feria.nombre}»", fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            FilaSwitch("Agregar directamente", "Sin esperar respuesta (quedan como «Agregada por la administración»)", directo, { directo = it })
            if (VitrinaStore.asociaciones.isNotEmpty()) {
                Text("Asociaciones completas", fontWeight = FontWeight.Bold)
                ContenedorChips(modifier = Modifier.fillMaxWidth(), espacioHorizontal = 8.dp, espacioVertical = 8.dp) {
                    VitrinaStore.asociaciones.forEach { a -> ChipFiltro(a.nombre, a.id in asociaciones, { asociaciones = if (a.id in asociaciones) asociaciones - a.id else asociaciones + a.id }) }
                }
            }
            Text("Tiendas", fontWeight = FontWeight.Bold)
            ContenedorChips(modifier = Modifier.fillMaxWidth(), espacioHorizontal = 8.dp, espacioVertical = 8.dp) {
                SesionManager.tiendas.forEach { t -> ChipFiltro(t.nombre, t.id in tiendas, { tiendas = if (t.id in tiendas) tiendas - t.id else tiendas + t.id }) }
            }
            BotonPrimario(texto = if (enviando) "Enviando…" else if (directo) "Agregar" else "Enviar invitaciones",
                enabled = !enviando && (tiendas.isNotEmpty() || asociaciones.isNotEmpty()), onClick = {
                    enviando = true
                    ambito.launch {
                        AdminStore.invitar(feria.id, tiendas.toList(), asociaciones.toList(), directo)
                            .onSuccess { r ->
                                fun num(k: String) = r[k]?.toString()?.trim('"')?.toIntOrNull() ?: 0
                                onResultado("Servidor: ${num("nuevas")} nuevas, ${num("cambiadas")} cambiadas, ${num("sin_cambio")} sin cambio.")
                                onCerrar()
                            }
                            .onFailure { onResultado(it.message ?: "No se pudo invitar.") }
                        enviando = false
                    }
                })
            BotonSecundario(texto = "Cancelar", onClick = onCerrar)
        }
    }
}
