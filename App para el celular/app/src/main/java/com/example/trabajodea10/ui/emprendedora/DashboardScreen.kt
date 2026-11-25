package com.example.trabajodea10.ui.emprendedora

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trabajodea10.data.EstadoInvitacion7
import com.example.trabajodea10.data.FeriasStore
import com.example.trabajodea10.data.ReglasFerias
import com.example.trabajodea10.data.ReglasVitrina
import com.example.trabajodea10.data.TramaStore
import com.example.trabajodea10.data.fechaLegible
import com.example.trabajodea10.data.formatearMoneda
import com.example.trabajodea10.ui.componentes.BarraSuperior
import com.example.trabajodea10.ui.componentes.Tarjeta
import com.example.trabajodea10.ui.componentes.TarjetaMetrica
import com.example.trabajodea10.ui.componentes.TituloSeccion
import com.example.trabajodea10.ui.componentes.Vacio
import com.example.trabajodea10.ui.navegacion.ControladorNavegacion
import com.example.trabajodea10.ui.navegacion.Ruta
import com.example.trabajodea10.ui.theme.BlancoPuro
import com.example.trabajodea10.ui.theme.EstadoAcaboFondo
import com.example.trabajodea10.ui.theme.EstadoAcaboTexto
import com.example.trabajodea10.ui.theme.EstadoPendFondo
import com.example.trabajodea10.ui.theme.EstadoPendTexto
import com.example.trabajodea10.ui.theme.FondoLimpio
import com.example.trabajodea10.ui.theme.Musgo
import com.example.trabajodea10.ui.theme.NegroApple
import com.example.trabajodea10.ui.theme.TextoSecundario
import com.example.trabajodea10.ui.theme.VerdeOk

/**
 * Inicio de la emprendedora. Pensado para la feria: con el celular en una mano, lo primero es
 * lo que espera respuesta (invitaciones) y las dos tareas de todos los días con texto visible:
 * «Registrar una venta» y «Mis productos». El resumen del mes va al final.
 */
