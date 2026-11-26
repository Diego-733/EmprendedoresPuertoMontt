package com.example.trabajodea10.ui.admin

import androidx.core.net.toUri
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
    // Rediseño v2: agrupadas por momento (hoy, próximas, anteriores, canceladas) con bloque de fecha.
    val grupos = com.example.trabajodea10.data.ReglasAdmin.clasificarFerias(
        com.example.trabajodea10.data.VitrinaStore.ferias.toList(), com.example.trabajodea10.data.ReglasFerias.fechaSantiago())
    val total = com.example.trabajodea10.data.VitrinaStore.ferias.size
    Column(modifier = Modifier.fillMaxSize().background(FondoLimpio)) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                com.example.trabajodea10.ui.componentes.CabeceraMarca(
                    sobretitulo = "Calendario",
                    titulo = "Ferias",
                    subtitulo = "$total en el servidor · ${grupos.enCurso.size + grupos.proximas.size} por venir"
                ) {
                    com.example.trabajodea10.ui.componentes.FilaCifras(
                        grupos.enCurso.size.toString() to "Hoy",
                        grupos.proximas.size.toString() to "Próximas",
                        grupos.anteriores.size.toString() to "Anteriores"
                    )
                }
            }
            item {
                com.example.trabajodea10.ui.componentes.AvisoAccion(
                    "Crear, publicar, cancelar, invitar, el checklist, los avisos y la asistencia se hacen en el panel web.",
                    com.example.trabajodea10.ui.componentes.Tono.AZUL, Modifier.padding(horizontal = 16.dp),
                    accion = "Abrir web",
                    onAccion = {
                        runCatching {
                            contexto.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                URL_PANEL_WEB.toUri()))
                        }.onFailure { avisar("No hay un navegador para abrir la web.") }
                    }
                )
            }
            if (total == 0) item { Vacio(titulo = "Sin ferias", mensaje = "Aún no hay ferias publicadas en el servidor.") }
            listOf(
                Triple("Hoy", grupos.enCurso, true), Triple("Próximas", grupos.proximas, false),
                Triple("Anteriores", grupos.anteriores, false), Triple("Canceladas", grupos.canceladas, false)
            ).filter { it.second.isNotEmpty() }.forEach { (titulo, lista, hoy) ->
                item {
                    com.example.trabajodea10.ui.componentes.EncabezadoSeccion(
                        "$titulo (${lista.size})", Modifier.padding(horizontal = 16.dp))
                }
                item {
                    com.example.trabajodea10.ui.componentes.GrupoFilas(Modifier.padding(horizontal = 16.dp)) {
                        lista.forEachIndexed { i, f ->
                            if (i > 0) androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                            FilaFeria(f, enCurso = hoy) { nav.irA(Ruta.FeriaPublica(f.id)) }
                        }
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
