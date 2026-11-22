package com.example.trabajodea10.ui.emprendedora

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trabajodea10.avisos.AvisosCelular
import com.example.trabajodea10.data.EstadoInvitacion7
import com.example.trabajodea10.data.FeriasStore
import com.example.trabajodea10.data.MiFeria
import com.example.trabajodea10.data.ReglasFerias
import com.example.trabajodea10.data.ReglasVitrina
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.BotonPrimario
import com.example.trabajodea10.ui.componentes.BotonSecundario
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.TituloSeccion
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.theme.TextoSecundario
import kotlinx.coroutines.launch

/**
 * Mis ferias (Entrega 7): invitaciones con respuesta de un toque (RF-FER-08) y
 * calendario de las ferias a las que está invitada o confirmada (RF-FER-09).
 * Todo viene del servidor; sin señal se muestra la copia del celular y una
 * respuesta tocada queda «pendiente de conexión» hasta que el servidor la confirme.
 */
@Composable
fun PantallaMisFerias(nav: ControladorNavegacion, avisar: (String) -> Unit) {
    val contexto = LocalContext.current
    val ambito = rememberCoroutineScope()
    var enviando by remember { mutableStateOf<String?>(null) }
    var puedeNotificar by remember { mutableStateOf(AvisosCelular.puedeNotificar(contexto)) }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        puedeNotificar = AvisosCelular.puedeNotificar(contexto)
        FeriasStore.permisoCambiado()
        avisar(if (ok) "Listo: te avisaremos de invitaciones y recordatorios." else "Sin permiso: revisa tus invitaciones en esta pantalla.")
    }

    LaunchedEffect(Unit) { FeriasStore.cargarMisFerias() }

    val hoy = ReglasFerias.fechaSantiago()
    val pendientes = FeriasStore.misFerias.filter {
        it.estadoInvitacion == EstadoInvitacion7.PENDIENTE && ReglasFerias.feriaActiva(it.estado, it.fechaInicio, it.fechaFin, hoy)
    }
    val calendario = ReglasFerias.calendario(FeriasStore.misFerias.toList(), hoy)

    fun responder(f: MiFeria, participo: Boolean) {
        enviando = f.feriaId
        ambito.launch {
            val (r, mensaje) = FeriasStore.responder(f.feriaId, participo)
            enviando = null
            avisar(when (r) {
                FeriasStore.ResultadoRespuesta.CONFIRMADA_POR_SERVIDOR -> if (participo) "Confirmaste tu participación en ${f.nombre}." else "Respondiste que no participas en ${f.nombre}."
                FeriasStore.ResultadoRespuesta.PENDIENTE_DE_CONEXION -> "Sin conexión: tu respuesta quedó guardada en el celular y se enviará al volver la señal."
                FeriasStore.ResultadoRespuesta.RECHAZADA -> mensaje ?: "No se pudo guardar tu respuesta."
            })
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraSuperior(titulo = "Mis ferias", subtitulo = "Solo por invitación de la administración")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!puedeNotificar) {
                item {
                    Tarjeta {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Notifications, contentDescription = null)
                                Text("  Activa los avisos del celular", fontWeight = FontWeight.Bold)
                            }
                            Text("Así recibes las invitaciones y recordatorios de ferias aunque no tengas la app abierta.",
                                style = MaterialTheme.typography.bodyMedium)
                            BotonPrimario(texto = "Activar avisos", onClick = {
                                if (Build.VERSION.SDK_INT >= 33) pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else avisar("Activa las notificaciones de la app en los ajustes del celular.")
                            })
                        }
                    }
                }
            }
            FeriasStore.errorFerias?.let { e -> item { Text(e, color = TextoSecundario) } }

            item { TituloSeccion(texto = "Invitaciones por responder (${pendientes.size})") }
            if (pendientes.isEmpty()) {
                item { Text(if (FeriasStore.cargandoFerias) "Cargando…" else "No tienes invitaciones pendientes.", color = TextoSecundario) }
            }
            items(pendientes, key = { "inv_" + it.feriaId }) { f ->
                Tarjeta {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(f.nombre, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(textoFechas(f), color = TextoSecundario)
                        if (f.ubicacion.isNotBlank()) Text("📍 ${f.ubicacion}", color = TextoSecundario)
                        f.respuestaPendiente?.let {
                            ChipTexto(if (it) "Participo · pendiente de conexión" else "No participo · pendiente de conexión", Color(0xFFFFEDD5))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BotonPrimario(texto = "Participo", modifier = Modifier.weight(1f), enabled = enviando == null,
                                onClick = { responder(f, true) })
                            BotonSecundario(texto = "No participo", modifier = Modifier.weight(1f), enabled = enviando == null,
                                onClick = { responder(f, false) })
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)); TituloSeccion(texto = "Calendario") }
            if (calendario.isEmpty()) {
                item {
                    Vacio(titulo = "Aún no tienes ferias", mensaje = "Cuando la administración te invite a una feria, la verás aquí.",
                        icono = { Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = TextoSecundario) })
                }
            }
            calendario.forEach { (mes, ferias) ->
                item(key = "mes_$mes") { Text(mes.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, color = TextoSecundario) }
                items(ferias, key = { "cal_" + it.feriaId }) { f -> FilaCalendario(f, enviando == null, onCambiar = { responder(f, it) }) }
            }
        }
    }
}

@Composable
private fun FilaCalendario(f: MiFeria, habilitado: Boolean, onCambiar: (Boolean) -> Unit) {
    val estado = f.estadoInvitacion
    Tarjeta {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.nombre, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                ChipTexto(estado.etiqueta, when (estado) {
                    EstadoInvitacion7.CONFIRMADA -> Color(0xFFDCFCE7)
                    EstadoInvitacion7.AGREGADA -> Color(0xFFDBEAFE)
                    EstadoInvitacion7.PENDIENTE -> Color(0xFFFEF9C3)
                    EstadoInvitacion7.RECHAZADA -> Color(0xFFFEE2E2)
                })
            }
            Text(textoFechas(f), color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
            if (f.ubicacion.isNotBlank()) Text("📍 ${f.ubicacion}", color = TextoSecundario, style = MaterialTheme.typography.bodySmall)
            if (estado == EstadoInvitacion7.CONFIRMADA) {
                BotonSecundario(texto = "Ya no puedo participar", enabled = habilitado, onClick = { onCambiar(false) })
            }
        }
    }
}

@Composable
private fun ChipTexto(texto: String, fondo: Color) {
    Surface(color = fondo, shape = RoundedCornerShape(50), modifier = Modifier.semantics { contentDescription = "Estado: $texto" }) {
        Text(texto, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

private fun textoFechas(f: MiFeria): String {
    val fechas = if (!f.fechaFin.isNullOrBlank() && f.fechaFin != f.fechaInicio)
        "${ReglasVitrina.fechaLegible(f.fechaInicio)} al ${ReglasVitrina.fechaLegible(f.fechaFin)}" else ReglasVitrina.fechaLegible(f.fechaInicio)
    return fechas + if (f.horario.isNotBlank()) " · ${f.horario}" else ""
}