@Composable
fun PantallaEmprendedoraInicio(
    nav: ControladorNavegacion,
    avisar: (String) -> Unit
) {
    val tienda = TramaStore.tienda
    val alertas = TramaStore.alertasStock()
    // Nombre del perfil de la cuenta con sesión (antes salía siempre «Camila», de los datos de ejemplo).
    val saludo = com.example.trabajodea10.data.SesionManager.saludo

    LaunchedEffect(Unit) { FeriasStore.cargarMisFerias() }
    val hoy = ReglasFerias.fechaSantiago()
    val porResponder = FeriasStore.misFerias.filter {
        it.estadoInvitacion == EstadoInvitacion7.PENDIENTE &&
            ReglasFerias.feriaActiva(it.estado, it.fechaInicio, it.fechaFin, hoy)
    }
    val proxima = ReglasFerias.calendario(FeriasStore.misFerias.toList(), hoy)
        .flatMap { it.second }.firstOrNull { it.estadoInvitacion.participa }

    Column(modifier = Modifier.fillMaxSize().background(FondoLimpio)) {
        BarraSuperior(
            titulo = saludo,
            subtitulo = tienda.nombre,
            acciones = {
                IconButton(onClick = { nav.irA(Ruta.Ajustes) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes y mi cuenta", tint = NegroApple)
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
            // 1. Lo que espera respuesta
            if (porResponder.isNotEmpty()) {
                AvisoConAccion(
                    texto = if (porResponder.size == 1) "Te invitaron a «${porResponder.first().nombre}». Falta tu respuesta."
                    else "Tienes ${porResponder.size} invitaciones a ferias por responder.",
                    accion = "Responder",
                    fondo = EstadoPendFondo, colorTexto = EstadoPendTexto,
                    onAccion = { nav.seleccionarTab(Ruta.EmpFerias) }
                )
            }

            // 2. Las dos tareas de todos los días
            BotonTarea(
                titulo = "Registrar una venta",
                detalle = "Descuenta lo que vendiste",
                icono = Icons.Filled.PointOfSale,
                principal = true,
                onClick = { nav.seleccionarTab(Ruta.EmpVenta) }
            )
            BotonTarea(
                titulo = "Mis productos",
                detalle = "Cuántos tengo, «Se acabó» y quién los ve",
                icono = Icons.Filled.Inventory2,
                principal = false,
                onClick = { nav.seleccionarTab(Ruta.EmpCatalogo) }
            )

            // 3. Lo que conviene revisar
            if (alertas.isNotEmpty()) {
                val acabados = alertas.filter { it.agotado }
                AvisoConAccion(
                    texto = if (acabados.isNotEmpty()) "Se acabó: " + acabados.take(3).joinToString(", ") { it.nombre } + (if (acabados.size > 3) "…" else "")
                    else "Quedan pocos: " + alertas.take(3).joinToString(", ") { it.nombre + " (" + it.stockTotal + ")" },
                    accion = "Revisar",
                    fondo = if (acabados.isNotEmpty()) EstadoAcaboFondo else EstadoPendFondo,
                    colorTexto = if (acabados.isNotEmpty()) EstadoAcaboTexto else EstadoPendTexto,
                    icono = Icons.Filled.Warning,
                    onAccion = { nav.seleccionarTab(Ruta.EmpCatalogo) }
                )
            }

            // 4. Próxima feria confirmada (del servidor)
            TituloSeccion(texto = "Próxima feria", accion = "Mis ferias", onAccion = { nav.seleccionarTab(Ruta.EmpFerias) })
            if (proxima == null) {
                Vacio(
                    titulo = "No tienes ferias confirmadas",
                    mensaje = "Cuando confirmes una invitación, la verás aquí con la fecha y el lugar.",
                    icono = { Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = TextoSecundario) }
                )
            } else {
                Tarjeta(onClick = { nav.seleccionarTab(Ruta.EmpFerias) }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = proxima.nombre, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ReglasVitrina.fechaLegible(proxima.fechaInicio) + (if (proxima.horario.isNotBlank()) " · " + proxima.horario else ""),
                            style = MaterialTheme.typography.bodyMedium, color = TextoSecundario
                        )
                        if (proxima.ubicacion.isNotBlank()) Text(text = "📍 " + proxima.ubicacion, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = proxima.estadoInvitacion.etiqueta, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = Musgo)
                    }
                }
            }

            // 5. Últimas ventas
            TituloSeccion(texto = "Últimas ventas", accion = "Ver historial", onAccion = { nav.irA(Ruta.HistorialVentas) })
            val ultimas = TramaStore.ultimasVentas(3)
            if (ultimas.isEmpty()) {
                Text(text = "Aún no registras ventas este mes.", style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
            } else {
                Tarjeta {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        ultimas.forEachIndexed { index, venta ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = TramaStore.feriaDeLaVenta(venta.feriaId), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = "${fechaLegible(venta.fecha)} · ${venta.medioPago.etiqueta}", style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                                }
                                Text(text = formatearMoneda(venta.total), fontWeight = FontWeight.SemiBold, color = VerdeOk)
                            }
                        }
                    }
                }
            }

            // 6. Resumen del mes (al final: se mira de vez en cuando)
            TituloSeccion(texto = "Resumen del mes")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaMetrica(titulo = "Ventas del mes", valor = formatearMoneda(TramaStore.ventasDelMes()), icono = Icons.Filled.PointOfSale, colorIcono = VerdeOk, modifier = Modifier.weight(1f))
                TarjetaMetrica(titulo = "Productos a la vista", valor = TramaStore.productosPublicados().toString(), icono = Icons.Filled.Inventory2, colorIcono = Musgo, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaMetrica(titulo = "Unidades disponibles", valor = TramaStore.stockTotal().toString(), icono = Icons.Filled.Layers, colorIcono = EstadoPendTexto, modifier = Modifier.weight(1f))
                TarjetaMetrica(titulo = "Seguidores", valor = TramaStore.seguidores().toString(), icono = Icons.Filled.People, colorIcono = Musgo, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Botón grande de una tarea diaria: texto visible, al menos 72 dp, crece con la letra del celular. */
@Composable
private fun BotonTarea(titulo: String, detalle: String, icono: ImageVector, principal: Boolean, onClick: () -> Unit) {
    val contenido: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icono, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(detalle, fontSize = 14.sp)
            }
        }
    }
    val modificador = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    if (principal) {
        Button(
            onClick = onClick, modifier = modificador, shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Musgo, contentColor = BlancoPuro),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        ) { contenido() }
    } else {
        OutlinedButton(
            onClick = onClick, modifier = modificador, shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.5.dp, Musgo),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = BlancoPuro, contentColor = Musgo),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        ) { contenido() }
    }
}

/** Aviso con texto y una acción con nombre (no solo color). */
@Composable
private fun AvisoConAccion(
    texto: String, accion: String, fondo: androidx.compose.ui.graphics.Color, colorTexto: androidx.compose.ui.graphics.Color,
    icono: ImageVector? = null, onAccion: () -> Unit
) {
    Surface(shape = RoundedCornerShape(18.dp), color = fondo, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icono != null) {
                Icon(icono, contentDescription = null, tint = colorTexto, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(text = texto, color = colorTexto, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onAccion, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(accion, fontWeight = FontWeight.Bold, color = colorTexto, fontSize = 16.sp)
            }
        }
    }
}
