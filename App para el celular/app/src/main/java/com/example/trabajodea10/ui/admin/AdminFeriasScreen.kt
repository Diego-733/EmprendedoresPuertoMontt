package com.example.trabajodea10.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trabajodea10.data.EstadoFeria
import com.example.trabajodea10.data.Feria
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.formatearMoneda
import com.example.trabajodea10.data.rangoDeFechas
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.ChipEstado
import com.example.trabajodea10.ui.componentes.ChipFiltro
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.componentes.colorDeFeria
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import androidx.compose.foundation.background
import com.example.trabajodea10.ui.theme.FondoLimpio
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.TextoSecundario
import com.example.trabajodea10.ui.theme.VerdeOk

/**
 * Entrega 7: la gestión de ferias (publicar, cancelar, invitar, checklist, avisos y
 * asistencia) se hace en la web del administrador. Aquí se ven las ferias reales del
 * servidor, sin los datos de ejemplo locales que antes podían contradecirlas.
 */
@Composable
fun PantallaAdminFerias(
    nav: ControladorNavegacion,
    avisar: (String) -> Unit
) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { com.example.trabajodea10.data.VitrinaStore.cargar() }
    val ferias = com.example.trabajodea10.data.VitrinaStore.ferias.sortedBy { it.fechaInicio ?: "9999" }
    Column(modifier = Modifier.fillMaxSize().background(FondoLimpio)) {
        BarraSuperior(titulo = "Ferias", subtitulo = "${ferias.size} en el servidor")
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Tarjeta {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("La gestión de ferias se hace en la web", fontWeight = FontWeight.Bold)
                        Text("Publicar, cancelar, invitar o agregar emprendedoras y asociaciones, el checklist, los avisos y la asistencia están en el Panel Admin de la web.",
                            style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        com.example.trabajodea10.ui.componentes.BotonPrimario(texto = "Abrir la web de administración", onClick = {
                            runCatching {
                                contexto.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://diego-733.github.io/EmprendedoresPuertoMontt/")))
                            }.onFailure { avisar("No hay un navegador para abrir la web.") }
                        })
                    }
                }
            }
            if (ferias.isEmpty()) item { Vacio(titulo = "Sin ferias", mensaje = "Aún no hay ferias publicadas en el servidor.") }
            items(ferias, key = { it.id }) { f ->
                Tarjeta(onClick = { nav.irA(Ruta.FeriaPublica(f.id)) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(f.nombre, fontWeight = FontWeight.SemiBold)
                        Text(com.example.trabajodea10.data.ReglasVitrina.rangoFechas(f) + " · " + when (f.estado) {
                            "CANCELADA" -> "Cancelada"; "FINALIZADA" -> "Finalizada"; "BORRADOR" -> "Borrador"; else -> "Publicada"
                        }, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        if (f.lugar.isNotBlank()) Text("📍 ${f.lugar}", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                    }
                }
            }
        }
    }
}

@Composable
fun TarjetaFeria(feria: Feria) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChipEstado(texto = feria.estado.etiqueta, color = colorDeFeria(feria.estado))
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (feria.precioEntrada == 0.0) "Entrada libre" else formatearMoneda(feria.precioEntrada),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = VerdeOk
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (feria.nombre.isBlank()) "Feria sin nombre" else feria.nombre,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Place,
                contentDescription = null,
                tint = TextoSecundario,
                modifier = Modifier.height(15.dp)
            )
            Text(
                text = "  ${feria.ubicacion.ifBlank { "Sin ubicación" }}",
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario
            )
        }
        Text(
            text = rangoDeFechas(feria.fechaInicio, feria.fechaFin),
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario
        )
        Spacer(modifier = Modifier.height(12.dp))
        val ocupados = feria.participaciones.size
        val proporcion = if (feria.cupos == 0) 0f else ocupados.toFloat() / feria.cupos
        LinearProgressIndicator(
            progress = { proporcion },
            modifier = Modifier.fillMaxWidth(),
            color = if (proporcion >= 1f) com.example.trabajodea10.ui.theme.NaranjaAlerta else VerdeOk
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$ocupados de ${feria.cupos} cupos ocupados",
            style = MaterialTheme.typography.bodySmall,
            color = TextoSecundario
        )
    }
}
