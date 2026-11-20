package com.example.trabajodea10.ui.emprendedora

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trabajodea10.data.EstadoInvitacion
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.fechaLegible
import com.example.trabajodea10.data.formatearMoneda
import com.example.trabajodea10.data.rangoDeFechas
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.ChipEstado
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.TarjetaMetrica
import com.example.trabajodea10.ui.componentes.TituloSeccion
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.componentes.colorDeInvitacion
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.FondoLimpio
import com.example.trabajodea10.ui.theme.NaranjaAlerta
import com.example.trabajodea10.ui.theme.NaranjaContenedor
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.RojoError
import com.example.trabajodea10.ui.theme.TextoSecundario
import com.example.trabajodea10.ui.theme.VerdeOk

@Composable
fun PantallaEmprendedoraInicio(
    nav: ControladorNavegacion,
    avisar: (String) -> Unit
) {
    val tienda = TramaStore.tienda
    val alertas = TramaStore.alertasStock()
    val nombreCorto = TramaStore.emprendedoraActual.nombre.split(" ").first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoLimpio)
    ) {
        BarraSuperior(
            titulo = "Hola, $nombreCorto",
            subtitulo = tienda.nombre,
            acciones = {
                IconButton(onClick = { avisar("No tenés notificaciones nuevas") }) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notificaciones", tint = NegroApple)
                }
                IconButton(onClick = { nav.irA(Ruta.Ajustes) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = NegroApple)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaMetrica(
                    titulo = "Ventas del mes",
                    valor = formatearMoneda(TramaStore.ventasDelMes()),
                    icono = Icons.Filled.PointOfSale,
                    colorIcono = VerdeOk,
                    modifier = Modifier.weight(1f)
                )
                TarjetaMetrica(
                    titulo = "Productos publicados",
                    valor = TramaStore.productosPublicados().toString(),
                    icono = Icons.Filled.Inventory2,
                    colorIcono = NegroApple,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaMetrica(
                    titulo = "Stock total",
                    valor = TramaStore.stockTotal().toString(),
                    icono = Icons.Filled.Layers,
                    colorIcono = NaranjaAlerta,
                    modifier = Modifier.weight(1f)
                )
                TarjetaMetrica(
                    titulo = "Seguidores",
                    valor = TramaStore.seguidores().toString(),
                    icono = Icons.Filled.People,
                    colorIcono = NegroApple,
                    modifier = Modifier.weight(1f)
                )
            }

            if (alertas.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = NaranjaContenedor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = NaranjaAlerta,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${alertas.size} productos con stock bajo o agotado",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = alertas.take(3).joinToString(", ") { it.nombre }.let {
                                    if (alertas.size > 3) "$it…" else it
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextoSecundario,
                                maxLines = 2
                            )
                        }
                        TextButton(onClick = { nav.seleccionarTab(Ruta.EmpCatalogo) }) {
                            Text("Revisar", fontWeight = FontWeight.SemiBold, color = RojoError)
                        }
                    }
                }
            }

            // Entrega 7: la próxima feria viene del servidor (mis_ferias), no de datos de ejemplo.
            TituloSeccion(texto = "Próxima feria", accion = "Mis ferias", onAccion = { nav.seleccionarTab(Ruta.EmpFerias) })
            androidx.compose.runtime.LaunchedEffect(Unit) { com.example.trabajodea10.data.FeriasStore.cargarMisFerias() }
            val hoy7 = com.example.trabajodea10.data.ReglasFerias.fechaSantiago()
            val invitacionesPorResponder = com.example.trabajodea10.data.FeriasStore.misFerias.count {
                it.estadoInvitacion == com.example.trabajodea10.data.EstadoInvitacion7.PENDIENTE &&
                    com.example.trabajodea10.data.ReglasFerias.feriaActiva(it.estado, it.fechaInicio, it.fechaFin, hoy7)
            }
            val proxima7 = com.example.trabajodea10.data.ReglasFerias.calendario(com.example.trabajodea10.data.FeriasStore.misFerias.toList(), hoy7)
                .flatMap { it.second }.firstOrNull { it.estadoInvitacion.participa }
            if (invitacionesPorResponder > 0) {
                Tarjeta(onClick = { nav.seleccionarTab(Ruta.EmpFerias) }) {
                    Text(
                        text = "Tienes " + invitacionesPorResponder + (if (invitacionesPorResponder == 1) " invitación" else " invitaciones") + " por responder",
                        modifier = Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (proxima7 == null) {
                Vacio(
                    titulo = "No tienes ferias confirmadas",
                    mensaje = "Cuando confirmes una invitación, la verás aquí con la fecha y el lugar.",
                    icono = { Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = TextoSecundario) }
                )
            } else {
                Tarjeta(onClick = { nav.seleccionarTab(Ruta.EmpFerias) }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = proxima7.nombre, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = com.example.trabajodea10.data.ReglasVitrina.fechaLegible(proxima7.fechaInicio) +
                                (if (proxima7.horario.isNotBlank()) " · " + proxima7.horario else ""),
                            style = MaterialTheme.typography.bodySmall, color = TextoSecundario
                        )
                        if (proxima7.ubicacion.isNotBlank()) Text(text = "📍 " + proxima7.ubicacion, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = proxima7.estadoInvitacion.etiqueta, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            TituloSeccion(
                texto = "Últimas ventas",
                accion = "Registrar venta",
                onAccion = { nav.seleccionarTab(Ruta.EmpVenta) }
            )
            val ultimas = TramaStore.ultimasVentas(3)
            if (ultimas.isEmpty()) {
                Text(
                    text = "Aún no registras ventas este mes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario
                )
            } else {
                Tarjeta {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        ultimas.forEachIndexed { index, venta ->
                            if (index > 0) {
                                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = TramaStore.feriaDeLaVenta(venta.feriaId),
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${fechaLegible(venta.fecha)} · ${venta.medioPago.etiqueta}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextoSecundario
                                    )
                                }
                                Text(
                                    text = formatearMoneda(venta.total),
                                    fontWeight = FontWeight.SemiBold,
                                    color = VerdeOk
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
